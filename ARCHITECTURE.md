# Reading Tracker 프로젝트 아키텍처 문서

## 프로젝트 개요

- **프로젝트명**: Reading Tracker (독서 기록 사이트)
- **기술 스택**: Spring Boot 3.2.0, Java 17, MySQL, JPA, Flyway
- **데이터베이스**: MySQL 8.0

## 패키지 구조 및 명명 규칙

### 패키지 명명 규칙
- **기본 패키지**: `com.readingtracker`
- **소문자 사용**: 모든 패키지명은 소문자로 구성
- **한 단어 사용**: 패키지명은 한 단어로만 구성되어야 함
- **DTO 패키지 예외**: DTO 관련 패키지만 가시성을 위해 'DTO' 글자만 대문자 사용 허용
  - 나머지 부분은 소문자로 작성
  - 예: `clientserverDTO`, `serverdbmsDTO`, `requestDTO`, `responseDTO`, `commandDTO`, `resultDTO`
- **3-tier Architecture**: 계층이 아닌 경계 중심으로 패키지 분리

### 3-tier Architecture 구조

#### Client ↔ Server 경계
- `server.controller` - REST API 컨트롤러 (클라이언트와의 통신 담당)
- `server.dto.clientserverDTO` - 클라이언트-서버 간 데이터 전송 객체
  - `requestDTO` - 클라이언트 → 서버 요청 DTO
  - `responseDTO` - 서버 → 클라이언트 응답 DTO
  - `ApiResponse.java`, `ErrorResponse.java` - 공통 응답 래퍼 (dto 바로 아래)

#### Server ↔ DBMS 경계
- `dbms.repository` - 데이터 접근 계층 (DBMS와의 통신 담당)
- `dbms.dto.serverdbmsDTO` - 서버-DBMS 간 데이터 전송 객체
  - `commandDTO` - 서비스 → DBMS 명령 DTO
  - `resultDTO` - DBMS → 서비스 결과 DTO
- `dbms.entity` - JPA 엔티티 (데이터베이스 테이블 매핑)

#### 서버 내부
- `server.service` - 비즈니스 로직 (서버 내부 처리)
  - `validation` - 검증 로직

#### 서버 공통 요소
- `server.common` - 서버 공통 요소
  - `constant` - 상수/Enum (BookCategory, ErrorCode)
  - `exception` - 예외 처리 (GlobalExceptionHandler)
  - `util` - 유틸리티 클래스 (JwtUtil, PasswordValidator)
- `server.config` - 서버 설정 클래스 (SecurityConfig, JwtConfig, CorsConfig 등)
- `server.security` - 보안 실행 컴포넌트 (JwtAuthenticationFilter)

#### 애플리케이션 진입점
- `ReadingTrackerApplication.java` - Spring Boot 메인 클래스 (루트 패키지)

```
com.readingtracker
├── ReadingTrackerApplication.java    # 애플리케이션 진입점
│
├── server                            # 서버 로직
│   ├── common                        # 서버 공통 요소
│   │   ├── constant                  # 상수/Enum
│   │   │   ├── BookCategory.java
│   │   │   └── ErrorCode.java
│   │   ├── exception                # 예외 처리
│   │   │   └── GlobalExceptionHandler.java
│   │   └── util                      # 유틸리티 클래스
│   │       ├── JwtUtil.java
│   │       └── PasswordValidator.java
│   ├── config                        # 서버 설정
│   │   ├── SecurityConfig.java
│   │   ├── JwtConfig.java
│   │   ├── CorsConfig.java
│   │   └── ...
│   ├── controller                    # Client ↔ Server 경계
│   │   └── v1                        # API 버전 관리
│   │       ├── AuthController.java
│   │       ├── BookController.java
│   │       └── ...
│   ├── security                       # Client ↔ Server 경계 (인증/인가)
│   │   └── JwtAuthenticationFilter.java
│   ├── service                       # 서버 내부 비즈니스 로직
│   │   ├── validation               # 검증 로직
│   │   │   ├── UserValidationService.java
│   │   │   └── BookValidationService.java
│   │   ├── AuthService.java
│   │   ├── UserService.java
│   │   ├── BookService.java
│   │   └── ...
│   └── dto                           # Client ↔ Server 경계
│       ├── clientserverDTO           # 클라이언트-서버 DTO
│       │   ├── requestDTO           # 클라이언트 → 서버 요청
│       │   │   ├── LoginRequest.java
│       │   │   └── ...
│       │   └── responseDTO          # 서버 → 클라이언트 응답
│       │       ├── LoginResponse.java
│       │       └── ...
│       ├── ApiResponse.java          # 공통 응답 래퍼
│       └── ErrorResponse.java        # 공통 에러 응답
│
└── dbms                              # DBMS 관련
    ├── repository                    # Server ↔ DBMS 경계
    │   ├── UserRepository.java
    │   ├── BookRepository.java
    │   └── ...
    ├── entity                        # Server ↔ DBMS 경계
    │   ├── User.java
    │   ├── Book.java
    │   └── ...
    └── dto                           # Server ↔ DBMS 경계
        └── serverdbmsDTO            # 서버-DBMS DTO
            ├── commandDTO            # 서비스 → DBMS 명령
            │   └── ...
            └── resultDTO            # DBMS → 서비스 결과
                └── ...

src/main/resources/                   # 리소스 파일 (위치 변경 없음)
├── application.yml                  # Spring Boot 전역 설정
└── db/migration/                    # Flyway 마이그레이션 파일
    ├── V1__Create_users_table.sql
    ├── V2__Create_user_devices_table.sql
    └── ...
```

### 클래스 명명 규칙

#### 엔티티 클래스
- **PascalCase** 사용
- 단수형 명사 사용
- 예: `User`, `Book`, `UserShelfBook`, `UserDevice`

#### 컨트롤러 클래스
- **PascalCase** + "Controller" 접미사
- 예: `AuthController`, `BookController`, `UserController`
- 모든 컨트롤러는 `BaseV1Controller`를 상속받아 `/api/v1` 경로 사용

#### 서비스 클래스
- **PascalCase** + "Service" 접미사
- 예: `AuthService`, `BookService`, `UserService`

#### Repository 인터페이스
- **PascalCase** + "Repository" 접미사
- `JpaRepository<Entity, ID>` 확장
- 예: `UserRepository`, `BookRepository`

#### DTO 클래스
- **RequestDTO**: `PascalCase` + "Request" 접미사
- **ResponseDTO**: `PascalCase` + "Response" 접미사
- **CommandDTO**: `PascalCase` + "Command" 접미사
- **ResultDTO**: `PascalCase` + "Result" 접미사

#### 설정 클래스
- **PascalCase** + "Config" 접미사
- 예: `SecurityConfig`, `JwtConfig`, `CorsConfig`

### 함수 및 변수 명명 규칙

- **Camel Case (낙타 표기법)** 사용
- 첫 글자는 **소문자**로 시작
- 이후 각 단어의 첫 글자는 **대문자**로 표기
- 예: `variableName`, `userName`, `getUserById`, `calculateTotalPrice`
- ❌ 잘못된 예: `VariableName`, `User_Name`, `get_user_by_id`

#### 함수 명명 규칙 (의미론적 접두사)

함수 이름만 보고도 기능을 알 수 있도록 **의미론적 접두사(prefix)**를 사용해야 합니다.

- **`show...`**: 무언가를 보여주는 함수
  - 예: `showMessage()`, `showDialog()`, `showUserInfo()`
- **`get...`**: 구조화된 객체의 속성을 직접 반환하는 함수 (단순 접근)
  - 예: `getAge()`, `getUserById()`, `getTotalPrice()`, `getDescription()`
  - 객체의 속성이나 필드에 직접 접근하여 값을 반환할 때 사용
  - 복잡한 변환 과정 없이 저장된 값을 그대로 반환
- **`extract...`**: 암호화/인코딩된 데이터나 구조화된 데이터에서 특정 정보를 추출하는 함수 (복잡한 변환 과정)
  - 예: `extractLoginId()`, `extractUserId()`, `extractClaim()`, `extractExpiration()`
  - 데이터 파싱, 디코딩, 검증 등의 복잡한 과정을 거쳐 정보를 추출할 때 사용
  - JWT 토큰, JSON, XML 등 구조화된 데이터에서 특정 값을 추출할 때 사용
  - 업계에서도 구조화된 데이터에서 정보를 추출할 때 널리 사용되는 관례
- **`calc...`**: 값을 계산하는 함수
  - 예: `calcSum()`, `calcTotalPrice()`, `calcAverage()`
- **`create...`**: 단순 객체 생성 함수 (무에서 유를 창조)
  - 예: `createForm()`, `createUser()`, `createToken()`
  - 단순한 객체 인스턴스 생성 시 사용
- **`generate...`**: 여러 정보를 조합·변환하여 새로운 값을 생성하는 함수 (단계에 따라 발생하는 과정)
  - 예: `generateAccessToken()`, `generateRefreshToken()`, `generateReport()`
  - 복잡한 변환 과정(암호화, 서명, 계산 등)을 거쳐 값을 생성할 때 사용
  - 여러 입력값을 조합하여 새로운 결과를 만들어낼 때 사용
- **`update...`**: 무언가를 업데이트하는 함수
  - 예: `updateUser()`, `updateStatus()`, `updatePrice()`
- **`delete...`**: 무언가를 삭제하는 함수
  - 예: `deleteUser()`, `deleteItem()`, `deleteToken()`
- **`validate...`**: 무언가를 검증하는 함수
  - 예: `validateEmail()`, `validatePassword()`, `validateInput()`
- **`check...`**: 무언가를 확인하는 동작을 수행하는 함수 (동작 강조)
  - 예: `checkPermission()`, `checkExists()`, `checkStatus()`
  - 확인/검증 동작을 수행하고 결과를 반환할 때 사용
  - 동작 자체를 강조하는 경우에 사용
- **`is...`**: 객체나 값의 상태/속성에 대한 boolean 질문 형태의 함수 (질문 형태)
  - 예: `isTokenExpired()`, `isRefreshToken()`, `isValid()`, `isEmpty()`, `isEnabled()`
  - "~인가?" 질문 형태로 상태나 속성을 묻는 boolean 반환 함수
  - Java Bean 명명 관례에 따라 boolean getter는 `is...` 사용
  - Java 표준 라이브러리에서도 널리 사용되는 관례 (예: `String.isEmpty()`, `Optional.isPresent()`)
  - 상태/속성에 대한 질문일 때 `is...`, 확인 동작을 강조할 때 `check...` 사용
- **`find...`**: 무언가를 찾는 함수
  - 예: `findUser()`, `findById()`, `findAll()`
- **`save...`**: 무언가를 저장하는 함수
  - 예: `saveUser()`, `saveData()`, `saveChanges()`
- **`handle...`**: 예외를 처리하는 함수 (예외 처리 전용)
  - 예: `handleValidationException()`, `handleIllegalArgumentException()`, `handleRuntimeException()`

이러한 접두사를 사용하면 코드의 가독성이 향상되고, 함수의 역할을 쉽게 파악할 수 있습니다.

#### 함수 단일 책임 원칙

함수는 **반드시 하나의 동작만 담당**해야 합니다. 함수 이름이 의미하는 동작만 수행하고, 다른 관련 동작은 별도의 함수로 분리해야 합니다.

**원칙:**
- 하나의 함수는 하나의 명확한 책임만 가져야 함
- 함수 이름이 나타내는 동작만 수행
- 부수 효과(side effect)를 최소화

**예시:**

✅ **올바른 예:**
```java
// getAge()는 나이를 얻어오는 동작만 수행
int age = getAge(userId);

// createForm()은 form을 만들고 반환하는 동작만 수행
Form form = createForm();

// checkPermission()은 승인 여부를 확인하고 결과를 반환하는 동작만 수행
boolean hasPermission = checkPermission(user);
```

❌ **잘못된 예:**
```java
// getAge()가 나이를 가져오면서 동시에 alert를 띄우는 것은 잘못됨
int getAge(int userId) {
    int age = user.getAge();
    alert("나이: " + age);  // 부수 효과
    return age;
}

// createForm()이 form을 만들면서 동시에 문서에 추가하는 것은 잘못됨
Form createForm() {
    Form form = new Form();
    document.appendChild(form);  // 부수 효과
    return form;
}

// checkPermission()이 확인하면서 동시에 메시지를 띄우는 것은 잘못됨
boolean checkPermission(User user) {
    boolean hasPermission = user.hasPermission();
    showMessage("승인 여부: " + hasPermission);  // 부수 효과
    return hasPermission;
}
```

**올바른 분리:**
```java
// 각 함수가 하나의 책임만 담당
int age = getAge(userId);
showAge(age);  // 별도 함수로 분리

Form form = createForm();
addFormToDocument(form);  // 별도 함수로 분리

boolean hasPermission = checkPermission(user);
showPermissionStatus(hasPermission);  // 별도 함수로 분리
```

이러한 단일 책임 원칙을 따르면 코드의 재사용성, 테스트 용이성, 유지보수성이 향상됩니다.

### 파일 명명 규칙

- **Java 파일**: 클래스명과 동일 (PascalCase)
- **SQL 마이그레이션**: `V{번호}__{설명}.sql` (Flyway 규칙)
  - 예: `V1__Create_users_table.sql`, `V9__Rename_tables.sql`

## 데이터베이스 구조

### 테이블 명명 규칙

**중요**: 모든 테이블 이름과 컬럼 이름은 **소문자로 구성되는 Snake Case(뱀 표기법)** 형식 사용

- 단어 사이는 언더스코어(`_`)로 구분
- 모든 문자는 소문자로 작성
- ✅ 올바른 예: `users`, `books`, `user_books`, `user_devices`, `refresh_tokens`, `password_reset_tokens`
- ❌ 잘못된 예: `Users`, `Books`, `User_Books`, `userDevices`

### 테이블 목록

1. **users** - 사용자 정보
2. **books** - 도서 정보
3. **user_books** - 사용자-도서 관계 (독서 상태 관리)
4. **user_devices** - 사용자 디바이스 정보
5. **refresh_tokens** - JWT 리프레시 토큰
6. **password_reset_tokens** - 비밀번호 재설정 토큰

### 테이블 관계

```
users (1) ← (N) user_devices
users (1) ← (N) refresh_tokens
users (1) ← (N) password_reset_tokens
users (1) ← (N) user_books
books (1) ← (N) user_books

```

- `users`와 `books`는 **직접적인 관계가 없음**
- `users`와 `user_books`는 1:N 관계
- `books`와 `user_books`는 1:N 관계
- `user_books`는 각각 독립적으로 관계를 맺는 테이블이며, users와 books를 "연결"하는 중간 테이블이 아님
- 모든 외래키는 `ON DELETE CASCADE` 적용

## Memo 테이블 구조 (향후 구현)

### 결정사항: 히스토리 방식 (옵션 1) 선택

**구조**:
- 단일 `memos` 테이블에 모든 버전 저장
- `version` 컬럼으로 버전 관리
- `is_deleted` 플래그로 삭제 여부 표시
- 최신 메모: `version DESC`, `is_deleted = false` 조건으로 조회

**장점**:
- 구현 단순
- 메모 변경 이력 추적 가능
- 이전 버전 복원 가능
- 기능적 유연성

**정리 작업**: 매 시간 정각에 만료된 사용된 토큰 정리

## DTO 구조

### 3-tier Architecture 기반 DTO 분리

#### 1. ClientServerDTO (Client ↔ Server 경계)
- **용도**: 클라이언트와 서버 간 통신
- **위치**: `server.dto.clientserverDTO`
- **구조**:
  - `requestDTO`: 클라이언트 → 서버 요청 DTO
  - `responseDTO`: 서버 → 클라이언트 응답 DTO
  - `ApiResponse.java`, `ErrorResponse.java`: 공통 응답 래퍼 (dto 바로 아래)
- **웹/앱 공유 정책**:
  - 웹과 앱이 완전히 동일한 DTO 사용
  - 날짜, 시간 등 데이터 포맷팅은 각 클라이언트에서 처리
  - 서버는 표준 형식(ISO 8601 등)으로 데이터 제공
- **클라이언트 구분 불필요**:
  - Controller와 Service는 요청이 웹에서 왔는지 앱에서 왔는지 구분할 필요 없음
  - 서버는 요청에 대한 응답만 되돌려주면 되며, 네트워크 라우팅이 응답을 올바른 클라이언트에게 전달
  - 동일한 HTTP/HTTPS 프로토콜과 동일한 DTO 형식을 사용하므로 클라이언트 종류를 구분할 필요 없음

#### 2. ServerDbmsDTO (Server ↔ DBMS 경계)
- **용도**: 서버 내부 로직과 DBMS 간 통신
- **위치**: `dbms.dto.serverdbmsDTO`
- **구조**:
  - `commandDTO`: 서비스 → DBMS 명령 DTO
  - `resultDTO`: DBMS → 서비스 결과 DTO

### API 응답 구조

모든 API는 `ApiResponse<T>` 래퍼 사용:
```java
{
  "ok": true/false,
  "data": {...},
  "error": {...}
}
```

## API 구조

### 경로 규칙
- **기본 경로**: `/api/v1`
- **버전 관리**: `v1` 패키지 사용
- **RESTful 원칙 준수**

### 예시 엔드포인트
- `POST /api/v1/auth/register` - 회원가입
- `POST /api/v1/auth/login` - 로그인
- `GET /api/v1/books/search` - 도서 검색
- `POST /api/v1/user/books` - 서재에 책 추가

## 보안 구조

### JWT 인증
- **Access Token**: 1시간 만료
- **Refresh Token**: 7일 만료
- **디바이스별 토큰 관리**: `User_Devices` 테이블 사용

### 비밀번호 재설정
- **토큰 방식**: 현재 UUID 기반 (향후 이메일 인증 번호로 전환 예정)
- **토큰 관리**: `used = true`로 마킹 (삭제하지 않음)
- **정리 작업**: 매 시간 정각에 만료된 사용된 토큰 정리

### 패스워드 정책
- `PasswordValidator` 사용
- 강력한 비밀번호 요구사항 적용

## 데이터베이스 마이그레이션

### Flyway 사용
- **마이그레이션 파일 위치**: `src/main/resources/db/migration/`
- **명명 규칙**: `V{번호}__{설명}.sql`
- **순차적 실행**: 버전 번호 순서대로 실행

### 주요 마이그레이션
- `V1__Create_users_table.sql`
- `V2__Create_user_devices_table.sql`
- `V3__Create_refresh_tokens_table.sql`
- `V4__Alter_refresh_tokens_token_size.sql`
- `V5__Create_password_reset_tokens_table.sql`
- `V6__Create_books_table.sql`
- `V7__Create_user_books_table.sql`
- `V8__Alter_user_books_table.sql`
- `V9__Rename_tables.sql` - 테이블 이름 명명 규칙 변경 (참고용, 현재는 소문자 snake_case 사용)

## 중요한 설계 결정사항

### 1. 테이블 및 컬럼 명명 규칙
- **결정**: 모든 테이블 이름과 컬럼 이름을 소문자 snake_case 형식 사용
- **형식**: 소문자로 구성되며, 단어 사이는 언더스코어(`_`)로 구분
- **이유**: 데이터베이스 표준 명명 규칙 일관성, 가독성 향상
- **예시**: `users`, `user_devices`, `password_reset_tokens`, `user_id`, `created_at`

### 2. Password Reset Token 관리
- **방식**: `used = true`로 마킹, 삭제하지 않음
- **이유**: 감사 추적, 공격 패턴 분석 가능
- **정리**: 매 시간 정각에 만료된 사용된 토큰 정리

### 3. Memo 테이블 구조
- **결정**: 히스토리 방식 (옵션 1) 선택
- **구조**: 단일 테이블에 모든 버전 저장, `version` 컬럼 사용
- **이유**: 구현 단순성, 기능적 유연성

### 4. DTO 경계 분리 (3-tier Architecture)
- **ClientServerDTO** (`server.dto.clientserverDTO`): Client ↔ Server 경계용
- **ServerDbmsDTO** (`dbms.dto.serverdbmsDTO`): Server ↔ DBMS 경계용
- **이유**: 경계 간 의존성 분리, 명확한 책임 구분, 유지보수성 향상

### 5. 패키지 구조 (3-tier Architecture)
- **server 패키지**: 서버 로직 및 Client ↔ Server 경계 관리
- **dbms 패키지**: DBMS 관련 및 Server ↔ DBMS 경계 관리
- **이유**: 경계 중심 구조로 의존성 방향 명확화, 확장성 향상

### 6. DTO 공유 및 데이터 포맷팅 정책
- **결정**: 웹과 앱이 완전히 동일한 DTO를 사용
- **포맷팅**: 날짜, 시간 등 데이터 포맷팅은 각 클라이언트(웹/앱)에서 처리
- **이유**: 
  - 서버는 순수한 데이터만 제공
  - 클라이언트별 UI 요구사항에 맞는 포맷팅 가능
  - 서버 로직 단순화 및 유지보수성 향상
- **예시**: 
  - 서버: `createdAt: "2024-01-15T10:30:00"` (ISO 8601 형식)
  - 웹: "2024년 1월 15일 10시 30분"으로 포맷팅
  - 앱: "1/15 10:30" 또는 "2시간 전"으로 포맷팅
- **주의사항**: 
  - 서버는 표준 형식(ISO 8601 등)으로 데이터 제공
  - 포맷팅 로직은 서버에 포함하지 않음
  - 클라이언트가 각자의 로케일/타임존에 맞게 처리

### 7. 클라이언트 구분 불필요 정책
- **핵심 원칙**: 서버 애플리케이션(Controller, Service)은 요청이 웹에서 왔는지 앱에서 왔는지 구분할 필요가 없음
- **클라이언트-서버 아키텍처의 응답 방식**:
  - 서버는 클라이언트와 직접적인 '연결'을 유지하지 않음
  - 클라이언트가 보낸 HTTP 요청에 대해 응답을 '되돌려주는' 방식으로 통신
  - 요청 흐름:
    1. **클라이언트 요청**: 웹 브라우저 또는 안드로이드 앱이 특정 서버 주소(URL)로 HTTP 요청(GET, POST 등) 전송
       - 요청은 클라이언트의 네트워크 주소(IP 주소와 포트) 정보를 포함
    2. **서버 처리**: Spring Boot 서버가 요청을 받아 처리하고, 비즈니스 로직을 수행하며, Response DTO 준비
    3. **응답 반환**: 서버는 Response DTO를 JSON으로 변환하고, 요청이 들어온 네트워크 경로를 통해 응답을 되돌려 전송
- **서버가 클라이언트를 구분할 필요가 없는 이유**:
  1. **동일한 통신 프로토콜**: 웹이든 앱이든 모두 표준 HTTP/HTTPS 프로토콜 사용
  2. **동일한 DTO**: DTO 형식이 동일하고, 포맷팅 책임이 클라이언트에 있으므로, Controller는 하나의 ResponseDTO 객체만 생성하면 됨
  3. **라우팅은 네트워크의 책임**: 응답이 클라이언트에게 정확하게 도달하는 것은 서버 애플리케이션의 책임이 아니라, OS의 네트워킹 스택과 인터넷 라우팅의 책임
- **구현 원칙**:
  - Controller는 요청의 출처(웹/앱)를 확인하거나 구분하는 로직을 포함하지 않음
  - Service 계층도 클라이언트 종류를 신경 쓰지 않음
  - 서버는 단순히 요청에 응답하는 것만으로 웹과 앱 모두에게 데이터를 정확하게 전달
- **주의사항**:
  - 서버는 자신이 받은 요청을 누가 보냈는지(웹인지 앱인지)를 별도로 기록하거나 추적할 필요가 없음
  - 요청을 보낸 주소로 그냥 응답을 보내면 됨

## 개발 가이드라인

### 엔티티 클래스 (Server ↔ DBMS 경계)
- `@Table(name = "테이블명")` 사용 (소문자 snake_case)
- 엔티티 클래스명은 PascalCase (예: `User`, `Book`)
- 테이블명은 소문자 snake_case (예: `users`, `user_books`)
- 컬럼명도 소문자 snake_case 사용 (예: `user_id`, `created_at`)
- `@EntityListeners(AuditingEntityListener.class)` 사용 (생성/수정 시간 자동 관리)
- `@CreatedDate`, `@LastModifiedDate` 사용
- 데이터베이스 테이블을 객체로 매핑

### 컨트롤러 (Client ↔ Server 경계)

#### 기본 원칙
- 모든 컨트롤러는 `BaseV1Controller` 상속
- 클라이언트 요청을 받아 `server.service`에 위임
- `server.dto.clientserverDTO` 사용 (Request/Response)
- Swagger 문서화 (`@Tag` 사용)
- `ApiResponse<T>` 래퍼 사용

#### 컨트롤러 설계 원칙 (SRP - Single Responsibility Principle)

**핵심 원칙**: 컨트롤러는 **기능별(도메인/책임)로 분리**되어야 하며, 각 컨트롤러는 **하나의 주요 기능(도메인)에 집중**하여 **단일 책임 원칙(SRP)**을 준수해야 합니다.

**원칙 1: 기능별(도메인) 분리**
- 각 컨트롤러는 하나의 명확한 도메인 또는 기능 영역에만 집중해야 합니다
- 예시:
  - ✅ `BookSearchController`: 책 검색 및 도서 세부 정보 조회 (비인증)
  - ✅ `BookShelfController`: 사용자 서재 관리 (인증 필요)
  - ✅ `AuthController`: 인증 및 인가 관련 기능
  - ❌ `BookController`: 책 검색과 서재 관리를 모두 포함 (책임이 혼재)

**원칙 2: 단일 책임 원칙(SRP) 준수**
- 각 컨트롤러는 자신의 명확한 역할만 수행해야 합니다
- 하나의 컨트롤러가 여러 개의 서로 다른 책임을 가지면 안 됩니다
- 예시:
  - ✅ `BookSearchController`: 외부 API를 통한 책 검색만 담당
  - ✅ `BookShelfController`: 사용자의 서재 관리만 담당
  - ❌ 하나의 컨트롤러가 검색과 서재 관리를 모두 담당

**원칙 3: 최소한의 서비스 의존**
- 각 컨트롤러는 필요한 최소한의 서비스만 의존해야 합니다
- 컨트롤러가 불필요한 서비스에 의존하면 결합도가 높아지고 유지보수가 어려워집니다
- 예시:
  - ✅ `BookSearchController`: `AladinApiService`만 의존
  - ✅ `BookShelfController`: `BookService`만 의존
  - ❌ 하나의 컨트롤러가 `AladinApiService`와 `BookService`를 모두 의존

**원칙 4: 인증 요구사항별 분리 고려**
- 인증이 필요한 기능과 비인증 기능은 별도 컨트롤러로 분리하는 것을 고려할 수 있습니다
- 예시:
  - `BookSearchController`: 비인증 접근 (공개 API)
  - `BookShelfController`: 인증 필요 (사용자별 데이터)

**원칙 5: 명확한 경로 구조**
- 각 컨트롤러의 경로는 해당 도메인을 명확히 나타내야 합니다
- 예시:
  - `/api/v1/books/search` → `BookSearchController`
  - `/api/v1/user/books` → `BookShelfController`

**원칙 6: 클라이언트 구분 불필요**
- 컨트롤러는 요청이 웹에서 왔는지 앱에서 왔는지 구분할 필요가 없습니다
- 클라이언트-서버 아키텍처의 응답 방식:
  - 서버는 클라이언트와 직접적인 '연결'을 유지하지 않음
  - 클라이언트가 보낸 HTTP 요청에 대해 응답을 '되돌려주는' 방식으로 통신
  - 요청을 보낸 네트워크 경로를 통해 응답을 되돌려 전송
- 서버가 클라이언트를 구분할 필요가 없는 이유:
  1. **동일한 통신 프로토콜**: 웹과 앱 모두 표준 HTTP/HTTPS 프로토콜 사용
  2. **동일한 DTO**: DTO 형식이 동일하므로 하나의 ResponseDTO만 생성하면 됨
  3. **라우팅은 네트워크의 책임**: 응답 전달은 OS의 네트워킹 스택과 인터넷 라우팅의 책임
- 구현 원칙:
  - ❌ 컨트롤러에서 요청의 출처(웹/앱)를 확인하거나 구분하는 로직 포함 금지
  - ✅ 서버는 단순히 요청에 응답하는 것만으로 웹과 앱 모두에게 데이터를 정확하게 전달
  - ✅ Service 계층도 클라이언트 종류를 신경 쓰지 않음

**분리 예시: BookController 분리 계획**
- **현재**: `BookController`가 책 검색과 서재 관리를 모두 담당
- **분리 후**:
  - `BookSearchController`: 책 검색(`/books/search`), 도서 세부 정보 조회(`/books/{isbn}`)
    - 의존: `AladinApiService`만
    - 인증: 불필요 (비인증)
  - `BookShelfController`: 서재 관리(`/user/books/*`)
    - 의존: `BookService`만
    - 인증: 필요

### 서비스 (서버 내부)
- 비즈니스 로직 구현
- `server.dto.clientserverDTO` ↔ `dbms.dto.serverdbmsDTO` 변환 담당
- 검증 서비스(`server.service.validation` 패키지) 활용
- `server.controller`에서 호출되며, `dbms.repository`를 통해 데이터 접근

### Repository (Server ↔ DBMS 경계)
- `JpaRepository` 확장
- `server.service`에서 호출되어 DBMS와 통신
- 커스텀 쿼리는 JPQL 사용 (엔티티 클래스 참조)
- 네이티브 쿼리 사용 지양

## 주의사항

1. **테이블 및 컬럼 이름**: 소문자 snake_case 형식 사용 (예: `users`, `user_devices`, `user_id`)
2. **Git 커밋**: 중요한 변경사항은 커밋 메시지에 명시
3. **마이그레이션**: 기존 마이그레이션 파일은 수정하지 않음
4. **DTO 분리**: 경계별 DTO 혼용 금지 (ClientServerDTO와 ServerDbmsDTO 혼용 금지)
5. **패키지 명명**: 모든 패키지명은 소문자 한 단어로만 구성 (단, DTO 패키지는 가시성을 위해 'DTO' 글자만 대문자 허용, 예: `clientserverDTO`, `requestDTO`, `commandDTO`)

## 참고 자료

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [JPA 공식 문서](https://spring.io/projects/spring-data-jpa)
- [Flyway 공식 문서](https://flywaydb.org/)

---

**최종 업데이트**: 2024년
**버전**: 1.0

