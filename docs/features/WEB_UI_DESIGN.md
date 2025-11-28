# 웹 UI 디자인 명세서

## 개요

이 문서는 **Reading Tracker (독서 기록 사이트)** 프론트엔드 프로젝트의 사용자 인터페이스(UI) 디자인 명세를 정의하며, 서버 프로젝트와의 연동을 고려한 상세 설계를 포함합니다.

**참고 문서:**
- [웹 UI 아키텍처 문서](./WEB_UI_ARCHITECTURE.md) - 기술적 구현 가이드
- [프로젝트 아키텍처 문서](../architecture/ARCHITECTURE.md) - 서버 아키텍처

## 프로젝트 정보

- **프로젝트명**: Reading Tracker (독서 기록 사이트)
- **서버 API**: Spring Boot REST API (`/api/v1`)
- **인증 방식**: JWT 기반 인증 (Access Token + Refresh Token)
- **데이터 통신**: JSON 형식

## 페이지 구조

### 1. 홈 페이지 (`index.html` / `pages/home.html`)

**설명**: 독서 기록 사이트의 메인 페이지로, 주요 기능 소개와 사용자 인사말을 제공합니다.

**구성 요소**:
- **헤더**: 
  - 로고 (Reading Tracker)
  - 내비게이션 메뉴 (도서 검색, 서재, 프로필)
  - 로그인/회원가입 버튼 (비인증 시) 또는 사용자 메뉴 (인증 시)
- **메인 배너**: 
  - 주요 서비스 소개 문구
  - "시작하기" 버튼 (회원가입 페이지로 이동)
- **기능 섹션**:
  - 도서 검색 기능 소개
  - 독서 기록 관리 기능 소개
  - 통계 및 분석 기능 소개
- **푸터**: 
  - 저작권 정보
  - 연락처 정보

**API 연동**:
- 비인증 페이지 (API 호출 없음)

---

### 2. 로그인 페이지 (`pages/login.html`)

**설명**: 사용자가 계정에 로그인할 수 있는 페이지입니다.

**구성 요소**:
- **로그인 폼**:
  - 로그인 ID 입력 필드 (필수)
  - 비밀번호 입력 필드 (필수)
  - "로그인 유지" 체크박스 (선택)
  - 로그인 버튼
- **추가 링크**:
  - 비밀번호 찾기 링크
  - 회원가입 링크

**API 연동**:
- `POST /api/v1/auth/login`
  - Request Body: `LoginRequest` (loginId, password)
  - Response: `LoginResponse` (accessToken, refreshToken, user)
  - 성공 시: 서재 페이지로 리다이렉트
  - 실패 시: 에러 메시지 표시

**상태 관리**:
- 로그인 성공 시 `AUTH_EVENTS.LOGIN` 이벤트 발행
- 인증 상태는 `AuthState`에서 관리

---

### 3. 회원가입 페이지 (`pages/register.html`)

**설명**: 새로운 사용자 계정을 생성하는 페이지입니다.

**구성 요소**:
- **회원가입 폼**:
  - 로그인 ID 입력 필드 (필수, 유효성 검사)
  - 이메일 입력 필드 (필수, 이메일 형식 검사)
  - 이름 입력 필드 (필수)
  - 비밀번호 입력 필드 (필수, 강도 검사)
  - 비밀번호 확인 입력 필드 (필수, 일치 여부 확인)
  - 회원가입 버튼
- **추가 링크**:
  - 로그인 페이지 링크

**API 연동**:
- `POST /api/v1/auth/signup`
  - Request Body: `RegistrationRequest` (loginId, email, name, password)
  - Response: `RegisterResponse` (user)
  - 성공 시: 로그인 페이지로 리다이렉트
  - 실패 시: 에러 메시지 표시 (필드별 에러 메시지 지원)

---

### 4. 도서 검색 페이지 (`pages/book-search.html`)

**설명**: 알라딘 API를 통해 도서를 검색하고 결과를 확인할 수 있는 페이지입니다.

**구성 요소**:
- **검색 영역**:
  - 검색어 입력 필드
  - 검색 타입 선택 (도서명, 저자, ISBN 등)
  - 검색 버튼
- **검색 결과 영역**:
  - 도서 카드 리스트 (그리드 레이아웃)
  - 각 카드 포함 정보:
    - 도서 표지 이미지
    - 도서명
    - 저자명
    - 출판사
    - 출판일
    - "서재에 추가" 버튼 (인증 시만 표시)
  - 페이지네이션 (검색 결과가 많은 경우)
- **필터링 옵션** (선택사항):
  - 정렬 기준 (관련도, 출판일순 등)

**API 연동**:
- `GET /api/v1/books/search?query={검색어}&queryType={검색타입}&start={시작위치}&maxResults={결과수}`
  - Response: `BookSearchResponse` (books[], totalResults)
  - 비인증 접근 가능

---

### 5. 도서 상세 페이지 (`pages/book-detail.html`)

**설명**: 선택한 도서의 상세 정보를 확인하고 서재에 추가할 수 있는 페이지입니다.

**구성 요소**:
- **도서 정보 영역**:
  - 도서 표지 이미지 (대형)
  - 도서명
  - 저자명
  - 출판사
  - 출판일
  - ISBN
  - 책 소개
  - 목차 (있는 경우)
- **액션 버튼**:
  - "서재에 추가" 버튼 (인증 시)
  - "돌아가기" 버튼

**API 연동**:
- `GET /api/v1/books/{isbn}`
  - Response: `BookDetailResponse` (book 정보)
  - 비인증 접근 가능

**상태 관리**:
- 도서 상세 정보는 페이지 로드 시 한 번만 조회

---

### 6. 서재 페이지 (`pages/bookshelf.html`) - 보호됨

**설명**: 사용자가 읽고 있는/읽은 도서 목록을 관리하는 페이지입니다.

**구성 요소**:
- **헤더 영역**:
  - 페이지 제목 ("내 서재")
  - 필터/정렬 옵션:
    - 읽기 상태별 필터 (전체, 읽는 중, 읽음, 읽고 싶음)
    - 정렬 기준 (추가일순, 제목순, 저자순)
- **도서 목록 영역**:
  - 도서 카드 리스트 (그리드 또는 리스트 레이아웃)
  - 각 카드 포함 정보:
    - 도서 표지 이미지
    - 도서명
    - 저자명
    - 독서 상태 배지
    - 추가일
    - 액션 메뉴 (상태 변경, 삭제)
- **통계 정보** (선택사항):
  - 전체 도서 수
  - 읽는 중인 도서 수
  - 읽은 도서 수

**API 연동**:
- `GET /api/v1/user/books`
  - Response: `BookshelfResponse` (userBooks[])
  - 인증 필요 (JWT 토큰)
- `PUT /api/v1/user/books/{userBookId}`
  - Request Body: `BookStatusUpdateRequest` (status)
  - 도서 상태 변경 시 사용
- `DELETE /api/v1/user/books/{userBookId}`
  - 서재에서 도서 삭제 시 사용

**상태 관리**:
- 서재 데이터는 `BookState`에서 관리
- 도서 추가/삭제/상태 변경 시 `BOOK_EVENTS` 이벤트 발행

---

### 7. 프로필 페이지 (`pages/profile.html`) - 보호됨

**설명**: 사용자 프로필 정보를 조회하고 수정할 수 있는 페이지입니다.

**구성 요소**:
- **프로필 정보 영역**:
  - 프로필 이미지 (기본 아바타 또는 사용자 업로드 이미지)
  - 이름
  - 이메일
  - 가입일
- **프로필 수정 폼**:
  - 이름 수정 필드
  - 이메일 수정 필드 (선택사항)
  - "저장" 버튼
- **계정 관리 섹션**:
  - 비밀번호 변경 링크
  - 로그아웃 버튼

**API 연동**:
- `GET /api/v1/user/profile`
  - Response: `UserProfileResponse` (user 정보)
  - 인증 필요
- `PUT /api/v1/user/profile`
  - Request Body: `ProfileUpdateRequest` (name, email)
  - 프로필 수정 시 사용

---

## UI 컴포넌트

### 1. 버튼 (Button)

#### 기본 버튼
- **색상**: Primary (#3B82F6) - 배경색
- **텍스트 색상**: 흰색 (#FFFFFF)
- **크기**: 
  - 기본: `padding: 0.5rem 1rem` (height: ~40px)
  - 큰 버튼: `padding: 0.75rem 1.5rem` (height: ~48px)
  - 작은 버튼: `padding: 0.375rem 0.75rem` (height: ~32px)
- **모서리 둥글기**: `border-radius: 0.5rem`
- **호버 효과**: 배경색 어둡게 (#2563EB)
- **비활성화**: `opacity: 0.5`, `cursor: not-allowed`

#### 보조 버튼 (Secondary)
- **색상**: 회색 배경 (#F3F4F6), 어두운 텍스트 (#111827)
- **호버 효과**: 배경색 밝게 (#F9FAFB)

#### 위험 버튼 (Danger)
- **색상**: 빨간색 (#EF4444)
- **호버 효과**: 어두운 빨간색 (#DC2626)
- **용도**: 삭제, 취소 등의 위험한 액션

#### 성공 버튼 (Success)
- **색상**: 녹색 (#10B981)
- **용도**: 확인, 저장 등의 긍정적인 액션

**CSS 클래스 예시:**
```css
.btn { /* 기본 스타일 */ }
.btn-primary { /* Primary 버튼 */ }
.btn-secondary { /* Secondary 버튼 */ }
.btn-danger { /* Danger 버튼 */ }
.btn-success { /* Success 버튼 */ }
.btn-large { /* 큰 버튼 */ }
.btn-small { /* 작은 버튼 */ }
.btn:disabled { /* 비활성화 */ }
```

---

### 2. 입력 필드 (Input)

#### 텍스트 입력
- **너비**: 100% (부모 요소 기준)
- **높이**: 40px
- **패딩**: `padding: 0.5rem 0.75rem`
- **테두리**: `1px solid #D1D5DB` (회색)
- **모서리 둥글기**: `border-radius: 0.375rem`
- **포커스 시**: 
  - 테두리 색상: Primary (#3B82F6)
  - 그림자: `box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1)`
- **에러 상태**: 
  - 테두리 색상: 빨간색 (#EF4444)
  - 에러 메시지 표시 (입력 필드 아래)

#### 레이블
- **위치**: 입력 필드 위쪽
- **폰트 크기**: `0.875rem` (14px)
- **폰트 굵기**: `font-weight: 500`
- **색상**: 어두운 회색 (#374151)
- **필수 표시**: 별표(*) 추가, 빨간색

**CSS 클래스 예시:**
```css
.input { /* 기본 입력 필드 */ }
.input:focus { /* 포커스 상태 */ }
.input.error { /* 에러 상태 */ }
.label { /* 레이블 */ }
.label.required::after { /* 필수 표시 */ }
```

---

### 3. 카드 (Card)

**설명**: 정보 블록을 표시하는 카드 컴포넌트입니다. 도서 정보, 통계 등에 사용됩니다.

**스타일**:
- **배경색**: 흰색 (#FFFFFF)
- **그림자**: `box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1)`
- **모서리 둥글기**: `border-radius: 0.75rem`
- **패딩**: `padding: 1.5rem`
- **호버 효과**: 그림자 증가 (`box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1)`)

**도서 카드 특화**:
- 도서 표지 이미지 (고정 비율, 예: 3:4)
- 도서 정보 (제목, 저자, 출판사)
- 액션 버튼 영역

**CSS 클래스 예시:**
```css
.card { /* 기본 카드 */ }
.card-book { /* 도서 카드 */ }
.card:hover { /* 호버 효과 */ }
```

---

### 4. 모달 (Modal)

**설명**: 사용자에게 중요한 정보를 표시하거나 확인을 받는 팝업 창입니다.

**구성 요소**:
- **배경 오버레이**: 반투명 검은색 (`rgba(0, 0, 0, 0.5)`)
- **모달 창**:
  - 배경: 흰색
  - 모서리 둥글기: `border-radius: 0.75rem`
  - 패딩: `padding: 1.5rem`
  - 최대 너비: `max-width: 500px`
  - 중앙 정렬
- **제목 영역**: 모달 제목
- **내용 영역**: 모달 본문
- **액션 영역**: 확인/취소 버튼

**CSS 클래스 예시:**
```css
.modal-overlay { /* 배경 오버레이 */ }
.modal { /* 모달 창 */ }
.modal-header { /* 제목 영역 */ }
.modal-body { /* 내용 영역 */ }
.modal-footer { /* 액션 영역 */ }
```

---

### 5. 로딩 스피너 (Loading Spinner)

**설명**: 비동기 작업 진행 중임을 나타내는 로딩 인디케이터입니다.

**스타일**:
- **애니메이션**: 회전하는 원형 스피너
- **색상**: Primary 색상 (#3B82F6)
- **크기**: 
  - 작은 스피너: `width: 20px, height: 20px`
  - 큰 스피너: `width: 40px, height: 40px`

**CSS 클래스 예시:**
```css
.spinner { /* 기본 스피너 */ }
.spinner-small { /* 작은 스피너 */ }
.spinner-large { /* 큰 스피너 */ }
```

---

### 6. 배지 (Badge)

**설명**: 독서 상태, 카테고리 등을 표시하는 작은 라벨입니다.

**스타일**:
- **독서 상태별 색상**:
  - 읽는 중: 파란색 (#3B82F6)
  - 읽음: 녹색 (#10B981)
  - 읽고 싶음: 회색 (#6B7280)
- **패딩**: `padding: 0.25rem 0.5rem`
- **모서리 둥글기**: `border-radius: 9999px` (완전히 둥글게)
- **폰트 크기**: `0.75rem` (12px)

**CSS 클래스 예시:**
```css
.badge { /* 기본 배지 */ }
.badge-reading { /* 읽는 중 */ }
.badge-read { /* 읽음 */ }
.badge-want-to-read { /* 읽고 싶음 */ }
```

---

## 색상 팔레트

### 주요 색상

- **Primary (주요 색상)**: `#3B82F6` (파란색)
  - 호버: `#2563EB`
  - 밝은 버전: `#60A5FA`
  - 배경용: `rgba(59, 130, 246, 0.1)`

- **Secondary (보조 색상)**: `#6B7280` (회색)
  - 호버: `#4B5563`
  - 밝은 버전: `#9CA3AF`

- **Success (성공)**: `#10B981` (녹색)
- **Danger (위험)**: `#EF4444` (빨간색)
- **Warning (경고)**: `#F59E0B` (노란색)
- **Info (정보)**: `#3B82F6` (파란색, Primary와 동일)

### 텍스트 색상

- **Primary Text**: `#111827` (거의 검은색)
- **Secondary Text**: `#6B7280` (중간 회색)
- **Muted Text**: `#9CA3AF` (밝은 회색)
- **White Text**: `#FFFFFF` (흰색, 배경 위)

### 배경 색상

- **Primary Background**: `#FFFFFF` (흰색)
- **Secondary Background**: `#F9FAFB` (밝은 회색)
- **Muted Background**: `#F3F4F6` (더 밝은 회색)
- **Overlay Background**: `rgba(0, 0, 0, 0.5)` (반투명 검은색)

### 테두리 색상

- **Default Border**: `#D1D5DB` (연한 회색)
- **Focus Border**: `#3B82F6` (Primary 색상)
- **Error Border**: `#EF4444` (빨간색)

---

## 폰트

### 폰트 패밀리

- **기본 폰트**: `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Noto Sans KR', sans-serif`
  - 시스템 기본 폰트 우선 사용
  - 한글 지원: 'Noto Sans KR'
  - 폴백: sans-serif

### 폰트 크기

- **H1 (큰 제목)**: `2rem` (32px)
- **H2 (중간 제목)**: `1.5rem` (24px)
- **H3 (작은 제목)**: `1.25rem` (20px)
- **Body (본문)**: `1rem` (16px)
- **Small (작은 텍스트)**: `0.875rem` (14px)
- **Tiny (매우 작은 텍스트)**: `0.75rem` (12px)

### 폰트 굵기

- **Normal**: `font-weight: 400`
- **Medium**: `font-weight: 500`
- **Semibold**: `font-weight: 600`
- **Bold**: `font-weight: 700`

---

## 간격 시스템 (Spacing)

일관된 간격을 위해 0.25rem 단위를 사용합니다.

- **xs**: `0.25rem` (4px)
- **sm**: `0.5rem` (8px)
- **md**: `1rem` (16px)
- **lg**: `1.5rem` (24px)
- **xl**: `2rem` (32px)
- **2xl**: `3rem` (48px)

---

## 반응형 디자인

### 브레이크포인트

- **모바일**: `320px ~ 768px`
- **태블릿**: `769px ~ 1024px`
- **데스크톱**: `1025px 이상`

### 모바일 (320px ~ 768px)

- **내비게이션**: 햄버거 메뉴로 축소
- **도서 카드**: 1열 레이아웃
- **폼 요소**: 전체 너비 사용
- **패딩**: 줄여서 화면 공간 최대 활용

### 태블릿 (769px ~ 1024px)

- **내비게이션**: 상단 바 형태로 표시
- **도서 카드**: 2열 그리드 레이아웃
- **콘텐츠**: 중간 크기 패딩 사용

### 데스크톱 (1025px 이상)

- **내비게이션**: 전체 메뉴 표시
- **도서 카드**: 3~4열 그리드 레이아웃
- **콘텐츠**: 넓은 최대 너비 설정 (예: `max-width: 1200px`)
- **여백**: 충분한 여백 확보

---

## 접근성 고려사항

### 시각적 접근성

- **색상 대비**: WCAG AA 기준 준수 (텍스트와 배경 간 최소 4.5:1)
- **포커스 표시**: 키보드 네비게이션 시 명확한 포커스 링 표시
- **텍스트 크기**: 최소 12px 이상 유지

### 키보드 접근성

- **탭 순서**: 논리적인 순서로 탭 이동 가능
- **키보드 단축키**: 주요 액션에 키보드 단축키 제공 (선택사항)
- **ESC 키**: 모달 닫기 등에 ESC 키 지원

### 스크린 리더 지원

- **시맨틱 HTML**: 적절한 HTML 태그 사용 (`<header>`, `<nav>`, `<main>`, `<footer>` 등)
- **ARIA 라벨**: 버튼, 링크 등에 명확한 `aria-label` 제공
- **이미지 대체 텍스트**: 모든 이미지에 `alt` 속성 제공
- **폼 라벨**: 모든 입력 필드에 연결된 `<label>` 제공

---

## 서버 API 연동 가이드

### API 기본 정보

- **Base URL**: `http://localhost:8080/api/v1` (개발 환경)
- **프로덕션 URL**: 프로덕션 서버 주소로 변경 필요
- **인증**: JWT Bearer 토큰 방식

### 인증 처리

#### 로그인 플로우

1. 사용자가 로그인 폼 제출
2. `POST /api/v1/auth/login` 요청
3. 성공 시:
   - `accessToken`, `refreshToken` 받음
   - `localStorage`에 토큰 저장
   - `AuthState.setUser()` 호출 (이벤트 자동 발행)
   - 서재 페이지로 리다이렉트
4. 실패 시:
   - 에러 메시지 표시
   - 폼 초기화하지 않음

#### API 요청 시 토큰 포함

모든 인증이 필요한 API 요청에는 `Authorization` 헤더에 토큰을 포함합니다:

```javascript
headers: {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
}
```

#### 토큰 갱신

- `accessToken` 만료 시 (401 에러)
- `refreshToken`으로 자동 갱신 시도
- 갱신 실패 시 자동 로그아웃 처리

### 주요 API 엔드포인트

#### 인증 관련

- **회원가입**: `POST /api/v1/auth/signup`
  - Request: `RegistrationRequest`
  - Response: `RegisterResponse`

- **로그인**: `POST /api/v1/auth/login`
  - Request: `LoginRequest`
  - Response: `LoginResponse` (tokens, user)

- **토큰 갱신**: `POST /api/v1/auth/refresh`
  - Request: `RefreshTokenRequest`
  - Response: `RefreshTokenResponse`

#### 도서 관련

- **도서 검색**: `GET /api/v1/books/search`
  - Query Params: `query`, `queryType`, `start`, `maxResults`
  - Response: `BookSearchResponse`

- **도서 상세**: `GET /api/v1/books/{isbn}`
  - Response: `BookDetailResponse`

#### 서재 관련 (인증 필요)

- **서재 조회**: `GET /api/v1/user/books`
  - Response: `BookshelfResponse`

- **도서 추가**: `POST /api/v1/user/books`
  - Request: `BookAdditionRequest`
  - Response: `BookAdditionResponse`

- **도서 상태 변경**: `PUT /api/v1/user/books/{userBookId}`
  - Request: `BookStatusUpdateRequest`
  - Response: `BookStatusUpdateResponse`

- **도서 삭제**: `DELETE /api/v1/user/books/{userBookId}`
  - Response: `ApiResponse`

#### 사용자 관련 (인증 필요)

- **프로필 조회**: `GET /api/v1/user/profile`
  - Response: `UserProfileResponse`

- **프로필 수정**: `PUT /api/v1/user/profile`
  - Request: `ProfileUpdateRequest`
  - Response: `UserProfileResponse`

### API 응답 형식

모든 API는 `ApiResponse<T>` 형식을 사용합니다:

```json
{
  "ok": true,
  "data": {
    // 실제 데이터
  },
  "error": null
}
```

에러 응답:
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

### 에러 처리

#### 클라이언트 측 에러 처리

1. **네트워크 에러**: 인터넷 연결 문제 안내
2. **401 에러**: 토큰 갱신 시도 또는 로그아웃 처리
3. **403 에러**: 권한 없음 안내
4. **404 에러**: 리소스를 찾을 수 없음 안내
5. **422 에러**: 입력값 검증 실패, 필드별 에러 메시지 표시
6. **500 에러**: 서버 오류 안내 (일반적인 메시지)

#### 사용자 피드백

- **성공**: 토스트 메시지 또는 인라인 성공 메시지
- **에러**: 인라인 에러 메시지 (폼 필드 아래)
- **로딩**: 로딩 스피너 표시

---

## 상태 관리 (Event-Driven)

### 이벤트 기반 상태 관리

모든 상태 변경은 Event Bus를 통해 이벤트로 발행되고, 필요한 컴포넌트에서 구독하여 처리합니다.

### 주요 이벤트

#### 인증 이벤트 (`AUTH_EVENTS`)

- `auth:login` - 로그인 성공 시
- `auth:logout` - 로그아웃 시
- `auth:stateChanged` - 인증 상태 변경 시
- `auth:tokenRefreshed` - 토큰 갱신 시

#### 애플리케이션 이벤트 (`APP_EVENTS`)

- `app:loadingStart` - 로딩 시작
- `app:loadingEnd` - 로딩 종료
- `app:error` - 에러 발생
- `app:pageChanged` - 페이지 변경
- `app:stateChanged` - 애플리케이션 상태 변경

#### 도서 이벤트 (`BOOK_EVENTS`)

- `book:bookshelfUpdated` - 서재 업데이트
- `book:bookAdded` - 도서 추가
- `book:bookRemoved` - 도서 삭제
- `book:bookStatusChanged` - 도서 상태 변경

### 이벤트 구독 예시

```javascript
// 로그인 이벤트 구독
const unsubscribe = eventBus.subscribe(AUTH_EVENTS.LOGIN, (data) => {
    console.log('User logged in:', data.user);
    // UI 업데이트
});

// 컴포넌트 제거 시 구독 해제
destroy() {
    unsubscribe();
}
```

---

## UI/UX 가이드라인

### 사용자 경험 원칙

1. **명확성**: 모든 액션과 상태가 명확하게 표시되어야 함
2. **일관성**: 모든 페이지에서 일관된 UI 패턴 사용
3. **피드백**: 모든 사용자 액션에 즉각적인 피드백 제공
4. **오류 방지**: 가능한 한 사용자의 실수를 방지하는 UI 설계
5. **효율성**: 최소한의 클릭/입력으로 작업 완료 가능

### 로딩 상태

- **즉각 피드백**: 버튼 클릭 시 즉시 로딩 상태 표시
- **스켈레톤 UI**: 데이터 로딩 중 스켈레톤 UI 표시 (선택사항)
- **로딩 메시지**: 로딩이 오래 걸릴 경우 메시지 표시

### 에러 처리

- **친절한 메시지**: 기술적인 용어보다 사용자 친화적인 메시지
- **해결 방법 제시**: 가능한 경우 에러 해결 방법 제시
- **재시도 옵션**: 네트워크 에러 등 재시도 가능한 경우 버튼 제공

---

## 개발 가이드라인

### 파일 구조

프로젝트의 디렉토리 구조는 [웹 UI 아키텍처 문서](./WEB_UI_ARCHITECTURE.md)를 참고하세요.

### 코드 스타일

- **파일명**: kebab-case (예: `book-search-view.js`)
- **클래스명**: PascalCase (예: `BookSearchView`)
- **함수/변수명**: camelCase (예: `handleLogin`)
- **상수명**: UPPER_SNAKE_CASE (예: `API_BASE_URL`)

### 컴포넌트 작성

- **클래스 기반**: 각 View는 클래스로 구현
- **초기화**: `init()` 메서드에서 이벤트 리스너 및 구독 등록
- **정리**: `destroy()` 메서드에서 모든 구독 해제 및 리소스 정리

### 스타일 작성

- **CSS 변수**: 공통 스타일은 CSS 변수로 정의 (`css/common/variables.css`)
- **모듈화**: 컴포넌트별, 페이지별 CSS 파일 분리
- **반응형**: 모바일 퍼스트 접근 방식 권장

---

## 테스트 고려사항

### 사용자 시나리오 테스트

다음 주요 사용자 플로우를 테스트해야 합니다:

1. **회원가입 → 로그인 → 도서 검색 → 서재에 추가**
2. **로그인 → 서재 조회 → 도서 상태 변경**
3. **로그인 → 프로필 수정**
4. **로그아웃 → 로그인 페이지 이동**

### 브라우저 호환성

- Chrome (최신 버전)
- Firefox (최신 버전)
- Safari (최신 버전)
- Edge (최신 버전)

---

## 참고 자료

- [웹 UI 아키텍처 문서](./WEB_UI_ARCHITECTURE.md) - 기술적 구현 가이드
- [프로젝트 아키텍처 문서](../architecture/ARCHITECTURE.md) - 서버 아키텍처
- [MDN Web Docs](https://developer.mozilla.org/ko/) - 웹 표준 참조
- [WCAG 가이드라인](https://www.w3.org/WAI/WCAG21/quickref/) - 접근성 가이드라인

---

**최종 업데이트**: 2024년
**버전**: 1.0

