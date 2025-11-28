# 태그 저장 방식 설계 결정서

> **참고**: 본 문서는 태그 저장 방식의 기술적 설계 결정에 집중합니다.  
> 태그의 비즈니스 로직, 사용 시나리오, 메모 기능과의 통합 등에 대한 상세 내용은 `MEMO_FEATURE_IMPLEMENTATION_PLAN.md` 문서의 섹션 12를 참조하세요.

## 0. 태그 시스템 개요

### 0.1 태그의 역할

태그는 메모를 분류하고 관리하는 데 사용되는 핵심 기능입니다:

1. **메모 분류**: 각 메모에 하나 이상의 태그를 설정하여 메모를 분류
2. **필터링**: 태그를 기준으로 메모를 필터링하여 조회
3. **통계 및 분석**: 태그별 메모 통계를 통한 독서 패턴 분석 (향후 기능)

### 0.2 태그 특징

- **전역 공유**: 태그는 모든 사용자 간에 공유됨 (동일한 태그 사용)
- **고정 카탈로그**: 태그는 대분류별(유형/주제) 최대 8개로 사전에 정의·등록되어 있으며, 사용자가 임의로 생성하지 않습니다
- **대분류 구분**: 태그는 "대분류"로 구분됩니다 (TYPE: 유형, TOPIC: 주제)
  - 각 대분류마다 태그 종류가 최대 8개로 관리됩니다
  - 정렬 시 선택된 대분류의 태그를 기준으로 그룹화됩니다
- **Many-to-Many**: 하나의 메모는 여러 태그를 가질 수 있고, 하나의 태그는 여러 메모에 사용될 수 있음
- **기본 태그 자동 할당**: 사용자가 태그를 입력하지 않았을 때 '기타' 태그가 자동으로 연결됩니다

### 0.3 대표 태그 결정 규칙

하나의 메모에는 여러 태그가 붙을 수 있지만, 정렬 및 그룹화에 사용하는 기준은 항상 **대표 태그 1개**입니다.

**기본 우선순위(TYPE 우선)**:
1. 1순위: `TYPE` 대분류(유형)에 속하는 태그들 중 `sort_order`가 가장 작은 태그
2. 2순위: `TOPIC` 대분류(주제)에 속하는 태그들 중 `sort_order`가 가장 작은 태그
3. 3순위: 태그가 없는 경우, 사전에 정의된 '기타' 태그(코드: `etc`)

**TAG 모드에서의 대분류 선택에 따른 우선순위 변경**:
- 사용자가 선택한 대분류가 1순위가 되고, 나머지 대분류가 2순위가 됩니다
- 예: "주제(TOPIC)" 선택 시 → `TOPIC` > `TYPE` > 기타(`etc`)

**참고**: 대표 태그 결정 규칙의 상세 내용과 사용 시나리오는 `MEMO_FEATURE_IMPLEMENTATION_PLAN.md` 섹션 12.2.2를 참조하세요.

---

## 1. 요구사항 분석

### 1.1 현재 요구사항
- 메모에 여러 태그를 부여할 수 있음 (예: ["인상깊은구절", "의문점", "인용"])
- 태그별로 메모를 조회할 수 있어야 함
- 태그 통계 및 관리 기능 (향후 확장)

---

## 2. 태그 저장 방식

### 2.1 선택된 방식: 별도 Tags 테이블 + Many-to-Many 관계

#### 구조
```sql
-- Tags 테이블 (태그 마스터)
CREATE TABLE tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category ENUM('TYPE', 'TOPIC') NOT NULL,  -- 태그 대분류 (유형/주제)
    code VARCHAR(50) NOT NULL UNIQUE,           -- 캐논컬 키 (예: 'impressive-quote')
    sort_order INT NOT NULL,                   -- 정렬 순서 (가나다순)
    is_active BOOLEAN NOT NULL DEFAULT TRUE,   -- 활성화 상태
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tags_code (code),
    INDEX idx_tags_category (category),
    INDEX idx_tags_category_sort (category, sort_order)
);

-- Memo-Tag 중간 테이블 (Many-to-Many)
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

-- Memo 테이블 (tags 컬럼 제거)
CREATE TABLE memo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    page_number INT NOT NULL,
    content TEXT NOT NULL,
    memo_start_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (book_id) REFERENCES user_books(id) ON DELETE CASCADE,
    INDEX idx_memo_user_book (user_id, book_id),
    INDEX idx_memo_created_at (created_at),
    INDEX idx_memo_page_number (book_id, page_number)
);
```

#### 장점
- ✅ **태그별 메모 조회가 효율적** (인덱스 활용 가능)
  ```sql
  -- 효율적인 쿼리
  SELECT m.* FROM memo m
  INNER JOIN memo_tags mt ON m.id = mt.memo_id
  INNER JOIN tags t ON mt.tag_id = t.id
  WHERE t.code = 'impressive-quote' AND m.book_id = ?
  ```
- ✅ **태그별 그룹화가 효율적** (DB 레벨에서 처리 가능)
- ✅ 태그 정규화 가능 (오타 방지, 대소문자 통일)
- ✅ 태그 통계/분석 용이
- ✅ 태그 자동완성, 인기 태그 등 확장 기능 구현 용이
- ✅ 데이터 무결성 보장 (외래키 제약)

#### 단점
- ❌ 구현 복잡도 증가 (추가 테이블 2개)
- ❌ 조인 쿼리 필요 (성능 최적화 필요)
- ❌ 메모 조회 시 태그를 함께 가져오려면 조인 필요

---

## 3. 쿼리 예시

### 3.1 태그별 메모 조회

```sql
-- 태그별 메모 조회 (인덱스 활용)
SELECT m.* 
FROM memo m
INNER JOIN memo_tags mt ON m.id = mt.memo_id
INNER JOIN tags t ON mt.tag_id = t.id
WHERE m.book_id = ? AND t.code = 'impressive-quote'
ORDER BY m.memo_start_time;
```

### 3.2 태그별로 그룹화된 메모 조회

```sql
-- 태그별로 그룹화된 결과
SELECT t.code, GROUP_CONCAT(m.id) as memo_ids
FROM memo m
INNER JOIN memo_tags mt ON m.id = mt.memo_id
INNER JOIN tags t ON mt.tag_id = t.id
WHERE m.book_id = ?
GROUP BY t.code;
```

### 3.3 성능 특징

- ✅ 인덱스 활용으로 빠른 조회 가능
- ✅ DB 레벨에서 그룹화 처리로 효율적

---

## 4. 채택 이유

### 4.1 결정 근거
1. **태그별 메모 조회 기능**: 태그를 기준으로 메모를 효율적으로 조회해야 함
2. **확장성**: 추후 태그 통계, 인기 태그, 태그 검색 등 기능 추가 용이
3. **데이터 무결성**: 태그 정규화로 데이터 품질 향상
4. **성능**: 인덱스 활용으로 태그별 조회 최적화

### 4.2 구현 구조

#### 엔티티 설계
```java
// Tag 엔티티
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
}

// TagCategory Enum
enum TagCategory {
    TYPE,   // 유형
    TOPIC   // 주제
}
```

// Memo 엔티티 (수정)
@Entity
@Table(name = "memo")
public class Memo {
    // ... 기존 필드들 ...
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "memo_tags",
        joinColumns = @JoinColumn(name = "memo_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags = new ArrayList<>();
}
```

#### Repository 메서드
```java
public interface MemoRepository extends JpaRepository<Memo, Long> {
    // 태그별 메모 조회
    @Query("SELECT m FROM Memo m " +
           "JOIN m.tags t " +
           "WHERE m.userShelfBook.id = :bookId AND t.code = :tagCode " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByBookIdAndTagCode(@Param("bookId") Long bookId, 
                                       @Param("tagCode") String tagCode);
    
    // 여러 태그 중 하나라도 포함된 메모 조회
    @Query("SELECT DISTINCT m FROM Memo m " +
           "JOIN m.tags t " +
           "WHERE m.userShelfBook.id = :bookId AND t.code IN :tagCodes " +
           "ORDER BY m.memoStartTime ASC")
    List<Memo> findByBookIdAndTagCodes(@Param("bookId") Long bookId, 
                                       @Param("tagCodes") List<String> tagCodes);
}
```

---

## 5. 구현 시 고려사항

### 5.1 태그 생성 전략
- **기본 태그 자동 할당**: 사용자가 태그를 입력하지 않았을 때만 자동 생성 및 할당
  - 기본값 태그 이름: `"기타"`
  - 메모 저장 시 태그가 비어있거나 없는 경우에만 적용
- **대소문자 처리**: 태그 이름을 소문자로 통일하여 저장
- **중복 방지**: UNIQUE 제약으로 동일 태그 중복 방지

### 5.2 성능 최적화

태그 기능과 메모 조회 기능의 효율적인 동작을 위한 성능 최적화 전략입니다.

#### 5.2.1 인덱스 전략

태그 관련 테이블에 대한 인덱스 구성은 조회 성능에 핵심적인 역할을 합니다.

**memo_tags 테이블:**
- `idx_memo_tags_memo (memo_id)`: 메모별 태그 조회 시 사용
  - 메모 조회 시 해당 메모의 태그를 빠르게 가져올 때 활용
  - 예: `SELECT * FROM memo_tags WHERE memo_id = ?`
- `idx_memo_tags_tag (tag_id)`: 태그별 메모 조회 시 사용
  - 특정 태그가 붙은 모든 메모를 조회할 때 활용
  - 예: `SELECT * FROM memo_tags WHERE tag_id = ?`
- `UNIQUE KEY uk_memo_tag (memo_id, tag_id)`: 중복 방지 및 조인 최적화
  - 동일 메모에 동일 태그 중복 방지
  - 조인 쿼리 시 복합 인덱스로 활용 가능

**tags 테이블:**
- `idx_tags_code (code)`: 태그 코드 검색 시 사용
  - 태그 코드로 태그 조회 시 활용 (태그 자동 연결 로직)
  - 예: `SELECT * FROM tags WHERE code = 'etc'`
- `idx_tags_category (category)`: 태그 대분류별 검색 시 사용
  - 특정 대분류(TYPE/TOPIC)의 태그 목록 조회 시 활용
  - 예: `SELECT * FROM tags WHERE category = 'TYPE' AND is_active = TRUE`
- `idx_tags_category_sort (category, sort_order)`: 태그 대분류별 정렬 검색 시 사용
  - 태그 그룹화 시 대분류별 정렬 순서로 조회
  - 예: `SELECT * FROM tags WHERE category = 'TYPE' ORDER BY sort_order ASC`
  - 복합 인덱스로 정렬과 필터링을 동시에 최적화

**memo 테이블 (태그 관련 조회 최적화):**
- `idx_memo_memo_start_time (memo_start_time)`: 날짜 범위 쿼리 및 타임라인 정렬 최적화
  - 날짜 기반 메모 조회 시 활용 (오늘의 흐름, 과거 날짜 조회)
  - 날짜 범위 쿼리(`>= startOfDay AND < startOfNextDay`)에서 인덱스 직접 활용
- `idx_memo_user_book (user_id, book_id)`: 사용자별 책별 메모 조회 최적화
  - 특정 책의 메모 조회 시 활용
  - 복합 인덱스로 사용자와 책 필터링 동시 최적화

#### 5.2.2 지연 로딩(Lazy Loading) 전략

N+1 문제를 방지하고 필요한 데이터만 로드하기 위한 전략입니다.

- **태그 지연 로딩**: `@ManyToMany(fetch = FetchType.LAZY)`
  - 메모 조회 시 태그는 기본적으로 로드하지 않음
  - 태그 정보가 필요한 경우에만 별도 쿼리로 로드
  - 태그별 그룹화나 태그 표시가 필요한 경우에만 조인

- **연관 엔티티 지연 로딩**:
  - `Memo.userShelfBook`: `@ManyToOne(fetch = FetchType.LAZY)`
  - `Memo.user`: `@ManyToOne(fetch = FetchType.LAZY)`
  - `UserShelfBook.book`: `@ManyToOne(fetch = FetchType.LAZY)`
  - 메모 리스트 조회 시 연관 엔티티를 즉시 로드하지 않아 초기 조회 속도 향상

#### 5.2.3 배치 조인(Batch Join) 전략

태그와 함께 메모를 조회해야 하는 경우 N+1 문제를 방지하기 위한 전략입니다.

- **`@EntityGraph` 활용**:
  - 특정 메모 조회 시 태그를 함께 로드해야 하는 경우 사용
  - 예: 메모 상세 조회 시 태그 정보가 필요한 경우

- **`JOIN FETCH` 활용**:
  - 태그별 메모 조회 쿼리에서 태그와 메모를 한 번에 조회
  - 예: `SELECT m FROM Memo m JOIN FETCH m.tags t WHERE t.code = :tagCode`
  - 태그별 그룹화 조회 시 태그 정보가 필요한 경우 활용

- **배치 조회 최적화**:
  - 여러 책 정보를 한 번에 조회하는 경우 `findAllById()` 활용
  - 최근 메모 작성 책 목록 조회 시 N+1 문제 방지

#### 5.2.4 쿼리 최적화 전략

태그 기능 관련 쿼리의 성능을 최적화하기 위한 전략입니다.

- **날짜 범위 쿼리 최적화**:
  - 날짜 필터링 시 범위 쿼리(`>= startOfDay AND < startOfNextDay`) 사용
  - `memo_start_time` 인덱스를 직접 활용하여 전체 스캔 방지
  - 예: 오늘의 흐름, 과거 날짜 조회, 특정 책의 날짜별 메모 조회

- **태그별 조회 쿼리 최적화**:
  - 태그 코드 기반 조회 시 인덱스 활용
  - 태그별 메모 조회 시 조인 순서 최적화 (tags → memo_tags → memo)
  - 복합 조건(날짜 + 태그) 조회 시 인덱스 조합 활용

- **태그 그룹화 쿼리 최적화**:
  - 태그별로 그룹화할 때 DB 레벨에서 `GROUP BY` 활용
  - 애플리케이션 레벨 그룹화보다 DB 레벨 그룹화가 효율적
  - 대표 태그 결정 로직은 애플리케이션 레벨에서 처리

#### 5.2.5 태그 자동 연결 최적화

메모 저장 시 태그를 자동으로 연결하는 과정의 성능 최적화입니다.

- **태그 조회 캐싱 고려** (향후 개선):
  - 태그 카탈로그는 자주 변경되지 않으므로 캐싱 가능
  - '기타' 태그(code: 'etc') 조회 빈도가 높으므로 캐싱 효과적
  - 현재는 매번 DB 조회, 향후 Redis 등 캐시 활용 가능

- **배치 태그 처리**:
  - 여러 태그를 한 번에 처리할 때 단일 쿼리로 최적화
  - 태그 코드 리스트를 받아서 IN 절로 한 번에 조회

#### 5.2.6 성능 모니터링 고려사항

- **쿼리 실행 계획 확인**: 인덱스가 제대로 활용되는지 확인
- **조인 쿼리 성능**: 태그별 그룹화 쿼리의 실행 시간 모니터링
- **대용량 데이터 대비**: 메모가 많아질 경우 페이징 처리 고려

### 5.3 태그 관리
- **태그 통계**: 태그별 메모 개수 통계 (추후 기능)

---

## 6. 결론

**선택된 방식: Many-to-Many 관계**

이 방식의 장점:
1. 태그별 메모 조회 및 필터링이 효율적으로 가능
2. DB 레벨에서 효율적인 태그별 그룹화 및 통계 가능
3. 확장성과 데이터 무결성 확보
4. 성능 최적화 가능 (인덱스 활용)

구현 복잡도는 증가하지만, 장기적으로 유지보수성과 확장성이 우수합니다.

