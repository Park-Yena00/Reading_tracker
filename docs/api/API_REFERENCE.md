# API 명세서

> **대상**: 프론트엔드/앱 개발자 (공개 문서)  
> **목적**: Reading Tracker API를 사용하는 클라이언트 개발자를 위한 완전한 API 명세서

## 개요

- **기본 경로**: `/api/v1`
- **인증 방식**: JWT (Bearer Token)
- **응답 형식**: `ApiResponse<T>` 래퍼 사용
- **Content-Type**: `application/json`
- **사용 대상**: 웹 클라이언트와 앱 클라이언트 모두 동일한 RESTful API 사용

---

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

---

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

## 5. 메모 관리 API (`/memos`, `/today-flow`)

### 5.1 메모 작성

**엔드포인트**: `POST /api/v1/memos`

**인증**: 필요

**요청 본문** (`MemoCreateRequest`):
```json
{
  "userBookId": 1,
  "pageNumber": 50,
  "content": "메모 내용",
  "tags": ["인사이트", "요약"],
  "memoStartTime": "2024-01-15T10:30:00"
}
```

**응답** (`MemoResponse`):
```json
{
  "ok": true,
  "data": {
    "id": 1,
    "userBookId": 1,
    "bookTitle": "책 제목",
    "bookIsbn": "9788937461234",
    "pageNumber": 50,
    "content": "메모 내용",
    "tags": ["인사이트", "요약"],
    "memoStartTime": "2024-01-15T10:30:00",
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  },
  "error": null
}
```

**설명**:
- 독서 중 메모를 작성합니다.
- 페이지당 메모 개수 제한 없이 자유롭게 기록할 수 있습니다.
- `pageNumber`는 메모 작성 시점의 SESSION 모드 기준 초기 위치를 나타냅니다.
- `tags`는 메모 분류 태그 리스트입니다 (하나 이상의 태그 설정 가능).

---

### 5.2 메모 수정

**엔드포인트**: `PUT /api/v1/memos/{memoId}`

**인증**: 필요

**경로 변수**:
- `memoId` (Long, required): 메모 ID

**요청 본문** (`MemoUpdateRequest`):
```json
{
  "content": "수정된 메모 내용",
  "tags": ["인사이트", "질문"]
}
```

**응답** (`MemoResponse`):
```json
{
  "ok": true,
  "data": {
    "id": 1,
    "userBookId": 1,
    "bookTitle": "책 제목",
    "bookIsbn": "9788937461234",
    "pageNumber": 50,
    "content": "수정된 메모 내용",
    "tags": ["인사이트", "질문"],
    "memoStartTime": "2024-01-15T10:30:00",
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T11:00:00"
  },
  "error": null
}
```

**설명**:
- 작성한 메모의 내용과 태그를 수정합니다.
- `pageNumber`는 메모 작성 시점의 시작 위치를 나타내는 메타데이터이므로 수정할 수 없습니다.
- 모든 필드는 선택사항(optional)입니다.

---

### 5.3 메모 삭제

**엔드포인트**: `DELETE /api/v1/memos/{memoId}`

**인증**: 필요

**경로 변수**:
- `memoId` (Long, required): 메모 ID

**응답**:
```json
{
  "ok": true,
  "data": "메모가 삭제되었습니다.",
  "error": null
}
```

**예시**:
```
DELETE /api/v1/memos/1
```

---

### 5.4 오늘의 흐름 조회

**엔드포인트**: `GET /api/v1/today-flow`

**인증**: 필요

**요청 파라미터**:
- `date` (String, optional): 조회할 날짜 (ISO 8601 형식: `YYYY-MM-DD`). 기본값: 오늘 날짜
- `sortBy` (String, optional): 정렬 방식. 기본값: `SESSION`
  - `SESSION`: 책별로 그룹화하여 반환. 프론트엔드에서 시간축에 재배치하여 세션 단위로 구성
  - `BOOK`: 책별로 그룹화하여 반환
  - `TAG`: 태그별로 그룹화하여 반환 (태그 그룹 내부에서 책별로 다시 그룹화)
- `tagCategory` (String, optional): 태그 대분류 (TAG 모드에서만 사용). 기본값: `TYPE`
  - `TYPE`: 유형 (기본값)
  - `TOPIC`: 주제
  - `tagCategory`가 지정되면 해당 대분류가 대표 태그 결정 시 1순위가 됩니다.

**응답** (`TodayFlowResponse`):

**SESSION/BOOK 모드 응답**:
```json
{
  "ok": true,
  "data": {
    "date": "2024-01-15",
    "sortBy": "SESSION",
    "memosByBook": {
      "1": {
        "bookId": 1,
        "bookTitle": "책 제목",
        "bookIsbn": "9788937461234",
        "memos": [
          {
            "id": 1,
            "userBookId": 1,
            "bookTitle": "책 제목",
            "bookIsbn": "9788937461234",
            "pageNumber": 50,
            "content": "메모 내용",
            "tags": ["인사이트", "요약"],
            "memoStartTime": "2024-01-15T10:30:00",
            "createdAt": "2024-01-15T10:30:00",
            "updatedAt": "2024-01-15T10:30:00"
          }
        ],
        "memoCount": 1,
        "sortBy": "SESSION"
      }
    },
    "memosByTag": null,
    "totalMemoCount": 1
  },
  "error": null
}
```

**TAG 모드 응답**:
```json
{
  "ok": true,
  "data": {
    "date": "2024-01-15",
    "sortBy": "TAG",
    "memosByBook": null,
    "memosByTag": {
      "INSIGHT": {
        "tagCode": "INSIGHT",
        "memos": [],
        "memosByBook": {
          "1": {
            "bookId": 1,
            "bookTitle": "책 제목",
            "bookIsbn": "9788937461234",
            "memos": [],
            "memosByTag": null,
            "memoCount": 1,
            "sortBy": "TAG"
          }
        },
        "memoCount": 1
      }
    },
    "totalMemoCount": 1
  },
  "error": null
}
```

**예시**:
```
GET /api/v1/today-flow
GET /api/v1/today-flow?date=2024-01-15&sortBy=BOOK
GET /api/v1/today-flow?date=2024-01-15&sortBy=TAG&tagCategory=TOPIC
```

**설명**:
- 특정 날짜의 메모를 조회합니다. 날짜 파라미터가 없으면 오늘 날짜의 메모를 조회합니다.
- 독서 캘린더와 연동하여 과거 날짜의 메모도 조회할 수 있습니다.
- `sortBy` 파라미터로 정렬 방식을 선택할 수 있습니다.
  - **SESSION 모드**: 책별로 그룹화하여 반환. 프론트엔드에서 시간축에 재배치하여 세션 단위로 구성
  - **BOOK 모드**: 책별로 그룹화하여 반환
  - **TAG 모드**: 태그별로 그룹화하여 반환 (태그 그룹 내부에서 책별로 다시 그룹화)
- TAG 모드 사용 시 `tagCategory` 파라미터로 태그 대분류를 선택할 수 있습니다.

---

### 5.5 특정 책의 메모 조회

**엔드포인트**: `GET /api/v1/memos/books/{userBookId}`

**인증**: 필요

**경로 변수**:
- `userBookId` (Long, required): 사용자 책 ID

**요청 파라미터**:
- `date` (String, optional): 조회할 날짜 (ISO 8601 형식: `YYYY-MM-DD`). 날짜 제한 없이 모든 메모 조회하려면 생략

**응답** (`List<MemoResponse>`):
```json
{
  "ok": true,
  "data": [
    {
      "id": 1,
      "userBookId": 1,
      "bookTitle": "책 제목",
      "bookIsbn": "9788937461234",
      "pageNumber": 50,
      "content": "메모 내용",
      "tags": ["인사이트", "요약"],
      "memoStartTime": "2024-01-15T10:30:00",
      "createdAt": "2024-01-15T10:30:00",
      "updatedAt": "2024-01-15T10:30:00"
    }
  ],
  "error": null
}
```

**예시**:
```
GET /api/v1/memos/books/1
GET /api/v1/memos/books/1?date=2024-01-15
```

**설명**:
- 오늘의 흐름 화면에서 특정 책을 선택하면 해당 책에 작성된 메모를 조회합니다.
- 날짜 파라미터가 있으면 해당 날짜에 작성된 메모만 조회하고, 날짜 파라미터가 없으면 날짜 제한 없이 해당 책의 모든 메모를 조회합니다.
- 오늘의 흐름과 동일한 화면 구성으로 표시되며, 메모는 타임라인 순서로 정렬됩니다 (memo_start_time 기준).

---

### 5.6 최근 메모 작성 책 목록 조회

**엔드포인트**: `GET /api/v1/memos/books/recent`

**인증**: 필요

**요청 파라미터**:
- `months` (Integer, optional): 조회 기간 (개월 수). 기본값: 1

**응답** (`List<BookResponse>`):
```json
{
  "ok": true,
  "data": [
    {
      "id": 1,
      "title": "책 제목",
      "author": "저자명",
      "isbn": "9788937461234",
      "category": "Reading",
      "readingProgress": 50,
      "lastMemoTime": "2024-01-15T10:30:00"
    }
  ],
  "error": null
}
```

**예시**:
```
GET /api/v1/memos/books/recent
GET /api/v1/memos/books/recent?months=3
```

**설명**:
- 최근 N개월 이내에 메모가 작성된 책들의 목록을 조회합니다.
- 각 책의 최신 메모 작성 시간 기준으로 내림차순 정렬됩니다.
- `months` 파라미터로 조회 기간을 조정할 수 있습니다 (기본값: 1개월).
- 책의 흐름 기능에서 월별 책 목록으로 사용됩니다.

---

### 5.7 책 덮기 (독서 활동 종료)

**엔드포인트**: `POST /api/v1/memos/books/{userBookId}/close`

**인증**: 필요

**경로 변수**:
- `userBookId` (Long, required): 사용자 책 ID

**요청 본문** (`CloseBookRequest`):
```json
{
  "lastReadPage": 100
}
```

**응답**:
```json
{
  "ok": true,
  "data": "독서 활동이 종료되었습니다.",
  "error": null
}
```

**예시**:
```
POST /api/v1/memos/books/1/close
```

**설명**:
- 독서 활동을 종료하고 마지막으로 읽은 페이지 수를 기록합니다.
- 독서 진행률이 업데이트되며, 진행률에 따라 카테고리가 자동으로 변경될 수 있습니다.
- 책 덮기 후 오늘의 흐름에서 해당 책의 메모가 섹션으로 구분되어 표시됩니다.

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
   - 개발 환경: `http://localhost:8080/swagger-ui/index.html`

---

**최종 업데이트**: 2024년 12월  
**버전**: 1.1

**변경 이력**:
- v1.1 (2024-12): 메모 관리 API 추가 (메모 작성/수정/삭제, 오늘의 흐름 조회, 책별 메모 조회, 책 덮기 등)
- v1.0 (2024): 초기 API 명세서 작성

