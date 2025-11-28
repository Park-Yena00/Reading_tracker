# REST API 엔드포인트 설계 가이드

> **대상**: 백엔드 개발자 (내부 문서)  
> **목적**: 본 프로젝트에서 RESTful API 엔드포인트를 설계할 때 따를 원칙과 기준을 정의

## 개요

이 문서는 Reading Tracker 프로젝트에서 RESTful API 엔드포인트를 설계할 때 따를 원칙과 기준을 설명합니다. 백엔드 개발자가 새로운 API를 추가하거나 기존 API를 수정할 때 이 가이드를 참고하여 일관성 있는 API를 설계할 수 있습니다.

---

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

---

### 2. HTTP 메서드를 통한 행위(Action) 정의

URI가 자원을 나타내면, 해당 자원에 대해 클라이언트가 수행하려는 행위는 URI 자체가 아닌 HTTP 메서드를 사용하여 정의해야 합니다.

| HTTP 메서드 | 자원에 대한 행위 | 독서 기록 예시 |
|------------|----------------|--------------|
| **GET** | 자원 조회 및 검색 (Read) | `GET /api/v1/books` (책 목록 조회)<br>`GET /api/v1/books/{isbn}` (특정 책 상세 조회) |
| **POST** | 새 자원 생성 (Create) | `POST /api/v1/user/books` (서재에 책 추가)<br>`POST /api/v1/user/books/{id}/start-reading` (읽기 시작) |
| **PUT** | 자원 전체 업데이트 (Update/Replace) | `PUT /api/v1/user/books/{id}/category` (읽기 상태 변경) |
| **PATCH** | 자원 부분 업데이트 (Partial Update) | `PUT /api/v1/user/books/{id}` (책 상세 정보 부분 변경) |
| **DELETE** | 자원 삭제 (Delete) | `DELETE /api/v1/user/books/{id}` (서재에서 책 삭제) |

---

### 3. 계층 구조 및 관계 표현

자원 간의 관계는 URI의 계층 구조를 통해 명확하게 표현해야 합니다.

**예시** (사용자 서재에 종속된 책):
- 특정 사용자의 서재 조회: `GET /api/v1/user/books`
- 특정 사용자의 서재에 책 추가: `POST /api/v1/user/books`
- 특정 사용자의 서재에서 책 제거: `DELETE /api/v1/user/books/{userBookId}`

**예시** (책에 종속된 메모 - 구현 완료):
- 특정 책의 메모 목록 조회: `GET /api/v1/memos/books/{userBookId}`
- 메모 생성: `POST /api/v1/memos`

---

### 4. 엔드포인트와 Entity (자원) 매핑

꼭 모든 Entity마다 엔드포인트를 설정해야 하는 것은 아닙니다. 엔드포인트는 데이터베이스의 Entity 구조보다는 **클라이언트가 접근하거나 조작해야 하는 '자원(Resource)'**을 중심으로 설계해야 합니다.

#### A. 1:1 매핑 (가장 흔함)

대부분의 주요 Entity는 그 자체로 하나의 독립적인 자원이 되어 엔드포인트를 가집니다.

**예시**:
- `Book` Entity → `/api/v1/books`
- `Memo` Entity → `/api/v1/memos` (구현 완료)
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

**예시**: 오늘의 흐름 (구현 완료)
- 여러 Entity 정보 조합: `User`, `UserShelfBook`, `Book`, `Memo` 등
- 복합 자원 엔드포인트: `/api/v1/today-flow`
  - 특정 날짜의 메모를 다양한 방식으로 그룹화하여 반환 (SESSION/BOOK/TAG 모드)

**현재 프로젝트 예시**: 내 서재 조회 (`GET /api/v1/user/books`)
- `UserShelfBook` Entity와 `Book` Entity의 정보를 조합하여 사용자에게 의미 있는 "내 서재" 자원으로 제공

#### 설계 원칙

이 프로젝트에서는 **A, B, C 세 가지 방법(1:1 매핑, 계층적 자원, 복합 자원)을 모두 복합적으로 사용**합니다.

- **1:1 매핑**: 주요 Entity (`Book`, `User`)는 독립적인 엔드포인트 제공
- **계층적 자원**: 종속 관계가 명확한 경우 (`/api/v1/user/books`, `/api/v1/books/{bookId}/memos`)
- **복합 자원**: 사용자 경험을 위해 여러 Entity를 조합한 자원 제공

**핵심 원칙**: 엔드포인트는 **데이터베이스 구조가 아닌 클라이언트의 관점**에서 설계되어야 합니다.

---

### 5. 버전 관리 (Versioning)

API의 구조가 나중에 변경될 가능성에 대비하여 버전을 명시합니다.

- 현재 버전: `/api/v1/books`
- 이전 버전과 호환되지 않는 큰 변경이 있을 경우: `/api/v2/books`

---

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

---

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

---

## 참고 문서

- **실제 API 명세서**: `API_REFERENCE.md` - 프론트엔드/앱 개발자용 API 명세서
- **프로젝트 아키텍처**: `ARCHITECTURE.md` - 전체 프로젝트 구조 및 아키텍처 설계 원칙

---

**최종 업데이트**: 2024년 12월  
**버전**: 1.0

