# Reading Tracker

> 독서 기록 및 관리 플랫폼 백엔드 API 서버

Reading Tracker는 사용자가 읽은 책을 기록하고 관리할 수 있는 독서 관리 플랫폼의 백엔드 서버입니다. 도서 검색, 서재 관리, 독서 메모 작성 등 다양한 기능을 제공하는 RESTful API를 제공합니다.

## 📋 목차

- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [시작하기](#시작하기)
- [API 문서](#api-문서)
- [프로젝트 구조](#프로젝트-구조)
- [문서](#문서)

---

## 주요 기능

### 🔐 인증 및 사용자 관리
- **회원가입 및 로그인**: JWT 기반 인증 시스템
- **토큰 관리**: Access Token (1시간) + Refresh Token (7일) 방식
- **아이디 찾기**: 이메일과 이름을 통한 아이디 조회
- **비밀번호 재설정**: 2단계 인증을 통한 안전한 비밀번호 변경
- **중복 확인**: 로그인 ID 및 이메일 중복 검사
- **다중 디바이스 지원**: 디바이스별 토큰 관리 및 동기화
- **사용자 프로필**: 내 프로필 정보 조회

### 📚 도서 검색 및 관리
- **도서 검색**: 알라딘 Open API를 활용한 실시간 도서 검색
  - 도서명, 저자명, 출판사명으로 검색 가능
  - 페이지네이션 지원
- **도서 상세 정보**: ISBN을 통한 도서 상세 정보 조회
- **서재 관리**: 
  - 내 서재에 책 추가/제거
  - 읽기 상태 관리 (읽을 책, 읽는 중, 거의 다 읽음, 읽은 책)
  - 독서 시작일 및 진행률 기록
  - 완독 처리 및 평점/후기 작성
  - 서재 목록 조회 및 정렬 (도서명, 저자명, 출판사명, 장르별)
  - 책 상세 정보 수정 (기대감, 진행률, 평점 등)

### 📝 독서 메모
- **메모 작성**: 독서 중 실시간 메모 작성
  - 페이지별 메모 기록 (페이지당 메모 개수 제한 없음)
  - 태그를 통한 메모 분류 (유형, 주제 등)
  - 메모 작성 시간 자동 기록
- **메모 관리**: 
  - 메모 내용 및 태그 수정
  - 메모 삭제
  - 특정 책의 메모 목록 조회
- **태그 시스템**: 
  - 자동 태그 생성 및 연결
  - 태그 대분류 지원 (TYPE, TOPIC 등)
  - 태그별 메모 그룹화

### 📅 오늘의 흐름
- **날짜별 메모 조회**: 특정 날짜의 독서 메모를 다양한 방식으로 조회
- **다양한 그룹화 방식**:
  - **세션 모드**: 시간축 기준 세션 단위 그룹화 (프론트엔드에서 시간축 재배치)
  - **책 모드**: 책별로 그룹화하여 조회
  - **태그 모드**: 태그별로 그룹화 (태그 내부에서 책별 재그룹화)
- **독서 활동 관리**:
  - 책 덮기 기능 (독서 활동 종료 및 마지막 페이지 기록)
  - 최근 메모 작성 책 목록 조회
  - 독서 캘린더 연동

---

## 기술 스택

### Backend Framework
- **Spring Boot** 3.2.0
- **Java** 17

### Database
- **MySQL** 8.0
- **Spring Data JPA** (ORM)
- **Flyway** (데이터베이스 마이그레이션)

### Security
- **Spring Security**
- **JWT** (JSON Web Token)
  - Access Token: 1시간 만료
  - Refresh Token: 7일 만료
  - Token Rotation 방식 지원
  - 디바이스별 토큰 관리

### API Documentation
- **SpringDoc OpenAPI** 2.2.0
- **Swagger UI**

### Build Tool
- **Maven** 3.6+

### 기타
- **Lombok** (보일러플레이트 코드 제거)
- **Validation** (입력값 검증)
- **MapStruct** (DTO 변환 매퍼)

---

## 시작하기

### 요구사항

- **Java** 17 이상
- **Maven** 3.6 이상
- **MySQL** 8.0 이상

### 데이터베이스 설정

1. MySQL 데이터베이스 생성:
```sql
CREATE DATABASE reading_tracker CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. `src/main/resources/application.yml` 파일에서 데이터베이스 연결 정보 수정:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/reading_tracker?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: {your_username}
    password: {your_password}
```

3. 알라딘 API 키 설정 (선택사항):
```yaml
aladin:
  api:
    key: {your_aladin_api_key}
    base-url: http://www.aladin.co.kr/ttb/api
```

### 실행 방법

1. 프로젝트 클론:
```bash
git clone https://github.com/Park-Yena00/Reading_tracker.git
cd Reading_tracker
```

2. 애플리케이션 실행:
```bash
mvn spring-boot:run
```

3. 애플리케이션 접속:
   - 서버 주소: `http://localhost:8080`
   - Swagger UI: `http://localhost:8080/swagger-ui/index.html`

### 데이터베이스 password
```
Yenapark1000
```

### 데이터베이스 마이그레이션

Flyway를 통해 데이터베이스 스키마가 자동으로 생성됩니다. 애플리케이션 실행 시 `src/main/resources/db/migration/` 디렉토리의 마이그레이션 파일들이 순차적으로 실행됩니다.

---

## API 문서

### Swagger UI

애플리케이션 실행 후 다음 주소에서 인터랙티브 API 문서를 확인할 수 있습니다:

**🔗 [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**

Swagger UI에서는 다음을 수행할 수 있습니다:
- 모든 API 엔드포인트 확인
- 요청/응답 스키마 확인
- API 직접 테스트
- 인증 토큰 설정 및 테스트

### API 기본 정보

- **기본 경로**: `/api/v1`
- **인증 방식**: JWT (Bearer Token)
- **응답 형식**: `ApiResponse<T>` 래퍼 사용
- **Content-Type**: `application/json`

### 주요 엔드포인트

#### 인증 (`/auth`)
- `GET /api/v1/users/duplicate/loginId` - 로그인 ID 중복 확인
- `GET /api/v1/users/duplicate/email` - 이메일 중복 확인
- `POST /api/v1/auth/signup` - 회원가입
- `POST /api/v1/auth/login` - 로그인
- `POST /api/v1/auth/refresh` - 토큰 갱신
- `POST /api/v1/auth/find-login-id` - 아이디 찾기
- `POST /api/v1/auth/verify-account` - 계정 확인 (비밀번호 재설정 Step 1)
- `POST /api/v1/auth/reset-password` - 비밀번호 재설정 (Step 2)

#### 사용자 (`/users`)
- `GET /api/v1/users/me` - 내 프로필 조회

#### 도서 검색 (`/books`)
- `GET /api/v1/books/search` - 도서 검색
- `GET /api/v1/books/{isbn}` - 도서 상세 정보 조회

#### 서재 관리 (`/user/books`)
- `GET /api/v1/user/books` - 내 서재 조회
- `POST /api/v1/user/books` - 서재에 책 추가
- `DELETE /api/v1/user/books/{userBookId}` - 서재에서 책 제거
- `PUT /api/v1/user/books/{userBookId}/category` - 읽기 상태 변경
- `POST /api/v1/user/books/{userBookId}/start-reading` - 책 읽기 시작
- `POST /api/v1/user/books/{userBookId}/finish-reading` - 책 완독
- `PUT /api/v1/user/books/{userBookId}` - 책 상세 정보 변경

#### 메모 관리 (`/memos`, `/today-flow`)
- `POST /api/v1/memos` - 메모 작성
- `PUT /api/v1/memos/{memoId}` - 메모 수정
- `DELETE /api/v1/memos/{memoId}` - 메모 삭제
- `GET /api/v1/today-flow` - 오늘의 흐름 조회 (세션/책/태그 모드)
- `GET /api/v1/memos/books/{userBookId}` - 특정 책의 메모 조회
- `GET /api/v1/memos/books/recent` - 최근 메모 작성 책 목록
- `POST /api/v1/memos/books/{userBookId}/close` - 책 덮기 (독서 활동 종료)

자세한 API 명세는 [docs/api/API_REFERENCE.md](docs/api/API_REFERENCE.md)를 참고하세요.

---

## 프로젝트 구조

### 아키텍처

본 프로젝트는 **3-tier Architecture**를 기반으로 설계되었습니다:

```
Client ↔ Server ↔ DBMS
```

- **Client ↔ Server 경계**: `server.controller`, `server.dto.ClientServerDTO`
- **Server 내부**: `server.service` (비즈니스 로직)
- **Server ↔ DBMS 경계**: `dbms.repository`, `dbms.entity`, `dbms.dto.ServerDbmsDTO`

### 컨트롤러 구조

프로젝트는 다음과 같은 컨트롤러로 구성되어 있습니다:

- **AuthController**: 인증 및 사용자 관리 (회원가입, 로그인, 토큰 갱신 등)
- **UserController**: 사용자 프로필 관리
- **BookSearchController**: 도서 검색 및 상세 정보 조회
- **BookShelfController**: 사용자 서재 관리 (책 추가/제거, 읽기 상태 관리 등)
- **MemoController**: 독서 메모 작성 및 관리, 오늘의 흐름 조회

### 패키지 구조

```
com.readingtracker
├── ReadingTrackerApplication.java    # 애플리케이션 진입점
│
├── server                            # 서버 로직
│   ├── controller                    # REST API 컨트롤러
│   │   └── v1                        # API 버전 관리
│   │       ├── AuthController.java
│   │       ├── UserController.java
│   │       ├── BookSearchController.java
│   │       ├── BookShelfController.java
│   │       └── MemoController.java
│   ├── service                       # 비즈니스 로직
│   │   ├── validation                # 검증 로직
│   │   ├── AuthService.java
│   │   ├── UserService.java
│   │   ├── BookService.java
│   │   ├── BookSearchService.java
│   │   ├── MemoService.java
│   │   ├── AladinApiService.java
│   │   └── JwtService.java
│   ├── dto                           # 클라이언트-서버 DTO
│   │   ├── ClientServerDTO
│   │   │   ├── requestdto
│   │   │   └── responsedto
│   │   ├── ApiResponse.java
│   │   └── ErrorResponse.java
│   ├── mapper                        # MapStruct 매퍼
│   ├── config                        # 설정 클래스
│   ├── security                      # 보안 컴포넌트
│   └── common                        # 공통 요소
│       ├── constant                  # 상수/Enum
│       ├── exception                 # 예외 처리
│       └── util                      # 유틸리티
│
└── dbms                              # DBMS 관련
    ├── repository                    # 데이터 접근 계층
    ├── entity                        # JPA 엔티티
    └── dto                           # 서버-DBMS DTO
        └── ServerDbmsDTO
            ├── commanddto
            └── resultdto
```

자세한 아키텍처 설계는 [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md)를 참고하세요.

---

## 문서

프로젝트 관련 상세 문서는 `docs/` 디렉토리에서 확인할 수 있습니다.

### 📁 docs/api/

API 관련 문서를 포함합니다:

- **[API_REFERENCE.md](docs/api/API_REFERENCE.md)**: 프론트엔드/앱 개발자용 완전한 API 명세서
  - 모든 엔드포인트의 상세 설명
  - 요청/응답 예시
  - 에러 코드 및 처리 방법
  - 인증 방법 및 토큰 관리
  
- **[REST_API_DESIGN.md](docs/api/REST_API_DESIGN.md)**: 백엔드 개발자용 REST API 설계 가이드
  - RESTful 원칙 및 엔드포인트 설계 기준
  - 명명 규칙 및 구조화 원칙
  - HTTP 메서드 사용 가이드

### 📁 docs/architecture/

아키텍처 설계 문서를 포함합니다:

- **[ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md)**: 프로젝트 구조 및 아키텍처 설계 원칙
  - 3-tier Architecture 구조
  - 패키지 구조 및 명명 규칙
  - 데이터베이스 구조 및 테이블 관계
  - DTO 구조 및 설계 원칙
  - MapStruct 기반 매퍼 아키텍처
  
- **[DEPLOYMENT_OPTIONS.md](docs/architecture/DEPLOYMENT_OPTIONS.md)**: 배포 옵션 가이드
  - 다양한 클라우드 플랫폼 배포 옵션 비교
  - 비용 및 추천 전략
  - 배포 구성 요소 설명

---

## 주요 특징

### 🏗️ 아키텍처 설계
- **3-tier Architecture**: Client-Server-DBMS 경계를 명확히 분리
- **DTO 분리**: 경계별 DTO를 분리하여 의존성 관리
- **MapStruct 활용**: 타입 안전한 DTO 변환

### 🔒 보안
- **JWT 기반 인증**: 안전한 토큰 기반 인증 시스템
- **Token Rotation**: 보안 강화를 위한 토큰 갱신 방식
- **다중 디바이스 지원**: 디바이스별 토큰 관리

### 📊 데이터 관리
- **Flyway 마이그레이션**: 데이터베이스 스키마 버전 관리
- **JPA Auditing**: 생성/수정 시간 자동 관리
- **트랜잭션 관리**: `@Transactional`을 통한 데이터 일관성 보장

### 🎯 사용자 경험
- **유연한 메모 시스템**: 페이지당 메모 개수 제한 없음
- **다양한 조회 방식**: 세션/책/태그 모드로 메모 조회
- **태그 시스템**: 자동 태그 생성 및 분류

---

## 라이선스

이 프로젝트는 개인 프로젝트입니다.

---

## 기여

이슈 및 개선 사항은 GitHub Issues를 통해 제안해 주세요.

---

**최종 업데이트**: 2024년 12월  
**버전**: 1.2
