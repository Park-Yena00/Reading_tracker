# 컨트롤러 개선 계획서

## 개요

각 컨트롤러의 역할 검증 결과, 다음과 같은 개선 사항이 발견되었습니다. 이 문서는 각 컨트롤러의 수정 사항을 단계별로 정리한 것입니다.

---

## 1. BookSearchController 개선

### 현재 문제점

**문제**: `searchBooks()` 메서드에서 `BookSearchRequest` DTO를 컨트롤러 내부에서 직접 생성하고 있습니다.

**현재 코드 구조**:
```java
public ApiResponse<BookSearchResponse> searchBooks(
    @RequestParam String query,
    @RequestParam(defaultValue = "TITLE") BookSearchFilter queryType,
    @RequestParam(defaultValue = "1") Integer start,
    @RequestParam(defaultValue = "10") Integer maxResults) {
    
    // 컨트롤러에서 DTO 직접 생성
    BookSearchRequest request = new BookSearchRequest();
    request.setQuery(query);
    request.setQueryType(queryType);
    request.setStart(start);
    request.setMaxResults(maxResults);
    
    BookSearchResponse response = aladinApiService.searchBooks(request);
    return ApiResponse.success(response);
}
```

**문제점**:
1. 컨트롤러가 DTO 생성 로직을 포함하여 책임이 과도함
2. 요청 파라미터 검증(`@Valid`)을 적용하기 어려움
3. RESTful API 설계 원칙에 맞지 않음 (Request Body를 사용하지 않음)

### 개선 방안

**옵션 1: Request Body 방식으로 변경 (권장)**
- `BookSearchRequest`를 `@RequestBody`로 받아 검증 가능
- 복잡한 검색 조건 확장에 유리
- POST 메서드 사용 필요

**옵션 2: 현재 방식 유지 + 검증 강화**
- `@RequestParam` 방식 유지
- `BookSearchRequest`에 검증 어노테이션 추가
- 컨트롤러에서 DTO 생성 후 수동 검증

### 단계별 수정 계획

#### 단계 1: BookSearchRequest DTO 개선
**파일**: `src/main/java/com/readingtracker/server/dto/clientserverDTO/requestDTO/BookSearchRequest.java`

**작업 내용**:
1. 검증 어노테이션 추가
   - `query`: `@NotBlank`, `@Size` 등
   - `start`: `@Min(1)` 등
   - `maxResults`: `@Min(1)`, `@Max(50)` 등
2. 기본값 설정 확인 (현재 기본값이 적절한지 검토)

**예상 코드**:
```java
public class BookSearchRequest {
    @NotBlank(message = "검색어는 필수입니다.")
    @Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
    private String query;
    
    private BookSearchFilter queryType = BookSearchFilter.TITLE;
    
    private String searchTarget = "Book";
    
    @Min(value = 1, message = "시작 페이지는 1 이상이어야 합니다.")
    private Integer start = 1;
    
    @Min(value = 1, message = "페이지당 결과 수는 1 이상이어야 합니다.")
    @Max(value = 50, message = "페이지당 결과 수는 50 이하여야 합니다.")
    private Integer maxResults = 10;
    
    // ... getters, setters
}
```

#### 단계 2: BookSearchController 메서드 개선
**파일**: `src/main/java/com/readingtracker/server/controller/v1/BookSearchController.java`

**작업 내용**:
1. `searchBooks()` 메서드 시그니처 변경
   - `@RequestParam`들을 `BookSearchRequest`로 통합
   - `@Valid` 어노테이션 추가하여 검증 활성화

**예상 코드 (옵션 1 - Request Body 방식)**:
```java
@PostMapping("/books/search")  // GET → POST 변경 필요
@Operation(
    summary = "책 검색", 
    description = "알라딘 Open API를 통해 책을 검색합니다 (비인증 접근 가능)"
)
public ApiResponse<BookSearchResponse> searchBooks(
        @Parameter(description = "검색 요청 정보", required = true)
        @Valid @RequestBody BookSearchRequest request) {
    
    BookSearchResponse response = aladinApiService.searchBooks(request);
    return ApiResponse.success(response);
}
```

**예상 코드 (옵션 2 - 현재 방식 유지)**:
```java
@GetMapping("/books/search")
@Operation(
    summary = "책 검색", 
    description = "알라딘 Open API를 통해 책을 검색합니다 (비인증 접근 가능)"
)
public ApiResponse<BookSearchResponse> searchBooks(
        @Parameter(description = "검색어", required = true)
        @RequestParam @NotBlank String query,
        @Parameter(description = "검색 필터")
        @RequestParam(defaultValue = "TITLE") BookSearchFilter queryType,
        @Parameter(description = "시작 페이지")
        @RequestParam(defaultValue = "1") @Min(1) Integer start,
        @Parameter(description = "페이지당 결과 수")
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer maxResults) {
    
    // DTO 생성 및 검증
    BookSearchRequest request = new BookSearchRequest();
    request.setQuery(query);
    request.setQueryType(queryType);
    request.setStart(start);
    request.setMaxResults(maxResults);
    
    // 수동 검증 (또는 Validator 사용)
    // 또는 Spring의 @Validated와 MethodValidationPostProcessor 사용
    
    BookSearchResponse response = aladinApiService.searchBooks(request);
    return ApiResponse.success(response);
}
```

**권장 사항**: 
- RESTful API 관점에서 GET 요청에 Request Body를 사용하는 것은 표준에 맞지 않지만, 복잡한 검색 조건의 경우 POST를 사용하는 것이 일반적입니다.
- 현재는 GET 방식 유지 + `@RequestParam`에 직접 검증 어노테이션 추가하는 방식을 권장합니다.

---

## 2. AuthController 개선

### 현재 문제점

**문제**: `signup()`, `login()` 등 일부 메서드에서 `@Valid` 어노테이션이 누락되어 있을 수 있습니다.

**현재 코드 구조**:
```java
@PostMapping("/auth/signup")
public ApiResponse<RegisterResponse> signup(
        @Parameter(description = "회원가입 정보", required = true)
        @RequestBody RegistrationRequest request) {  // @Valid 없음
        
    // ...
}

@PostMapping("/auth/login")
public ApiResponse<LoginResponse> login(
        @Parameter(description = "로그인 정보", required = true)
        @RequestBody LoginRequest request) {  // @Valid 없음
        
    // ...
}
```

**문제점**:
1. 요청 데이터 검증이 제대로 수행되지 않을 수 있음
2. 잘못된 데이터가 Service 계층까지 전달될 수 있음
3. 컨트롤러의 검증 역할이 누락됨

### 개선 방안

Request DTO에 검증 어노테이션을 추가하고, 컨트롤러에서 `@Valid`를 사용하여 검증을 활성화합니다.

### 단계별 수정 계획

#### 단계 1: RegistrationRequest DTO 개선
**파일**: `src/main/java/com/readingtracker/server/dto/clientserverDTO/requestDTO/RegistrationRequest.java`

**작업 내용**:
1. 검증 어노테이션 추가
   - `loginId`: `@NotBlank`, `@Size`, `@Pattern` 등
   - `email`: `@NotBlank`, `@Email` 등
   - `name`: `@NotBlank`, `@Size` 등
   - `password`: `@NotBlank`, `@Size`, `@Pattern` 등

**예상 코드**:
```java
public class RegistrationRequest {
    @NotBlank(message = "로그인 ID는 필수입니다.")
    @Size(min = 4, max = 20, message = "로그인 ID는 4자 이상 20자 이하여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "로그인 ID는 영문과 숫자만 사용 가능합니다.")
    private String loginId;
    
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;
    
    @NotBlank(message = "이름은 필수입니다.")
    @Size(min = 2, max = 50, message = "이름은 2자 이상 50자 이하여야 합니다.")
    private String name;
    
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다.")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$", 
             message = "비밀번호는 대소문자, 숫자, 특수문자를 포함해야 합니다.")
    private String password;
    
    // ... getters, setters
}
```

#### 단계 2: LoginRequest DTO 개선
**파일**: `src/main/java/com/readingtracker/server/dto/clientserverDTO/requestDTO/LoginRequest.java`

**작업 내용**:
1. 검증 어노테이션 추가
   - `loginId`: `@NotBlank` 등
   - `password`: `@NotBlank` 등

**예상 코드**:
```java
public class LoginRequest {
    @NotBlank(message = "로그인 ID는 필수입니다.")
    private String loginId;
    
    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
    
    // ... getters, setters
}
```

#### 단계 3: AuthController 메서드 개선
**파일**: `src/main/java/com/readingtracker/server/controller/v1/AuthController.java`

**작업 내용**:
1. `signup()` 메서드에 `@Valid` 추가
2. `login()` 메서드에 `@Valid` 추가
3. 다른 `@RequestBody`를 사용하는 메서드들도 확인하여 `@Valid` 추가

**수정 대상 메서드**:
- `signup()` - `RegistrationRequest`
- `login()` - `LoginRequest`
- `findLoginId()` - `LoginIdRetrievalRequest` (확인 필요)
- `verifyAccount()` - `AccountVerificationRequest` (확인 필요)
- `resetPassword()` - `PasswordResetRequest` (확인 필요)
- `refreshToken()` - `RefreshTokenRequest` (확인 필요)

**예상 코드**:
```java
@PostMapping("/auth/signup")
@Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다")
public ApiResponse<RegisterResponse> signup(
        @Parameter(description = "회원가입 정보", required = true)
        @Valid @RequestBody RegistrationRequest request) {  // @Valid 추가
        
    // ...
}

@PostMapping("/auth/login")
@Operation(summary = "로그인", description = "사용자 로그인을 처리합니다")
public ApiResponse<LoginResponse> login(
        @Parameter(description = "로그인 정보", required = true)
        @Valid @RequestBody LoginRequest request) {  // @Valid 추가
        
    // ...
}
```

---

## 3. 기타 컨트롤러 확인 사항

### BookShelfController
**상태**: ✅ 양호
- 모든 `@RequestBody`에 `@Valid` 적용됨
- 추가 수정 불필요

### UserController
**상태**: ✅ 양호
- Request Body를 사용하지 않음 (인증 정보만 사용)
- 추가 수정 불필요

---

## 수정 우선순위

### 높은 우선순위
1. **AuthController - @Valid 추가**
   - 보안 및 데이터 무결성에 직접적인 영향
   - 잘못된 데이터가 Service 계층까지 전달되는 것을 방지

### 중간 우선순위
2. **BookSearchController - 검증 강화**
   - 사용자 입력 검증 강화
   - API 안정성 향상

---

## 검증 어노테이션 참고

다음과 같은 Jakarta Bean Validation 어노테이션을 사용할 수 있습니다:

- `@NotBlank`: null이 아니고, 공백이 아닌 문자열
- `@NotNull`: null이 아님
- `@NotEmpty`: null이 아니고, 빈 값이 아님
- `@Size(min=, max=)`: 문자열/컬렉션 크기 제한
- `@Min(value=)`: 최소값 제한
- `@Max(value=)`: 최대값 제한
- `@Email`: 이메일 형식 검증
- `@Pattern(regexp=)`: 정규식 패턴 검증
- `@Positive`: 양수만 허용
- `@Negative`: 음수만 허용

---

## 주의사항

1. **검증 어노테이션 추가 시**
   - DTO에 검증 어노테이션을 추가한 후, 컨트롤러에서 `@Valid`를 사용해야 검증이 활성화됩니다.
   - 검증 실패 시 `MethodArgumentNotValidException`이 발생하며, `GlobalExceptionHandler`에서 처리해야 합니다.

2. **기존 검증 로직과의 충돌**
   - Service 계층에 이미 검증 로직이 있다면, 컨트롤러 검증과 중복되지 않도록 주의해야 합니다.
   - 컨트롤러 검증은 기본적인 형식 검증, Service 검증은 비즈니스 로직 검증으로 역할을 구분하는 것이 좋습니다.

3. **API 호환성**
   - 검증 규칙을 추가하면 기존에 통과하던 요청이 실패할 수 있습니다.
   - 클라이언트 코드와의 호환성을 확인해야 합니다.

---

**작성일**: 2024년
**버전**: 1.0

