# 오늘의 흐름(메모 작성) 기능 구현 계획서

## 1. 개요

### 1.1 기능 설명
"오늘의 흐름"은 사용자가 독서를 진행하며 실시간으로 메모를 작성하고 관리하는 핵심 기능입니다. 
하루 동안의 메모는 기본적으로 “세션 그룹화 방식(독서 세션 순서)”으로 표시되며, 사용자는 필요 시 책별/태그별 그룹화를 선택할 수 있습니다. 
모든 정렬의 최하위 기준은 항상 시간 순(메모 작성 시작 시간 기준)입니다.

**날짜 기반 페이지 분리**
- 실제 시간을 반영하여 날짜가 바뀌면 (자정이 지나면), 이전 날짜의 메모 작성까지를 저장하고 완전히 새로운 페이지를 보여줍니다.
- 각 날짜는 완전히 독립적인 "바인더 노트"로 취급되며, 날짜가 바뀌면 새로운 바인더 노트를 사용자에게 제공하는 것처럼 동작합니다.
- 프론트엔드에서 날짜 변경을 감지하여 자동으로 해당 날짜의 메모만 조회하고 표시합니다.
- 백엔드는 `memo_start_time`의 날짜 부분을 기준으로 메모를 필터링하여 제공합니다.

**자정 경과 시 메모 저장 및 표시 로직**
- 날짜 판단 기준: 사용자가 메모 작성을 시작한 시간(`memo_start_time`)을 기준으로 날짜를 판단합니다.
- 날짜 변경 감지: 날짜가 변경되면 (자정 경과) 프론트엔드에서 자동으로 새로운 날짜의 빈 바인더 노트를 표시합니다.
- 이전 날짜의 메모는 이미 저장된 상태로 유지되며, 새로운 날짜에는 완전히 빈 페이지가 표시됩니다.

**참고**: `memo_start_time`과 `created_at`의 차이 및 자정 경과 시 처리 상세 내용은 섹션 9.1.5를 참조하세요. 

#### 1.1.1 핵심 구조 및 특징

**메모 수 제한**
- 제한 없음. 사용자는 페이지당 작성할 수 있는 메모 개수에 구애받지 않고 자유롭게 기록합니다.

**레이아웃**
- 메모 내용의 **실제 용량(길이)**에 따라 화면에 출력되는 메모의 개수와 높이가 동적으로 결정됩니다.

**섹션 구성**
- 한 페이지는 중앙의 긴 가로선(세로선)을 기준으로 좌측 섹션과 우측 섹션으로 나뉩니다.

**자동 배치**
- 메모는 좌측 섹션 상단에서 시작하여 상 → 하로 채워지며, 좌측이 가득 차면 자동으로 우측 섹션 상단으로 넘어가 이어서 채워집니다.

**재배치**
- 메모 작성 순서가 곧 기록 순서가 되므로, 메모 재배치 기능은 없습니다.

#### 1.1.2 메모 정렬 및 그룹화 (하이브리드 정렬 체계)

모든 정렬 모드에서 최하위 정렬 기준은 항상 “시간 순(메모 작성 시작 시간, 오름차순)”입니다.

1) 기본 정렬: 세션 그룹화 방식 (Default: SESSION)
- 목적: 병렬 독서의 실제 흐름을 그대로 기록/표시
- 1차 기준: 독서 세션 순서(시간 흐름에 따라 사용자가 실제로 책을 전환한 순서대로 세션 그룹 생성)
- 2차 기준: 태그 그룹화(선택 시) — 각 세션 그룹 내부에서 “선택된 태그 대분류(유형/주제)”의 **대표 태그(1개)**를 기준으로 하위 그룹화 (대표 태그 결정 규칙은 12.2.2 참조)
- 3차 기준: 시간 순(각 태그 그룹 또는 세션 그룹 내부에서 메모는 시간 순 정렬)
- 구조: 세션 그룹(시간순 배열) -> [선택] 태그 그룹 -> 메모(시간순)
- 태그가 없는 메모: "기타" 그룹으로 포함되며, 세션 내부에서 시간 순으로 표시
- 태그 대분류 선택: 프론트에서 사용자가 대분류(예: 유형 vs 주제)를 선택하며, 백엔드 API에 `tagCategory` 파라미터로 전달됨. 해당 대분류 내 최대 8개 태그 중 실제로 붙은 태그 기준으로 그룹화됨

2) 보조 정렬 1: 책별 그룹화 (BOOK)
- 목적: 특정 책의 하루 전체 메모를 한눈에 보기 위함
- 1차 기준: 책별 그룹화 (예: 책 A → 책 B)
  - 책 그룹의 배치 순서는 “해당 날짜에 첫 메모가 작성된 시간”을 기준으로 결정
- 2차 기준: 시간 순(각 책 그룹 내부에서 세션 구분 없이 작성된 시간 순으로 정렬)
- 구조: 책 그룹 -> 메모(시간순)

3) 보조 정렬 2: 태그별 그룹화 (TAG)
- 목적: 메모 태그 기준으로, 태그별로 전체를 모아보기 위함
- 기본 동작: 태그별 그룹화를 선택하면 기본적으로 태그 대분류 중 1순위인 **TYPE(유형)**에 속하는 태그별로 그룹화됩니다.
- 태그 대분류 전환: UI에서 스위치나 칩스 등의 컴포넌트를 통해 **TOPIC(주제)** 대분류로 선택하여 전환할 수 있습니다. 사용자가 대분류를 선택하면, 프론트엔드에서 백엔드 API에 `tagCategory` 파라미터로 전달되며, 백엔드에서 해당 대분류를 기준으로 대표 태그를 결정하고 그룹화를 수행합니다.
- 1차 기준: "선택된 태그 대분류(유형/주제)" 내의 **대표 태그(1개)**별 그룹화 (태그 카탈로그의 `sort_order` 오름차순, "기타(태그 없음)" 그룹은 항상 맨 마지막)
- 2차 기준: 책별 그룹화 (각 태그 그룹 내부에서 출처인 책에 따라 다시 묶음)
- 3차 기준: 시간 순(각 책별 하위 그룹 내부에서 메모 작성 시간 순으로 정렬)
- 구조: 태그 그룹(대표 태그 기준) -> 책 그룹 -> 메모(시간순)
- 효과: 특정 태그에 관련된 모든 메모를 한데 모으되, 각 태그 그룹 내에서 책별로 구분하여 메모의 맥락(어느 책에서 나온 생각인지)을 명확히 보존함

#### 1.1.3 페이지 전환 UX (권장 사항)

**수평 슬라이딩 방식**
- 가독성을 높이는 한 페이지 단위 표시를 위해 수평 슬라이딩(Horizontal Sliding) 방식을 권장합니다.

**모션**
- 사용자가 다음 페이지로 이동할 때, 현재 페이지가 왼쪽으로 사라지고 새 페이지가 오른쪽에서 들어오는 부드러운 슬라이드 모션을 사용합니다.

**목적**
- 사용자가 페이지가 연결되어 있음을 인지하고 **기록의 공간감(바인더 노트 느낌)**을 유지하게 합니다.

**좌/우 조화**
- 좌/우 섹션을 구분하는 **중앙선(제본 영역)**의 시각적 요소를 강조하여, 페이지가 넘어갈 때 중앙선을 중심으로 전환되는 듯한 인상을 연출할 수 있습니다.

**참고**: 노션 문서 (https://www.notion.so/2994a8c85009811497c9cde5714029ed?p=29c4a8c8500980d29a14eb07ba876688&pm=s)

### 1.2 주요 요구사항
- **메모 작성**: 메모 시작 시간, 페이지 수, 메모 내용(필수), 메모 분류 태그
  - 사용자가 '오늘의 흐름'에서 해당 날짜에 대한 첫 메모를 작성하려고 할 때, 먼저 어떤 책에 메모를 작성할 것인지 선택해야 합니다.
  - 책 선택 버튼을 클릭하면 내 서재에 저장된 책들 중 `Finished`(완독) 카테고리를 제외한 나머지 카테고리(예: `ToRead`, `Reading`, `AlmostFinished`)의 책들이 목록으로 표시됩니다.
  - 사용자가 목록에서 특정 책을 선택하면, 오늘의 흐름 화면 상단 제목 영역에 해당 책의 도서명과 저자명이 함께 표시되고, 그 아래에 메모 입력 영역이 활성화되어 해당 책에 대한 메모를 바로 작성할 수 있습니다.
- **메모 수 제한**: 페이지당 메모 개수 제한 없음 (자유롭게 기록 가능)
- **메모 저장 조건**: 내용 없으면 저장 불가능
- **기본 정렬**: 세션 그룹화(SESSION) — 독서 세션 순서로 그룹을 구성하고, 각 그룹 내부는 **메모가 작성된 시간(memo_start_time)** 기준 **시간순(오름차순)**
- **정렬 기능**: SESSION(기본) / BOOK / TAG
- **메모 관리**: 작성, 삭제, 수정, 정렬 변경 (재배치 기능 없음 - 작성 순서가 기록 순서)
  - **메모 삭제**: 사용자가 '오늘의 흐름'에서 작성한 메모 중 특정 메모를 선택해서 삭제하면, memo DB에 저장된 해당 메모의 record는 물리적으로 삭제됩니다.
  - **메모 수정**: 사용자가 '오늘의 흐름'에서 작성한 메모 중 특정 메모를 선택해서 수정하면, memo DB에 저장된 해당 메모의 record가 수정됩니다. 사용자가 수정할 수 있는 항목은 다음과 같습니다:
    - 메모 내용(`content`)
    - 메모에 지정한 태그의 종류 변경 (태그 추가, 수정, 삭제)
- **메모 태그**: 
  - 각 메모에 하나 이상의 태그를 설정할 수 있음
  - 태그는 메모 분류 카테고리로 사용됨
  - 메모 정렬 변경 시 태그를 기준으로 필터링 가능
- **날짜 기반 페이지 분리**: 실제 시간을 반영하여 날짜가 바뀌면 (자정이 지나면) 자동으로 해당 날짜의 메모만 조회하고 완전히 새로운 페이지를 표시합니다. 각 날짜는 완전히 독립적인 "바인더 노트"로 취급됩니다.
- **과거 기록 조회**: 독서 캘린더와 연동하여 특정 날짜의 오늘의 흐름 조회 및 불러오기
- **책 덮기**: 독서 활동 종료 시 마지막으로 읽은 페이지 수를 기록하고 독서 진행률을 업데이트하는 기능
  - 마지막으로 읽은 페이지 수를 `user_books.reading_progress`에 기록
  - 독서 진행률 업데이트 (전체 페이지 수 대비 퍼센티지로 계산)
  - 독서 진행률에 따라 카테고리 자동 변경 (BookService 연동)
  - 오늘의 흐름에서 해당 책의 메모가 섹션으로 구분되어 표시됨
  - 상세 기능 설명은 섹션 15를 참조하세요
- **특정 책 필터링**: 오늘의 흐름에서 특정 책의 메모만 확인 가능

**참고**: 태그 관련 상세 사항은 노션 문서 (https://www.notion.so/2994a8c8500981999b61ed46802fabbf) 참조

### 1.3 다른 기능과의 관계

#### 1.3.1 내 서재 (UserShelfBook)와의 관계
- **외래키 관계**: `memo.book_id`는 `user_books.id`를 참조
- **독서 진행률 연동**: 책 덮기 시 `user_books.reading_progress` 업데이트
- **카테고리 자동 변경**: 독서 진행률에 따라 `user_books.category` 자동 변경 가능
  - 카테고리 변경 기준은 섹션 15.4.2 데이터베이스 업데이트의 "카테고리 변경 기준 (예시)"를 참조하세요.
- **책 삭제 시**: 사용자가 특정 책을 선택하여 삭제하면, 해당 책의 데이터(`user_books`)와 해당 책에 작성된 메모의 데이터(`memo`)가 함께 전부 삭제됨 (`ON DELETE CASCADE`)
- **참고**: `user_books.memo` 컬럼은 엔티티에 정의되어 있으나 현재 사용되지 않으며, 메모 기능은 `memo` 테이블을 통해 구현됩니다

**참고**: 책 덮기 기능 상세 사항은 노션 문서 (https://www.notion.so/29d4a8c85009803aa90df9f6bdbf3568) 및 섹션 14 참조

#### 1.3.2 독서 캘린더와의 관계
- **과거 기록 조회**: 독서 캘린더에서 날짜 선택 시 해당 날짜의 오늘의 흐름 조회
  - 상세 기능 설명은 섹션 14를 참조하세요
- **완독 책 표시**: 완독한 책의 표지가 캘린더에 표시 (메모 작성과는 별개, 프론트에서 구현)
- **날짜 기반 네비게이션**: 캘린더를 통해 과거 날짜의 독서 활동을 쉽게 탐색 가능

**참고**: 과거의 오늘의 흐름 기능 상세 사항은 노션 문서 (https://www.notion.so/29c4a8c850098058892bc37ed7f6f68a) 및 섹션 14 참조

#### 1.3.3 책의 흐름 기능과의 관계
- **월별 책 목록 조회**: 실제 시간을 반영한 월별로, 메모가 작성된 적이 있는 책들의 목록을 사용자에게 보여줍니다.
- **내 서재 책 목록 조회 (검색 기준)**: 월별 목록에 표시되지 않는 오래된 책의 메모도 조회할 수 있도록, 내 서재에 등록된 모든 책들의 목록 또한 선택 기준으로 제공합니다.
- **책 선택 및 메모 조회**: 사용자가 월별 책 목록 또는 내 서재 책 목록 중 하나를 선택하면, 해당 책의 모든 메모를 오늘의 흐름 형식으로 조회할 수 있습니다.
- **전체 메모 조회**: 선택한 책의 모든 메모를 오늘의 흐름 형식으로 조회 (날짜 제한 없음)

#### 1.3.4 특정 책의 메모만 확인 기능과의 관계
- **오늘의 흐름에서 필터링**: 오늘의 흐름 화면에서 특정 책을 선택하면 해당 책의 메모만 표시됩니다.
  - 상세 기능 설명은 섹션 13을 참조하세요

**참고**: 특정 책의 메모만 확인 기능 상세 사항은 노션 문서 (https://www.notion.so/29c4a8c8500980fc932cd55dcaa28ab1) 및 섹션 13 참조

---

## 2. 데이터베이스 설계

### 2.1 데이터베이스 테이블 설계

#### Memo 테이블
```sql
CREATE TABLE memo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,  -- UserShelfBook의 id를 참조
    page_number INT NOT NULL,  -- 메모 작성 시점의 SESSION 모드 기준 초기 위치 (정렬 방식 변경 시에도 변경하지 않는 메타데이터)
    content TEXT NOT NULL,  -- 메모 내용 (필수)
    memo_start_time TIMESTAMP NOT NULL,  -- 메모가 작성된 시간 (사용자가 메모 작성을 시작한 시간, 타임라인 정렬 기준)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DB 레코드 생성 시간 (감사 목적)
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (book_id) REFERENCES user_books(id) ON DELETE CASCADE,
    INDEX idx_memo_user_book (user_id, book_id),
    INDEX idx_memo_created_at (created_at),
    INDEX idx_memo_page_number (book_id, page_number),
    INDEX idx_memo_memo_start_time (memo_start_time)  -- 타임라인 정렬용
);
```

#### Tags 테이블 (태그 마스터)
```sql
CREATE TABLE tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category ENUM('TYPE', 'TOPIC') NOT NULL,  -- 태그 대분류 (유형/주제)
    code VARCHAR(50) NOT NULL UNIQUE,           -- 캐논컬 키 (예: 'impressive-quote')
    sort_order INT NOT NULL,                   -- 정렬 순서 (가나다순)
    is_active BOOLEAN NOT NULL DEFAULT TRUE,   -- 활성화 상태
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tags_category (category),
    INDEX idx_tags_code (code),
    INDEX idx_tags_category_sort (category, sort_order)
);
```

#### Memo-Tag 중간 테이블 (Many-to-Many 관계)
```sql
CREATE TABLE memo_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    memo_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (memo_id) REFERENCES memo(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE,
    UNIQUE KEY uk_memo_tag (memo_id, tag_id),  -- 중복 방지
    INDEX idx_memo_tags_memo (memo_id),
    INDEX idx_memo_tags_tag (tag_id)
);
```

**설계 결정 근거:**
- 태그별 메모 조회 및 그룹화가 핵심 기능이므로 Many-to-Many 관계 채택
- 태그별 조회 성능 최적화 (인덱스 활용)
- 태그 정규화로 데이터 무결성 확보
- 상세한 설계 결정은 `TAG_STORAGE_DESIGN_DECISION.md` 참조

### 2.2 제약사항
- **메모 수 제한**: 페이지당 메모 개수 제한 없음 (사용자가 자유롭게 기록 가능)
- **메모 내용 필수**: DB 레벨에서 NOT NULL 제약 (내용 없으면 저장 불가능)
- **책 참조 필수**: 메모는 반드시 `user_books` 테이블의 책에 연결되어야 함
- **사용자 소유권**: 메모는 작성한 사용자만 수정/삭제 가능
- **소프트 삭제 고려**: 현재는 물리 삭제, 필요시 `deleted_at` 컬럼 추가 가능

### 2.3 테이블 간 관계도

```
users (1) ────< (N) user_books (1) ────< (N) memo
                              │
                              │ (1)
                              │
                              ▼
                            books (1)
                              
memo (N) ────< (N) memo_tags (N) ────> (N) tags
```

**관계 설명:**
- `memo.user_id` → `users.id` (사용자)
- `memo.book_id` → `user_books.id` (사용자의 책)
- `memo` ↔ `tags` (Many-to-Many, `memo_tags` 중간 테이블)
- `user_books.book_id` → `books.id` (책 정보)

### 2.4 인덱스 전략

**memo 테이블:**
- `idx_memo_user_book (user_id, book_id)`: 사용자별 책별 메모 조회
- `idx_memo_created_at (created_at)`: DB 레코드 생성 시간 기준 조회 (감사 목적)
- `idx_memo_page_number (book_id, page_number)`: 페이지별 메모 조회 (참고: page_number는 SESSION 모드 기준 초기 위치 메타데이터)
- `idx_memo_memo_start_time (memo_start_time)`: **메모가 작성된 시간 기준 정렬 및 날짜 필터링 (기본 정렬 필수)**
  - 기본 정렬: 시간순(오름차순) - 가장 오래된 메모가 위에, 가장 최근 메모가 아래에 표시
  - 날짜 필터링: 날짜 범위 쿼리(`>= startOfDay AND < startOfNextDay`)로 인덱스를 직접 활용하여 성능 최적화
  - `memo_start_time`은 사용자가 실제로 메모를 작성한 시간을 나타내며, `created_at`과는 다름
  - 타임존 고려: 현재는 서버 타임존 기준, 향후 사용자 타임존 지원 가능 (섹션 14.4.2 참조)

**memo_tags 테이블:**
- `idx_memo_tags_memo (memo_id)`: 메모별 태그 조회
- `idx_memo_tags_tag (tag_id)`: 태그별 메모 조회

**tags 테이블:**
- `idx_tags_code (code)`: 태그 코드 검색
- `idx_tags_category (category)`: 태그 대분류별 검색
- `idx_tags_category_sort (category, sort_order)`: 태그 대분류별 정렬 순서 검색

---

## 3. 엔티티 설계

### 3.1 엔티티 설계

#### Tag 엔티티
```java
package com.readingtracker.dbms.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tags")
@EntityListeners(AuditingEntityListener.class)
public class Tag {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private TagCategory category;  // TYPE 또는 TOPIC
    
    @Column(name = "code", unique = true, nullable = false, length = 50)
    private String code;  // 캐논컬 키 (예: 'impressive-quote')
    
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;  // 정렬 순서 (가나다순)
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;  // 활성화 상태
    
    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    private List<Memo> memos = new ArrayList<>();
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Constructors
    public Tag() {}
    
    public Tag(TagCategory category, String code, Integer sortOrder) {
        this.category = category;
        this.code = code;
        this.sortOrder = sortOrder;
        this.isActive = true;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public TagCategory getCategory() { return category; }
    public void setCategory(TagCategory category) { this.category = category; }
    
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public List<Memo> getMemos() { return memos; }
    public void setMemos(List<Memo> memos) { this.memos = memos; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

// TagCategory Enum
enum TagCategory {
    TYPE,   // 유형
    TOPIC   // 주제
}
```

#### Memo 엔티티
```java
package com.readingtracker.dbms.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "memo")
@EntityListeners(AuditingEntityListener.class)
public class Memo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private UserShelfBook userShelfBook;  // User_Books 테이블 참조
    
    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;  // 메모 작성 시점의 SESSION 모드 기준 초기 위치 (정렬 방식 변경 시에도 변경하지 않는 메타데이터)
    
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "memo_tags",
        joinColumns = @JoinColumn(name = "memo_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags = new ArrayList<>();
    
    /**
     * 메모가 작성된 시간 (사용자가 메모 작성을 시작한 시간)
     * - 타임라인 정렬의 기준으로 사용됨
     * - 클라이언트에서 전송하거나 서버에서 자동 생성 가능
     * - 과거 시간도 설정 가능 (과거의 오늘의 흐름 기록용)
     */
    @Column(name = "memo_start_time", nullable = false)
    private LocalDateTime memoStartTime;
    
    /**
     * DB 레코드 생성 시간 (기술적 메타데이터)
     * - 서버에서 자동으로 설정 (DEFAULT CURRENT_TIMESTAMP)
     * - 언제 DB에 저장되었는지를 추적하는 감사(audit) 목적
     * - 메모 수정 시 변경되지 않음
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Constructors
    public Memo() {}
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    
    public UserShelfBook getUserShelfBook() { return userShelfBook; }
    public void setUserShelfBook(UserShelfBook userShelfBook) { 
        this.userShelfBook = userShelfBook; 
    }
    
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public List<Tag> getTags() { return tags; }
    public void setTags(List<Tag> tags) { this.tags = tags; }
    
    public LocalDateTime getMemoStartTime() { return memoStartTime; }
    public void setMemoStartTime(LocalDateTime memoStartTime) { 
        this.memoStartTime = memoStartTime; 
    }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    // Helper methods
    public Long getUserId() { return user != null ? user.getId() : null; }
    public Long getUserShelfBookId() { 
        return userShelfBook != null ? userShelfBook.getId() : null; 
    }
}
```

### 3.2 Repository 인터페이스

#### TagRepository
```java
package com.readingtracker.dbms.repository;

import com.readingtracker.dbms.entity.Tag;
import com.readingtracker.dbms.entity.TagCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByCode(String code);
    boolean existsByCode(String code);
    List<Tag> findByCategoryAndIsActiveTrueOrderBySortOrderAsc(TagCategory category);
    List<Tag> findByIsActiveTrueOrderBySortOrderAsc();
}
```

#### MemoRepository
```java
package com.readingtracker.dbms.repository;

import com.readingtracker.dbms.entity.Memo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface MemoRepository extends JpaRepository<Memo, Long> {
    
    // 특정 사용자의 특정 책에 대한 메모 조회 (페이지별)
    @Query("SELECT m FROM Memo m WHERE m.user.id = :userId " +
           "AND m.userShelfBook.id = :userShelfBookId " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByUserIdAndUserShelfBookIdOrderByMemoStartTimeAsc(
        @Param("userId") Long userId, 
        @Param("userShelfBookId") Long userShelfBookId
    );
    
    // 특정 사용자의 특정 책의 특정 페이지에 대한 메모 개수 조회
    @Query("SELECT COUNT(m) FROM Memo m WHERE m.user.id = :userId " +
           "AND m.userShelfBook.id = :userShelfBookId " +
           "AND m.pageNumber = :pageNumber")
    long countByUserIdAndUserShelfBookIdAndPageNumber(
        @Param("userId") Long userId, 
        @Param("userShelfBookId") Long userShelfBookId, 
        @Param("pageNumber") Integer pageNumber
    );
    
    // 특정 사용자의 특정 날짜의 메모 조회 (오늘의 흐름 - 시간순)
    // 태그별 그룹화를 위해 시간순으로만 정렬 (TAG 모드용)
    @Query("SELECT m FROM Memo m WHERE m.user.id = :userId " +
           "AND m.memoStartTime >= :startOfDay " +
           "AND m.memoStartTime < :startOfNextDay " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByUserIdAndDateOrderByMemoStartTimeAsc(
        @Param("userId") Long userId, 
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("startOfNextDay") LocalDateTime startOfNextDay
    );
    
    // 특정 사용자의 특정 날짜의 메모 조회 (오늘의 흐름 - 책별 그룹화)
    // 책별로 그룹화: book_id를 기준으로 먼저 정렬하고, 각 책 그룹 내에서 타임라인 순으로 정렬
    // 기본 정렬: 메모가 작성된 시간(memo_start_time) 기준 시간순(오름차순)
    // 가장 오래된 메모가 첫 번째, 가장 최근 메모가 마지막에 위치
    @Query("SELECT m FROM Memo m WHERE m.user.id = :userId " +
           "AND m.memoStartTime >= :startOfDay " +
           "AND m.memoStartTime < :startOfNextDay " +
           "ORDER BY m.userShelfBook.id ASC, m.memoStartTime ASC")
    List<Memo> findByUserIdAndDateOrderByBookAndMemoStartTimeAsc(
        @Param("userId") Long userId, 
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("startOfNextDay") LocalDateTime startOfNextDay
    );
    
    // 특정 사용자의 특정 날짜의 메모 조회 (태그별 정렬)
    // 태그별로 그룹화하고, 각 태그 그룹 내에서 타임라인 순으로 정렬
    @Query("SELECT m FROM Memo m " +
           "JOIN m.tags t " +
           "WHERE m.user.id = :userId " +
           "AND m.memoStartTime >= :startOfDay " +
           "AND m.memoStartTime < :startOfNextDay " +
           "ORDER BY t.sortOrder ASC, m.memoStartTime ASC")
    List<Memo> findByUserIdAndDateOrderByTagAndMemoStartTimeAsc(
        @Param("userId") Long userId, 
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("startOfNextDay") LocalDateTime startOfNextDay
    );
    
    // 특정 사용자의 모든 메모 조회 (타임라인 정렬)
    @Query("SELECT m FROM Memo m WHERE m.user.id = :userId " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByUserIdOrderByMemoStartTimeAsc(@Param("userId") Long userId);
    
    // 특정 사용자의 특정 기간 메모 조회
    @Query("SELECT m FROM Memo m WHERE m.user.id = :userId " +
           "AND m.memoStartTime BETWEEN :startDate AND :endDate " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    // 태그별 메모 조회
    @Query("SELECT m FROM Memo m " +
           "JOIN m.tags t " +
           "WHERE m.userShelfBook.id = :bookId AND t.code = :tagCode " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByBookIdAndTagCode(
        @Param("bookId") Long bookId, 
        @Param("tagCode") String tagCode
    );
    
    // 태그별로 그룹화된 메모 조회
    @Query("SELECT t.code, m FROM Memo m " +
           "JOIN m.tags t " +
           "WHERE m.userShelfBook.id = :bookId " +
           "ORDER BY t.sortOrder, m.memoStartTime ASC")
    List<Object[]> findMemosGroupedByTag(@Param("bookId") Long bookId);
    
    // 특정 날짜의 특정 책의 메모 조회
    @Query("SELECT m FROM Memo m WHERE m.user.id = :userId " +
           "AND m.userShelfBook.id = :userShelfBookId " +
           "AND m.memoStartTime >= :startOfDay " +
           "AND m.memoStartTime < :startOfNextDay " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByUserIdAndUserShelfBookIdAndDate(
        @Param("userId") Long userId,
        @Param("userShelfBookId") Long userShelfBookId,
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("startOfNextDay") LocalDateTime startOfNextDay
    );
    
    // 최근 기간 내에 메모가 작성된 책별 최신 메모 작성 시간 조회
    @Query("SELECT m.userShelfBook.id, MAX(m.memoStartTime) as lastMemoTime " +
           "FROM Memo m " +
           "WHERE m.user.id = :userId " +
           "AND m.memoStartTime >= :startDate " +
           "GROUP BY m.userShelfBook.id " +
           "ORDER BY MAX(m.memoStartTime) DESC")
    List<Object[]> findUserShelfBookIdsWithLastMemoTime(
        @Param("userId") Long userId,
        @Param("startDate") LocalDateTime startDate
    );
}
```

---

## 4. DTO 설계

### 4.1 Request DTOs

#### MemoCreateRequest
```java
package com.readingtracker.server.dto.requestDTO;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public class MemoCreateRequest {
    
    @NotNull(message = "책 ID는 필수입니다.")
    private Long userBookId;  // UserShelfBook의 id
    
    @NotNull(message = "페이지 수는 필수입니다.")
    @Min(value = 1, message = "페이지 수는 1 이상이어야 합니다.")
    private Integer pageNumber;  // 메모 작성 시점의 SESSION 모드 기준 초기 위치
    
    @NotBlank(message = "메모 내용은 필수입니다.")
    @Size(max = 5000, message = "메모 내용은 5000자를 초과할 수 없습니다.")
    private String content;
    
    private List<String> tags;  // 메모 분류 태그 리스트 (하나 이상의 태그 설정 가능)
    
    @NotNull(message = "메모 시작 시간은 필수입니다.")
    private LocalDateTime memoStartTime;
}
```

#### MemoUpdateRequest
```java
package com.readingtracker.server.dto.requestDTO;

import jakarta.validation.constraints.*;
import java.util.List;

public class MemoUpdateRequest {
    
    @Size(max = 5000, message = "메모 내용은 5000자를 초과할 수 없습니다.")
    private String content;
    
    private List<String> tags;
    
    // 참고: pageNumber는 메모 작성 시점의 시작 위치를 나타내는 메타데이터이므로 수정 불가
    // 메모 수정 시에도 원본 위치 정보를 보존해야 하며, UI 레이아웃은 프론트엔드에서 처리합니다.
}
```

#### CloseBookRequest (책 덮기)
```java
package com.readingtracker.server.dto.requestDTO;

import jakarta.validation.constraints.*;

public class CloseBookRequest {
    
    @NotNull(message = "마지막으로 읽은 페이지 수는 필수입니다.")
    @Min(value = 1, message = "페이지 수는 1 이상이어야 합니다.")
    private Integer lastReadPage;
}
```

### 4.2 Response DTOs

#### MemoResponse
```java
package com.readingtracker.server.dto.responseDTO;

import java.time.LocalDateTime;
import java.util.List;

public class MemoResponse {
    private Long id;
    private Long userBookId;
    private String bookTitle;
    private String bookIsbn;
    private Integer pageNumber;  // 메모 작성 시점의 SESSION 모드 기준 초기 위치 (참조용 메타데이터)
    private String content;
    private List<String> tags;
    private LocalDateTime memoStartTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### BookResponse (책 정보 응답)
```java
package com.readingtracker.server.dto.responseDTO;

import com.readingtracker.server.common.constant.BookCategory;
import java.time.LocalDateTime;

public class BookResponse {
    private Long id;  // UserShelfBook.id
    private String title;
    private String author;
    private String isbn;
    private BookCategory category;
    private Integer readingProgress;
    private LocalDateTime lastMemoTime;  // 최신 메모 작성 시간 (선택)
}
```

#### TagMemoGroup (태그별 메모 그룹)
```java
package com.readingtracker.server.dto.responseDTO;

import java.util.List;
import java.util.Map;

public class TagMemoGroup {
    private String tagCode;  // 태그 캐논컬 키 (프론트엔드에서 카탈로그를 참조하여 라벨 표시)
    private List<MemoResponse> memos;  // SESSION 모드: 타임라인 순으로 정렬됨
    private Map<Long, BookMemoGroup> memosByBook;  // TAG 모드: 태그 그룹 내부의 책별 하위 그룹
    private Integer memoCount;
}
```

#### BookMemoGroup (책별 메모 그룹)
```java
package com.readingtracker.server.dto.responseDTO;

import java.util.List;
import java.util.Map;

public class BookMemoGroup {
    private Long bookId;
    private String bookTitle;
    private String bookIsbn;
    private List<MemoResponse> memos;  // BOOK 모드: 책 그룹 내부의 메모(시간순)
    private Map<String, TagMemoGroup> memosByTag;  // TAG 모드: 책 그룹 내부의 태그별 하위 그룹
    private Integer memoCount;
    private String sortBy;  // "SESSION" | "BOOK" | "TAG"
}
```

#### TodayFlowResponse (오늘의 흐름)
```java
package com.readingtracker.server.dto.responseDTO;

import java.time.LocalDate;
import java.util.Map;

public class TodayFlowResponse {
    private LocalDate date;
    private Map<Long, BookMemoGroup> memosByBook;  // SESSION/BOOK 모드: 책별로 그룹화된 메모
    private Map<String, TagMemoGroup> memosByTag;  // TAG 모드: 태그별로 그룹화된 메모
    private Long totalMemoCount;
    private String sortBy;  // "SESSION" | "BOOK" | "TAG"
}
```

**참고:**
- 프론트엔드는 `sortBy` 값에 따라 데이터를 해석하고 화면을 구성합니다.
  - `SESSION`(기본): `memosByBook`을 사용하여 시간 흐름에 따라 사용자 독서 전환 시점별 "세션 그룹"을 구성하여 표시합니다. 서버 응답이 책별 Map 형태인 경우에도, 각 책의 메모를 시간축에 재배치하여 세션 단위를 UI에서 구성합니다.
  - `BOOK`: `memosByBook`의 각 `BookMemoGroup.memos`를 시간 순으로 렌더링합니다.
  - `TAG`: `memosByTag`를 사용하여 태그별 그룹을 렌더링합니다. 각 태그 그룹 내부에서 `TagMemoGroup.memosByBook`의 각 `BookMemoGroup.memos`를 시간 순으로 렌더링합니다. "기타(태그 없음)"는 항상 마지막에 표시합니다.
 - 모든 모드에서 각 그룹 내부 메모는 시간 순(오름차순)입니다.

---

## 5. Service 레이어 설계

### 5.1 MemoService

```java
package com.readingtracker.server.service;

import com.readingtracker.dbms.entity.Memo;
import com.readingtracker.dbms.entity.Tag;
import com.readingtracker.dbms.entity.TagCategory;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.dbms.repository.MemoRepository;
import com.readingtracker.dbms.repository.UserShelfBookRepository;
import com.readingtracker.server.service.BookService;
import com.readingtracker.server.dto.responseDTO.BookMemoGroup;
import com.readingtracker.server.dto.responseDTO.MemoResponse;
import com.readingtracker.server.dto.responseDTO.TagMemoGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class MemoService {
    
    @Autowired
    private MemoRepository memoRepository;
    
    @Autowired
    private UserShelfBookRepository userShelfBookRepository;
    
    @Autowired
    private BookService bookService;
    
    /**
     * 메모 작성
     * - 메모 내용 필수 검증
     * - 태그 자동 생성 및 연결
     * - 페이지당 메모 개수 제한 없음 (사용자가 자유롭게 기록 가능)
     * - pageNumber: 메모 작성 시점의 SESSION 모드 기준 초기 위치를 저장 (정렬 방식 변경 시에도 변경하지 않음)
     * 
     * 참고: ARCHITECTURE 원칙에 따라 Entity만 사용합니다.
     * RequestDTO → Entity 변환은 Mapper 계층에서 처리됩니다.
     */
    public Memo createMemo(User user, Memo memo) {
        // 1. UserShelfBook 소유권 확인
        UserShelfBook userShelfBook = memo.getUserShelfBook();
        if (userShelfBook == null) {
            throw new IllegalArgumentException("책을 찾을 수 없습니다.");
        }
        
        if (!userShelfBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 2. 태그 처리 (태그 코드 리스트를 Tag 엔티티 리스트로 변환)
        // 태그 코드는 이미 Mapper에서 처리되었거나, Service에서 추가 처리 필요 시 processTags 호출
        // 현재는 Mapper에서 태그 코드를 받아서 처리하거나, Service에서 처리
        // 여기서는 Entity의 태그가 이미 설정되어 있다고 가정 (Mapper에서 처리)
        
        // 3. Memo 엔티티 저장
        memo.setUser(user);
        return memoRepository.save(memo);
    }
    
    /**
     * 메모 수정
     * 
     * 참고: ARCHITECTURE 원칙에 따라 Entity만 사용합니다.
     * RequestDTO → Entity 변환은 Mapper 계층에서 처리됩니다.
     */
    public Memo updateMemo(User user, Long memoId, Memo memo) {
        // 1. 메모 조회 및 소유권 확인
        Memo existingMemo = memoRepository.findById(memoId)
            .orElseThrow(() -> new IllegalArgumentException("메모를 찾을 수 없습니다."));
        
        if (!existingMemo.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 2. 필드 업데이트 (content와 tags만 수정 가능)
        // 참고: pageNumber는 메모 작성 시점의 시작 위치를 나타내는 메타데이터이므로 수정 불가
        // Mapper에서 이미 변환된 Entity의 필드만 업데이트
        // Mapper의 updateMemoFromRequest에서 이미 필드가 업데이트되었으므로 여기서는 저장만 수행
        
        return memoRepository.save(existingMemo);
    }
    
    /**
     * 메모 삭제
     */
    public void deleteMemo(User user, Long memoId) {
        Memo memo = memoRepository.findById(memoId)
            .orElseThrow(() -> new IllegalArgumentException("메모를 찾을 수 없습니다."));
        
        if (!memo.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        memoRepository.delete(memo);
    }
    
    /**
     * 오늘의 흐름 조회 (책별 그룹화)
     * 
     * 기능:
     * - 오늘 날짜의 메모 조회 (기본값)
     * - 과거 날짜의 메모 조회 가능 (독서 캘린더 연동)
     * - 책별로 그룹화하여 반환
     * - 각 책 그룹 내에서 타임라인 순으로 정렬 (memo_start_time 기준)
     * 
     * 백엔드 책임:
     * - Repository: book_id를 기준으로 데이터를 조회
     * - Service: 데이터를 책별로 그룹화하는 최종 변환 담당
     * 
     * 참고: 노션 문서 (https://www.notion.so/29c4a8c850098058892bc37ed7f6f68a)
     */
    /**
     * 날짜 범위 계산 헬퍼 메서드
     * 
     * LocalDate를 LocalDateTime 범위로 변환합니다.
     * 향후 타임존 지원을 위해 구조화되어 있습니다.
     * 
     * 현재 구현: 서버 타임존 기준으로 처리
     * 향후 확장: 사용자 타임존 정보를 받아 해당 타임존 기준으로 계산 가능
     * 
     * @param date 조회할 날짜
     * @return 날짜 범위 (startOfDay, startOfNextDay)를 담은 배열
     */
    private LocalDateTime[] calculateDateRange(LocalDate date) {
        // 현재: 서버 타임존 기준으로 처리
        // 향후: 사용자 타임존을 받아 처리할 수 있도록 구조화
        // 예: ZoneId userTimeZone = getUserTimeZone(user);
        //     ZonedDateTime startOfDay = date.atStartOfDay(userTimeZone);
        //     return new LocalDateTime[] { 
        //         startOfDay.toLocalDateTime(), 
        //         startOfDay.plusDays(1).toLocalDateTime() 
        //     };
        
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime startOfNextDay = date.plusDays(1).atStartOfDay();
        return new LocalDateTime[] { startOfDay, startOfNextDay };
    }
    
    @Transactional(readOnly = true)
    public Map<Long, BookMemoGroup> getTodayFlowGroupedByBook(User user, LocalDate date) {
        LocalDateTime[] dateRange = calculateDateRange(date);
        List<Memo> memos = memoRepository.findByUserIdAndDateOrderByBookAndMemoStartTimeAsc(
            user.getId(), dateRange[0], dateRange[1]
        );
        
        // 책별로 그룹화
        return memos.stream()
            .collect(Collectors.groupingBy(
                m -> m.getUserShelfBook().getId(),
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    memoList -> {
                        BookMemoGroup group = new BookMemoGroup();
                        Memo firstMemo = memoList.get(0);
                        group.setBookId(firstMemo.getUserShelfBook().getId());
                        group.setBookTitle(firstMemo.getUserShelfBook().getBook().getTitle());
                        group.setBookIsbn(firstMemo.getUserShelfBook().getBook().getIsbn());
                        group.setMemos(memoMapper.toMemoResponseList(memoList));
                        group.setMemoCount(memoList.size());
                        return group;
                    }
                )
            ));
    }
    
    /**
     * 오늘의 흐름 조회 (태그별 그룹화)
     * 
     * 기능:
     * - 태그별로 그룹화하여 반환 (1차 그룹화)
     * - 각 태그 그룹 내부에서 책별로 다시 그룹화 (2차 그룹화)
     * - 각 책 그룹 내에서 타임라인 순으로 정렬 (3차 정렬)
     * 
     * 대표 태그 결정 로직:
     * - 대표 태그 결정 규칙은 섹션 12.2.2를 참조하세요.
     * - tagCategory에 따라 대표 태그 결정 우선순위가 변경됩니다 (기본값: TYPE).
     * 
     * 백엔드 책임:
     * - Repository: 날짜 기준으로 모든 메모를 조회
     * - Service: 태그별로 그룹화한 후, 각 태그 그룹 내부에서 책별로 다시 그룹화하는 최종 변환 담당
     * 
     * @param tagCategory 태그 대분류 (TYPE 또는 TOPIC). 선택된 대분류가 대표 태그 결정 시 1순위가 됨 (기본값: TYPE)
     * @return 태그별로 그룹화된 메모 (태그 그룹 -> 책 그룹 -> 메모 구조)
     */
    @Transactional(readOnly = true)
    public Map<String, TagMemoGroup> getTodayFlowGroupedByTag(User user, LocalDate date, TagCategory tagCategory) {
        // 날짜 기준으로 모든 메모를 시간순으로 조회
        LocalDateTime[] dateRange = calculateDateRange(date);
        List<Memo> memos = memoRepository.findByUserIdAndDateOrderByMemoStartTimeAsc(
            user.getId(), dateRange[0], dateRange[1]
        );
        
        // 대표 태그 결정 헬퍼 메서드
        // tagCategory에 따라 우선순위가 변경됨:
        // - tagCategory가 선택되면 해당 대분류가 1순위
        // - 선택된 대분류 내에서는 sort_order가 가장 작은 태그를 대표 태그로 사용
        Function<Memo, Tag> getRepresentativeTag = (memo) -> {
            if (memo.getTags().isEmpty()) {
                return null;  // 태그가 없으면 null 반환 (나중에 "etc" 처리)
            }
            
            // 선택된 대분류의 태그들을 먼저 찾기
            List<Tag> categoryTags = memo.getTags().stream()
                .filter(tag -> tag.getCategory() == tagCategory)
                .sorted(Comparator.comparing(Tag::getSortOrder))
                .collect(Collectors.toList());
            
            if (!categoryTags.isEmpty()) {
                return categoryTags.get(0);  // 선택된 대분류의 첫 번째 태그 (sort_order 최소)
            }
            
            // 선택된 대분류에 태그가 없으면 다른 대분류의 태그 찾기
            TagCategory otherCategory = (tagCategory == TagCategory.TYPE) 
                ? TagCategory.TOPIC 
                : TagCategory.TYPE;
            List<Tag> otherCategoryTags = memo.getTags().stream()
                .filter(tag -> tag.getCategory() == otherCategory)
                .sorted(Comparator.comparing(Tag::getSortOrder))
                .collect(Collectors.toList());
            
            if (!otherCategoryTags.isEmpty()) {
                return otherCategoryTags.get(0);  // 다른 대분류의 첫 번째 태그
            }
            
            return null;  // 태그가 있지만 대분류가 맞지 않는 경우 (이론적으로 발생하지 않아야 함)
        };
        
        // 1차 그룹화: 태그별로 그룹화
        Map<String, List<Memo>> memosByTagCode = new LinkedHashMap<>();
        
        for (Memo memo : memos) {
            Tag representativeTag = getRepresentativeTag.apply(memo);
            String tagCode = (representativeTag == null) ? "etc" : representativeTag.getCode();
            memosByTagCode.computeIfAbsent(tagCode, k -> new ArrayList<>()).add(memo);
        }
        
        // 태그별 그룹을 sort_order 기준으로 정렬 (etc는 마지막)
        List<Map.Entry<String, List<Memo>>> sortedTagEntries = memosByTagCode.entrySet().stream()
            .sorted((e1, e2) -> {
                if ("etc".equals(e1.getKey())) return 1;  // etc는 항상 마지막
                if ("etc".equals(e2.getKey())) return -1;
                
                // 각 태그 그룹의 첫 번째 메모의 대표 태그 sort_order로 비교
                Tag tag1 = getRepresentativeTag.apply(e1.getValue().get(0));
                Tag tag2 = getRepresentativeTag.apply(e2.getValue().get(0));
                if (tag1 != null && tag2 != null) {
                    return tag1.getSortOrder().compareTo(tag2.getSortOrder());
                }
                return 0;
            })
            .collect(Collectors.toList());
        
        // 결과 구성: 태그 그룹 -> 책 그룹 -> 메모
        Map<String, TagMemoGroup> result = new LinkedHashMap<>();
        
        for (Map.Entry<String, List<Memo>> tagEntry : sortedTagEntries) {
            String tagCode = tagEntry.getKey();
            List<Memo> tagMemos = tagEntry.getValue();
            
            TagMemoGroup tagGroup = new TagMemoGroup();
            tagGroup.setTagCode(tagCode);
            tagGroup.setMemoCount(tagMemos.size());
            
            // 2차 그룹화: 각 태그 그룹 내에서 책별로 그룹화
            Map<Long, List<Memo>> memosByBook = tagMemos.stream()
                .collect(Collectors.groupingBy(m -> m.getUserShelfBook().getId()));
            
            Map<Long, BookMemoGroup> bookGroups = new LinkedHashMap<>();
            
            for (Map.Entry<Long, List<Memo>> bookEntry : memosByBook.entrySet()) {
                Long bookId = bookEntry.getKey();
                List<Memo> bookMemos = bookEntry.getValue();
                
                // 3차 정렬: 각 책 그룹 내에서 시간순으로 정렬
                bookMemos.sort(Comparator.comparing(Memo::getMemoStartTime));
                
                BookMemoGroup bookGroup = new BookMemoGroup();
                Memo firstMemo = bookMemos.get(0);
                bookGroup.setBookId(bookId);
                bookGroup.setBookTitle(firstMemo.getUserShelfBook().getBook().getTitle());
                bookGroup.setBookIsbn(firstMemo.getUserShelfBook().getBook().getIsbn());
                bookGroup.setMemoCount(bookMemos.size());
                
                // 메모를 MemoResponse로 변환
                List<MemoResponse> memoResponses = bookMemos.stream()
                    .map(memoMapper::toMemoResponse)
                    .collect(Collectors.toList());
                bookGroup.setMemos(memoResponses);
                
                bookGroups.put(bookId, bookGroup);
            }
            
            tagGroup.setMemosByBook(bookGroups);
            result.put(tagCode, tagGroup);
        }
        
        return result;
    }
    
    /**
     * 특정 책의 메모 조회
     */
    @Transactional(readOnly = true)
    public List<Memo> getBookMemos(User user, Long userBookId, LocalDate date) {
        // 소유권 확인
        UserShelfBook userShelfBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userShelfBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 날짜 범위 쿼리 사용 (인덱스 활용 최적화)
        LocalDateTime[] dateRange = calculateDateRange(date);
        return memoRepository.findByUserIdAndUserShelfBookIdAndDate(
            user.getId(), userBookId, dateRange[0], dateRange[1]
        );
    }
    
    /**
     * 책 덮기 (독서 활동 종료)
     * 
     * 기능 요약:
     * - 마지막으로 읽은 페이지 수를 기록하고 독서 진행률을 업데이트합니다.
     * - 독서 진행률에 따라 카테고리가 자동으로 변경됩니다.
     * - 오늘의 흐름에서 해당 책의 메모가 섹션으로 구분되어 표시됩니다.
     * 
     * 상세 기능 설명은 섹션 1.2 주요 요구사항의 "책 덮기" 항목 및 섹션 15를 참조하세요.
     * 
     * 참고: 노션 문서 (https://www.notion.so/29d4a8c85009803aa90df9f6bdbf3568)
     */
    public void closeBook(User user, Long userBookId, Integer lastReadPage) {
        UserShelfBook userShelfBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userShelfBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 2. 페이지 수 유효성 검증
        if (lastReadPage == null || lastReadPage < 1) {
            throw new IllegalArgumentException("페이지 수는 1 이상이어야 합니다.");
        }
        
        // 전체 페이지 수 확인 및 검증
        Integer totalPages = userShelfBook.getBook() != null ? 
            userShelfBook.getBook().getTotalPages() : null;
        
        if (totalPages != null && lastReadPage > totalPages) {
            throw new IllegalArgumentException(
                String.format("페이지 수는 전체 페이지 수(%d페이지)를 초과할 수 없습니다.", totalPages)
            );
        }
        
        // 3. 진행률 업데이트
        userShelfBook.setReadingProgress(lastReadPage);
        userShelfBookRepository.save(userShelfBook);
        
        // 4. 카테고리 자동 변경 로직은 BookService에 위임
        // 진행률에 따라 카테고리 자동 변경
        // 진행률 계산: (lastReadPage / totalPages) * 100
        // 카테고리 변경 기준은 섹션 15.4.2 데이터베이스 업데이트의 "카테고리 변경 기준 (예시)"를 참조하세요.
        bookService.updateBookCategory(userShelfBook);
    }
    
    /**
     * 특정 날짜의 특정 책의 메모 조회 (오늘의 흐름에서 특정 책 필터링)
     * 
     * 기능 설명:
     * - 오늘의 흐름 화면에서 특정 책을 선택하면 해당 책의 메모만 표시
     * - 선택한 날짜에 작성된 메모만 조회
     * - 오늘의 흐름과 동일한 화면 구성으로 표시
     * - 메모는 타임라인 순서로 정렬됨 (memo_start_time 기준)
     * 
     * 참고: 노션 문서 (https://www.notion.so/29c4a8c8500980fc932cd55dcaa28ab1)
     */
    @Transactional(readOnly = true)
    public List<Memo> getBookMemosByDate(User user, Long userBookId, LocalDate date) {
        UserShelfBook userShelfBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userShelfBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 날짜 범위 쿼리 사용 (인덱스 활용 최적화)
        LocalDateTime[] dateRange = calculateDateRange(date);
        return memoRepository.findByUserIdAndUserShelfBookIdAndDate(
            user.getId(), userBookId, dateRange[0], dateRange[1]
        );
    }
    
    /**
     * 특정 책의 모든 메모 조회 (날짜 제한 없음)
     * 
     * 기능 설명:
     * - 특정 책에 작성된 모든 메모를 날짜 제한 없이 조회
     * - 오늘의 흐름 형식으로 표시 가능
     * - 메모는 타임라인 순서로 정렬됨 (memo_start_time 기준)
     * - 가장 오래된 메모가 위에, 가장 최근 메모가 아래에 표시
     * 
     * 사용 시나리오:
     * - 책의 흐름 기능: 월별 책 목록 또는 내 서재 책 목록에서 선택한 책의 모든 메모 조회
     * - 전체 메모 조회: 선택한 책의 모든 메모를 오늘의 흐름 형식으로 조회 (날짜 제한 없음)
     * 
     * 참고: 섹션 1.3.3 책의 흐름 기능과의 관계
     */
    @Transactional(readOnly = true)
    public List<Memo> getAllBookMemos(User user, Long userBookId) {
        UserShelfBook userShelfBook = userShelfBookRepository.findById(userBookId)
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        if (!userShelfBook.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        // 날짜 제한 없이 모든 메모 조회 (타임라인 순서)
        return memoRepository.findByUserIdAndUserShelfBookIdOrderByMemoStartTimeAsc(
            user.getId(), userBookId
        );
    }
    
    /**
     * 최근 기간 내에 메모가 작성된 책 목록 조회
     * 
     * 기능:
     * - 최근 N개월 이내에 메모가 작성된 책들의 목록을 반환
     * - 각 책의 최신 메모 작성 시간 기준으로 내림차순 정렬 (가장 최근에 메모를 작성한 책이 위에)
     * - 책 정보(제목, 저자, ISBN, 카테고리 등) 포함
     * 
     * 사용 시나리오:
     * - 책의 흐름 기능: 월별 책 목록 조회
     * - 최근 활동한 책들을 우선적으로 표시
     * - 내 서재와 연동하여 최근 메모 작성 책 목록 제공
     * 
     * 구현 방식:
     * 1. Repository에서 최근 기간 내에 메모가 작성된 책 ID와 최신 메모 작성 시간 조회
     * 2. 조회된 책 ID 목록을 기반으로 UserShelfBook 정보 조회
     * 3. 최신 메모 작성 시간 기준으로 정렬하여 반환
     * 
     * 참고: 섹션 1.3.3 책의 흐름 기능과의 관계
     */
    @Transactional(readOnly = true)
    public List<UserShelfBook> getBooksWithRecentMemos(User user, int monthsAgo) {
        // 최근 N개월 전 날짜 계산
        LocalDateTime startDate = LocalDateTime.now().minusMonths(monthsAgo);
        
        // 최근 기간 내에 메모가 작성된 책 ID 목록 조회 (최신 메모 작성 시간 기준 정렬)
        List<Object[]> results = memoRepository.findUserShelfBookIdsWithLastMemoTime(
            user.getId(), startDate
        );
        
        // 책 ID 목록 추출
        List<Long> bookIds = results.stream()
            .map(result -> (Long) result[0])
            .collect(Collectors.toList());
        
        if (bookIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // UserShelfBook 정보 조회 (최신 메모 작성 시간 순서 유지)
        List<UserShelfBook> books = userShelfBookRepository.findAllById(bookIds);
        
        // 결과 순서를 최신 메모 작성 시간 기준으로 정렬
        Map<Long, LocalDateTime> lastMemoTimeMap = results.stream()
            .collect(Collectors.toMap(
                result -> (Long) result[0],
                result -> (LocalDateTime) result[1]
            ));
        
        // 정렬: 최신 메모 작성 시간 기준 내림차순
        // 주의: findUserShelfBookIdsWithLastMemoTime 쿼리는 메모가 있는 책 ID만 반환하지만,
        // 쿼리 시점과 findAllById() 시점 사이에 사용자가 책을 삭제할 수 있음.
        // 사용자가 특정 책을 선택하여 삭제하면, 해당 책의 데이터와 해당 책에 작성된 메모의 데이터가 함께 전부 삭제됨 (ON DELETE CASCADE).
        // 이 경우 findAllById()는 삭제된 책을 찾지 못하므로 결과에서 자동으로 제외됨 (정상 동작).
        // getOrDefault()는 방어적 프로그래밍으로 동시성 이슈나 예외 상황에 대비.
        books.sort((b1, b2) -> {
            LocalDateTime time1 = lastMemoTimeMap.getOrDefault(b1.getId(), LocalDateTime.MIN);
            LocalDateTime time2 = lastMemoTimeMap.getOrDefault(b2.getId(), LocalDateTime.MIN);
            return time2.compareTo(time1); // 내림차순 (최신이 위에)
        });
        
        return books;
    }
    
    /**
     * 태그 처리: 태그 코드 리스트를 Tag 엔티티 리스트로 변환
     * 
     * 참고: ARCHITECTURE 원칙에 따라 태그 처리는 Mapper 계층에서 수행됩니다.
     * 이 메서드는 Service에서 직접 호출하지 않으며, Mapper의 processTags 메서드를 사용합니다.
     * 
     * 태그 자동 연결 규칙: 사용자가 태그를 선택하지 않은 경우 '기타' 태그를 자동으로 연결합니다.
     * 상세 내용은 섹션 18.3 태그 사용 규칙 및 MemoMapper의 processTags 메서드를 참조하세요.
     * 
     * 참고: 노션 문서 (https://www.notion.so/2994a8c8500981999b61ed46802fabbf)
     * 
     * @deprecated 이 메서드는 더 이상 사용되지 않습니다. 태그 처리는 Mapper 계층(MemoMapper.processTags)에서 수행됩니다.
     * 실제 구현에서는 이 메서드를 제거하고, Mapper의 processTags 메서드를 사용하세요.
     */
}
```

---

## 6. Controller 레이어 설계

### 6.1 MemoController

```java
package com.readingtracker.server.controller.v1;

import com.readingtracker.dbms.entity.Memo;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.dbms.repository.UserRepository;
import com.readingtracker.server.dto.ApiResponse;
import com.readingtracker.server.dto.requestDTO.MemoCreateRequest;
import com.readingtracker.server.dto.requestDTO.MemoUpdateRequest;
import com.readingtracker.server.dto.requestDTO.CloseBookRequest;
import com.readingtracker.server.dto.responseDTO.BookMemoGroup;
import com.readingtracker.server.dto.responseDTO.BookResponse;
import com.readingtracker.server.dto.responseDTO.MemoResponse;
import com.readingtracker.server.dto.responseDTO.TodayFlowResponse;
import com.readingtracker.server.mapper.MemoMapper;
import com.readingtracker.server.service.MemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "오늘의 흐름", description = "독서 메모 작성 및 관리 API")
public class MemoController extends BaseV1Controller {
    
    @Autowired
    private MemoService memoService;
    
    @Autowired
    private MemoMapper memoMapper;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserShelfBookRepository userShelfBookRepository;
    
    @Autowired
    private TagRepository tagRepository;
    
    /**
     * 메모 작성
     * POST /api/v1/memos
     */
    @PostMapping("/memos")
    @Operation(
        summary = "메모 작성",
        description = "독서 중 메모를 작성합니다. 페이지당 메모 개수 제한 없이 자유롭게 기록할 수 있습니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<MemoResponse> createMemo(
            @Parameter(description = "메모 작성 요청", required = true)
            @Valid @RequestBody MemoCreateRequest request) {
        
        User user = getCurrentUser();
        
        // UserShelfBook 조회 (Mapper에서 처리하기 어려우므로 Controller에서 조회)
        UserShelfBook userShelfBook = userShelfBookRepository.findById(request.getUserBookId())
            .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
        
        // Mapper를 통한 RequestDTO → Entity 변환 (ARCHITECTURE 원칙 준수)
        // TagRepository는 @Mapper(uses = TagRepository.class)로 주입됨
        Memo memo = memoMapper.toMemoEntity(request, user, userShelfBook, tagRepository);
        
        // Service는 Entity만 받음
        Memo savedMemo = memoService.createMemo(user, memo);
        
        // Mapper를 통한 Entity → ResponseDTO 변환
        MemoResponse response = memoMapper.toMemoResponse(savedMemo);
        return ApiResponse.success(response);
    }
    
    /**
     * 메모 수정
     * PUT /api/v1/memos/{memoId}
     */
    @PutMapping("/memos/{memoId}")
    @Operation(
        summary = "메모 수정",
        description = "작성한 메모의 내용과 태그를 수정합니다. " +
                     "pageNumber는 메모 작성 시점의 시작 위치를 나타내는 메타데이터이므로 수정할 수 없습니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<MemoResponse> updateMemo(
            @Parameter(description = "메모 ID", required = true)
            @PathVariable Long memoId,
            @Parameter(description = "메모 수정 요청", required = true)
            @Valid @RequestBody MemoUpdateRequest request) {
        
        User user = getCurrentUser();
        
        // 기존 메모 조회
        Memo existingMemo = memoService.getMemoById(user, memoId);
        
        // Mapper를 통한 RequestDTO → Entity 변환 (부분 업데이트)
        // @Context로 TagRepository 전달
        memoMapper.updateMemoFromRequest(existingMemo, request, tagRepository);
        
        // Service는 Entity만 받음
        Memo updatedMemo = memoService.updateMemo(user, memoId, existingMemo);
        
        // Mapper를 통한 Entity → ResponseDTO 변환
        MemoResponse response = memoMapper.toMemoResponse(updatedMemo);
        return ApiResponse.success(response);
    }
    
    /**
     * 메모 삭제
     * DELETE /api/v1/memos/{memoId}
     */
    @DeleteMapping("/memos/{memoId}")
    @Operation(
        summary = "메모 삭제",
        description = "작성한 메모를 삭제합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<String> deleteMemo(
            @Parameter(description = "메모 ID", required = true)
            @PathVariable Long memoId) {
        
        User user = getCurrentUser();
        memoService.deleteMemo(user, memoId);
        return ApiResponse.success("메모가 삭제되었습니다.");
    }
    
    /**
     * 오늘의 흐름 조회
     * GET /api/v1/today-flow
     * 
     * 기능:
     * - 오늘 날짜의 메모 조회 (기본값)
     * - 과거 날짜의 메모 조회 가능 (독서 캘린더 연동)
     * - sortBy 파라미터에 따라 다른 정렬 방식으로 반환:
     *   - SESSION (기본값): 책별로 그룹화하여 반환. 프론트엔드에서 시간축에 재배치하여 세션 단위로 구성
     *   - BOOK: 책별로 그룹화하여 반환
     *   - TAG: 태그별로 그룹화하여 반환 (태그 그룹 내부에서 책별로 다시 그룹화)
     * 
     * 백엔드 책임:
     * - Controller: sortBy 파라미터에 따라 적절한 Service 메서드를 호출하여 정렬을 제어
     * - Repository & Service: 
     *   - SESSION/BOOK 모드: book_id를 기준으로 데이터를 조회하고, 책별로 그룹화하는 최종 변환 담당
     *   - TAG 모드: 날짜 기준으로 모든 메모를 조회하고, 태그별로 그룹화한 후 각 태그 그룹 내에서 책별로 그룹화하는 최종 변환 담당
     * 
     * 프론트엔드 책임:
     * - 정렬 옵션 제공: 사용자가 SESSION/BOOK/TAG 정렬 옵션을 선택할 수 있는 UI 요소 제공
     * - Response DTO 사용: 
     *   - SESSION/BOOK 모드: Map<Long, BookMemoGroup> 형태의 데이터를 해석하여 UI에 표시
     *   - TAG 모드: Map<String, TagMemoGroup> 형태의 데이터를 해석하여 UI에 표시
     * 
     * 참고: 노션 문서 (https://www.notion.so/29c4a8c850098058892bc37ed7f6f68a)
     */
    @GetMapping("/today-flow")
    @Operation(
        summary = "오늘의 흐름 조회",
        description = "특정 날짜의 메모를 조회합니다. " +
                     "날짜 파라미터가 없으면 오늘 날짜의 메모를 조회합니다. " +
                     "독서 캘린더와 연동하여 과거 날짜의 메모도 조회할 수 있습니다. " +
                     "sortBy 파라미터로 정렬 방식을 선택할 수 있습니다 (SESSION: 세션 그룹화 기본, BOOK: 책별 그룹화, TAG: 태그별 그룹화). " +
                     "TAG 모드 사용 시 tagCategory 파라미터로 태그 대분류를 선택할 수 있습니다 (TYPE: 유형 기본값, TOPIC: 주제). " +
                     "tagCategory가 지정되면 해당 대분류가 대표 태그 결정 시 1순위가 됩니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<TodayFlowResponse> getTodayFlow(
            @Parameter(description = "조회할 날짜 (기본값: 오늘)", required = false)
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "정렬 방식 (SESSION | BOOK | TAG)", 
                       required = false)
            @RequestParam(required = false, defaultValue = "SESSION") String sortBy,
            @Parameter(description = "태그 대분류 (TYPE | TOPIC) - TAG 모드에서만 사용", 
                       required = false)
            @RequestParam(required = false) String tagCategory) {
        
        if (date == null) {
            date = LocalDate.now();
        }
        
        User user = getCurrentUser();
        TodayFlowResponse response = new TodayFlowResponse();
        response.setDate(date);
        response.setSortBy(sortBy);
        
        if ("TAG".equals(sortBy)) {
            // 태그별 그룹화 (태그 그룹 내부에서 책별로 다시 그룹화)
            // tagCategory가 null이면 기본값으로 TYPE 사용 (기본 우선순위)
            TagCategory category = (tagCategory != null && "TOPIC".equalsIgnoreCase(tagCategory)) 
                ? TagCategory.TOPIC 
                : TagCategory.TYPE;
            Map<String, TagMemoGroup> memosByTag = 
                memoService.getTodayFlowGroupedByTag(user, date, category);
            response.setMemosByTag(memosByTag);
            long totalCount = memosByTag.values().stream()
                .mapToLong(TagMemoGroup::getMemoCount)
                .sum();
            response.setTotalMemoCount(totalCount);
        } else if ("BOOK".equals(sortBy)) {
            // 책별 그룹화
            Map<Long, BookMemoGroup> memosByBook = 
                memoService.getTodayFlowGroupedByBook(user, date);
            response.setMemosByBook(memosByBook);
            long totalCount = memosByBook.values().stream()
                .mapToLong(BookMemoGroup::getMemoCount)
                .sum();
            response.setTotalMemoCount(totalCount);
        } else {
            // 기본: SESSION — 시간 순 데이터 제공(세션 UI 구성은 프론트에서 처리)
            Map<Long, BookMemoGroup> memosByBook = 
                memoService.getTodayFlowGroupedByBook(user, date);
            response.setMemosByBook(memosByBook);
            long totalCount = memosByBook.values().stream()
                .mapToLong(BookMemoGroup::getMemoCount)
                .sum();
            response.setTotalMemoCount(totalCount);
        }
        
        return ApiResponse.success(response);
    }
    
    /**
     * 특정 책의 메모 조회 (오늘의 흐름에서 특정 책 필터링)
     * GET /api/v1/memos/books/{userBookId}
     * 
     * 기능 설명:
     * - 오늘의 흐름 화면에서 특정 책 아이콘을 선택하면 해당 책의 메모만 표시
     * - 날짜 파라미터가 있으면 해당 날짜에 작성된 메모만 조회
     * - 날짜 파라미터가 없으면 날짜 제한 없이 해당 책의 모든 메모를 조회 (전체 메모 조회)
     * - 오늘의 흐름과 동일한 화면 구성으로 표시
     * - 메모는 타임라인 순서로 정렬됨 (memo_start_time 기준)
     * 
     * 참고: 노션 문서 (https://www.notion.so/29c4a8c8500980fc932cd55dcaa28ab1)
     */
    @GetMapping("/memos/books/{userBookId}")
    @Operation(
        summary = "특정 책의 메모 조회",
        description = "오늘의 흐름 화면에서 특정 책을 선택하면 해당 책에 작성된 메모를 조회합니다. " +
                     "날짜 파라미터가 있으면 해당 날짜에 작성된 메모만 조회하고, " +
                     "날짜 파라미터가 없으면 날짜 제한 없이 해당 책의 모든 메모를 조회합니다. " +
                     "오늘의 흐름과 동일한 화면 구성으로 표시되며, 메모는 타임라인 순서로 정렬됩니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<List<MemoResponse>> getBookMemos(
            @Parameter(description = "사용자 책 ID", required = true)
            @PathVariable Long userBookId,
            @Parameter(description = "조회할 날짜 (선택, 없으면 날짜 제한 없이 모든 메모 조회)", required = false)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        User user = getCurrentUser();
        List<Memo> memos;
        
        if (date != null) {
            // 특정 날짜의 메모만 조회
            memos = memoService.getBookMemosByDate(user, userBookId, date);
        } else {
            // 날짜 제한 없이 모든 메모 조회
            memos = memoService.getAllBookMemos(user, userBookId);
        }
        
        List<MemoResponse> response = memoMapper.toMemoResponseList(memos);
        return ApiResponse.success(response);
    }
    
    /**
     * 최근 기간 내에 메모가 작성된 책 목록 조회
     * GET /api/v1/memos/books/recent
     * 
     * 기능:
     * - 최근 N개월 이내에 메모가 작성된 책들의 목록을 반환
     * - 각 책의 최신 메모 작성 시간 기준으로 내림차순 정렬
     * - 책의 흐름 기능에서 월별 책 목록으로 사용
     * 
     * 참고: 섹션 1.3.3 책의 흐름 기능과의 관계
     */
    @GetMapping("/memos/books/recent")
    @Operation(
        summary = "최근 메모 작성 책 목록 조회",
        description = "최근 N개월 이내에 메모가 작성된 책들의 목록을 조회합니다. " +
                     "각 책의 최신 메모 작성 시간 기준으로 내림차순 정렬됩니다. " +
                     "months 파라미터로 조회 기간을 조정할 수 있습니다 (기본값: 1개월). " +
                     "책의 흐름 기능에서 월별 책 목록으로 사용됩니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<List<BookResponse>> getBooksWithRecentMemos(
            @Parameter(description = "조회 기간 (개월 수, 기본값: 1)", required = false)
            @RequestParam(required = false, defaultValue = "1")
            @Min(value = 1, message = "조회 기간은 1개월 이상이어야 합니다.")
            Integer months) {
        
        User user = getCurrentUser();
        List<UserShelfBook> books = memoService.getBooksWithRecentMemos(user, months);
        
        // Mapper를 통한 Entity → DTO 변환 (ARCHITECTURE 원칙 준수)
        List<BookResponse> response = memoMapper.toBookResponseList(books);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 책 덮기 (독서 활동 종료)
     * POST /api/v1/memos/books/{userBookId}/close
     * 
     * 기능 요약:
     * - 마지막으로 읽은 페이지 수를 기록하고 독서 진행률을 업데이트합니다.
     * - 독서 진행률에 따라 카테고리가 자동으로 변경됩니다.
     * - 오늘의 흐름에서 해당 책의 메모가 섹션으로 구분되어 표시됩니다.
     * 
     * 상세 기능 설명은 섹션 1.2 주요 요구사항의 "책 덮기" 항목 및 섹션 15를 참조하세요.
     * 
     * 참고: 노션 문서 (https://www.notion.so/29d4a8c85009803aa90df9f6bdbf3568)
     */
    @PostMapping("/memos/books/{userBookId}/close")
    @Operation(
        summary = "책 덮기",
        description = "독서 활동을 종료하고 마지막으로 읽은 페이지 수를 기록합니다. " +
                     "독서 진행률이 업데이트되며, 진행률에 따라 카테고리가 자동으로 변경될 수 있습니다. " +
                     "책 덮기 후 오늘의 흐름에서 해당 책의 메모가 섹션으로 구분되어 표시됩니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<String> closeBook(
            @Parameter(description = "사용자 책 ID", required = true)
            @PathVariable Long userBookId,
            @Parameter(description = "책 덮기 요청", required = true)
            @Valid @RequestBody CloseBookRequest request) {
        
        User user = getCurrentUser();
        memoService.closeBook(user, userBookId, request.getLastReadPage());
        return ApiResponse.success("독서 활동이 종료되었습니다.");
    }
    
    /**
     * 현재 로그인한 사용자 조회 헬퍼 메서드
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginId = (String) authentication.getPrincipal();
        return userRepository.findActiveUserByLoginId(loginId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }
}
```

---

## 7. Mapper 설계

### 7.1 MemoMapper (MapStruct)

```java
package com.readingtracker.server.mapper;

import com.readingtracker.dbms.entity.Memo;
import com.readingtracker.dbms.entity.Tag;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.dbms.repository.TagRepository;
import com.readingtracker.server.dto.requestDTO.MemoCreateRequest;
import com.readingtracker.server.dto.requestDTO.MemoUpdateRequest;
import com.readingtracker.server.dto.responseDTO.BookResponse;
import com.readingtracker.server.dto.responseDTO.MemoResponse;
import com.readingtracker.server.dto.responseDTO.TodayFlowResponse;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring", uses = {TagRepository.class})
public interface MemoMapper {
    
    // ========== RequestDTO → Entity 변환 ==========
    
    /**
     * MemoCreateRequest → Memo Entity 변환
     * 
     * 태그 처리: 태그 코드 리스트를 Tag 엔티티 리스트로 변환
     * 태그 자동 연결 규칙은 섹션 18.3 태그 사용 규칙을 참조하세요.
     * 
     * @param request MemoCreateRequest DTO
     * @param user User 엔티티 (Context로 전달)
     * @param userShelfBook UserShelfBook 엔티티 (Controller에서 조회 후 전달)
     * @param tagRepository TagRepository (Context로 전달, 태그 처리용)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "userShelfBook", source = "userShelfBook")
    @Mapping(target = "pageNumber", source = "request.pageNumber")
    @Mapping(target = "content", source = "request.content")
    @Mapping(target = "tags", expression = "java(processTags(request.getTags(), tagRepository))")
    @Mapping(target = "memoStartTime", source = "request.memoStartTime")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Memo toMemoEntity(MemoCreateRequest request, @Context User user, 
                      @Context UserShelfBook userShelfBook, @Context TagRepository tagRepository);
    
    /**
     * MemoUpdateRequest → Memo Entity 부분 업데이트
     * 
     * 기존 Memo 엔티티에 RequestDTO의 필드만 업데이트합니다.
     * content와 tags만 수정 가능하며, pageNumber는 수정 불가입니다.
     * 
     * @param memo 기존 Memo 엔티티 (업데이트 대상)
     * @param request MemoUpdateRequest DTO
     * @param tagRepository TagRepository (Context로 전달, 태그 처리용)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "userShelfBook", ignore = true)
    @Mapping(target = "pageNumber", ignore = true)  // 수정 불가
    @Mapping(target = "content", source = "request.content")
    @Mapping(target = "tags", expression = "java(processTags(request.getTags(), tagRepository))")
    @Mapping(target = "memoStartTime", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateMemoFromRequest(@MappingTarget Memo memo, MemoUpdateRequest request, 
                                @Context TagRepository tagRepository);
    
    // ========== Entity → ResponseDTO 변환 ==========
    
    @Mapping(target = "userBookId", source = "userShelfBook.id")
    @Mapping(target = "bookTitle", source = "userShelfBook.book.title")
    @Mapping(target = "bookIsbn", source = "userShelfBook.book.isbn")
    @Mapping(target = "tags", expression = "java(convertTagsToStringList(memo.getTags()))")
    MemoResponse toMemoResponse(Memo memo);
    
    List<MemoResponse> toMemoResponseList(List<Memo> memos);
    
    /**
     * UserShelfBook 엔티티를 BookResponse DTO로 변환
     * Book 엔티티 null 체크 포함 (방어적 프로그래밍)
     */
    default BookResponse toBookResponse(com.readingtracker.dbms.entity.UserShelfBook book) {
        if (book == null) {
            return null;
        }
        
        BookResponse response = new BookResponse();
        response.setId(book.getId());
        response.setCategory(book.getCategory());
        response.setReadingProgress(book.getReadingProgress());
        
        // Book 엔티티 null 체크 (방어적 프로그래밍)
        // 이론적으로는 null이 아니어야 하지만, LAZY 로딩 미초기화나 데이터 무결성 이슈 대비
        if (book.getBook() != null) {
            response.setTitle(book.getBook().getTitle());
            response.setAuthor(book.getBook().getAuthor());
            response.setIsbn(book.getBook().getIsbn());
        }
        
        return response;
    }
    
    List<BookResponse> toBookResponseList(List<com.readingtracker.dbms.entity.UserShelfBook> books);
    
    default TodayFlowResponse toTodayFlowResponse(List<Memo> memos, LocalDate date, String sortBy) {
        TodayFlowResponse response = new TodayFlowResponse();
        response.setDate(date);
        response.setMemos(toMemoResponseList(memos));
        response.setTotalMemoCount((long) memos.size());
        response.setSortBy(sortBy);
        return response;
    }
    
    /**
     * Tag 엔티티 리스트를 태그 코드 리스트로 변환
     */
    default List<String> convertTagsToStringList(List<com.readingtracker.dbms.entity.Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
            .map(com.readingtracker.dbms.entity.Tag::getCode)
            .toList();
    }
    
    /**
     * 태그 코드 리스트를 Tag 엔티티 리스트로 변환
     * 
     * 태그 자동 연결 규칙:
     * - 태그 미선택 시 '기타' 태그 자동 연결
     * - 허용된 태그 코드만 연결 (카탈로그 검증)
     * - 활성화된 태그만 연결
     * 
     * 참고: TagRepository는 @Context 파라미터로 전달됩니다.
     * 상세 내용은 섹션 18.3 태그 사용 규칙을 참조하세요.
     */
    default List<com.readingtracker.dbms.entity.Tag> processTags(
            List<String> tagCodes,
            @Context com.readingtracker.dbms.repository.TagRepository tagRepository) {
        if (tagCodes == null || tagCodes.isEmpty()) {
            // 태그 미선택 시 '기타' 태그 자동 연결
            return List.of(tagRepository.findByCode("etc")
                .orElseThrow(() -> new IllegalArgumentException("'기타' 태그가 카탈로그에 존재하지 않습니다.")));
        }
        
        List<com.readingtracker.dbms.entity.Tag> tags = new ArrayList<>();
        for (String tagCode : tagCodes) {
            if (tagCode == null || tagCode.trim().isEmpty()) {
                continue;
            }
            
            // 태그 코드로 조회 (카탈로그에 존재하는 태그만 허용)
            com.readingtracker.dbms.entity.Tag tag = tagRepository.findByCode(tagCode.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("태그 코드 '%s'는 허용된 카탈로그에 존재하지 않습니다.", tagCode)));
            
            // 활성화된 태그만 연결
            if (!tag.getIsActive()) {
                throw new IllegalArgumentException(
                    String.format("태그 코드 '%s'는 비활성화된 태그입니다.", tagCode));
            }
            
            tags.add(tag);
        }
        return tags;
    }
}
```

---

## 8. 구현 단계

### Phase 1: 데이터베이스 및 엔티티 (1주)
1. ✅ Flyway 마이그레이션 파일 작성 
   - `V12__Create_memo_table.sql` (memo 테이블)
   - `V12__Create_tags_table.sql` (tags 테이블)
   - `V12__Create_memo_tags_table.sql` (memo_tags 중간 테이블)
2. ✅ Tag 엔티티 생성
3. ✅ Memo 엔티티 생성 (Many-to-Many 관계 설정)
4. ✅ TagRepository 인터페이스 작성
5. ✅ MemoRepository 인터페이스 작성
6. ✅ 테스트 데이터 생성 및 검증

### Phase 2: Service 레이어 (1주)
1. ✅ MemoService 구현
   - 태그 연결 로직 (`processTags` 메서드) — 사전 정의된 카탈로그 내 태그만 허용
   - 페이지당 제한 체크
   - 소유권 확인
2. ✅ 비즈니스 로직 검증 (페이지당 제한, 소유권 확인, 태그 카탈로그 검증 등)
3. ✅ 단위 테스트 작성 (태그 연결/검증 테스트 포함)

### Phase 3: DTO 및 Mapper (3일)
1. ✅ Request/Response DTO 작성
2. ✅ MemoMapper 인터페이스 작성
3. ✅ MapStruct 컴파일 검증

### Phase 4: Controller 레이어 (3일)
1. ✅ MemoController 구현
2. ✅ API 엔드포인트 테스트
3. ✅ Swagger 문서화

### Phase 5: 통합 테스트 및 최적화 (1주)
1. ✅ 전체 기능 통합 테스트
2. ✅ 성능 최적화 (인덱스 검증, 쿼리 최적화)
3. ✅ 예외 처리 강화
4. ✅ 문서화 완료

---

## 9. 프론트엔드와 백엔드 책임 구분

### 9.1 프론트엔드 책임

#### 9.1.1 Response DTO 사용
- **책임**: 서버로부터 받은 데이터를 `sortBy`에 맞게 해석하여 UI를 구성합니다.
- **구현**: 
  - **오늘의 흐름 진입 및 책 선택 UX**:
    - 사용자가 오늘의 흐름 화면에 진입했을 때, 해당 날짜에 아직 선택된 책이 없다면 책 선택 모달 또는 패널을 표시합니다.
    - 책 선택 목록은 내 서재에 등록된 책들 중 `Finished`(완독) 카테고리를 제외한 나머지 카테고리(`ToRead`, `Reading`, `AlmostFinished`)의 책들로 구성됩니다.
    - 사용자가 특정 책을 선택하면, 그 책의 도서명과 저자명을 오늘의 흐름 화면 상단 제목 영역에 표시하고, 이후 작성되는 메모는 해당 책(`userBookId`)에 자동으로 연결됩니다.
    - 사용자가 다른 책으로 전환하고자 할 때도 동일한 책 선택 UX를 재사용하며, 책이 전환되는 시점이 새로운 세션의 시작점이 됩니다(SESSION 모드의 세션 구분 기준).
  - `SESSION`: 시간 순으로 세션을 분할하여 화면에 순차 배치 (책 전환 시 새로운 세션 시작)
  - `BOOK`: `memosByBook`의 각 `BookMemoGroup`을 섹션으로 렌더링하고 내부 `memos`는 시간 순
  - `TAG`: 각 `BookMemoGroup` 내 `memosByTag`를 태그명 오름차순으로 렌더링(“기타”는 마지막)

#### 9.1.2 정렬 옵션 제공
- **책임**: 사용자가 `SESSION(기본) / BOOK / TAG` 정렬 옵션을 선택할 수 있는 **UI 요소(버튼, 드롭다운 등)**를 제공하고, 선택된 값을 Controller의 파라미터(`sortBy`)로 서버에 전달합니다.
- **구현**:
  - 정렬 옵션 선택 UI 컴포넌트 구현
  - 선택된 값을 `sortBy` 쿼리 파라미터로 API 호출
  - 허용 값: `SESSION` | `BOOK` | `TAG` (기본값: `SESSION`)

#### 9.1.6 태그 대분류 선택 UX
- **책임**: 태그 기반 그룹화(SESSION의 2차, TAG 모드) 시, “태그 대분류(유형/주제)” 중 하나를 사용자가 선택할 수 있는 UI를 제공합니다.
- **구현**:
  - 대분류 선택 컴포넌트(토글/드롭다운)
  - 선택된 대분류 내 실제로 사용된 태그만 표시(체크박스 목록)
  - 태그명은 가나다/알파벳 오름차순, “기타(해당 대분류 태그 없음)”는 항상 마지막
  - 초기 값: 제품 정책에 따라 기본 대분류를 지정(예: 유형)

#### 9.1.3 메모 배치 로직
- **책임**: 메모의 `content` 길이를 기반으로 높이를 동적으로 계산하고, 좌측 섹션 → 우측 섹션으로 이어지는 바인더 노트 레이아웃을 구현합니다.
- **구현**:
  - 메모 내용의 실제 길이를 측정하여 높이 계산
  - 좌측 섹션 상단부터 메모 배치
  - 좌측 섹션이 가득 차면 우측 섹션 상단으로 자동 이동
  - 중앙선(제본 영역) 시각적 요소 강조
  - 수평 슬라이딩 방식으로 페이지 전환
  - 메모가 길어서 다른 페이지로 넘어가는 경우 처리
  - 여백을 남겨놓고 채워지는 레이아웃 처리

**책임 분리:**
- **프론트엔드**: UI 레이아웃 및 메모 배치 로직 담당
  - **현재 선택된 정렬 방식에 따라 메모 순서 결정**
    - `SESSION` 모드: 시간 흐름에 따라 세션 그룹 구성
    - `BOOK` 모드: 책별 그룹화 후 시간순 정렬
    - `TAG` 모드: 태그별 그룹화 → 책별 그룹화 → 시간순 정렬
  - **각 메모의 실제 위치를 동적으로 계산**
    - 정렬된 메모 리스트를 순회하며 각 메모의 `content` 길이를 기반으로 높이 계산
    - 첫 번째 메모부터 순차적으로 배치하여 실제 시작 페이지 계산
    - 메모 수정 시 `content` 길이 변경에 따라 다른 메모들의 위치 자동 재조정
  - **정렬 방식 변경 시 즉시 레이아웃 재조정**
    - 정렬 방식 변경 시 프론트엔드에서만 재계산하여 즉시 반영
    - DB 업데이트 없이 UI만 변경
  - 페이지 넘김 처리 및 여백 관리
  - `pageNumber`는 "원본 위치(SESSION 모드 기준)" 표시용으로만 사용 (옵션)
    - 실제 렌더링 위치와는 독립적이며, 참조용 메타데이터로 활용
- **백엔드**: 메모 작성 시점의 `pageNumber` 자동 생성 및 저장
  - 메모 작성 시 SESSION 모드 기준 UI 위치를 감지하여 `pageNumber` 생성
  - 메모의 "출발점" 정보를 DB에 보존 (정렬 방식 변경 시에도 변경하지 않음)
  - 메모 수정 시 `pageNumber`는 변경하지 않음 (원본 위치 정보 보존)
  - 정렬 방식별 데이터 제공 (위치 계산은 프론트엔드에서 처리)

#### 9.1.4 메모 수정 및 삭제 처리
- **책임**: 사용자가 특정 메모를 선택하여 수정 또는 삭제할 수 있는 UI를 제공하고, 메모 ID를 사용하여 적절한 API를 호출합니다.
- **구현**:
  - **메모 ID 관리**: 서버로부터 받은 `MemoResponse`에는 각 메모의 고유 ID(`id` 필드)가 포함되어 있습니다.
  - **메모 선택**: 사용자가 메모 카드의 수정 버튼 또는 삭제 버튼(X 버튼)을 클릭하면, 해당 메모의 `id` 값을 사용하여 API를 호출합니다.
  - **메모 수정 API 호출**: 
    - 엔드포인트: `PUT /api/v1/memos/{memoId}`
    - 메모 ID는 URL 경로에 포함됩니다 (예: `/api/v1/memos/123`)
    - Request Body에는 `MemoUpdateRequest` DTO를 전달합니다 (`content`, `tags`만 수정 가능)
    - `pageNumber`는 메모 작성 시점의 시작 위치를 나타내는 메타데이터이므로 수정할 수 없습니다.
    - 메모 ID는 Request Body에 포함되지 않으며, URL 경로에서만 전달됩니다.
  - **메모 삭제 API 호출**:
    - 엔드포인트: `DELETE /api/v1/memos/{memoId}`
    - 메모 ID는 URL 경로에 포함됩니다 (예: `/api/v1/memos/123`)
    - Request Body는 필요하지 않습니다.
  - **백엔드 처리**: 백엔드는 URL 경로에서 받은 `memoId`를 사용하여 DB에서 해당 메모를 조회(`memoRepository.findById(memoId)`)한 후, 소유권 확인을 거쳐 수정 또는 삭제를 수행합니다.

#### 9.1.5 날짜 변경 감지 및 새 페이지 표시
- **책임**: 실제 시간을 반영하여 날짜가 바뀌면 (자정이 지나면) 자동으로 해당 날짜의 메모만 조회하고 완전히 새로운 페이지를 표시합니다.
- **구현**:
  - 현재 날짜를 주기적으로 확인 (예: 1분마다 또는 사용자 액션 시)
  - 날짜가 변경되었을 때 (자정 경과) 자동으로 새 날짜의 메모를 조회
  - 이전 날짜의 메모는 저장된 상태로 유지되며, 새로운 날짜의 빈 페이지를 표시
  - 각 날짜를 완전히 독립적인 "바인더 노트"로 취급하여 날짜별로 완전히 분리된 화면 제공
  - 날짜 변경 시 부드러운 전환 애니메이션 적용 (선택 사항)

**`memo_start_time`과 `created_at`의 차이:**
- **`memo_start_time`**: 사용자가 실제로 메모 작성을 시작한 시간 (비즈니스 로직상 의미 있는 시간)
  - 클라이언트에서 전송하거나 서버에서 자동 생성 가능
  - **타임라인 정렬의 기준**으로 사용됨
  - **날짜 필터링의 기준**으로 사용됨 (어느 날짜의 바인더 노트에 속할지 결정)
  - 과거 시간도 설정 가능 (과거의 오늘의 흐름 기록용)
  - 사용자가 메모를 언제 작성했는지를 나타내는 시간
- **`created_at`**: 데이터베이스 레코드가 생성된 시간 (기술적 메타데이터)
  - 서버에서 자동으로 설정 (`DEFAULT CURRENT_TIMESTAMP`)
  - 언제 DB에 저장되었는지를 추적하는 감사(audit) 목적
  - 메모 수정 시 변경되지 않음 (`updatable = false`)
  - 일반적으로 `memo_start_time`과 같거나 약간 늦을 수 있음 (네트워크 지연 등)
  - 날짜 필터링에는 사용되지 않음 (오직 `memo_start_time` 기준)

**자정 경과 시 메모 저장 및 표시 처리:**
- 사용자가 메모 작성을 시작한 시간(`memo_start_time`)을 기준으로 날짜를 판단합니다.
- 자정 전에 메모 작성을 시작했지만 자정 후에 저장하는 경우:
  - `memo_start_time`이 자정 전이면, 해당 메모는 이전 날짜의 바인더 노트에 저장되고 표시됩니다.
  - 저장 시점(`created_at`)이 자정 후여도, `memo_start_time` 기준으로 필터링되므로 이전 날짜의 바인더 노트에 포함됩니다.
- 날짜가 변경되면 (자정 경과) 프론트엔드에서 자동으로 새로운 날짜의 빈 바인더 노트를 표시합니다.
- 이전 날짜의 메모는 이미 저장된 상태로 유지되며, 새로운 날짜에는 완전히 빈 페이지가 표시됩니다.

**예시:**
- **일반적인 경우**: 사용자가 오후 2시에 메모 작성을 시작했지만, 네트워크 지연으로 오후 2시 1분에 저장된 경우:
  - `memo_start_time`: 2024-01-15 14:00:00 (사용자가 작성 시작한 시간)
  - `created_at`: 2024-01-15 14:01:00 (DB에 저장된 시간)
  - 타임라인 정렬은 `memo_start_time` 기준으로 정렬되므로, 오후 2시에 작성한 메모로 표시됨

- **자정 경과 시**: 사용자가 2024년 1월 15일 23시 50분에 메모 작성을 시작했지만, 자정(2024년 1월 16일 00시 10분)이 지난 후에 저장 버튼을 누른 경우:
  - `memo_start_time`: 2024-01-15 23:50:00 (사용자가 작성 시작한 시간)
  - `created_at`: 2024-01-16 00:10:00 (DB에 저장된 시간)
  - 날짜 필터링은 `memo_start_time` 기준으로 수행되므로, 이 메모는 2024년 1월 15일의 바인더 노트에 저장되고 표시됩니다.
  - 저장 시점(`created_at`)이 1월 16일이더라도, `memo_start_time` 기준으로 필터링되므로 1월 15일의 바인더 노트에 포함됩니다.

**참고**: 데이터베이스 컬럼 상세 설명은 섹션 18.1을 참조하세요.

### 9.2 백엔드 책임

#### 9.2.1 Repository & Service
- **책임**: `book_id`를 기준으로 데이터를 조회하고, Service에서 이 데이터를 책별로 그룹화하는 최종 변환을 담당합니다. 또한 날짜 기반 필터링을 통해 특정 날짜의 메모만 조회합니다. 메모 작성 시 `pageNumber` 자동 생성 및 저장도 담당합니다.
- **구현**:
  - **`pageNumber`의 의미**:
    - `pageNumber`는 "메모 작성 시점의 SESSION 모드 기준 초기 위치"를 나타내는 메타데이터입니다.
    - 정렬 방식(SESSION/BOOK/TAG)이 변경되어도 `pageNumber`는 변경하지 않으며, 원본 위치 정보를 보존합니다.
    - 실제 UI 렌더링 위치는 프론트엔드에서 현재 선택된 정렬 방식에 따라 동적으로 계산됩니다.
    - `pageNumber`는 참조용 메타데이터이며, 실제 렌더링 위치와는 독립적입니다.
  - **메모 작성 시 `pageNumber` 자동 생성**:
    - 메모 작성 시점의 SESSION 모드 기준 UI 위치를 감지하여 `pageNumber` 생성
    - 메모가 시작된 페이지 번호를 DB에 저장
    - 메모의 "출발점" 정보를 보존
    - 사용자가 직접 입력하지 않으며, 서버가 자동으로 생성
  - **메모 수정 시 `pageNumber` 보존**:
    - 메모 수정 시 `pageNumber`는 변경하지 않음
    - 원본 위치 정보를 보존하여 메모의 무결성 유지
    - UI 레이아웃은 프론트엔드에서 처리하며, 백엔드는 메타데이터 보존에 집중
  - **정렬 방식별 데이터 제공**:
    - `SESSION` 모드: 시간순 데이터 제공 (프론트엔드에서 세션 그룹 구성)
    - `BOOK` 모드: 책별 그룹화된 데이터 제공
    - `TAG` 모드: 태그별 그룹화된 데이터 제공
    - 위치 계산은 프론트엔드에서 처리하며, 백엔드는 정렬된 데이터만 제공
  - Repository: 날짜 범위 쿼리(`>= startOfDay AND < startOfNextDay`)로 특정 날짜의 메모만 필터링
    - **중요**: `memo_start_time`을 기준으로 필터링하므로, 사용자가 메모 작성을 시작한 시간이 해당 날짜에 속하면 그 날짜의 바인더 노트에 포함됩니다.
    - 저장 시점(`created_at`)이 다른 날짜여도, `memo_start_time` 기준으로 필터링되므로 올바른 날짜의 바인더 노트에 표시됩니다.
  - Repository: `ORDER BY book_id, memo_start_time` 쿼리로 책별 그룹화 및 타임라인 정렬
  - Service: 조회된 메모 리스트를 `Map<Long, BookMemoGroup>` 형태로 변환
  - 각 `BookMemoGroup`에 책 정보 및 해당 책의 메모 리스트 포함
  - 날짜 파라미터가 없으면 기본적으로 오늘 날짜(`LocalDate.now()`)의 메모만 조회

#### 9.2.2 Controller
- **책임**: `sortBy` 파라미터(`SESSION` | `BOOK` | `TAG`)에 따라 적절한 서비스 메서드를 호출합니다. 또한 메모 수정/삭제 시 URL 경로에서 메모 ID를 받아 처리합니다.
- **구현(문서 기준):**
  - `SESSION` (기본): 시간 축을 기반으로 세션 그룹 UI 구성이 가능하도록 시간 순 데이터 제공
  - `BOOK`: 책별 그룹화 후 시간 순 정렬된 데이터 제공
  - `TAG`: 책별 그룹 내부에서 태그별로 다시 그룹화된 데이터 제공
  - 주의: 실제 메서드 명/응답 구조는 구현 단계에서 조정 가능
- **메모 수정/삭제 처리**:
  - **메모 ID 수신**: URL 경로 변수(`@PathVariable Long memoId`)로 메모 ID를 받습니다.
  - **메모 수정**: `PUT /api/v1/memos/{memoId}` - URL 경로의 `memoId`와 Request Body의 `MemoUpdateRequest`를 Service에 전달합니다.
  - **메모 삭제**: `DELETE /api/v1/memos/{memoId}` - URL 경로의 `memoId`를 Service에 전달합니다.
  - **Service 호출**: Service의 `updateMemo(user, memoId, request)` 또는 `deleteMemo(user, memoId)` 메서드를 호출합니다.
  - **Service 처리**: Service는 `memoRepository.findById(memoId)`로 DB에서 메모를 조회한 후, 소유권 확인을 거쳐 수정 또는 삭제를 수행합니다.

### 9.3 책임 분리 원칙

**백엔드:**
- 데이터 조회 및 그룹화
- 정렬 로직 처리
- 비즈니스 규칙 적용

**프론트엔드:**
- 사용자 인터페이스 제공
- 사용자 입력 처리
- 데이터 시각화 및 레이아웃 배치
- 사용자 경험 최적화

---

## 10. 주요 고려사항

### 10.1 성능 최적화
- **인덱스 활용**: 
  - `(user_id, book_id)`, `created_at`, `(book_id, page_number)` 인덱스 활용
  - `memo_tags(memo_id)`, `memo_tags(tag_id)` 인덱스로 태그 조회 최적화
  - `tags(code)`, `tags(category, sort_order)` 인덱스로 태그 검색 및 정렬 최적화
  - `idx_memo_memo_start_time (memo_start_time)` 인덱스로 날짜 범위 쿼리 최적화 (최근 메모 작성 책 목록 조회)
- **페이징 처리**: 메모가 많을 경우 페이징 고려
- **지연 로딩**: UserShelfBook, Book, Tag는 LAZY 로딩으로 N+1 문제 방지
- **배치 조인**: 태그별 메모 조회 시 `@EntityGraph` 또는 `JOIN FETCH` 활용
- **배치 조회**: 최근 메모 작성 책 목록 조회 시 `findAllById()`로 한 번에 여러 책 정보 조회하여 N+1 문제 방지

### 10.2 데이터 무결성
- **외래키 제약**: `ON DELETE CASCADE`로 책 삭제 시 해당 책의 메모도 함께 자동 삭제, 메모 삭제 시 태그 관계 자동 삭제
- **태그 정규화**: 태그 이름을 소문자로 통일하여 중복 방지
- **태그 허용 목록 검증**: 대분류별 사전 정의된 8개 태그 목록 이외의 태그는 저장/연결 불가(서버 단 검증)
- **메모 수 제한**: 페이지당 메모 개수 제한 없음 (사용자가 자유롭게 기록 가능)
- **소유권 확인**: 모든 작업에서 사용자 소유권 확인

### 10.3 확장성
- **태그 시스템**: Many-to-Many 관계로 구현되어 태그별 메모 조회 및 그룹화가 효율적
- **태그 통계**: 태그별 메모 개수 통계 기능 추가 용이
- **메모 통계**: 사용자별 메모 작성 통계 기능 추가 가능

**참고**: 태그 관련 확장 기능 상세 사항은 노션 문서 (https://www.notion.so/2994a8c8500981999b61ed46802fabbf) 참조

### 10.4 보안
- **인증 필수**: 모든 엔드포인트에 JWT 인증 필요
- **소유권 검증**: 사용자는 자신의 메모만 접근 가능
- **입력 검증**: DTO 레벨에서 `@Valid` 검증

---

## 11. API 엔드포인트 요약

### 11.1 메모 관련 API

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/v1/memos` | 메모 작성 | ✅ |
| PUT | `/api/v1/memos/{memoId}` | 메모 수정 | ✅ |
| DELETE | `/api/v1/memos/{memoId}` | 메모 삭제 | ✅ |
| GET | `/api/v1/today-flow` | 오늘의 흐름 조회 (타임라인) | ✅ |
| GET | `/api/v1/memos/books/{userBookId}` | 특정 책의 메모 조회 | ✅ |
| GET | `/api/v1/memos/books/recent` | 최근 메모 작성 책 목록 조회 | ✅ |
| POST | `/api/v1/memos/books/{userBookId}/close` | 책 덮기 | ✅ |

### 11.2 태그 관련 API (향후 구현)

| Method | Endpoint | 설명 | 인증 | 상태 |
|--------|----------|------|------|------|
| GET | `/api/v1/tags/catalog` | 태그 카탈로그 조회(대분류별 최대 8개, 라벨/정렬/활성) | ✅ | 향후 구현 |
| GET | `/api/v1/tags/{tagId}/memos` | 특정 태그의 메모 조회 | ✅ | 향후 구현 |
| GET | `/api/v1/tags/search` | 태그 검색 (자동완성용) | ✅ | 향후 구현 |
| GET | `/api/v1/tags/statistics` | 태그별 통계 조회 | ✅ | 향후 구현 |
| PUT | `/api/v1/tags/{tagId}/merge` | 태그 병합 | ✅ | 향후 구현 |
| DELETE | `/api/v1/tags/{tagId}` | 태그 삭제 | ✅ | 향후 구현 |

**참고**: 태그 관련 상세 API 설계는 노션 문서 (https://www.notion.so/2994a8c8500981999b61ed46802fabbf) 참조

---

## 12. 태그 시스템 상세

> **참고**: 태그 저장 방식의 기술적 설계 결정(Many-to-Many 관계, 인덱스 전략, 성능 최적화 등)에 대한 상세 내용은 `TAG_STORAGE_DESIGN_DECISION.md` 문서를 참조하세요.  
> 본 섹션은 태그의 비즈니스 로직, 사용 시나리오, 메모 기능과의 통합에 초점을 맞춥니다.

### 12.1 태그의 역할

태그는 메모를 분류하고 관리하는 데 사용되는 핵심 기능입니다:

1. **메모 분류**: 각 메모에 하나 이상의 태그를 설정하여 메모를 분류
2. **필터링**: 태그를 기준으로 메모를 필터링하여 조회
3. **통계 및 분석**: 태그별 메모 통계를 통한 독서 패턴 분석

### 12.2 태그 특징

- **전역 공유**: 태그는 모든 사용자 간에 공유됨 (동일한 태그 이름 사용)
- **고정 카탈로그**: 태그는 대분류별(유형/주제) 최대 8개로 사전에 정의·등록되어 있으며, 사용자가 임의로 생성하지 않습니다
- **정규화**: 태그 이름은 소문자로 정규화되어 저장 (대소문자 통일)
- **Many-to-Many**: 하나의 메모는 여러 태그를 가질 수 있고, 하나의 태그는 여러 메모에 사용될 수 있음

### 12.2.1 태그 대분류
- **대분류 구분**: 태그는 “대분류”로 구분됩니다(예: 유형 / 주제).
- **대분류별 태그 구성**: 각 대분류마다 태그 종류가 최대 8개로 관리됩니다(노션 기준).
- **정렬 시 활용**: 
  - SESSION 모드의 2차 태그 그룹화 및 TAG 모드의 1차 그룹화에서 "선택된 대분류"의 태그를 기준으로 그룹화합니다.
  - TAG 모드에서는 프론트엔드에서 선택한 대분류가 `tagCategory` 파라미터로 백엔드에 전달되며, 백엔드에서 해당 대분류를 기준으로 대표 태그를 결정하고 그룹화를 수행합니다.
  - 선택되지 않은 대분류의 태그는 그룹화에 사용되지 않습니다(해당 메모는 "기타"로 포함될 수 있음).
- **구현 메모**: `tags` 테이블에 `category` 컬럼(ENUM('TYPE','TOPIC'))이 정의되어 있습니다. 태그 목록 자체는 시드 데이터로 관리하며 운영에서 라벨/정렬/활성화만 변경할 수 있습니다.

### 12.2.2 대표 태그 결정 규칙
- **대표 태그 1개 원칙**: 하나의 메모에는 여러 태그가 붙을 수 있지만, SESSION/TAG 정렬에서 그룹화 및 정렬에 사용하는 기준은 항상 **대표 태그 1개**입니다.
- **기본 우선순위(TYPE 우선)**:
  - 1순위: `TYPE` 대분류(유형)에 속하는 태그들
    - 이 중 `sort_order`가 가장 작은 태그를 대표 태그로 사용합니다.
  - 2순위: `TOPIC` 대분류(주제)에 속하는 태그들
    - `TYPE` 태그가 하나도 없는 경우, `TOPIC` 태그들 중 `sort_order`가 가장 작은 태그를 대표 태그로 사용합니다.
  - 3순위: 둘 다 없는 경우(어떠한 태그도 지정되지 않은 메모)
    - 사전에 정의된 '기타' 태그(코드: `etc`)를 대표 태그로 사용합니다.
- **TAG 모드에서의 대분류 선택에 따른 우선순위 변경**:
  - 사용자가 TAG 모드에서 태그 대분류(유형/주제) 중 하나를 선택하면, 프론트엔드에서 `tagCategory` 파라미터로 백엔드에 전달됩니다.
  - 백엔드에서 `tagCategory`를 받아 해당 대분류가 대표 태그 결정 시 **1순위**가 되도록 처리합니다.
    - 예: 사용자가 "주제(TOPIC)"를 선택한 경우 → `TOPIC` > `TYPE` > 기타(`etc`)
    - 예: 사용자가 "유형(TYPE)"을 선택한 경우 → `TYPE` > `TOPIC` > 기타(`etc`)
    - 예: `tagCategory`가 전달되지 않은 경우 → 기본값으로 `TYPE` 사용 (`TYPE` > `TOPIC` > 기타(`etc`))
  - 선택된 대분류 1순위 내에서는 항상 `sort_order`가 가장 작은 태그가 대표 태그로 선택됩니다.
- **대표 태그의 역할 범위**:
  - 대표 태그는 **정렬 및 그룹화 기준**으로만 사용되며, UI에서는 메모에 붙어 있는 **모든 태그를 태그 리스트 형태로 그대로 표시**합니다.
  - 따라서 사용자는 하나의 메모에 여러 태그가 붙어 있더라도, 그룹/정렬은 대표 태그 기준으로 이뤄지고, 실제 표시되는 태그 정보는 손실되지 않습니다.

### 12.3 태그 사용 시나리오

**시나리오 1: 메모 작성 시 태그 설정**
```
사용자가 메모 작성 시:
- 태그: ["요약", "액션/실천", "인물/캐릭터"]
- 태그 종류는 각 대분류 유형마다 8개씩 이미 생성되어 있습니다. 따라서 사용자가 태그를 선택하면, 시스템이 기존 태그를 연결하는 것입니다.
```

**시나리오 2: 태그별 메모 조회**
```
사용자가 태그의 '대분류(유형/주제)'를 정렬 기준으로 선택하면:
- 선택된 대분류에 속하는 태그들이 태그 카탈로그의 `sort_order` 오름차순으로 상위 그룹으로 배열됩니다.
- 각 메모는 대표 태그(12.2.2의 대표 태그 결정 규칙에 따라 선택된 1개의 태그)를 기준으로 단일 태그 그룹에 속합니다.
- 태그가 지정되지 않은 메모는 '미지정/기타' 그룹에 묶이며, 이 그룹은 항상 마지막에 표시됩니다.
- 각 태그 그룹(및 미지정/기타 그룹) 내부의 메모는 가장 기본 속성인 시간순(오름차순)으로 정렬됩니다.
```

### 12.4 태그 관리 기능 (향후 구현)

**기본 기능 (현재 구현):**
- 태그 카탈로그 조회(API) 및 연결
- 허용 코드 검증 + 캐논컬 저장
  - 허용 목록 검증: 전달된 값이 카탈로그 코드와 정확히 일치하는지 체크(오타·케이스 차이 방지)
  - 캐논컬 저장: DB에는 대분류/코드(캐논컬 키)만 저장, 라벨은 i18n/카탈로그로 매핑
  - 예외 경로 대비: 향후 배치/마이그레이션/외부 연동 등에서 들어오는 비정규 값 방지
- 태그별 메모 조회

**향후 구현 예정:**
- 태그 통계 (태그별 메모 개수, 최근 사용 날짜 등)

**참고**: 태그 관련 상세 요구사항 및 향후 기능은 노션 문서 (https://www.notion.so/2994a8c8500981999b61ed46802fabbf) 참조

---

## 13. 특정 책의 메모만 확인 기능 상세

### 13.1 기능 개요

"특정 책의 메모만 확인" 기능은 오늘의 흐름 화면에서 특정 책을 선택하여 해당 책에 작성된 메모만 필터링하여 보는 기능입니다.

**참고**: 노션 문서 (https://www.notion.so/29c4a8c8500980fc932cd55dcaa28ab1)

### 13.2 주요 특징

1. **오늘의 흐름에서 필터링**
   - 오늘의 흐름 화면에서 특정 책 아이콘을 선택하면 해당 책의 메모만 표시
   - 여러 책의 메모가 섞여 있는 상태에서 특정 책의 메모만 확인 가능
   - 해당 책을 제외한 나머지 책들의 메모는 표시되지 않음

2. **날짜 기반 필터링**
   - 날짜 파라미터가 있으면: 선택한 날짜에 작성된 메모만 조회
   - 날짜 파라미터가 없으면: 날짜 제한 없이 해당 책의 모든 메모 조회
   - 날짜 파라미터를 통해 과거 날짜의 메모도 조회 가능
   - 독서 캘린더와 연동하여 특정 날짜의 특정 책 메모 조회 가능
   - 책의 흐름 기능과 연동하여 전체 메모 조회 가능 (날짜 제한 없음)

3. **화면 구성**
   - 오늘의 흐름과 동일한 화면 구성으로 표시
   - 사용자가 일관된 UI/UX 경험을 제공받음
   - 메모 카드, 태그, 시간 표시 등 모든 요소가 동일하게 표시됨

4. **정렬 방식**
   - 메모는 타임라인 순서로 정렬됨 (memo_start_time 기준)
   - 가장 오래된 메모가 위에, 가장 최근 메모가 아래에 표시
   - 오늘의 흐름과 동일한 정렬 기준 적용

### 13.3 사용 시나리오

**시나리오 1: 오늘의 흐름에서 특정 책 선택**
```
1. 사용자가 오늘의 흐름 화면을 확인
2. 여러 책의 메모가 섞여 있음
3. 특정 책의 아이콘/제목을 클릭
4. 해당 책에 작성된 오늘 날짜의 메모만 표시됨
5. 오늘의 흐름과 동일한 화면 구성으로 표시
```

**시나리오 2: 과거 날짜의 특정 책 메모 조회**
```
1. 독서 캘린더에서 특정 날짜 선택
2. 해당 날짜의 오늘의 흐름 화면 표시
3. 특정 책을 선택하여 해당 날짜의 해당 책 메모만 조회
4. 날짜 파라미터를 통해 API 호출
```

**시나리오 3: 여러 책 비교**
```
1. 오늘의 흐름에서 책 A 선택 → 책 A의 메모 확인
2. 뒤로 가기 또는 필터 해제
3. 책 B 선택 → 책 B의 메모 확인
4. 각 책의 독서 패턴을 비교 분석 가능
```

**시나리오 4: 책의 흐름 기능 - 전체 메모 조회**
```
1. 월별 책 목록 또는 내 서재 책 목록에서 특정 책 선택
2. 날짜 파라미터 없이 API 호출
3. 해당 책의 모든 메모를 날짜 제한 없이 조회
4. 오늘의 흐름 형식으로 전체 메모 표시
```

### 13.4 구현 세부사항

#### 13.4.1 API 엔드포인트

**GET `/api/v1/memos/books/{userBookId}`**

**요청 파라미터:**
- `userBookId` (Path Variable, 필수): 사용자 책 ID (UserShelfBook.id)
- `date` (Query Parameter, 선택): 조회할 날짜 (ISO 8601 형식)
  - `date`가 있으면: 해당 날짜에 작성된 메모만 조회
  - `date`가 없으면: 날짜 제한 없이 해당 책의 모든 메모 조회

**응답:**
- `List<MemoResponse>`: 해당 책의 메모 목록
  - `date`가 있으면: 해당 날짜에 작성된 메모만 반환
  - `date`가 없으면: 날짜 제한 없이 모든 메모 반환
- 메모는 타임라인 순서로 정렬됨 (memo_start_time 기준)

**예시:**
```
GET /api/v1/memos/books/123?date=2024-01-15
→ 2024년 1월 15일에 작성된 책 ID 123의 메모 목록 반환

GET /api/v1/memos/books/123
→ 날짜 제한 없이 책 ID 123의 모든 메모 목록 반환
```

#### 13.4.2 데이터베이스 쿼리

**특정 날짜의 메모 조회:**
```sql
SELECT m FROM Memo m 
WHERE m.user.id = :userId 
  AND m.userShelfBook.id = :userShelfBookId 
  AND m.memoStartTime >= :startOfDay 
  AND m.memoStartTime < :startOfNextDay 
ORDER BY m.memoStartTime ASC
```

**날짜 제한 없이 모든 메모 조회:**
```sql
SELECT m FROM Memo m 
WHERE m.user.id = :userId 
  AND m.userShelfBook.id = :userShelfBookId 
ORDER BY m.memoStartTime ASC
```

**인덱스 활용:**
- `idx_memo_user_book (user_id, book_id)`: 사용자별 책별 메모 조회 최적화
- `idx_memo_memo_start_time (memo_start_time)`: 타임라인 정렬 최적화
- 날짜 범위 쿼리(`>= startOfDay AND < startOfNextDay`)로 인덱스를 직접 활용하여 성능 최적화

#### 13.4.3 권한 및 보안

- **인증 필수**: JWT 토큰 필요
- **소유권 확인**: 사용자는 자신이 소유한 책의 메모만 조회 가능
- **책 존재 확인**: 존재하지 않는 책 ID인 경우 에러 반환
- **날짜 유효성**: 유효하지 않은 날짜 형식인 경우 에러 반환

### 13.5 오늘의 흐름과의 차이점

| 항목 | 오늘의 흐름 | 특정 책의 메모만 확인 |
|------|------------|---------------------|
| **조회 범위** | '오늘' 독서 활동을 한 모든 책의 메모 | 특정 책의 메모만 |
| **필터링** | 날짜 기준 | 날짜 + 책 기준 |
| **화면 구성** | 타임라인/책별 정렬 | 타임라인 정렬 (기본) |
| **사용 목적** | 전체 독서 활동 확인 | 특정 책의 독서 활동 확인 |

### 13.6 프론트엔드 연동

**UI/UX 고려사항:**
- 오늘의 흐름 화면에서 책 아이콘/제목 클릭 시 해당 책의 메모만 표시
- 필터 활성화 상태 표시 (현재 어떤 책이 선택되었는지 명확히 표시)
- 필터 해제 버튼 제공 (전체 메모로 돌아가기)
- 로딩 상태 표시 (메모 조회 중)
- 빈 상태 처리 (해당 날짜에 메모가 없는 경우)

**참고**: 프론트엔드 구현 상세 사항은 `MEMO_UI.md` 참조

---

## 14. 과거의 오늘의 흐름 기능 상세

### 14.1 기능 개요

"과거의 오늘의 흐름" 기능은 독서 캘린더와 연동하여 과거 특정 날짜에 작성한 메모를 조회하는 기능입니다. 사용자는 독서 캘린더에서 날짜를 선택하여 해당 날짜의 독서 활동을 확인할 수 있습니다.

**참고**: 노션 문서 (https://www.notion.so/29c4a8c850098058892bc37ed7f6f68a)

### 14.2 주요 특징

1. **독서 캘린더 연동**
   - 독서 캘린더에서 날짜 선택 시 해당 날짜의 오늘의 흐름 조회
   - 캘린더 UI를 통해 직관적으로 날짜 선택 가능
   - 월별/연별로 독서 활동을 시각적으로 확인 가능

2. **날짜 기반 메모 조회**
   - 선택한 날짜에 작성된 모든 메모를 조회
   - `memo_start_time`의 날짜 부분을 기준으로 필터링
   - 해당 날짜에 메모가 없는 경우 빈 상태 표시

3. **오늘의 흐름과 동일한 화면 구성**
   - 과거 날짜의 메모도 오늘의 흐름과 동일한 형식으로 표시
   - 책별로 그룹화되어 표시 (기본값)
   - 각 책 그룹 내에서 타임라인 순으로 정렬
   - 메모 카드, 태그, 시간 표시 등 모든 요소가 동일하게 표시됨

4. **특정 책 필터링 연동**
   - 과거 날짜의 오늘의 흐름에서도 특정 책을 선택하여 필터링 가능
   - 시나리오 2: 과거 날짜의 특정 책 메모 조회 기능과 연동

### 14.3 사용 시나리오

**시나리오 1: 독서 캘린더에서 과거 날짜 선택**
```
1. 사용자가 독서 캘린더 화면 확인
2. 특정 날짜 (예: 2024년 1월 15일) 클릭
3. 해당 날짜의 오늘의 흐름 화면으로 이동
4. 그날 작성한 모든 메모가 타임라인 순서로 표시됨
```

**시나리오 2: 과거 날짜의 특정 책 메모 조회**
```
1. 독서 캘린더에서 특정 날짜 선택
2. 해당 날짜의 오늘의 흐름 화면 표시
3. 특정 책을 선택하여 해당 날짜의 해당 책 메모만 조회
4. 날짜 파라미터를 통해 API 호출
```

**시나리오 3: 월별 독서 활동 확인**
```
1. 독서 캘린더에서 각 날짜를 클릭하여 확인
2. 각 날짜별로 작성한 메모 확인
3. 월별 독서 패턴 파악 가능
```

### 14.4 구현 세부사항

#### 14.4.1 API 엔드포인트

**GET `/api/v1/today-flow`**

**기능 요약:**
- 과거 특정 날짜의 메모를 조회합니다.
- `date` 파라미터로 조회할 날짜를 지정합니다 (기본값: 오늘).
- 독서 캘린더와 연동하여 과거 날짜의 메모를 조회할 수 있습니다.

**상세 API 설명은 섹션 6.1 MemoController의 `getTodayFlow` 메서드 주석을 참조하세요.**

**과거 날짜 조회 예시:**
```
GET /api/v1/today-flow?date=2024-01-15
→ 2024년 1월 15일에 작성된 모든 메모 목록 반환
```

**특정 책 필터링과의 연동:**
```
GET /api/v1/memos/books/{userBookId}?date=2024-01-15
→ 2024년 1월 15일에 작성된 특정 책의 메모 목록 반환
```

#### 14.4.2 데이터베이스 쿼리

**오늘의 흐름 조회 쿼리 (책별 그룹화):**
```sql
SELECT m FROM Memo m 
WHERE m.user.id = :userId 
  AND m.memoStartTime >= :startOfDay 
  AND m.memoStartTime < :startOfNextDay 
ORDER BY m.userShelfBook.id ASC, m.memoStartTime ASC
```

**특정 책의 과거 날짜 메모 조회 쿼리:**
```sql
SELECT m FROM Memo m 
WHERE m.user.id = :userId 
  AND m.userShelfBook.id = :userShelfBookId 
  AND m.memoStartTime >= :startOfDay 
  AND m.memoStartTime < :startOfNextDay 
ORDER BY m.memoStartTime ASC
```

**인덱스 활용:**
- `idx_memo_user_book (user_id, book_id)`: 사용자별 책별 메모 조회 최적화
- `idx_memo_memo_start_time (memo_start_time)`: 날짜 필터링 및 타임라인 정렬 최적화
- **날짜 범위 쿼리 사용**: `>= startOfDay AND < startOfNextDay` 형태로 인덱스를 직접 활용하여 성능 최적화

**타임존 고려 사항:**
- 현재 구현: 서버 타임존 기준으로 날짜 범위 계산 (`LocalDate.atStartOfDay()`)
- 향후 확장: 사용자 타임존 정보를 활용하여 다중 타임존 지원 가능
  - 오프라인 환경에서 작성된 메모의 타임존 정보를 보존
  - 온라인 동기화 시 사용자 타임존 기준으로 날짜 필터링
  - 예: 비행기에서 다른 나라로 이동 중 작성한 메모의 날짜 경계 처리
- 구현 위치: `MemoService.calculateDateRange()` 메서드에서 타임존 처리 로직 확장 가능

#### 14.4.3 독서 캘린더 연동

**캘린더에서 날짜 선택 시:**
1. 프론트엔드에서 선택한 날짜를 `date` 파라미터로 전달
2. `/api/v1/today-flow?date={selectedDate}` API 호출
3. 해당 날짜의 메모 목록을 받아서 오늘의 흐름 화면에 표시

**캘린더 표시 데이터:**
- 독서 캘린더는 완독한 책의 표지를 표시 (메모 작성과는 별개)
- 메모 작성 여부는 별도로 표시 가능 (향후 기능)

#### 14.4.4 권한 및 보안

- **인증 필수**: JWT 토큰 필요
- **소유권 확인**: 사용자는 자신이 작성한 메모만 조회 가능
- **날짜 유효성**: 유효하지 않은 날짜 형식인 경우 에러 반환
- **날짜 범위 제한**: 미래 날짜는 조회 불가 (선택 사항)

### 14.5 오늘의 흐름과의 차이점

| 항목 | 오늘의 흐름 (오늘) | 과거의 오늘의 흐름 |
|------|------------------|-------------------|
| **기본 날짜** | 오늘 날짜 | 선택한 날짜 |
| **접근 방법** | 직접 접근 | 독서 캘린더를 통한 접근 |
| **데이터 수정** | 가능 (메모 작성/수정/삭제) | 가능 (과거 메모도 수정 가능) |
| **화면 구성** | 동일 | 동일 |
| **정렬 방식** | 타임라인/책별 | 타임라인/책별 |

### 14.6 프론트엔드 연동

**UI/UX 고려사항:**
- 독서 캘린더에서 날짜 클릭 시 해당 날짜의 오늘의 흐름 화면으로 이동
- 현재 조회 중인 날짜를 명확히 표시 (헤더에 날짜 표시)
- 날짜 네비게이션: 이전 날짜/다음 날짜로 이동 버튼 제공
- 빈 상태 처리: 해당 날짜에 메모가 없는 경우 안내 메시지 표시
- 로딩 상태 표시 (메모 조회 중)

**독서 캘린더 연동:**
- 캘린더에서 날짜 선택 시 날짜 파라미터를 포함하여 오늘의 흐름 화면으로 라우팅
- URL 예시: `/today-flow?date=2024-01-15`

**참고**: 프론트엔드 구현 상세 사항은 `MEMO_UI.md` 참조

### 14.7 데이터 일관성

**과거 메모 수정 시:**
- 과거 날짜의 메모도 수정/삭제 가능
- 메모 수정 시 `updated_at`은 변경되지만 `memo_start_time`은 유지됨
- 과거 메모 수정은 현재 날짜의 오늘의 흐름에 영향을 주지 않음

**메모 작성 시간:**
- `memo_start_time`은 메모가 작성된 실제 시간을 나타냄
- 과거 날짜의 오늘의 흐름을 조회할 때는 `memo_start_time`의 날짜 부분을 기준으로 필터링
- 네트워크 지연 등으로 인해 `created_at`과 `memo_start_time`이 다를 수 있지만, 정렬은 `memo_start_time` 기준

---

## 15. 책 덮기(독서 활동 종료) 기능 상세

### 15.1 기능 개요

"책 덮기" 기능은 사용자가 독서 활동을 종료할 때 마지막으로 읽은 페이지 수를 기록하고, 독서 진행률을 업데이트하는 기능입니다. 오늘의 흐름 화면에서 메모 작성 후 독서 활동을 종료할 때 사용됩니다.

**참고**: 노션 문서 (https://www.notion.so/29d4a8c85009803aa90df9f6bdbf3568)

### 15.2 주요 특징

1. **독서 진행률 업데이트**
   - 마지막으로 읽은 페이지 수를 `user_books.reading_progress`에 기록
   - 내 서재 화면에서 독서 진행률이 자동으로 업데이트됨
   - 진행률은 전체 페이지 수 대비 퍼센티지로 계산됨

2. **카테고리 자동 변경**
   - 독서 진행률에 따라 책의 카테고리가 자동으로 변경됨
   - 카테고리 변경 기준:
     - `ToRead` (읽을 책) → `Reading` (읽는 중)
     - `Reading` (읽는 중) → `AlmostFinished` (거의 다 읽은 책)
     - `AlmostFinished` (거의 다 읽은 책) → `Finished` (다 읽은 책)
   - 카테고리 변경 로직은 `BookService`에 위임됨

3. **오늘의 흐름에서 섹션 구분**
   - 책 덮기 후 오늘의 흐름에서 해당 책의 메모가 섹션으로 구분됨
   - 독서 활동이 종료된 책과 진행 중인 책을 시각적으로 구분 가능
   - 사용자가 하루 동안 읽은 여러 책의 독서 활동을 명확히 파악 가능

4. **메모 작성과의 연계**
   - 메모 작성 후 독서 활동 종료 가능
   - 메모 작성 없이도 독서 활동 종료 가능
   - 독서 활동 종료 시점의 페이지 수가 기록됨
   - 한 세션은 **책 선택 → 해당 책에 대한 메모 작성 → 책 덮기(독서 활동 종료)**로 구성되며, 책 덮기 이후 다른 책에 메모를 작성하려면 다시 책 선택 UX를 통해 새 책을 선택해야 합니다. 이때 새로 선택된 책의 `userBookId`가 세션 구분 기준이 되며, 이 전환 시점이 새로운 세션의 시작점이 됩니다.

### 15.3 사용 시나리오

**시나리오 1: 메모 작성 후 책 덮기**
```
1. 사용자가 오늘의 흐름에서 메모 작성
2. 독서 활동 종료 시점에 도달
3. "책 덮기" 버튼 클릭
4. 마지막으로 읽은 페이지 수 입력 (예: 150페이지)
5. 독서 진행률 업데이트 및 카테고리 자동 변경
6. 오늘의 흐름에서 해당 책의 메모가 섹션으로 구분됨
```

**시나리오 2: 메모 없이 책 덮기**
```
1. 사용자가 독서를 진행하지만 메모는 작성하지 않음
2. 독서 활동 종료 시점에 도달
3. "책 덮기" 버튼 클릭
4. 마지막으로 읽은 페이지 수 입력
5. 독서 진행률만 업데이트됨 (메모는 없음)
```

**시나리오 3: 진행률에 따른 카테고리 변경**
```
1. 사용자가 책을 처음 읽기 시작 (카테고리: ToRead)
2. 첫 독서 활동 종료 시 페이지 수 입력 (예: 50페이지)
3. 진행률 계산 (예: 50/500 = 10%)
4. 카테고리가 자동으로 "Reading"으로 변경됨
5. 이후 독서 활동 종료 시마다 진행률 업데이트
6. 진행률이 특정 기준에 도달하면 카테고리 자동 변경
```

### 15.4 구현 세부사항

#### 15.4.1 API 엔드포인트

**POST `/api/v1/memos/books/{userBookId}/close`**

**요청 파라미터:**
- `userBookId` (Path Variable, 필수): 사용자 책 ID (UserShelfBook.id)
- `lastReadPage` (Request Body, 필수): 마지막으로 읽은 페이지 수 (1 이상)

**요청 Body 예시:**
```json
{
  "lastReadPage": 150
}
```

**응답:**
- 성공 시: 성공 메시지 반환
- 실패 시: 에러 메시지 반환 (책을 찾을 수 없음, 권한 없음, 유효하지 않은 페이지 수 등)

**예시:**
```
POST /api/v1/memos/books/123/close
Body: { "lastReadPage": 150 }
→ 독서 진행률 업데이트 및 카테고리 자동 변경
```

#### 15.4.2 데이터베이스 업데이트

**UserShelfBook 테이블 업데이트:**
```sql
UPDATE user_books 
SET reading_progress = :lastReadPage,
    updated_at = CURRENT_TIMESTAMP
WHERE id = :userBookId
```

**카테고리 자동 변경:**
- `BookService.updateBookCategory()` 메서드 호출
- 진행률에 따라 카테고리 자동 변경:
  - 진행률 0% → `ToRead`
  - 진행률 1~99% → `Reading` 또는 `AlmostFinished` (기준에 따라)
  - 진행률 100% → `Finished`

**카테고리 변경 기준 (예시):**
- 진행률 0%: `ToRead`
- 진행률 1~80%: `Reading`
- 진행률 81~99%: `AlmostFinished`
- 진행률 100%: `Finished`

#### 15.4.3 비즈니스 로직

**책 덮기 프로세스:**
1. 사용자 소유권 확인
2. 책 존재 여부 확인
3. 마지막 페이지 수 유효성 검증 (1 이상, 전체 페이지 수 이하)
4. `user_books.reading_progress` 업데이트
5. 진행률 계산 (전체 페이지 수 대비 퍼센티지)
6. `BookService.updateBookCategory()` 호출하여 카테고리 자동 변경
7. 성공 응답 반환

**진행률 계산:**
```java
double progressPercentage = (lastReadPage / totalPages) * 100;
```

**카테고리 변경 로직:**
- `BookService`에 위임하여 진행률에 따라 카테고리 자동 변경
- 카테고리 변경 기준은 `BookService`에서 관리

#### 15.4.4 오늘의 흐름에서의 표시

**섹션 구분:**
- 책 덮기 후 오늘의 흐름에서 해당 책의 메모가 섹션으로 구분됨
- 독서 활동이 종료된 책과 진행 중인 책을 시각적으로 구분
- 섹션 헤더에 책 정보 및 독서 활동 종료 시간 표시 가능

**프론트엔드 구현:**
- 책 덮기 버튼은 오늘의 흐름 화면에서 특정 책의 메모 영역에 표시
- 책 덮기 후 해당 책의 메모 영역이 섹션으로 구분되어 표시됨
- 섹션 헤더에 "독서 활동 종료" 표시 및 종료 시간 표시

### 15.5 유효성 검증

**입력값 검증:**
- `lastReadPage`는 필수값이며, 1 이상이어야 함
- `lastReadPage`는 해당 책의 전체 페이지 수를 초과할 수 없음
- 전체 페이지 수는 `user_books` 테이블의 `book` 정보에서 가져옴

**에러 처리:**
- 책을 찾을 수 없는 경우: `IllegalArgumentException("책을 찾을 수 없습니다.")`
- 권한이 없는 경우: `IllegalArgumentException("권한이 없습니다.")`
- 유효하지 않은 페이지 수: `IllegalArgumentException("페이지 수는 1 이상이어야 합니다.")`
- 전체 페이지 수 초과: `IllegalArgumentException("페이지 수는 전체 페이지 수를 초과할 수 없습니다.")`

### 15.6 다른 기능과의 연동

**내 서재와의 연동:**
- 독서 진행률이 업데이트되면 내 서재 화면에 즉시 반영됨
- 진행률 바(Progress Bar)가 업데이트됨
- 카테고리 변경 시 내 서재의 책 목록에서도 카테고리 변경됨

**독서 캘린더와의 연동:**
- 완독한 책(`Finished` 카테고리)의 표지가 캘린더에 표시됨
- 완독 날짜는 `user_books.finished_at`에 기록될 수 있음 (향후 기능)

**오늘의 흐름과의 연동:**
- 책 덮기 후 오늘의 흐름에서 해당 책의 메모가 섹션으로 구분됨
- 독서 활동 종료 시간이 기록되어 섹션 헤더에 표시 가능

### 15.7 프론트엔드 연동

**UI/UX 고려사항:**
- 오늘의 흐름 화면에서 특정 책의 메모 영역에 "책 덮기" 버튼 표시
- 책 덮기 버튼 클릭 시 모달 또는 입력 폼 표시
- 마지막 페이지 수 입력 필드 제공
- 입력값 유효성 검증 (1 이상, 전체 페이지 수 이하)
- 책 덮기 후 해당 책의 메모 영역이 섹션으로 구분되어 표시
- 섹션 헤더에 "독서 활동 종료" 표시 및 종료 시간 표시
- 로딩 상태 표시 (진행률 업데이트 중)
- 성공/실패 메시지 표시

**책 덮기 모달/폼:**
```
┌─────────────────────────────────┐
│  책 덮기                        │
├─────────────────────────────────┤
│  책 제목: [책 제목]              │
│  현재 진행률: 30% (150/500페이지)│
│                                 │
│  마지막으로 읽은 페이지:        │
│  [____] 페이지                  │
│                                 │
│  [취소]  [확인]                 │
└─────────────────────────────────┘
```

**참고**: 프론트엔드 구현 상세 사항은 `MEMO_UI.md` 참조

### 15.8 데이터 일관성

**진행률 업데이트:**
- `reading_progress`는 항상 최신 값으로 유지됨
- 여러 번 책 덮기를 호출해도 마지막 값이 유지됨
- 진행률은 전체 페이지 수 대비 퍼센티지로 계산됨

**카테고리 변경:**
- 카테고리 변경은 진행률에 따라 자동으로 수행됨
- 사용자가 수동으로 카테고리를 변경할 수도 있음 (내 서재에서)
- 카테고리 변경 로직은 `BookService`에서 중앙 관리됨

**메모와의 관계:**
- 책 덮기는 메모 작성과 독립적으로 동작 가능
- 메모 작성 없이도 독서 활동 종료 가능
- 책 덮기 후에도 메모 작성 가능 (다음 독서 활동 시작 시)

---

## 16. 다음 단계

1. **태그 관리 기능**: 태그별 메모 개수 통계 기능
2. **메모 통계**: 사용자별 메모 작성 통계 기능

**참고**: 태그 관련 상세 요구사항은 노션 문서 (https://www.notion.so/2994a8c8500981999b61ed46802fabbf) 참조

---

## 17. 참고사항

- 기존 코드베이스의 패턴을 따름 (Controller → Service → Repository)
- MapStruct를 사용한 DTO 변환
- Flyway를 사용한 데이터베이스 마이그레이션
- JWT 기반 인증 시스템 활용
- Spring Data JPA를 사용한 데이터 접근
- **태그 저장 방식**: Many-to-Many 관계 채택 (태그별 메모 조회 및 그룹화 최적화)
- **태그 설계 결정**: 상세한 설계 결정 근거는 `TAG_STORAGE_DESIGN_DECISION.md` 참조
- **태그 상세 사항**: 태그 관련 추가 설명은 노션 문서 (https://www.notion.so/2994a8c8500981999b61ed46802fabbf) 참조
- **프론트엔드 구현**: UI/UX 관련 사항은 `MEMO_UI.md` 참조

## 18. 데이터베이스 컬럼 상세

### 18.1 memo 테이블 컬럼 설명

| 컬럼명 | 타입 | 설명 | 제약조건 |
|--------|------|------|----------|
| `id` | BIGINT | 메모 고유 ID | PRIMARY KEY, AUTO_INCREMENT |
| `user_id` | BIGINT | 작성자 ID | NOT NULL, FOREIGN KEY → users(id) |
| `book_id` | BIGINT | 사용자 책 ID (UserShelfBook.id) | NOT NULL, FOREIGN KEY → user_books(id) |
| `page_number` | INT | 메모 작성 시점의 SESSION 모드 기준 초기 위치 | NOT NULL |
| `content` | TEXT | 메모 내용 | NOT NULL (내용 없으면 저장 불가) |
| `memo_start_time` | TIMESTAMP | 메모가 작성된 시간 (메모 시작 시간) | NOT NULL |
| `created_at` | TIMESTAMP | 메모 생성 시간 | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| `updated_at` | TIMESTAMP | 메모 수정 시간 | NOT NULL, ON UPDATE CURRENT_TIMESTAMP |

**중요 사항:**
- `book_id`는 `user_books.id`를 참조 (ISBN이 아님)
- **`page_number`의 의미:**
  - 메모 작성 시점의 SESSION 모드 기준 초기 위치를 나타내는 메타데이터입니다.
  - 정렬 방식(SESSION/BOOK/TAG)이 변경되어도 `page_number`는 변경하지 않으며, 원본 위치 정보를 보존합니다.
  - 실제 UI 렌더링 위치는 프론트엔드에서 현재 선택된 정렬 방식에 따라 동적으로 계산됩니다.
  - 메모 수정 시에도 `page_number`는 변경하지 않습니다 (원본 위치 정보 보존).
  - `page_number`는 참조용 메타데이터이며, 실제 렌더링 위치와는 독립적입니다.
- **`memo_start_time`과 `created_at`의 차이:**
  - **`memo_start_time`**: 사용자가 실제로 메모 작성을 시작한 시간 (비즈니스 로직상 의미 있는 시간)
    - 클라이언트에서 전송하거나 서버에서 자동 생성 가능
    - **타임라인 정렬의 기준**으로 사용됨
    - **날짜 필터링의 기준**으로 사용됨 (어느 날짜의 바인더 노트에 속할지 결정)
    - 과거 시간도 설정 가능 (과거의 오늘의 흐름 기록용)
    - 사용자가 메모를 언제 작성했는지를 나타내는 시간
  - **`created_at`**: 데이터베이스 레코드가 생성된 시간 (기술적 메타데이터)
    - 서버에서 자동으로 설정 (`DEFAULT CURRENT_TIMESTAMP`)
    - 언제 DB에 저장되었는지를 추적하는 감사(audit) 목적
    - 메모 수정 시 변경되지 않음 (`updatable = false`)
    - 일반적으로 `memo_start_time`과 같거나 약간 늦을 수 있음 (네트워크 지연 등)
    - 날짜 필터링에는 사용되지 않음 (오직 `memo_start_time` 기준)
- `content`는 필수이며, 빈 문자열은 저장 불가

**사용 예시:**
- 사용자가 오후 2시에 메모 작성을 시작했지만, 네트워크 지연으로 오후 2시 1분에 저장된 경우:
  - `memo_start_time`: 2024-01-15 14:00:00 (사용자가 작성 시작한 시간)
  - `created_at`: 2024-01-15 14:01:00 (DB에 저장된 시간)
  - 타임라인 정렬은 `memo_start_time` 기준으로 정렬되므로, 오후 2시에 작성한 메모로 표시됨

**자정 경과 시 메모 저장 예시:**
- 사용자가 2024년 1월 15일 23시 50분에 메모 작성을 시작했지만, 자정(2024년 1월 16일 00시 10분)이 지난 후에 저장 버튼을 누른 경우:
  - `memo_start_time`: 2024-01-15 23:50:00 (사용자가 작성 시작한 시간)
  - `created_at`: 2024-01-16 00:10:00 (DB에 저장된 시간)
  - 날짜 필터링은 `memo_start_time` 기준으로 수행되므로, 이 메모는 2024년 1월 15일의 바인더 노트에 저장되고 표시됩니다.
  - 저장 시점(`created_at`)이 1월 16일이더라도, `memo_start_time` 기준으로 필터링되므로 1월 15일의 바인더 노트에 포함됩니다.

### 18.2 memo_tags 테이블 컬럼 설명

| 컬럼명 | 타입 | 설명 | 제약조건 |
|--------|------|------|----------|
| `id` | BIGINT | 관계 고유 ID | PRIMARY KEY, AUTO_INCREMENT |
| `memo_id` | BIGINT | 메모 ID | NOT NULL, FOREIGN KEY → memo(id) |
| `tag_id` | BIGINT | 태그 ID | NOT NULL, FOREIGN KEY → tags(id) |
| `created_at` | TIMESTAMP | 관계 생성 시간 | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

**중요 사항:**
- `(memo_id, tag_id)` 조합은 UNIQUE 제약으로 중복 방지
- 메모 삭제 시 `ON DELETE CASCADE`로 관계 자동 삭제

### 18.3 tags 테이블 컬럼 설명

| 컬럼명 | 타입 | 설명 | 제약조건 |
|--------|------|------|----------|
| `id` | BIGINT | 태그 고유 ID | PRIMARY KEY, AUTO_INCREMENT |
| `category` | ENUM('TYPE', 'TOPIC') | 태그 대분류 (유형/주제) | NOT NULL |
| `code` | VARCHAR(50) | 태그 캐논컬 키 (예: 'impressive-quote') | NOT NULL, UNIQUE |
| `sort_order` | INT | 정렬 순서 (가나다순) | NOT NULL |
| `is_active` | BOOLEAN | 활성화 상태 | NOT NULL, DEFAULT TRUE |
| `created_at` | TIMESTAMP | 태그 생성 시간 | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| `updated_at` | TIMESTAMP | 태그 수정 시간 | NOT NULL, ON UPDATE CURRENT_TIMESTAMP |

**중요 사항:**
- 태그는 고정 카탈로그로 관리되며, 대분류별(유형/주제) 최대 8개로 사전에 정의·등록되어 있습니다
- 태그 코드(`code`)는 캐논컬 키로 저장되며, 라벨(표시명)은 i18n/카탈로그로 매핑됩니다
- 태그 코드는 UNIQUE 제약으로 중복 방지됩니다
- 태그는 전역적으로 공유되며, 모든 사용자가 동일한 태그를 사용할 수 있습니다
- 태그는 메모와 Many-to-Many 관계로, 하나의 태그는 여러 메모에 사용될 수 있습니다
- 태그 대분류(`category`)는 정렬 시 그룹화 기준으로 사용됩니다

**태그 사용 규칙:**
- 각 메모에는 하나 이상의 태그를 설정할 수 있습니다 (선택 사항이지만 권장)
- 태그는 메모 분류 카테고리로 사용됩니다
- 태그 코드는 최대 50자까지 가능합니다
- **태그 자동 연결**: 사용자가 메모에 대해 어떠한 태그도 선택하지 않은 경우, 시스템에서 자동으로 '기타' 태그를 연결합니다
  - 이는 태그가 자동으로 생성되는 것이 아니라, 사전에 정의된 '기타' 태그가 자동으로 연결되는 것입니다
  - '기타' 태그는 카탈로그에 미리 등록되어 있어야 하며, 코드는 'etc'로 관리됩니다
- 태그는 사전 정의된 카탈로그 내에서만 선택 가능하며, 사용자가 임의로 생성할 수 없습니다
- 허용 목록 검증: 전달된 태그 코드가 카탈로그 코드와 정확히 일치하는지 체크하여 오타·케이스 차이를 방지합니다
- 태그는 전역적으로 공유되므로 모든 사용자가 동일한 태그를 사용할 수 있습니다
- 메모 정렬 변경 시 태그를 기준으로 필터링하여 특정 태그가 있는 메모만 표시 가능합니다

**태그와 분류 카테고리의 관계:**
- 태그는 메모를 분류하는 데 사용되는 카테고리 역할을 합니다
- 태그는 대분류(유형/주제)로 구분되며, 정렬 시 선택된 대분류의 태그를 기준으로 그룹화됩니다
- 메모 정렬 변경 시 태그를 기준으로 메모를 필터링할 수 있습니다

**참고**: 태그 관련 상세 사항은 노션 문서 (https://www.notion.so/2994a8c8500981999b61ed46802fabbf) 참조

