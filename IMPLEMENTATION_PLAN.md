# 도서 검색 및 내 서재 기능 구현 계획

## 📋 개요

이 문서는 도서 검색 기능과 내 서재 하위 기능의 구현 계획 및 순서를 기록합니다.

**작성일**: 2024년  
**기준 문서**: 기능 정의서 (기능 정의서 2994a8c850098070a0b8ff24fb71e366.csv)

---

## 🎯 현재 상태

### ✅ 이미 구현된 기능
- **도서 검색 (기본)**: 알라딘 API를 통한 기본 도서 검색
- **내 서재에 책 추가 (기본)**: 검색한 책을 서재에 추가하는 기본 기능
- **내 서재 조회**: 서재에 저장된 책 목록 조회
- **책 제거**: 서재에서 책 제거
- **책 카테고리 변경 (수동)**: 책의 읽기 상태 수동 변경

### ⚠️ 개선 및 추가 필요 기능
- 도서 검색 필터 명확화
- 도서 세부 정보 검색 API
- 내 서재에 책 직접 등록
- 책 상세 정보 변경 (독서 현황 정보)
- 책 카테고리 자동 변경 (진행률 기반)
- 저장된 책 정렬 변경

---

## 📝 구현 우선순위

### Phase 1: 도서 검색 기능 완성 (최우선)

#### ✅ 1-1. 도서 검색 필터 개선
**우선순위**: 최상  
**상태**: 계획 완료, 구현 대기

**요구사항**:
- 필터 타입: **Title(도서명)**, **Author(저자명)**, **Publisher(출판사명)**만 사용
- `BookSearchRequest`에 필터 타입을 명확하게 정의

**구현 계획**:
1. **Enum 클래스 생성**
   - 파일: `src/main/java/com/readingtracker/server/common/constant/BookSearchFilter.java`
   - Enum 값:
     - `TITLE("Title")` - 도서명
     - `AUTHOR("Author")` - 저자명
     - `PUBLISHER("Publisher")` - 출판사명
   - 알라딘 API QueryType 값과 매핑하는 메서드 포함 (`getApiValue()`)

2. **BookSearchRequest 수정**
   - `queryType` 필드 타입: `String` → `BookSearchFilter`로 변경
   - 기본값: `BookSearchFilter.TITLE`
   - 생성자 및 getter/setter 수정

3. **AladinApiService 수정**
   - `BookSearchFilter`를 알라딘 API QueryType 문자열로 변환
   - `searchBooks` 메서드에서 Enum의 API 값 사용

4. **BookController 수정**
   - `searchBooks` 메서드의 `queryType` 파라미터 타입 변경
   - Swagger 문서 업데이트: "검색 필터 (TITLE: 도서명, AUTHOR: 저자명, PUBLISHER: 출판사명)"

**예상 작업 파일**:
- `BookSearchFilter.java` (신규)
- `BookSearchRequest.java` (수정)
- `AladinApiService.java` (수정)
- `BookController.java` (수정)

---

#### 📌 1-2. 도서 세부 정보 검색 API 추가
**우선순위**: 높음  
**상태**: 계획 완료, 구현 대기

**요구사항**:
- 선택한 책의 상세 정보를 조회
- ISBN을 통해 알라n API에서 상세 정보 조회

**구현 계획**:
1. **DTO 생성**
   - `BookDetailResponse.java` (신규)
   - 필드: 도서명, 저자명, 출판사명, ISBN, 출판일, 표지URL, 책소개, 전체 페이지 수, 메인 태그

2. **AladinApiService 확장**
   - `getBookDetail(String isbn)` 메서드 추가
   - 알라딘 API ItemLookUp 호출

3. **BookController 엔드포인트 추가**
   - `GET /api/v1/books/{isbn}` 또는 `GET /api/v1/books/detail?isbn={isbn}`
   - 비인증 접근 가능

**예상 작업 파일**:
- `BookDetailResponse.java` (신규)
- `AladinApiService.java` (수정)
- `BookController.java` (수정)

---

### Phase 2: 내 서재 기능 확장

#### 📌 2-1. 내 서재에 책 정보 직접 등록
**우선순위**: 높음  
**상태**: 계획 완료, 구현 대기

**요구사항**:
- 사용자가 찾는 책이 없을 경우, 직접 책 상세 정보 입력
- **필수 입력 필드** (입력하지 않으면 저장 실패):
  - ISBN (필수)
  - 도서명 (필수)
  - 저자명 (필수)
- Book 테이블에 이미 존재하는 ISBN이면 저장 실패

**카테고리별 입력값 상세**:
**저장 위치**: 아래 모든 값들은 **`user_books` 테이블**에 저장됩니다. (Memo table과 구분)

1. **읽고 싶은 책 (ToRead)**
   - 기대평 (선택사항)
     - Column: `expectation` (VARCHAR(500))
     - Table: `user_books`
     - Type: String
     - Nullable: true
     - 설명: 사용자가 책을 읽기 전에 입력하는 500자 이하의 글. 사용자가 내 서재에 책을 저장할 때 'ToRead' 카테고리로 설정하는 경우, 원하는 경우에만 입력 가능. 입력하지 않아도 해당 책의 상세 정보를 UserShelfBook table에 저장 가능
     - 검증: 500자 이하
     - **주의**: Memo table과 혼동을 피하기 위해 `memo` 대신 `expectation` 컬럼명 사용. 독서 메모는 별도의 Memo table에 저장됨

2. **읽고 있는 책 (Reading)**
   - 독서 시작일 (필수)
     - Column: `reading_start_date` (DATE)
     - Table: `user_books`
     - Type: LocalDate
     - Format: 년-월-날짜 (yyyy-MM-dd)
     - Nullable: false
   - 현재 읽은 페이지 수 (필수)
     - Column: `reading_progress` (INT)
     - Table: `user_books`
     - Type: Integer
     - 설명: 전체 분량은 Book table에서 가져오는 책의 상세 정보(`total_pages`)에서 가져오기, 사용자는 현재 마지막으로 읽은 페이지 수만 정수로 입력
     - Nullable: false
     - 검증: 0 이상, total_pages 이하
   - 구매/대여 여부 (선택사항)
     - Column: `purchase_type` (ENUM 또는 VARCHAR, 신규 추가 필요)
     - Table: `user_books`
     - Type: Enum (예: `PurchaseType.PURCHASE`, `PurchaseType.RENTAL`)
     - Nullable: true
     - 설명: 구매 혹은 대여 중 하나의 값을 enum으로 입력
     - **주의**: 현재 UserShelfBook 엔티티에 해당 필드가 없으므로, DB 마이그레이션 파일 추가 필요

3. **거의 다 읽은 책 (AlmostFinished)**
   - 독서 시작일 (필수)
     - Column: `reading_start_date` (DATE)
     - Table: `user_books`
     - Type: LocalDate
     - Format: 년-월-날짜 (yyyy-MM-dd)
     - Nullable: false
   - 현재 읽은 페이지 수 (필수)
     - Column: `reading_progress` (INT)
     - Table: `user_books`
     - Type: Integer
     - 설명: 전체 분량은 Book table에서 가져오는 책의 상세 정보(`total_pages`)에서 가져오기, 사용자는 현재 마지막으로 읽은 페이지 수만 정수로 입력
     - Nullable: false
     - 검증: 0 이상, total_pages 이하

4. **완독한 책 (Finished)**
   - 독서 시작일 (필수)
     - Column: `reading_start_date` (DATE)
     - Table: `user_books`
     - Type: LocalDate
     - Format: 년-월-날짜 (yyyy-MM-dd)
     - Nullable: false
   - 독서 종료일 (필수)
     - Column: `reading_finished_date` (DATE)
     - Table: `user_books`
     - Type: LocalDate
     - Format: 년-월-날짜 (yyyy-MM-dd)
     - Nullable: false
     - 검증: 독서 시작일 이후 날짜
   - 평점 (필수)
     - Column: `rating` (INT)
     - Table: `user_books`
     - Type: Integer
     - 설명: 5점 만점에 정수로 표시 (예: 1, 2, 3, 4, 5)
     - Nullable: false
     - 검증: 1 이상 5 이하
   - 후기 (선택사항)
     - Column: `review` (TEXT)
     - Table: `user_books`
     - Type: String
     - Nullable: true

**구현 계획**:
1. **Enum 생성** (필요시)
   - `PurchaseType.java` (신규, 선택사항)
   - 위치: `server.common.constant.PurchaseType`
   - 값: `PURCHASE("구매")`, `RENTAL("대여")`

2. **DB 마이그레이션 파일 생성** (필요시)
   - `V10__Add_expectation_and_purchase_type_to_user_books.sql` (신규, 필요)
   - `user_books` 테이블에 다음 컬럼 추가:
     - `expectation` (VARCHAR(500), NULL) - ToRead 카테고리용 기대평 (선택사항)
     - `purchase_type` (ENUM 또는 VARCHAR(20), NULL) - Reading 카테고리용 구매/대여 여부 (선택사항)
   - **주의**: 기존 `memo` 컬럼이 있다면 `expectation`으로 변경 또는 별도 컬럼으로 추가

3. **DTO 생성**
   - `ManualBookAdditionRequest.java` (신규)
   - **필수 필드**:
     - `isbn` (String, 필수) - ISBN
     - `title` (String, 필수) - 도서명
     - `author` (String, 필수) - 저자명
   - **선택 필드**:
     - `publisher` (String, 선택) - 출판사명
     - `pubDate` (LocalDate, 선택) - 출판일
     - `totalPages` (Integer, 선택) - 전체 페이지 수
     - `mainGenre` (String, 선택) - 메인 태그
     - `description` (String, 선택) - 책 소개
     - `coverUrl` (String, 선택) - 표지 URL
   - **카테고리별 입력값 필드**:
     - `expectation` (String, 선택) - ToRead용 기대평 (500자 이하)
     - `readingStartDate` (LocalDate, 필수) - Reading, AlmostFinished, Finished용
     - `readingProgress` (Integer, 필수) - Reading, AlmostFinished용
     - `purchaseType` (PurchaseType, 선택) - Reading용
     - `readingFinishedDate` (LocalDate, 필수) - Finished용
     - `rating` (Integer, 필수) - Finished용
     - `review` (String, 선택) - Finished용

4. **BookService 확장**
   - `addManualBookToShelf(String loginId, ManualBookAdditionRequest request)` 메서드 추가
   - **필수 필드 검증 로직**:
     - ISBN 검증 (null 체크, 형식 검증) - 입력하지 않으면 저장 실패
     - 도서명 검증 (null 체크, 빈 문자열 체크) - 입력하지 않으면 저장 실패
     - 저자명 검증 (null 체크, 빈 문자열 체크) - 입력하지 않으면 저장 실패
   - Book 테이블에 이미 존재하는 ISBN인지 확인
   - **카테고리별 입력값 검증 로직**:
     - 선택된 카테고리에 해당하는 필드만 입력받아야 함
     - ToRead: `expectation`만 입력받음 (선택사항, 500자 이하, 없어도 됨)
     - Reading: `readingStartDate`, `readingProgress` 필수, `purchaseType` 선택
     - AlmostFinished: `readingStartDate`, `readingProgress` 필수
     - Finished: `readingStartDate`, `readingFinishedDate`, `rating` 필수, `review` 선택
     - 선택된 카테고리와 관련 없는 필드는 무시하거나 검증 오류 처리
   - 직접 입력한 정보로 Book 생성 후 UserShelfBook 등록

5. **BookController 엔드포인트 추가**
   - `POST /api/v1/user/books/manual` (인증 필요)

**예상 작업 파일**:
- `PurchaseType.java` (신규, 선택사항)
- `V10__Add_expectation_and_purchase_type_to_user_books.sql` (신규, 필요)
- `ManualBookAdditionRequest.java` (신규)
- `BookService.java` (수정)
- `BookController.java` (수정)

---

#### 📌 2-2. 책 상세 정보 변경
**우선순위**: 중간  
**상태**: 계획 완료, 구현 대기

**요구사항**:
- 독서 시작일, 독서 종료일, 진행률(페이지수), 평점, 후기 변경
- 카테고리에 따라 입력값 다름

**카테고리별 입력값 상세**:
**저장 위치**: 아래 모든 값들은 **`user_books` 테이블**에 저장됩니다. (Memo table과 구분)

1. **읽고 싶은 책 (ToRead)**
   - 기대평 (선택사항)
     - Column: `expectation` (VARCHAR(500))
     - Table: `user_books`
     - Type: String
     - Nullable: true
     - 설명: 사용자가 책을 읽기 전에 입력하는 500자 이하의 글. 사용자가 원하는 경우에만 입력 가능
     - 검증: 500자 이하
     - **주의**: Memo table과 혼동을 피하기 위해 `memo` 대신 `expectation` 컬럼명 사용. 독서 메모는 별도의 Memo table에 저장됨

2. **읽고 있는 책 (Reading)**
   - 독서 시작일 (필수)
     - Column: `reading_start_date` (DATE)
     - Table: `user_books`
     - Type: LocalDate
     - Format: 년-월-날짜 (yyyy-MM-dd)
     - Nullable: false
   - 현재 읽은 페이지 수 (필수)
     - Column: `reading_progress` (INT)
     - Table: `user_books`
     - Type: Integer
     - 설명: 전체 분량은 Book table에서 가져오는 책의 상세 정보(`total_pages`)에서 가져오기, 사용자는 현재 마지막으로 읽은 페이지 수만 정수로 입력
     - Nullable: false
     - 검증: 0 이상, total_pages 이하
   - 구매/대여 여부 (선택사항)
     - Column: `purchase_type` (ENUM 또는 VARCHAR, 신규 추가 필요)
     - Table: `user_books`
     - Type: Enum (예: `PurchaseType.PURCHASE`, `PurchaseType.RENTAL`)
     - Nullable: true

3. **거의 다 읽은 책 (AlmostFinished)**
   - 독서 시작일 (필수)
     - Column: `reading_start_date` (DATE)
     - Table: `user_books`
     - Type: LocalDate
     - Format: 년-월-날짜 (yyyy-MM-dd)
     - Nullable: false
   - 현재 읽은 페이지 수 (필수)
     - Column: `reading_progress` (INT)
     - Table: `user_books`
     - Type: Integer
     - 설명: 전체 분량은 Book table에서 가져오는 책의 상세 정보(`total_pages`)에서 가져오기, 사용자는 현재 마지막으로 읽은 페이지 수만 정수로 입력
     - Nullable: false
     - 검증: 0 이상, total_pages 이하
   - 구매/대여 여부 (선택사항)
     - Column: `purchase_type` (ENUM 또는 VARCHAR, 신규 추가 필요)
     - Table: `user_books`
     - Type: Enum (예: `PurchaseType.PURCHASE`, `PurchaseType.RENTAL`)
     - Nullable: true

4. **완독한 책 (Finished)**
   - 독서 시작일 (필수)
     - Column: `reading_start_date` (DATE)
     - Table: `user_books`
     - Type: LocalDate
     - Format: 년-월-날짜 (yyyy-MM-dd)
     - Nullable: false
   - 독서 종료일 (필수)
     - Column: `reading_finished_date` (DATE)
     - Table: `user_books`
     - Type: LocalDate
     - Format: 년-월-날짜 (yyyy-MM-dd)
     - Nullable: false
     - 검증: 독서 시작일 이후 날짜
   - 구매/대여 여부 (선택사항)
     - Column: `purchase_type` (ENUM 또는 VARCHAR, 신규 추가 필요)
     - Table: `user_books`
     - Type: Enum (예: `PurchaseType.PURCHASE`, `PurchaseType.RENTAL`)
     - Nullable: true
   - 평점 (필수)
     - Column: `rating` (INT)
     - Table: `user_books`
     - Type: Integer
     - 설명: 5점 만점에 정수로 표시 (예: 1, 2, 3, 4, 5)
     - Nullable: false
     - 검증: 1 이상 5 이하
   - 후기 (선택사항)
     - Column: `review` (TEXT)
     - Table: `user_books`
     - Type: String
     - Nullable: true

**구현 계획**:
1. **DTO 생성**
   - `BookDetailUpdateRequest.java` (신규)
   - 카테고리별 입력값 필드 포함:
     - `expectation` (String, 선택) - ToRead용 기대평 (500자 이하)
     - `readingStartDate` (LocalDate, 필수) - Reading, AlmostFinished, Finished용
     - `readingProgress` (Integer, 필수) - Reading, AlmostFinished용
     - `purchaseType` (PurchaseType, 선택) - Reading용
     - `readingFinishedDate` (LocalDate, 필수) - Finished용
     - `rating` (Integer, 필수) - Finished용
     - `review` (String, 선택) - Finished용

2. **BookService 확장**
   - `updateBookDetail(String loginId, Long userBookId, BookDetailUpdateRequest request)` 메서드 추가
   - **카테고리 변경 시 필드 처리 원칙**:
     - **이전에 기록된 필드값은 유지**: 카테고리를 변경하더라도 기존에 입력된 필드값은 그대로 보존
     - **새 카테고리의 필수 필드만 입력받음**: 새로 바뀐 카테고리에서 필요한 column값만 새로 입력받아야 함 (선택사항 제외)
     - **예시**: ToRead → Reading으로 변경 시
       - 기존 `expectation` 값은 유지 (null로 변경하지 않음)
       - `readingStartDate`, `readingProgress` 필수 입력받음
       - `purchaseType`은 선택사항이므로 입력받지 않아도 됨
     - **예시**: Reading → Finished로 변경 시
       - 기존 `readingStartDate`, `readingProgress` 값은 유지
       - `readingFinishedDate`, `rating` 필수 입력받음
       - `review`는 선택사항이므로 입력받지 않아도 됨
   - **카테고리별 입력값 검증 로직**:
     - ToRead: 기대평(expectation)만 입력받음 (선택사항, 500자 이하)
     - Reading: 독서 시작일, 진행률 필수, 구매/대여 여부(선택)
     - AlmostFinished: 독서 시작일, 진행률 필수
     - Finished: 독서 시작일, 종료일, 평점(필수), 후기(선택)
     - 선택된 카테고리와 관련 없는 필드는 무시하거나 검증 오류 처리
   - 입력값 검증 (날짜 유효성, 진행률 범위, 평점 범위 등)

3. **BookController 엔드포인트 추가**
   - `PUT /api/v1/user/books/{userBookId}` (인증 필요)

**예상 작업 파일**:
- `BookDetailUpdateRequest.java` (신규)
- `BookService.java` (수정)
- `BookController.java` (수정)

---

#### 📌 2-3. 저장된 책 정렬 변경
**우선순위**: 중간  
**상태**: 계획 완료, 구현 대기

**요구사항**:
- **적용 범위**: 내 서재에서만 적용되는 기능
- **정렬 기준**: 도서명, 저자명, 출판사명, 태그(메인 장르)만 사용
  - 추가일(created_at)은 정렬 기준에서 제외
- **참고**: 추후 추가되는 메모 정렬 기준과는 별개의 기능임
- DB 변경 없음, UI 변경만 존재

**기능 설명**:
- 내 서재에 저장된 책들의 목록이 존재할 때, 사용자가 선택한 정렬 기준에 따라 책들의 순서가 필터링되어 해당 정렬 기준을 중점으로 순서가 바뀌게 됩니다.
- **정렬 방법**: 모든 정렬 기준의 정렬방법은 **가나다순**입니다 (한글의 경우). 영어라면 **ABC순**입니다.
  - 도서명 정렬: 도서명 기준 가나다순/ABC순
  - 저자명 정렬: 저자명 기준 가나다순/ABC순
  - 출판사명 정렬: 출판사명 기준 가나다순/ABC순
  - 태그(메인 장르) 정렬: 메인 장르 기준 가나다순/ABC순

**구현 계획**:
1. **Enum 생성**
   - `BookSortCriteria.java` (신규)
   - 값: `TITLE` (도서명), `AUTHOR` (저자명), `PUBLISHER` (출판사명), `GENRE` (태그/메인 장르)
   - **주의**: `CREATED_AT` (추가일)은 포함하지 않음

2. **UserShelfBookRepository 확장**
   - 정렬 메서드 추가 (JPQL 사용)
   - `findByUserIdOrderByTitleAsc`, `findByUserIdOrderByAuthorAsc`, `findByUserIdOrderByPublisherAsc`, `findByUserIdOrderByGenreAsc` 등
   - **주의**: 
     - `created_at` 기준 정렬 메서드는 생성하지 않음
     - 정렬은 오름차순(Asc)으로만 구현 (가나다순/ABC순)
     - MySQL의 기본 정렬은 한글은 가나다순, 영어는 ABC순으로 자동 처리됨

3. **BookService 확장**
   - `getMyShelf` 메서드에 정렬 파라미터 추가
   - 정렬 기준에 따라 적절한 Repository 메서드 호출
   - 정렬 기준이 지정되지 않은 경우 기본 정렬(예: 도서명 오름차순) 적용
   - **정렬 방식**: 모든 정렬은 오름차순(Asc)으로 처리 (가나다순/ABC순)

4. **BookController 수정**
   - `getMyShelf` 엔드포인트에 `sortBy` 파라미터 추가
   - `sortBy` 파라미터는 `BookSortCriteria` Enum 타입으로 받음
   - Swagger 문서에 정렬 기준 명시: 도서명, 저자명, 출판사명, 태그(메인 장르)

**예상 작업 파일**:
- `BookSortCriteria.java` (신규)
- `UserShelfBookRepository.java` (수정)
- `BookService.java` (수정)
- `BookController.java` (수정)

---

#### 📌 2-4. 책 카테고리 자동 변경 로직
**우선순위**: 중간  
**상태**: 계획 완료, 구현 대기

**요구사항**:
- 진행률(페이지수)를 기반으로 카테고리 자동 변경
- 진행률 계산 기준: (현재 페이지 수 / 전체 분량) × 100%

**책 카테고리 변경 기준치**:
- 진행률 계산식: (현재 페이지 수 / 전체 분량) × 100%
- **0%** → `ToRead` (읽고 싶은 책)
- **1~69%** → `Reading` (읽는 중)
- **70~99%** → `AlmostFinished` (거의 다 읽음)
- **100%** → `Finished` (완독)

**카테고리 간 변경 가능한 상황**:

1. **ToRead (읽기 전) → Reading / AlmostFinished / Finished**
   - 변경 조건: "책 읽기 시작" 버튼을 눌러야 함
   - 입력 UI: 진행률(페이지 수), 독서 시작일 입력 UI 등장
   - 설명: ToRead 상태에서 독서를 시작하려면 명시적으로 "책 읽기 시작" 버튼을 눌러야 하며, 이때 진행률과 독서 시작일을 입력받아야 함

2. **Reading (읽는 중) → AlmostFinished / Finished**
   - 변경 조건: 진행률 업데이트 시 자동으로 변경
   - 설명: 
     - 진행률이 70% 이상 99% 이하 → `AlmostFinished`로 자동 변경
     - 진행률이 100% → `Finished`로 자동 변경 (시스템이 자동으로 완독 상태로 갱신)
   - **주의**: 진행률이 100%가 되면 자동으로 `Finished`로 변경되지만, 독서 종료일, 평점, 후기는 별도로 입력받아야 함

3. **AlmostFinished (거의 다 읽는 중) → Finished**
   - 변경 조건: "완독" 버튼을 눌러야 함
   - 입력 UI: 독서 종료일, 평점, 후기 입력 UI 등장
   - 설명: AlmostFinished 상태에서 완독하려면 명시적으로 "완독" 버튼을 눌러야 하며, 이때 독서 종료일, 평점(필수), 후기(선택)를 입력받아야 함

**구현 계획**:
1. **BookService 확장**
   - `calculateProgressPercentage(Integer readingProgress, Integer totalPages)` 메서드 추가
     - 계산식: `(readingProgress / totalPages) × 100`
     - 반환값: 0~100 사이의 정수 (퍼센티지)
   - `determineCategoryByProgress(Integer progressPercentage)` 메서드 추가
     - 0% → `ToRead`
     - 1~69% → `Reading`
     - 70~99% → `AlmostFinished`
     - 100% → `Finished`
   - 진행률 업데이트 시 자동으로 카테고리 변경 로직 추가

2. **카테고리 변경 시나리오별 처리**
   - **ToRead → Reading/AlmostFinished/Finished**: 
     - "책 읽기 시작" 버튼 클릭 시 처리
     - 진행률과 독서 시작일 필수 입력 검증
   - **Reading → AlmostFinished/Finished**:
     - 진행률 업데이트 시 자동 변경
     - 진행률 70% 이상 99% 이하 → `AlmostFinished`로 자동 변경
     - 진행률 100% → `Finished`로 자동 변경 (시스템이 자동으로 완독 상태로 갱신)
     - `updateBookDetail` 또는 `updateReadingProgress` 메서드에서 처리
   - **AlmostFinished → Finished**:
     - "완독" 버튼 클릭 시 처리
     - 독서 종료일, 평점(필수), 후기(선택) 입력 검증
     - 진행률이 100%인지 확인

3. **진행률 업데이트 로직 통합**
   - `updateBookDetail` 메서드에서 진행률 변경 시 자동 카테고리 변경
   - 또는 별도의 `updateReadingProgress` 메서드 생성
   - 진행률 변경 시 `calculateProgressPercentage` → `determineCategoryByProgress` 순서로 호출

**예상 작업 파일**:
- `BookService.java` (수정)
- `BookController.java` (수정) - "책 읽기 시작", "완독" 버튼 관련 엔드포인트 추가 필요

---

### Phase 3: 기타 기능

#### 📌 3-1. 저장된 책 정보 삭제 (메모/해설집 연동)
**우선순위**: 낮음  
**상태**: 계획 완료, 구현 대기 (메모 테이블 구현 후)

**요구사항**:
- 책 정보, 책에 저장된 모든 독서 메모, 해설집 전부 삭제
- 현재는 기본 삭제 기능만 있음

**구현 계획**:
- 메모 테이블 구현 후 연동
- 현재는 기본 삭제 기능 유지

---

## 📊 구현 진행 상황 체크리스트

### Phase 1: 도서 검색 기능 완성
- [x] 1-1. 도서 검색 필터 개선
  - [x] BookSearchFilter Enum 생성
  - [x] BookSearchRequest 수정
  - [x] AladinApiService 수정
  - [x] BookController 수정
- [x] 1-2. 도서 세부 정보 검색 API 추가

### Phase 2: 내 서재 기능 확장
- [x] 2-1. 내 서재에 책 정보 직접 등록
- [x] 2-2. 책 상세 정보 변경
- [x] 2-3. 저장된 책 정렬 변경
- [ ] 2-4. 책 카테고리 자동 변경 로직

### Phase 3: 기타 기능
- [ ] 3-1. 저장된 책 정보 삭제 (메모/해설집 연동)

---

## 🔧 필요한 데이터베이스 변경사항

### 데이터베이스 설계 원칙

#### ✅ 카테고리별 입력값 저장 위치 원칙
**중요**: 카테고리를 선택했을 때 입력해야 하는 값들은 모두 **`user_books` 테이블**에 저장됩니다.

**저장 위치**:
- **`user_books` 테이블에 저장되는 값들**:
  - `expectation` - 기대평 (ToRead 카테고리)
  - `reading_start_date` - 독서 시작일 (Reading, AlmostFinished, Finished 카테고리)
  - `reading_finished_date` - 독서 종료일 (Finished 카테고리)
  - `reading_progress` - 현재 읽은 페이지 수 (Reading, AlmostFinished 카테고리)
  - `purchase_type` - 구매/대여 여부 (Reading 카테고리)
  - `rating` - 평점 (Finished 카테고리)
  - `review` - 후기 (Finished 카테고리)

- **Memo table에 저장되는 값들**:
  - 독서 메모 (페이지별 메모, 독서 과정에서 생성되는 콘텐츠)

- **해설집 table에 저장되는 값들** (별도 테이블):
  - 해설집 (메모 기반으로 생성되는 콘텐츠)
  - **주의**: Memo table과 해설집 데이터를 저장하는 table은 **따로 생성**합니다

**설계 이유**:
- 카테고리별 입력값들은 **독서 상태 및 평가 정보**로, 사용자-책 관계의 메타데이터입니다
- 독서 메모는 **독서 과정에서 생성되는 콘텐츠**로, 별도의 엔티티(Memo table)로 분리됩니다
- 해설집은 **메모 기반으로 생성되는 콘텐츠**로, Memo table과 별도로 해설집 전용 테이블에 저장됩니다
- 이렇게 구분함으로써 데이터 접근 패턴이 명확해지고, 쿼리 효율성이 향상됩니다

---

#### ✅ 중복 컬럼 제거 원칙
**중요**: 각 카테고리별로 중복되는 입력 column은 테이블에 **한 개씩만** 존재해야 합니다.

**예시**:
- `reading_start_date`: Reading, AlmostFinished, Finished 카테고리에서 모두 사용되지만, 테이블에는 **하나의 컬럼만** 존재
- `reading_progress`: Reading, AlmostFinished 카테고리에서 모두 사용되지만, 테이블에는 **하나의 컬럼만** 존재

**결과**: `user_books` 테이블은 카테고리별로 컬럼이 분리되지 않고, 공통 컬럼을 공유하는 구조입니다.

---

### 현재 상태
- ✅ `books` 테이블: 현재 구조로 충분
- ✅ `user_books` 테이블: 기본 구조는 충분하나, 일부 필드 추가 검토 필요

### 추가/수정 필요 사항

#### 1. `user_books` 테이블 - 기대평 필드 (필수 추가)
**필드명**: `expectation`  
**타입**: VARCHAR(500)  
**Nullable**: true  
**설명**: "읽고 싶은 책 (ToRead)" 카테고리에서 사용되는 기대평 필드 (선택사항)  
**특징**:
- 사용자가 책을 읽기 전에 입력하는 500자 이하의 글
- ToRead 카테고리로 책을 저장할 때 원하는 경우에만 입력
- Memo table과 혼동을 피하기 위해 `memo` 대신 `expectation` 컬럼명 사용

#### 2. `user_books` 테이블 - 구매/대여 여부 필드 (선택사항)
**필드명**: `purchase_type`  
**타입**: ENUM 또는 VARCHAR(20)  
**Nullable**: true  
**설명**: "읽고 있는 책 (Reading)" 카테고리에서만 사용되는 선택사항 필드  
**Enum 값**: 
- `PURCHASE` - 구매
- `RENTAL` - 대여

**마이그레이션 파일**: `V10__Add_expectation_and_purchase_type_to_user_books.sql` (신규, 필요)

**주의사항**:
- `expectation` 필드는 필수 추가 (기능 구현에 필요)
- `purchase_type` 필드는 선택사항이므로, 구현하지 않아도 기본 기능은 동작함
- 구현 시에는 DB 마이그레이션 파일과 UserShelfBook 엔티티 수정 필요

### 기존 컬럼 확인

#### `user_books` 테이블의 카테고리별 사용 컬럼:
- `expectation` (VARCHAR(500)) - ToRead: 기대평 (500자 이하, 선택사항)
- `reading_start_date` (DATE) - Reading, AlmostFinished, Finished: 독서 시작일 (공통 컬럼)
- `reading_progress` (INT) - Reading, AlmostFinished: 현재 읽은 페이지 수 (공통 컬럼)
- `purchase_type` (ENUM 또는 VARCHAR(20)) - Reading: 구매/대여 여부 (선택사항)
- `reading_finished_date` (DATE) - Finished: 독서 종료일
- `rating` (INT) - Finished: 평점 (1~5)
- `review` (TEXT) - Finished: 후기

**공통 컬럼 사용 예시**:
- `reading_start_date`: 세 카테고리(Reading, AlmostFinished, Finished)에서 공통으로 사용하지만 테이블에는 하나의 컬럼만 존재
- `reading_progress`: 두 카테고리(Reading, AlmostFinished)에서 공통으로 사용하지만 테이블에는 하나의 컬럼만 존재

---

## 📌 구현 시 주의사항

### 1. 카테고리별 입력값 처리 원칙

#### ✅ 책 저장 시 (신규 등록)
- **선택된 카테고리에 해당하는 필드만 입력받아야 함**
  - ToRead 선택 → `expectation`만 입력받음 (선택사항, 500자 이하, 없어도 됨)
  - Reading 선택 → `readingStartDate`, `readingProgress` 필수, `purchaseType` 선택
  - AlmostFinished 선택 → `readingStartDate`, `readingProgress` 필수
  - Finished 선택 → `readingStartDate`, `readingFinishedDate`, `rating` 필수, `review` 선택
- **선택된 카테고리와 관련 없는 필드는 무시하거나 검증 오류 처리**

#### ✅ 카테고리 변경 시 (기존 책 수정)

**카테고리 변경 기준치**:
- 진행률 계산: (현재 페이지 수 / 전체 분량) × 100%
- **0%** → `ToRead` (읽고 싶은 책)
- **1~69%** → `Reading` (읽는 중)
- **70~99%** → `AlmostFinished` (거의 다 읽음)
- **100%** → `Finished` (완독)

**카테고리 간 변경 가능한 상황**:

1. **ToRead → Reading / AlmostFinished / Finished**
   - 변경 조건: "책 읽기 시작" 버튼 클릭 필요
   - 입력 UI: 진행률(페이지 수), 독서 시작일 입력 UI 등장
   - 필수 입력: `readingStartDate`, `readingProgress`
   - 선택 입력: `purchaseType` (Reading 카테고리인 경우)

2. **Reading → AlmostFinished / Finished**
   - 변경 조건: 진행률 업데이트 시 자동 변경
   - 진행률이 70% 이상 → `AlmostFinished`로 자동 변경
   - 진행률이 100% → `AlmostFinished`로 자동 변경 (이후 "완독" 버튼 클릭 시 `Finished`로 변경)

3. **AlmostFinished → Finished**
   - 변경 조건: "완독" 버튼 클릭 필요
   - 입력 UI: 독서 종료일, 평점, 후기 입력 UI 등장
   - 필수 입력: `readingFinishedDate`, `rating`
   - 선택 입력: `review`

**필드값 처리 원칙**:
- **이전에 기록된 필드값은 유지**
  - 기존에 입력된 모든 필드값은 그대로 보존
  - 카테고리 변경 시에도 기존 데이터는 삭제하지 않음
- **새로 바뀐 카테고리에서 필요한 필수 필드만 새로 입력받음**
  - 선택사항은 입력받지 않아도 됨
  - 예: ToRead → Reading 변경 시
    - 기존 `expectation` 값 유지
    - `readingStartDate`, `readingProgress` 필수 입력받음
    - `purchaseType`은 선택사항이므로 입력받지 않아도 됨
- **필드값 병합 로직**
  - 기존 필드값이 있고 새로 입력받은 필드가 있으면 → 새 값으로 업데이트
  - 기존 필드값이 있고 새로 입력받은 필드가 없으면 → 기존 값 유지
  - 기존 필드값이 없고 새로 입력받은 필드가 있으면 → 새 값 저장

---

### 2. 패키지 명명 규칙 준수
- 모든 패키지명은 소문자 한 단어
- DTO 패키지만 'DTO' 글자만 대문자 (`clientserverDTO`, `requestDTO`, `responseDTO`)

### 3. DTO 경계 분리
- Client ↔ Server: `server.dto.clientserverDTO`
- Server ↔ DBMS: `dbms.dto.serverdbmsDTO`

### 4. 함수 명명 규칙
- `get...`: 단순 접근
- `extract...`: 복잡한 변환
- `calc...`: 계산
- `update...`: 업데이트

### 5. 테이블/컬럼 명명
- 소문자 snake_case 사용 (예: `user_books`, `reading_progress`)

### 6. API 응답 구조
- 모든 API는 `ApiResponse<T>` 래퍼 사용

---

## 📚 참고 자료

- [ARCHITECTURE.md](./ARCHITECTURE.md) - 프로젝트 아키텍처 문서
- [기능 정의서](./ExportBlock-d560bb13-18ba-4ffc-949d-a097c3930af6-Part-1/기능%20정의서%202994a8c850098070a0b8ff24fb71e366.csv) - 상세 기능 명세

---

**최종 업데이트**: 2024년  
**버전**: 1.0

