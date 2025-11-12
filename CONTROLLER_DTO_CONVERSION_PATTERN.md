# 컨트롤러 DTO 변환 패턴 분석

## 개요

각 컨트롤러에서 DTO 변환이 어떻게 이루어지는지 분석한 문서입니다.

**중요**: 클라이언트는 **Entity를 직접 보내지 않습니다**. 클라이언트는 **Request DTO**를 보내고, 서버는 **Response DTO**를 반환합니다. Entity는 서버 내부에서만 사용됩니다.

---

## DTO 변환 흐름

### 일반적인 흐름
```
클라이언트 → Request DTO → 컨트롤러 → Service → Entity (서버 내부)
                                                      ↓
클라이언트 ← Response DTO ← 컨트롤러 ← Service ← Entity (서버 내부)
```

---

## 1. BookSearchController

### 변환 패턴 분석

#### Request 처리
- **방식**: `@RequestParam`으로 개별 파라미터를 받아서 컨트롤러에서 `BookSearchRequest` DTO 생성
- **위치**: 컨트롤러 내부에서 DTO 생성

**코드 예시**:
```java
public ApiResponse<BookSearchResponse> searchBooks(
    @RequestParam String query,
    @RequestParam(defaultValue = "TITLE") BookSearchFilter queryType,
    @RequestParam(defaultValue = "1") Integer start,
    @RequestParam(defaultValue = "10") Integer maxResults) {
    
    // 컨트롤러에서 Request DTO 생성
    BookSearchRequest request = new BookSearchRequest();
    request.setQuery(query);
    request.setQueryType(queryType);
    request.setStart(start);
    request.setMaxResults(maxResults);
    
    // Service 호출 (Service가 Response DTO 반환)
    BookSearchResponse response = aladinApiService.searchBooks(request);
    return ApiResponse.success(response);
}
```

#### Response 처리
- **방식**: Service에서 `BookSearchResponse` DTO를 직접 반환받아 그대로 반환
- **변환 위치**: 변환 없음 (Service가 이미 DTO 반환)

**특징**:
- ✅ Service가 DTO를 반환하므로 컨트롤러에서 변환 불필요
- ❌ Request DTO를 컨트롤러에서 직접 생성하는 것은 개선 여지 있음

---

## 2. BookShelfController

### 변환 패턴 분석

#### Request 처리
- **방식**: `@RequestBody`로 Request DTO를 직접 받음
- **변환**: 없음 (클라이언트가 이미 Request DTO를 보냄)

**코드 예시**:
```java
public ApiResponse<BookAdditionResponse> addBookToShelf(
    @Valid @RequestBody BookAdditionRequest request) {
    
    // Request DTO를 그대로 Service에 전달
    UserShelfBook userBook = bookService.addBookToShelf(loginId, request);
    
    // Entity → Response DTO 변환 (컨트롤러에서 수행)
    BookAdditionResponse response = new BookAdditionResponse(...);
    return ApiResponse.success(response);
}
```

#### Response 처리
- **방식**: Service에서 Entity를 반환받아 컨트롤러에서 Response DTO로 변환
- **변환 위치**: 컨트롤러 내부

**변환 패턴별 상세**:

1. **단순 Entity → Response DTO 변환** (`addBookToShelf`)
   ```java
   UserShelfBook userBook = bookService.addBookToShelf(loginId, request);
   BookAdditionResponse response = new BookAdditionResponse(
       "책이 내 서재에 추가되었습니다.",
       userBook.getBookId(),
       request.getTitle(),
       userBook.getCategory()
   );
   ```

2. **Entity 리스트 → Response DTO 리스트 변환** (`getMyShelf`)
   ```java
   List<UserShelfBook> userBooks = bookService.getMyShelf(loginId, category, sortBy);
   
   // 헬퍼 메서드 사용
   List<MyShelfResponse.ShelfBook> shelfBooks = userBooks.stream()
       .map(this::convertToShelfBook)  // Entity → DTO 변환
       .collect(Collectors.toList());
   
   MyShelfResponse response = new MyShelfResponse(shelfBooks, shelfBooks.size());
   ```

3. **헬퍼 메서드** (`convertToShelfBook`)
   ```java
   private MyShelfResponse.ShelfBook convertToShelfBook(UserShelfBook userBook) {
       MyShelfResponse.ShelfBook shelfBook = new MyShelfResponse.ShelfBook();
       // Entity의 필드를 DTO에 매핑
       shelfBook.setUserBookId(userBook.getId());
       shelfBook.setBookId(userBook.getBookId());
       // ... (Book 엔티티의 정보도 포함)
       return shelfBook;
   }
   ```

**특징**:
- ✅ Request DTO는 클라이언트가 직접 보내므로 변환 불필요
- ✅ Entity → Response DTO 변환을 컨트롤러에서 수행
- ✅ 복잡한 변환은 헬퍼 메서드로 분리

---

## 3. AuthController

### 변환 패턴 분석

#### Request 처리
- **방식**: `@RequestBody`로 Client Request DTO를 받아서 Command DTO로 변환
- **변환 위치**: 컨트롤러 내부

**코드 예시**:
```java
public ApiResponse<RegisterResponse> signup(
    @RequestBody RegistrationRequest request) {
    
    // Client Request DTO → Command DTO 변환 (컨트롤러에서 수행)
    UserCreationCommand command = new UserCreationCommand(
        request.getLoginId(),
        request.getEmail(),
        request.getName(),
        request.getPassword()
    );
    
    // Service 호출 (Service가 Result DTO 반환)
    UserResult userResult = authService.register(command);
    
    // Result DTO → Client Response DTO 변환 (컨트롤러에서 수행)
    RegisterResponse response = new RegisterResponse(userResult);
    return ApiResponse.success(response);
}
```

#### Response 처리
- **방식**: Service에서 Result DTO를 반환받아 Client Response DTO로 변환
- **변환 위치**: 컨트롤러 내부

**변환 패턴별 상세**:

1. **Result DTO → Client Response DTO 변환** (`signup`)
   ```java
   UserResult userResult = authService.register(command);
   RegisterResponse response = new RegisterResponse(userResult);
   ```

2. **복잡한 Response DTO 생성** (`login`)
   ```java
   AuthService.LoginResult result = authService.login(command);
   LoginResponse response = new LoginResponse(
       result.getAccessToken(),
       result.getRefreshToken(),
       result.getUser()  // UserResult를 UserInfo로 변환
   );
   ```

3. **중첩된 DTO 변환** (`LoginResponse.UserInfo`)
   ```java
   public static class LoginResponse {
       private UserInfo user;
       
       public LoginResponse(String accessToken, String refreshToken, UserResult user) {
           this.user = new UserInfo(user);  // UserResult → UserInfo 변환
       }
       
       public static class UserInfo {
           public UserInfo(UserResult user) {
               // UserResult의 필드를 UserInfo에 매핑
               this.id = user.getId();
               this.loginId = user.getLoginId();
               // ...
           }
       }
   }
   ```

**특징**:
- ✅ 3-tier Architecture의 DTO 분리 원칙 준수
  - Client ↔ Server: `clientserverDTO` (Request/Response)
  - Server ↔ DBMS: `serverdbmsDTO` (Command/Result)
- ✅ 컨트롤러에서 Client DTO ↔ Command/Result DTO 변환 수행
- ✅ Response DTO는 컨트롤러 내부 클래스로 정의

---

## 4. UserController

### 변환 패턴 분석

#### Request 처리
- **방식**: Request Body 없음 (인증 정보만 사용)
- **변환**: 없음

#### Response 처리
- **방식**: Service에서 Entity를 반환받아 컨트롤러에서 Response DTO로 변환
- **변환 위치**: 컨트롤러 내부

**코드 예시**:
```java
public ApiResponse<UserProfileResponse> getMyProfile() {
    User user = userService.findActiveUserByLoginId(loginId);
    
    // Entity → Response DTO 변환 (컨트롤러에서 수행)
    UserProfileResponse response = new UserProfileResponse(user);
    return ApiResponse.success(response);
}

public static class UserProfileResponse {
    public UserProfileResponse(User user) {
        // Entity의 필드를 DTO에 매핑
        this.id = user.getId();
        this.loginId = user.getLoginId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.role = user.getRole().name();  // Enum → String 변환
        this.status = user.getStatus().name();  // Enum → String 변환
    }
}
```

**특징**:
- ✅ Entity → Response DTO 변환을 컨트롤러에서 수행
- ✅ Enum 타입을 String으로 변환하는 추가 처리 포함
- ✅ Response DTO는 컨트롤러 내부 클래스로 정의

---

## 변환 패턴 요약

### 패턴 1: Service가 DTO 반환 (변환 없음)
- **컨트롤러**: BookSearchController
- **특징**: Service가 이미 DTO를 반환하므로 컨트롤러에서 변환 불필요

### 패턴 2: Entity → Response DTO 변환 (컨트롤러에서 수행)
- **컨트롤러**: BookShelfController, UserController
- **특징**: 
  - Service가 Entity를 반환
  - 컨트롤러에서 Response DTO로 변환
  - 헬퍼 메서드 사용 가능

### 패턴 3: Client DTO ↔ Command/Result DTO 변환 (컨트롤러에서 수행)
- **컨트롤러**: AuthController
- **특징**:
  - 3-tier Architecture의 DTO 분리 원칙 준수
  - Client Request DTO → Command DTO 변환
  - Result DTO → Client Response DTO 변환

---

## 변환 위치 비교

| 컨트롤러 | Request 변환 | Response 변환 | 변환 위치 |
|---------|------------|-------------|---------|
| BookSearchController | RequestParam → Request DTO | 없음 (Service가 DTO 반환) | 컨트롤러 |
| BookShelfController | 없음 (직접 Request DTO 받음) | Entity → Response DTO | 컨트롤러 |
| AuthController | Client Request DTO → Command DTO | Result DTO → Client Response DTO | 컨트롤러 |
| UserController | 없음 (Request Body 없음) | Entity → Response DTO | 컨트롤러 |

---

## 주요 발견 사항

### 1. Entity는 클라이언트와 직접 주고받지 않음
- ✅ **올바른 구조**: 클라이언트는 Request DTO를 보내고, Response DTO를 받음
- ✅ Entity는 서버 내부(Service, Repository)에서만 사용됨
- ✅ 컨트롤러는 Entity를 클라이언트에 노출하지 않음

### 2. 변환 위치의 일관성
- ✅ **일관된 패턴**: 모든 컨트롤러에서 Entity → Response DTO 변환을 컨트롤러에서 수행
- ✅ **예외**: BookSearchController는 Service가 이미 DTO를 반환하므로 변환 불필요

### 3. 변환 방식의 다양성
- **직접 생성**: `new ResponseDTO(entity)`
- **헬퍼 메서드**: `convertToShelfBook()`
- **내부 클래스**: `RegisterResponse`, `LoginResponse`, `UserProfileResponse`

### 4. 개선 여지
- **BookSearchController**: RequestParam을 직접 받아 DTO를 생성하는 방식보다 Request Body 방식 고려
- **일관성**: 모든 컨트롤러가 동일한 변환 패턴을 사용하도록 통일 고려

---

## 결론

**질문에 대한 답변**: 
- ❌ **아니요**, 컨트롤러에서 사용자가 보낸 Entity를 DTO로 변환하는 것이 아닙니다.
- ✅ 클라이언트는 **Request DTO**를 보냅니다.
- ✅ 서버는 **Response DTO**를 반환합니다.
- ✅ 컨트롤러에서 수행하는 변환은:
  1. **Request DTO → Command DTO** (AuthController)
  2. **Entity → Response DTO** (BookShelfController, UserController)
  3. **Result DTO → Client Response DTO** (AuthController)

**현재 구조는 올바르게 설계되어 있으며**, Entity는 서버 내부에서만 사용되고 클라이언트와는 DTO를 통해 통신합니다.

---

**작성일**: 2024년
**버전**: 1.0

