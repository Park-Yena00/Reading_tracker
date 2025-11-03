# Reading Tracker 프로젝트 아키텍처 문서

## 프로젝트 개요

- **프로젝트명**: Reading Tracker (독서 기록 사이트)
- **기술 스택**: Spring Boot 3.2.0, Java 17, MySQL, JPA, Flyway
- **데이터베이스**: MySQL 8.0

## 패키지 구조 및 명명 규칙

### 패키지 명명 규칙
- **기본 패키지**: `com.readingtracker`
- **소문자 사용**: 모든 패키지명은 소문자
- **계층 구조**: 계층별로 패키지 분리

```
com.readingtracker
├── common          # 공통 유틸리티, 상수
├── config          # 설정 클래스
├── controller      # REST API 컨트롤러
│   └── v1         # API 버전 관리
├── dto             # 데이터 전송 객체
│   ├── ClientServerDTO    # 클라이언트-서버 DTO
│   │   ├── RequestDTO
│   │   └── ResponseDTO
│   └── ServerDbmsDTO      # 서버-DBMS DTO
│       ├── CommandDto
│       └── ResultDTO
├── entity          # JPA 엔티티
├── repository      # 데이터 접근 계층
├── security        # 보안 관련
├── service         # 비즈니스 로직
│   └── validation # 검증 로직
└── util            # 유틸리티 클래스
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

### 파일 명명 규칙

- **Java 파일**: 클래스명과 동일 (PascalCase)
- **SQL 마이그레이션**: `V{번호}__{설명}.sql` (Flyway 규칙)
  - 예: `V1__Create_users_table.sql`, `V9__Rename_tables.sql`

## 데이터베이스 구조

### 테이블 명명 규칙

**중요**: 모든 테이블 이름은 **대문자로 시작**하는 PascalCase 형식 사용

- ✅ 올바른 예: `Users`, `Books`, `User_Books`, `User_Devices`, `RefreshTokens`, `Password_ResetTokens`
- ❌ 잘못된 예: `users`, `books`, `user_books`

### 테이블 목록

1. **Users** - 사용자 정보
2. **Books** - 도서 정보
3. **User_Books** - 사용자-도서 관계 (독서 상태 관리)
4. **User_Devices** - 사용자 디바이스 정보
5. **RefreshTokens** - JWT 리프레시 토큰
6. **Password_ResetTokens** - 비밀번호 재설정 토큰

### 테이블 관계

```
Users (1) ← (N) User_Devices
Users (1) ← (N) RefreshTokens
Users (1) ← (N) Password_ResetTokens
Users (1) ← (N) User_Books
Books (1) ← (N) User_Books

Users (N) ↔ (N) Books (중간 테이블: User_Books)
```

- `Users`와 `Books`는 **직접 관계 없음**
- `User_Books`를 통해서만 연결됨
- 모든 외래키는 `ON DELETE CASCADE` 적용

## Memo 테이블 구조 (향후 구현)

### 결정사항: 히스토리 방식 (옵션 1) 선택

**구조**:
- 단일 `Memos` 테이블에 모든 버전 저장
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

### 계층별 DTO 분리

#### 1. ClientServerDTO
- **용도**: 클라이언트 ↔ 서버 통신
- **위치**: `dto.ClientServerDTO`
- **구조**:
  - `RequestDTO`: 클라이언트 → 서버 요청
  - `ResponseDTO`: 서버 → 클라이언트 응답

#### 2. ServerDbmsDTO
- **용도**: 서버 내부 로직 ↔ DBMS
- **위치**: `dto.ServerDbmsDTO`
- **구조**:
  - `CommandDto`: 서비스 → DBMS 명령
  - `ResultDTO`: DBMS → 서비스 결과

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
- `V9__Rename_tables.sql` - 테이블 이름 대문자로 변경

## 중요한 설계 결정사항

### 1. 테이블 이름 대소문자
- **결정**: 모든 테이블 이름을 대문자로 시작
- **이유**: 데이터베이스 명명 규칙 일관성
- **적용**: V9 마이그레이션에서 완료

### 2. Password Reset Token 관리
- **방식**: `used = true`로 마킹, 삭제하지 않음
- **이유**: 감사 추적, 공격 패턴 분석 가능
- **정리**: 매 시간 정각에 만료된 사용된 토큰 정리

### 3. Memo 테이블 구조
- **결정**: 히스토리 방식 (옵션 1) 선택
- **구조**: 단일 테이블에 모든 버전 저장, `version` 컬럼 사용
- **이유**: 구현 단순성, 기능적 유연성

### 4. DTO 계층 분리
- **ClientServerDTO**: 외부 API용
- **ServerDbmsDTO**: 내부 로직용
- **이유**: 계층 간 의존성 분리, 유지보수성 향상

## 개발 가이드라인

### 엔티티 클래스
- `@Table(name = "테이블명")` 사용 (대문자 시작)
- `@EntityListeners(AuditingEntityListener.class)` 사용 (생성/수정 시간 자동 관리)
- `@CreatedDate`, `@LastModifiedDate` 사용

### 컨트롤러
- 모든 컨트롤러는 `BaseV1Controller` 상속
- Swagger 문서화 (`@Tag` 사용)
- `ApiResponse<T>` 래퍼 사용

### 서비스
- 비즈니스 로직 구현
- DTO 변환 담당
- 검증 서비스(`validation` 패키지) 활용

### Repository
- `JpaRepository` 확장
- 커스텀 쿼리는 JPQL 사용 (엔티티 클래스 참조)
- 네이티브 쿼리 사용 지양

## 주의사항

1. **테이블 이름**: 항상 대문자로 시작하는 이름 사용
2. **Git 커밋**: 중요한 변경사항은 커밋 메시지에 명시
3. **마이그레이션**: 기존 마이그레이션 파일은 수정하지 않음
4. **DTO 분리**: 계층별 DTO 혼용 금지

## 참고 자료

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [JPA 공식 문서](https://spring.io/projects/spring-data-jpa)
- [Flyway 공식 문서](https://flywaydb.org/)

---

**최종 업데이트**: 2024년
**버전**: 1.0

