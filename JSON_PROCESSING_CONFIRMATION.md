# 클라이언트 JSON 처리 확인 결과

## 질문

**클라이언트가 데이터를 보낼 때는 JSON 형식으로 보내야 하는가?**

---

## 확인 결과

### ✅ **예, 클라이언트가 데이터를 보낼 때는 JSON 형식으로 보내야 합니다.**

**핵심 요구사항**: 
- 클라이언트(웹/앱)가 서버에 데이터를 전송할 때는 **반드시 JSON 형식**으로 보내야 합니다.
- 서버는 JSON 형식의 데이터만 처리할 수 있습니다.

---

## 확인 근거

### 1. Spring Boot의 기본 JSON 처리

#### `@RestController` 어노테이션 사용
- **모든 Controller가 `@RestController` 사용**:
  - `AuthController.java`
  - `BookShelfController.java`
  - `BookSearchController.java`
  - `UserController.java`
  - `BaseV1Controller.java`

- **`@RestController`의 의미**:
  ```java
  @RestController = @Controller + @ResponseBody
  ```
  - 자동으로 반환값을 JSON으로 직렬화
  - HTTP 요청 본문을 JSON으로 역직렬화

#### `@RequestBody` 어노테이션 사용
- **POST 요청에서 `@RequestBody` 사용**:
  ```java
  // AuthController.java
  @PostMapping("/auth/signup")
  public ApiResponse<RegisterResponse> signup(
      @RequestBody RegistrationRequest request) { ... }
  
  // BookShelfController.java
  @PostMapping("/user/books")
  public ApiResponse<BookAdditionResponse> addBookToShelf(
      @Valid @RequestBody BookAdditionRequest request) { ... }
  ```

- **`@RequestBody`의 의미**:
  - HTTP 요청 본문을 자동으로 JSON으로 파싱
  - DTO 객체로 자동 변환
  - 기본 Content-Type: `application/json`

### 2. Spring Boot Starter Web 의존성

#### `pom.xml` 확인
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

- **`spring-boot-starter-web`에 포함된 것**:
  - **Jackson** (JSON 처리 라이브러리)
  - `MappingJackson2HttpMessageConverter` (자동 설정)
  - JSON 직렬화/역직렬화 자동 처리

### 3. DTO 클래스 구조

#### POJO (Plain Old Java Object) 구조
```java
// RegistrationRequest.java 예시
public class RegistrationRequest {
    private String loginId;
    private String email;
    private String name;
    private String password;
    
    // Getter/Setter 메서드
    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    // ...
}
```

- **Jackson이 자동으로 처리**:
  - Getter/Setter를 통해 JSON 필드 매핑
  - 기본 생성자로 객체 생성
  - 필드명을 JSON 키로 사용

### 4. JSON 어노테이션 사용

#### `@JsonInclude` 사용
```java
// ApiResponse.java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> { ... }

// ErrorResponse.java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse { ... }
```

- **의미**: null 값은 JSON 응답에서 제외
- **Jackson 어노테이션 사용**: JSON 처리 라이브러리로 Jackson 사용 중

### 5. 명시적 Content-Type 설정 없음

#### Spring Boot 기본 동작 사용
- **명시적 설정 없음**: `produces` 또는 `consumes` 속성 미사용
- **기본값 사용**:
  - 요청: `Content-Type: application/json` (클라이언트가 보내야 함)
  - 응답: `Content-Type: application/json` (자동 설정)

---

## 데이터 흐름

### 클라이언트 → 서버 (요청)

```
웹/앱 클라이언트
  ↓ JSON 데이터 전송
  Content-Type: application/json
  {
    "loginId": "user123",
    "email": "user@example.com",
    "name": "홍길동",
    "password": "password123"
  }
  ↓
Spring Boot (@RestController)
  ↓ @RequestBody
Jackson (자동 역직렬화)
  ↓
DTO 객체 (RegistrationRequest)
  ↓
Controller 메서드 파라미터
```

### 서버 → 클라이언트 (응답)

```
Controller 메서드 반환값
  ↓ ApiResponse<RegisterResponse>
Jackson (자동 직렬화)
  ↓
Spring Boot (@RestController)
  ↓ Content-Type: application/json
  {
    "ok": true,
    "data": {
      "message": "회원가입이 완료되었습니다.",
      "userId": 1
    }
  }
  ↓
웹/앱 클라이언트
```

---

## 확인된 Controller 예시

### 1. AuthController
```java
@RestController
@RequestMapping("/api/v1")
public class AuthController extends BaseV1Controller {
    
    @PostMapping("/auth/signup")
    public ApiResponse<RegisterResponse> signup(
        @RequestBody RegistrationRequest request) {
        // JSON → RegistrationRequest 자동 변환
        // ...
    }
}
```

### 2. BookShelfController
```java
@RestController
@RequestMapping("/api/v1")
public class BookShelfController extends BaseV1Controller {
    
    @PostMapping("/user/books")
    public ApiResponse<BookAdditionResponse> addBookToShelf(
        @Valid @RequestBody BookAdditionRequest request) {
        // JSON → BookAdditionRequest 자동 변환
        // ...
    }
}
```

### 3. BookSearchController
```java
@RestController
@RequestMapping("/api/v1")
public class BookSearchController extends BaseV1Controller {
    
    @GetMapping("/books/search")
    public ApiResponse<BookSearchResponse> searchBooks(...) {
        // 반환값 → JSON 자동 변환
        return ApiResponse.success(response);
    }
}
```

---

## 결론

### ✅ 클라이언트가 데이터를 보낼 때는 JSON 형식으로 보내야 합니다.

**핵심 규칙**:
- **클라이언트 → 서버**: 반드시 JSON 형식으로 전송해야 함
- **서버 → 클라이언트**: JSON 형식으로 응답함

**이유**:
1. **`@RestController` 사용**: 자동 JSON 직렬화/역직렬화
2. **`@RequestBody` 사용**: HTTP 요청 본문을 JSON으로 파싱
3. **Jackson 포함**: `spring-boot-starter-web`에 기본 포함
4. **POJO 구조**: DTO 클래스가 Jackson과 호환되는 구조
5. **기본 Content-Type**: `application/json` 사용

### 클라이언트 요구사항 (필수 준수사항)

**웹/앱 클라이언트는 반드시 다음을 준수해야 합니다:**

#### 1. 요청 헤더 (필수)
```
Content-Type: application/json
```

#### 2. 요청 본문 (필수)
- **반드시 JSON 형식**으로 전송해야 함
- 다른 형식(xml, form-data 등)은 지원하지 않음

**예시**:
```json
{
  "loginId": "user123",
  "email": "user@example.com",
  "name": "홍길동",
  "password": "password123"
}
```

#### 3. 응답 처리
- 서버 응답도 **JSON 형식**으로 받음

**예시**:
```json
{
  "ok": true,
  "data": {
    "message": "회원가입이 완료되었습니다.",
    "userId": 1
  }
}
```

### 중요 사항

⚠️ **클라이언트가 JSON 형식이 아닌 데이터를 보내면 서버에서 처리할 수 없습니다.**
- 서버는 `@RequestBody`를 통해 JSON만 파싱하도록 설정되어 있음
- 다른 형식(xml, form-data, plain text 등)은 지원하지 않음

---

**작성일**: 2024년
**버전**: 1.0

