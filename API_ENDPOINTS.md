# API 엔드포인트 명세서

## 개요

- **기본 경로**: `/api/v1`
- **인증 방식**: JWT (Bearer Token)
- **응답 형식**: `ApiResponse<T>` 래퍼 사용
- **Content-Type**: `application/json`
- **사용 대상**: 웹 클라이언트와 앱 클라이언트 모두 동일한 RESTful API 사용

## REST API 엔드포인트 정의 기준

### 1. 자원(Resource) 중심의 명사 사용

엔드포인트(URI)는 사용자가 접근하려는 데이터 자원을 나타내야 하며, 이 자원은 명사를 사용하고 복수형으로 표현하는 것이 일반적입니다.

**좋은 예**:
- 책 목록: `/api/v1/books`
- 메모 목록: `/api/v1/memos`
- 서재 목록: `/api/v1/user/books`

**피해야 할 예** (행위 포함):
- ❌ `/api/v1/getBooks`
- ❌ `/api/v1/createMemo`

### 2. HTTP 메서드를 통한 행위(Action) 정의

URI가 자원을 나타내면, 해당 자원에 대해 클라이언트가 수행하려는 행위는 URI 자체가 아닌 HTTP 메서드를 사용하여 정의해야 합니다.

| HTTP 메서드 | 자원에 대한 행위 | 독서 기록 예시 |
|------------|----------------|--------------|
| **GET** | 자원 조회 및 검색 (Read) | `GET /api/v1/books` (책 목록 조회)<br>`GET /api/v1/books/{isbn}` (특정 책 상세 조회) |
| **POST** | 새 자원 생성 (Create) | `POST /api/v1/user/books` (서재에 책 추가)<br>`POST /api/v1/user/books/{id}/start-reading` (읽기 시작) |
| **PUT** | 자원 전체 업데이트 (Update/Replace) | `PUT /api/v1/user/books/{id}/category` (읽기 상태 변경) |
| **PATCH** | 자원 부분 업데이트 (Partial Update) | `PUT /api/v1/user/books/{id}` (책 상세 정보 부분 변경) |
| **DELETE** | 자원 삭제 (Delete) | `DELETE /api/v1/user/books/{id}` (서재에서 책 삭제) |

### 3. 계층 구조 및 관계 표현

자원 간의 관계는 URI의 계층 구조를 통해 명확하게 표현해야 합니다.

**예시** (사용자 서재에 종속된 책):
- 특정 사용자의 서재 조회: `GET /api/v1/user/books`
- 특정 사용자의 서재에 책 추가: `POST /api/v1/user/books`
- 특정 사용자의 서재에서 책 제거: `DELETE /api/v1/user/books/{userBookId}`

**예시** (책에 종속된 메모 - 향후 구현):
- 특정 책의 메모 목록 조회: `GET /api/v1/books/{bookId}/memos`
- 특정 책에 대한 메모 생성: `POST /api/v1/books/{bookId}/memos`

### 4. 엔드포인트와 Entity (자원) 매핑

꼭 모든 Entity마다 엔드포인트를 설정해야 하는 것은 아닙니다. 엔드포인트는 데이터베이스의 Entity 구조보다는 **클라이언트가 접근하거나 조작해야 하는 '자원(Resource)'**을 중심으로 설계해야 합니다.

#### A. 1:1 매핑 (가장 흔함)

대부분의 주요 Entity는 그 자체로 하나의 독립적인 자원이 되어 엔드포인트를 가집니다.

**예시**:
- `Book` Entity → `/api/v1/books`
- `Memo` Entity → `/api/v1/memos` (향후 구현)
- `User` Entity → `/api/v1/users`

이러한 엔드포인트는 해당 Entity의 CRUD 작업을 직접적으로 제공합니다.

#### B. 1:N 관계 (계층적 자원)

다른 자원에 종속되어 존재하는 Entity는 독립적인 엔드포인트 대신 계층적인 엔드포인트를 가질 수 있습니다.

**예시**: `Book` Entity와 `Memo` Entity가 1:N 관계일 때

- **독립적인 엔드포인트** (선택사항):
  - `GET /api/v1/memos` - 모든 메모 조회
  - `POST /api/v1/memos` - 새 메모 생성

- **계층적 엔드포인트** (권장):
  - `GET /api/v1/books/{bookId}/memos` - 특정 책의 메모 목록 조회
  - `POST /api/v1/books/{bookId}/memos` - 특정 책에 대한 메모 생성
  - `PUT /api/v1/books/{bookId}/memos/{memoId}` - 특정 책의 메모 수정
  - `DELETE /api/v1/books/{bookId}/memos/{memoId}` - 특정 책의 메모 삭제

**현재 프로젝트 예시**: 사용자 서재에 종속된 책
- `UserShelfBook` Entity는 독립적인 `/api/v1/user-shelf-books` 엔드포인트 대신
- 계층적 엔드포인트 `/api/v1/user/books`를 사용하여 사용자 서재에 종속된 책으로 표현

#### C. 복합 자원 (Composite Resource)

때로는 여러 Entity의 데이터를 조합하여 사용자에게 의미 있는 하나의 자원으로 제공해야 할 때가 있습니다.

**예시**: 사용자가 보는 "내 서재 대시보드"
- 여러 Entity 정보 조합: `User`, `UserShelfBook`, `Book`, `Memo` 등
- 복합 자원 엔드포인트: `/api/v1/dashboard` (향후 구현)
  - 사용자 통계, 최근 추가한 책, 읽고 있는 책, 완독한 책 수 등

**현재 프로젝트 예시**: 내 서재 조회 (`GET /api/v1/user/books`)
- `UserShelfBook` Entity와 `Book` Entity의 정보를 조합하여 사용자에게 의미 있는 "내 서재" 자원으로 제공

#### 설계 원칙

이 프로젝트에서는 **A, B, C 세 가지 방법(1:1 매핑, 계층적 자원, 복합 자원)을 모두 복합적으로 사용**합니다.

- **1:1 매핑**: 주요 Entity (`Book`, `User`)는 독립적인 엔드포인트 제공
- **계층적 자원**: 종속 관계가 명확한 경우 (`/api/v1/user/books`, `/api/v1/books/{bookId}/memos`)
- **복합 자원**: 사용자 경험을 위해 여러 Entity를 조합한 자원 제공

**핵심 원칙**: 엔드포인트는 **데이터베이스 구조가 아닌 클라이언트의 관점**에서 설계되어야 합니다.

### 5. 버전 관리 (Versioning)

API의 구조가 나중에 변경될 가능성에 대비하여 버전을 명시합니다.

- 현재 버전: `/api/v1/books`
- 이전 버전과 호환되지 않는 큰 변경이 있을 경우: `/api/v2/books`

### 6. JSON 응답과 엔드포인트

Spring Boot의 RESTful API에서 클라이언트와 데이터를 주고받는 모든 경로는 URL에 매핑되어야 하며, 일반적으로 `@RestController` 내의 메서드에 `@GetMapping`, `@PostMapping` 등의 어노테이션으로 엔드포인트를 설정합니다.

**주의**: 단순히 메서드의 반환 타입이 `String`이나 `int`여도 Spring Boot의 `@RestController`는 이를 JSON 객체(`"string"`, `123`)로 변환하여 응답하므로, 이 역시 엔드포인트로 정의되어야 합니다.

**예시**:
```java
@DeleteMapping("/user/books/{userBookId}")
public ApiResponse<String> removeBookFromShelf(...) {
    // ...
    return ApiResponse.success("책이 내 서재에서 제거되었습니다.");
}
// 응답: { "ok": true, "data": "책이 내 서재에서 제거되었습니다.", "error": null }
```

### 7. 엔드포인트 지정 위치: Controller 계층

엔드포인트(URI)를 지정하는 것은 Spring Boot에서 **Controller 계층의 역할**입니다. Service나 Mapper 클래스는 HTTP 통신과 관련된 책임을 지지 않습니다.

#### Controller (@RestController)

Controller는 클라이언트의 HTTP 요청을 가장 먼저 받는 진입점이며, 요청의 URI와 HTTP 메서드를 해석하여 특정 메서드(핸들러)와 연결하는 역할을 합니다.

**지정 방식**: Spring MVC의 애너테이션을 사용합니다.

- **클래스 레벨**: `@RequestMapping`을 사용하여 기본 자원 경로를 설정합니다.
  ```java
  @RestController
  @RequestMapping("/api/v1")
  public class BookShelfController extends BaseV1Controller {
      // ...
  }
  ```

- **메서드 레벨**: `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping` 등을 사용하여 특정 HTTP 메서드와 세부 경로를 매핑합니다.
  ```java
  @GetMapping("/user/books")
  public ApiResponse<MyShelfResponse> getMyShelf(...) {
      // ...
  }
  ```

**책임 분리**:
- **Controller**: HTTP 요청/응답 처리, 엔드포인트 정의
- **Service**: 비즈니스 로직 처리 (HTTP와 무관)
- **Mapper**: DTO ↔ Entity 변환 (HTTP와 무관)

## 공통 응답 형식

모든 API는 다음 형식의 응답을 반환합니다:

### 성공 응답

```json
{
  "ok": true,
  "data": {
    // 실제 응답 데이터
  },
  "error": null
}
```

### 에러 응답

```json
{
  "ok": false,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "에러 메시지",
    "fieldErrors": [
      {
        "field": "fieldName",
        "message": "필드별 에러 메시지"
      }
    ]
  }
}
```

## 인증

인증이 필요한 API는 HTTP 헤더에 JWT 토큰을 포함해야 합니다:

```
Authorization: Bearer {accessToken}
```

인증이 필요한 엔드포인트는 각 엔드포인트 설명에 **인증 필요**로 표시되어 있습니다.

---

## 1. 인증 관련 API (`/auth`)

### 1.1 로그인 ID 중복 확인

**엔드포인트**: `GET /api/v1/users/duplicate/loginId`

**인증**: 불필요

**요청 파라미터**:
- `value` (String, required): 확인할 로그인 ID

**응답**:
```json
{
  "ok": true,
  "data": true,  // true: 중복됨, false: 중복되지 않음
  "error": null
}
```

**예시**:
```
GET /api/v1/users/duplicate/loginId?value=user123
```

---

### 1.2 이메일 중복 확인

**엔드포인트**: `GET /api/v1/users/duplicate/email`

**인증**: 불필요

**요청 파라미터**:
- `value` (String, required): 확인할 이메일

**응답**:
```json
{
  "ok": true,
  "data": true,  // true: 중복됨, false: 중복되지 않음
  "error": null
}
```

**예시**:
```
GET /api/v1/users/duplicate/email?value=user@example.com
```

---

### 1.3 회원가입

**엔드포인트**: `POST /api/v1/auth/signup`

**인증**: 불필요

**요청 본문** (`RegistrationRequest`):
```json
{
  "loginId": "user123",
  "email": "user@example.com",
  "name": "홍길동",
  "password": "Password123!"
}
```

**응답** (`RegisterResponse`):
```json
{
  "ok": true,
  "data": {
    "id": 1,
    "loginId": "user123",
    "email": "user@example.com",
    "name": "홍길동",
    "role": "USER",
    "status": "ACTIVE"
  },
  "error": null
}
```

---

### 1.4 로그인

**엔드포인트**: `POST /api/v1/auth/login`

**인증**: 불필요

**요청 본문** (`LoginRequest`):
```json
{
  "loginId": "user123",
  "password": "Password123!"
}
```

**응답** (`LoginResponse`):
```json
{
  "ok": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": 1,
      "loginId": "user123",
      "email": "user@example.com",
      "name": "홍길동"
    }
  },
  "error": null
}
```

**설명**:
- 로그인 성공 시 `accessToken`과 `refreshToken`을 받습니다.
- 이후 API 요청 시 `Authorization` 헤더에 `Bearer {accessToken}` 형식으로 토큰을 포함해야 합니다.
- `accessToken` 만료 시간: 1시간
- `refreshToken` 만료 시간: 7일

---

### 1.5 아이디 찾기

**엔드포인트**: `POST /api/v1/auth/find-login-id`

**인증**: 불필요

**요청 본문** (`LoginIdRetrievalRequest`):
```json
{
  "email": "user@example.com",
  "name": "홍길동"
}
```

**응답** (`LoginIdRetrievalResponse`):
```json
{
  "ok": true,
  "data": {
    "loginId": "user123"
  },
  "error": null
}
```

---

### 1.6 계정 확인 및 토큰 발급 (비밀번호 재설정 Step 1)

**엔드포인트**: `POST /api/v1/auth/verify-account`

**인증**: 불필요

**요청 본문** (`AccountVerificationRequest`):
```json
{
  "loginId": "user123",
  "email": "user@example.com"
}
```

**응답** (`AccountVerificationResponse`):
```json
{
  "ok": true,
  "data": {
    "message": "계정이 확인되었습니다. 새 비밀번호를 입력해주세요.",
    "resetToken": "uuid-token-string"
  },
  "error": null
}
```

**설명**:
- 비밀번호 재설정의 첫 번째 단계입니다.
- 계정 확인 후 `resetToken`을 발급받습니다.
- 이 토큰을 사용하여 다음 단계에서 비밀번호를 변경합니다.

---

### 1.7 비밀번호 변경 (비밀번호 재설정 Step 2)

**엔드포인트**: `POST /api/v1/auth/reset-password`

**인증**: 불필요

**요청 본문** (`PasswordResetRequest`):
```json
{
  "resetToken": "uuid-token-string",
  "newPassword": "NewPassword123!",
  "confirmPassword": "NewPassword123!"
}
```

**응답** (`PasswordResetResponse`):
```json
{
  "ok": true,
  "data": {
    "id": 1,
    "loginId": "user123",
    "email": "user@example.com",
    "name": "홍길동"
  },
  "error": null
}
```

---

### 1.8 토큰 갱신

**엔드포인트**: `POST /api/v1/auth/refresh`

**인증**: 불필요 (하지만 Refresh Token 필요)

**요청 본문** (`RefreshTokenRequest`):
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**응답** (`RefreshTokenResponse`):
```json
{
  "ok": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  },
  "error": null
}
```

**설명**:
- `accessToken`이 만료되었을 때 `refreshToken`을 사용하여 새로운 토큰을 발급받습니다.
- Token Rotation 방식: 새로운 `accessToken`과 `refreshToken`을 모두 발급받습니다.

---

## 2. 도서 검색 API (`/books`)

### 2.1 책 검색

**엔드포인트**: `GET /api/v1/books/search`

**인증**: 불필요

**요청 파라미터**:
- `query` (String, required): 검색어
- `queryType` (String, optional): 검색 필터
  - `TITLE`: 도서명 (기본값)
  - `AUTHOR`: 저자명
  - `PUBLISHER`: 출판사명
- `start` (Integer, optional): 시작 페이지 (기본값: 1)
- `maxResults` (Integer, optional): 페이지당 결과 수, 최대 50 (기본값: 10)

**응답** (`BookSearchResponse`):
```json
{
  "ok": true,
  "data": {
    "totalResults": 100,
    "start": 1,
    "maxResults": 10,
    "books": [
      {
        "isbn": "9788937461234",
        "title": "책 제목",
        "author": "저자명",
        "publisher": "출판사명",
        "pubDate": "2024-01-01",
        "description": "책 설명",
        "coverImageUrl": "https://..."
      }
    ]
  },
  "error": null
}
```

**예시**:
```
GET /api/v1/books/search?query=자바&queryType=TITLE&start=1&maxResults=10
```

---

### 2.2 도서 세부 정보 조회

**엔드포인트**: `GET /api/v1/books/{isbn}`

**인증**: 불필요

**경로 변수**:
- `isbn` (String, required): 도서 ISBN

**응답** (`BookDetailResponse`):
```json
{
  "ok": true,
  "data": {
    "isbn": "9788937461234",
    "title": "책 제목",
    "author": "저자명",
    "publisher": "출판사명",
    "pubDate": "2024-01-01",
    "description": "책 설명",
    "coverImageUrl": "https://...",
    "price": 15000,
    "category": "소설"
  },
  "error": null
}
```

**예시**:
```
GET /api/v1/books/9788937461234
```

---

## 3. 사용자 서재 관리 API (`/user/books`)

### 3.1 내 서재에 책 추가

**엔드포인트**: `POST /api/v1/user/books`

**인증**: 필요

**요청 본문** (`BookAdditionRequest`):
```json
{
  "isbn": "9788937461234",
  "title": "책 제목",
  "author": "저자명",
  "publisher": "출판사명",
  "pubDate": "2024-01-01",
  "description": "책 설명",
  "coverImageUrl": "https://...",
  "category": "ToRead",
  "expectation": "이 책에 대한 기대감"
}
```

**응답** (`BookAdditionResponse`):
```json
{
  "ok": true,
  "data": {
    "userBookId": 1,
    "isbn": "9788937461234",
    "title": "책 제목",
    "category": "ToRead",
    "addedAt": "2024-01-15T10:30:00"
  },
  "error": null
}
```

---

### 3.2 내 서재 조회

**엔드포인트**: `GET /api/v1/user/books`

**인증**: 필요

**요청 파라미터**:
- `category` (String, optional): 카테고리 필터
  - `ToRead`: 읽을 책
  - `Reading`: 읽는 중
  - `AlmostFinished`: 거의 다 읽음
  - `Finished`: 읽은 책
- `sortBy` (String, optional): 정렬 기준 (기본값: `TITLE`)
  - `TITLE`: 도서명
  - `AUTHOR`: 저자명
  - `PUBLISHER`: 출판사명
  - `GENRE`: 태그/메인 장르

**응답** (`MyShelfResponse`):
```json
{
  "ok": true,
  "data": {
    "totalCount": 10,
    "books": [
      {
        "userBookId": 1,
        "isbn": "9788937461234",
        "title": "책 제목",
        "author": "저자명",
        "publisher": "출판사명",
        "coverImageUrl": "https://...",
        "category": "ToRead",
        "addedAt": "2024-01-15T10:30:00",
        "readingStartDate": null,
        "readingProgress": null,
        "readingFinishedDate": null,
        "rating": null,
        "review": null
      }
    ]
  },
  "error": null
}
```

**예시**:
```
GET /api/v1/user/books?category=ToRead&sortBy=TITLE
```

**설명**:
- 모든 정렬은 가나다순/ABC순 오름차순입니다.

---

### 3.3 내 서재에서 책 제거

**엔드포인트**: `DELETE /api/v1/user/books/{userBookId}`

**인증**: 필요

**경로 변수**:
- `userBookId` (Long, required): 사용자 책 ID

**응답**:
```json
{
  "ok": true,
  "data": "책이 내 서재에서 제거되었습니다.",
  "error": null
}
```

**예시**:
```
DELETE /api/v1/user/books/1
```

---

### 3.4 책 읽기 상태 변경

**엔드포인트**: `PUT /api/v1/user/books/{userBookId}/category`

**인증**: 필요

**경로 변수**:
- `userBookId` (Long, required): 사용자 책 ID

**요청 파라미터**:
- `category` (String, required): 새로운 카테고리
  - `ToRead`: 읽을 책
  - `Reading`: 읽는 중
  - `AlmostFinished`: 거의 다 읽음
  - `Finished`: 읽은 책

**응답**:
```json
{
  "ok": true,
  "data": "책의 읽기 상태가 변경되었습니다.",
  "error": null
}
```

**예시**:
```
PUT /api/v1/user/books/1/category?category=Reading
```

---

### 3.5 책 읽기 시작

**엔드포인트**: `POST /api/v1/user/books/{userBookId}/start-reading`

**인증**: 필요

**경로 변수**:
- `userBookId` (Long, required): 사용자 책 ID

**요청 본문** (`StartReadingRequest`):
```json
{
  "readingStartDate": "2024-01-15",
  "readingProgress": 50,
  "purchaseType": "PURCHASED"  // optional: PURCHASED, BORROWED, GIFTED, LIBRARY
}
```

**응답**:
```json
{
  "ok": true,
  "data": "책 읽기를 시작했습니다.",
  "error": null
}
```

**설명**:
- `ToRead` 상태의 책을 `Reading` 상태로 변경합니다.
- 독서 시작일과 진행률(페이지 수)을 입력받습니다.

---

### 3.6 책 완독

**엔드포인트**: `POST /api/v1/user/books/{userBookId}/finish-reading`

**인증**: 필요

**경로 변수**:
- `userBookId` (Long, required): 사용자 책 ID

**요청 본문** (`FinishReadingRequest`):
```json
{
  "readingFinishedDate": "2024-01-20",
  "rating": 5,  // 1-5점
  "review": "매우 좋은 책이었습니다."  // optional
}
```

**응답**:
```json
{
  "ok": true,
  "data": "책이 완독 처리되었습니다.",
  "error": null
}
```

**설명**:
- `AlmostFinished` 상태의 책을 `Finished` 상태로 변경합니다.
- 독서 종료일과 평점을 입력받습니다.

---

### 3.7 책 상세 정보 변경

**엔드포인트**: `PUT /api/v1/user/books/{userBookId}`

**인증**: 필요

**경로 변수**:
- `userBookId` (Long, required): 사용자 책 ID

**요청 본문** (`BookDetailUpdateRequest`):
```json
{
  "category": "Reading",  // optional
  "expectation": "기대감",  // optional
  "readingStartDate": "2024-01-15",  // optional
  "readingProgress": 100,  // optional
  "purchaseType": "PURCHASED",  // optional
  "readingFinishedDate": "2024-01-20",  // optional
  "rating": 5,  // optional
  "review": "후기"  // optional
}
```

**응답**:
```json
{
  "ok": true,
  "data": "책 상세 정보가 변경되었습니다.",
  "error": null
}
```

**설명**:
- 독서 시작일, 독서 종료일, 진행률(페이지수), 평점, 후기 등 책의 상세 정보를 변경합니다.
- 기존 값은 유지되고, 입력된 값만 업데이트됩니다 (Partial Update).
- 모든 필드는 선택사항(optional)입니다.

---

## 4. 사용자 프로필 API (`/users`)

### 4.1 내 프로필 조회

**엔드포인트**: `GET /api/v1/users/me`

**인증**: 필요

**응답** (`UserProfileResponse`):
```json
{
  "ok": true,
  "data": {
    "id": 1,
    "loginId": "user123",
    "email": "user@example.com",
    "name": "홍길동",
    "role": "USER",
    "status": "ACTIVE"
  },
  "error": null
}
```

---

## HTTP 상태 코드

- **200 OK**: 요청 성공
- **400 Bad Request**: 잘못된 요청 (검증 실패 등)
- **401 Unauthorized**: 인증 실패 (토큰 없음, 만료 등)
- **403 Forbidden**: 권한 없음
- **404 Not Found**: 리소스를 찾을 수 없음
- **500 Internal Server Error**: 서버 내부 오류

## 에러 코드

에러 응답의 `error.code` 필드에 포함될 수 있는 값들:

- `VALIDATION_ERROR`: 입력값 검증 실패
- `AUTHENTICATION_ERROR`: 인증 실패
- `AUTHORIZATION_ERROR`: 권한 없음
- `NOT_FOUND`: 리소스를 찾을 수 없음
- `DUPLICATE_ERROR`: 중복된 데이터
- `INTERNAL_SERVER_ERROR`: 서버 내부 오류

## 참고 사항

1. **인증 토큰**: 인증이 필요한 API는 `Authorization: Bearer {accessToken}` 헤더를 포함해야 합니다.

2. **날짜 형식**: 날짜는 ISO 8601 형식 (`YYYY-MM-DD`)을 사용합니다.
   - 예: `2024-01-15`

3. **날짜시간 형식**: 날짜시간은 ISO 8601 형식 (`YYYY-MM-DDTHH:mm:ss`)을 사용합니다.
   - 예: `2024-01-15T10:30:00`

4. **페이지네이션**: 검색 API는 `start`와 `maxResults` 파라미터를 사용합니다.
   - `start`: 시작 페이지 (1부터 시작)
   - `maxResults`: 페이지당 결과 수

5. **Swagger UI**: API 문서는 Swagger UI를 통해 확인할 수 있습니다.
   - 개발 환경: `http://localhost:8080/swagger-ui.html`

---

**최종 업데이트**: 2024년
**버전**: 1.0

