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
- **3-tier Architecture**: 계층이 아닌 경계 중심으로 패키지 분리

### 3-tier Architecture 구조

#### Client ↔ Server 경계
- `server.controller` - REST API 컨트롤러 (클라이언트와의 통신 담당)
- `server.dto.ClientServerDTO` - 클라이언트-서버 간 데이터 전송 객체
  - `requestdto` - 클라이언트 → 서버 요청 DTO
  - `responsedto` - 서버 → 클라이언트 응답 DTO
  - `ApiResponse.java`, `ErrorResponse.java` - 공통 응답 래퍼 (dto 바로 아래)

#### Server ↔ DBMS 경계
- `dbms.repository` - 데이터 접근 계층 (DBMS와의 통신 담당)
- `dbms.dto.ServerDbmsDTO` - 서버-DBMS 간 데이터 전송 객체
  - `commanddto` - 서비스 → DBMS 명령 DTO
  - `resultdto` - DBMS → 서비스 결과 DTO
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
│       ├── ClientServerDTO           # 클라이언트-서버 DTO
│       │   ├── requestdto           # 클라이언트 → 서버 요청
│       │   │   ├── LoginRequest.java
│       │   │   └── ...
│       │   └── responsedto          # 서버 → 클라이언트 응답
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
        └── ServerDbmsDTO            # 서버-DBMS DTO
            ├── commanddto            # 서비스 → DBMS 명령
            │   └── ...
            └── resultdto            # DBMS → 서비스 결과
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
- **`get...`**: 값을 반환하는 함수
  - 예: `getAge()`, `getUserById()`, `getTotalPrice()`
- **`calc...`**: 값을 계산하는 함수
  - 예: `calcSum()`, `calcTotalPrice()`, `calcAverage()`
- **`create...`**: 무언가를 생성하는 함수
  - 예: `createForm()`, `createUser()`, `createToken()`
- **`update...`**: 무언가를 업데이트하는 함수
  - 예: `updateUser()`, `updateStatus()`, `updatePrice()`
- **`delete...`**: 무언가를 삭제하는 함수
  - 예: `deleteUser()`, `deleteItem()`, `deleteToken()`
- **`validate...`**: 무언가를 검증하는 함수
  - 예: `validateEmail()`, `validatePassword()`, `validateInput()`
- **`check...`**: 무언가를 확인하는 함수
  - 예: `checkPermission()`, `checkExists()`, `checkStatus()`
- **`find...`**: 무언가를 찾는 함수
  - 예: `findUser()`, `findById()`, `findAll()`
- **`save...`**: 무언가를 저장하는 함수
  - 예: `saveUser()`, `saveData()`, `saveChanges()`

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
- **위치**: `server.dto.ClientServerDTO`
- **구조**:
  - `requestdto`: 클라이언트 → 서버 요청 DTO
  - `responsedto`: 서버 → 클라이언트 응답 DTO
  - `ApiResponse.java`, `ErrorResponse.java`: 공통 응답 래퍼 (dto 바로 아래)

#### 2. ServerDbmsDTO (Server ↔ DBMS 경계)
- **용도**: 서버 내부 로직과 DBMS 간 통신
- **위치**: `dbms.dto.ServerDbmsDTO`
- **구조**:
  - `commanddto`: 서비스 → DBMS 명령 DTO
  - `resultdto`: DBMS → 서비스 결과 DTO

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
- **ClientServerDTO** (`server.dto.ClientServerDTO`): Client ↔ Server 경계용
- **ServerDbmsDTO** (`dbms.dto.ServerDbmsDTO`): Server ↔ DBMS 경계용
- **이유**: 경계 간 의존성 분리, 명확한 책임 구분, 유지보수성 향상

### 5. 패키지 구조 (3-tier Architecture)
- **server 패키지**: 서버 로직 및 Client ↔ Server 경계 관리
- **dbms 패키지**: DBMS 관련 및 Server ↔ DBMS 경계 관리
- **이유**: 경계 중심 구조로 의존성 방향 명확화, 확장성 향상

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
- 모든 컨트롤러는 `BaseV1Controller` 상속
- 클라이언트 요청을 받아 `server.service`에 위임
- `server.dto.ClientServerDTO` 사용 (Request/Response)
- Swagger 문서화 (`@Tag` 사용)
- `ApiResponse<T>` 래퍼 사용

### 서비스 (서버 내부)
- 비즈니스 로직 구현
- ClientServerDTO ↔ ServerDbmsDTO 변환 담당
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
5. **패키지 명명**: 모든 패키지명은 소문자 한 단어로만 구성

## 참고 자료

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [JPA 공식 문서](https://spring.io/projects/spring-data-jpa)
- [Flyway 공식 문서](https://flywaydb.org/)

---

**최종 업데이트**: 2024년
**버전**: 1.0

