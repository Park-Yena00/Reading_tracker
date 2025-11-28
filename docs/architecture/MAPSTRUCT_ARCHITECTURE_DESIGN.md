# MapStruct 기반 DTO-Entity 변환 구조 설계

## 개요

모든 DTO와 Entity의 변환을 Mapper 클래스가 담당하고, Controller는 변환 작업을 신경쓰지 않으며, Service 계층에서는 Entity만 사용하는 구조로 변경하는 설계 문서입니다.

---

## 핵심 원칙

### 1. 변환 책임 분리
- **Controller**: 변환 작업 없음, Mapper만 호출
- **Mapper**: 모든 DTO ↔ Entity 변환 담당
- **Service**: Entity만 사용 (DTO 사용 안 함)

### 2. Service 계층의 Entity 사용
- Service 간 통신: Entity 사용
- DB 작업: Entity 사용
- 비즈니스 로직: Entity 기반

### 3. MapStruct 사용
- 컴파일 타임에 매핑 코드 자동 생성
- 타입 안정성 보장
- 성능 최적화

### 4. DTO 공유 및 데이터 포맷팅 정책
- **웹/앱 DTO 공유**: 웹과 앱이 완전히 동일한 DTO 사용
- **포맷팅 책임**: 날짜, 시간 등 데이터 포맷팅은 각 클라이언트(웹/앱)에서 처리
- **서버 역할**: 표준 형식(ISO 8601 등)으로 순수 데이터만 제공
- **이유**: 
  - 서버 로직 단순화 및 유지보수성 향상
  - 클라이언트별 UI 요구사항에 맞는 포맷팅 가능
  - 서버는 비즈니스 로직에 집중
- **예시**: 
  - 서버 응답: `createdAt: "2024-01-15T10:30:00"` (ISO 8601)
  - 웹 클라이언트: "2024년 1월 15일 10시 30분"으로 포맷팅
  - 앱 클라이언트: "1/15 10:30" 또는 "2시간 전"으로 포맷팅

### 5. 클라이언트 구분 불필요 정책
- **핵심 원칙**: Controller와 Service는 요청이 웹에서 왔는지 앱에서 왔는지 구분할 필요가 없음
- **클라이언트-서버 아키텍처의 응답 방식**:
  - 서버는 클라이언트와 직접적인 '연결'을 유지하지 않음
  - 클라이언트가 보낸 HTTP 요청에 대해 응답을 '되돌려주는' 방식으로 통신
  - 요청 흐름:
    1. 클라이언트가 HTTP 요청 전송 (네트워크 주소 정보 포함)
    2. 서버가 요청 처리 및 Response DTO 준비
    3. 서버가 요청이 들어온 네트워크 경로를 통해 응답을 되돌려 전송
- **서버가 클라이언트를 구분할 필요가 없는 이유**:
  1. **동일한 통신 프로토콜**: 웹과 앱 모두 표준 HTTP/HTTPS 프로토콜 사용
  2. **동일한 DTO**: DTO 형식이 동일하므로 Controller는 하나의 ResponseDTO만 생성하면 됨
  3. **라우팅은 네트워크의 책임**: 응답 전달은 OS의 네트워킹 스택과 인터넷 라우팅의 책임
- **구현 원칙**:
  - Controller와 Service는 클라이언트 종류를 확인하거나 구분하는 로직을 포함하지 않음
  - 서버는 단순히 요청에 응답하는 것만으로 웹과 앱 모두에게 데이터를 정확하게 전달

---

## 현재 구조 vs 변경 후 구조

### 현재 구조 (BookShelfController 예시)

```java
// Controller
public ApiResponse<BookAdditionResponse> addBookToShelf(
    @Valid @RequestBody BookAdditionRequest request) {
    
    // Controller에서 변환 없이 Service 호출
    UserShelfBook userBook = bookService.addBookToShelf(loginId, request);
    
    // ❌ Controller에서 Entity → ResponseDTO 변환 수행
    BookAdditionResponse response = new BookAdditionResponse(
        "책이 내 서재에 추가되었습니다.",
        userBook.getBookId(),
        request.getTitle(),
        userBook.getCategory()
    );
    
    return ApiResponse.success(response);
}

// Service
public UserShelfBook addBookToShelf(String loginId, BookAdditionRequest request) {
    // ❌ Service에서 RequestDTO 직접 사용
    // ...
    Book book = createBookFromRequest(request);  // RequestDTO → Entity 변환
    // ...
}
```

### 변경 후 구조 (BookShelfController 예시)

```java
// Controller
public ApiResponse<BookAdditionResponse> addBookToShelf(
    @Valid @RequestBody BookAdditionRequest request) {
    
    // ✅ Mapper를 통해 RequestDTO → Entity 변환
    UserShelfBook userBookEntity = bookMapper.toEntity(request, loginId);
    
    // ✅ Service는 Entity만 받음
    UserShelfBook savedUserBook = bookService.addBookToShelf(userBookEntity);
    
    // ✅ Mapper를 통해 Entity → ResponseDTO 변환
    BookAdditionResponse response = bookMapper.toBookAdditionResponse(savedUserBook);
    
    return ApiResponse.success(response);
}

// Service
public UserShelfBook addBookToShelf(UserShelfBook userBook) {
    // ✅ Service는 Entity만 사용
    // 비즈니스 로직 처리
    return userBookRepository.save(userBook);
}
```

---

## 구조 변경 상세

### 1. Controller 계층 변경

#### 변경 전
```java
@RestController
public class BookShelfController {
    @Autowired
    private BookService bookService;
    
    public ApiResponse<BookAdditionResponse> addBookToShelf(
        @Valid @RequestBody BookAdditionRequest request) {
        
        // Service 호출 (RequestDTO 전달)
        UserShelfBook userBook = bookService.addBookToShelf(loginId, request);
        
        // ❌ Controller에서 Entity → ResponseDTO 변환
        BookAdditionResponse response = new BookAdditionResponse(...);
        return ApiResponse.success(response);
    }
    
    public ApiResponse<MyShelfResponse> getMyShelf(...) {
        // Service 호출
        List<UserShelfBook> userBooks = bookService.getMyShelf(loginId, category, sortBy);
        
        // ❌ Controller에서 Entity → ResponseDTO 변환
        List<MyShelfResponse.ShelfBook> shelfBooks = userBooks.stream()
            .map(this::convertToShelfBook)  // 헬퍼 메서드 사용
            .collect(Collectors.toList());
        
        return ApiResponse.success(new MyShelfResponse(shelfBooks, shelfBooks.size()));
    }
}
```

#### 변경 후
```java
@RestController
public class BookShelfController {
    @Autowired
    private BookService bookService;
    
    @Autowired
    private BookMapper bookMapper;  // ✅ Mapper 주입
    
    public ApiResponse<BookAdditionResponse> addBookToShelf(
        @Valid @RequestBody BookAdditionRequest request) {
        
        // ✅ Mapper로 RequestDTO → Entity 변환
        UserShelfBook userBookEntity = bookMapper.toUserShelfBookEntity(request, loginId);
        
        // ✅ Service는 Entity만 받음
        UserShelfBook savedUserBook = bookService.addBookToShelf(userBookEntity);
        
        // ✅ Mapper로 Entity → ResponseDTO 변환
        BookAdditionResponse response = bookMapper.toBookAdditionResponse(savedUserBook);
        
        return ApiResponse.success(response);
    }
    
    public ApiResponse<MyShelfResponse> getMyShelf(...) {
        // ✅ Service는 Entity만 반환
        List<UserShelfBook> userBooks = bookService.getMyShelf(loginId, category, sortBy);
        
        // ✅ Mapper로 Entity 리스트 → ResponseDTO 리스트 변환
        MyShelfResponse response = bookMapper.toMyShelfResponse(userBooks);
        
        return ApiResponse.success(response);
    }
    
    // ❌ convertToShelfBook() 헬퍼 메서드 제거 (Mapper로 대체)
}
```

### 2. Service 계층 변경

#### 변경 전
```java
@Service
public class BookService {
    
    // ❌ Service가 RequestDTO를 받음
    public UserShelfBook addBookToShelf(String loginId, BookAdditionRequest request) {
        // RequestDTO 사용
        if (bookRepository.existsByIsbn(request.getIsbn())) {
            // ...
        }
        Book book = createBookFromRequest(request);  // RequestDTO → Entity 변환
        // ...
    }
    
    // ❌ Service가 Entity를 반환하지만 RequestDTO를 사용
    public List<UserShelfBook> getMyShelf(String loginId, BookCategory category, BookSortCriteria sortBy) {
        // Entity 반환
        return userBookRepository.findByUserIdAndCategoryOrderByTitleAsc(userId, category);
    }
}
```

#### 변경 후
```java
@Service
public class BookService {
    
    // ✅ Service는 Entity만 받음
    public UserShelfBook addBookToShelf(UserShelfBook userBook) {
        // Entity만 사용
        if (bookRepository.existsByIsbn(userBook.getBook().getIsbn())) {
            // ...
        }
        // 비즈니스 로직 처리
        return userBookRepository.save(userBook);
    }
    
    // ✅ Service는 Entity만 반환
    public List<UserShelfBook> getMyShelf(String loginId, BookCategory category, BookSortCriteria sortBy) {
        // Entity 반환
        return userBookRepository.findByUserIdAndCategoryOrderByTitleAsc(userId, category);
    }
    
    // ✅ Service 간 통신도 Entity 사용
    public UserShelfBook updateBookCategory(UserShelfBook userBook, BookCategory category) {
        userBook.setCategory(category);
        return userBookRepository.save(userBook);
    }
}
```

### 3. Mapper 계층 추가 (MapStruct 사용)

#### Mapper 인터페이스 정의

```java
// BookMapper.java
@Mapper(componentModel = "spring")
public interface BookMapper {
    
    // RequestDTO → Entity 변환
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)  // Service에서 설정
    @Mapping(target = "book", expression = "java(createBookFromRequest(request))")
    @Mapping(target = "category", source = "request.category")
    @Mapping(target = "expectation", source = "request.expectation")
    @Mapping(target = "readingStartDate", source = "request.readingStartDate")
    @Mapping(target = "readingProgress", source = "request.readingProgress")
    @Mapping(target = "purchaseType", source = "request.purchaseType")
    @Mapping(target = "readingFinishedDate", source = "request.readingFinishedDate")
    @Mapping(target = "rating", source = "request.rating")
    @Mapping(target = "review", source = "request.review")
    @Mapping(target = "categoryManuallySet", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserShelfBook toUserShelfBookEntity(BookAdditionRequest request, @Context String loginId);
    
    // Entity → ResponseDTO 변환
    @Mapping(target = "message", constant = "책이 내 서재에 추가되었습니다.")
    @Mapping(target = "bookId", source = "userBook.bookId")
    @Mapping(target = "title", source = "userBook.book.title")
    @Mapping(target = "category", source = "userBook.category")
    BookAdditionResponse toBookAdditionResponse(UserShelfBook userBook);
    
    // Entity 리스트 → ResponseDTO 변환
    @Mapping(target = "books", source = "userBooks")
    @Mapping(target = "totalCount", expression = "java(userBooks.size())")
    MyShelfResponse toMyShelfResponse(List<UserShelfBook> userBooks);
    
    // Entity → ShelfBook 변환
    @Mapping(target = "userBookId", source = "id")
    @Mapping(target = "bookId", source = "bookId")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "lastReadPage", source = "lastReadPage")
    @Mapping(target = "lastReadAt", source = "lastReadAt")
    @Mapping(target = "addedAt", source = "addedAt")
    @Mapping(target = "isbn", source = "book.isbn")
    @Mapping(target = "title", source = "book.title")
    @Mapping(target = "author", source = "book.author")
    @Mapping(target = "publisher", source = "book.publisher")
    @Mapping(target = "description", source = "book.description")
    @Mapping(target = "coverUrl", source = "book.coverUrl")
    @Mapping(target = "totalPages", source = "book.totalPages")
    @Mapping(target = "mainGenre", source = "book.mainGenre")
    @Mapping(target = "pubDate", source = "book.pubDate")
    MyShelfResponse.ShelfBook toShelfBook(UserShelfBook userBook);
    
    // RequestDTO → Book Entity 변환 (내부 메서드)
    default Book createBookFromRequest(BookAdditionRequest request) {
        Book book = new Book(
            request.getIsbn(),
            request.getTitle(),
            request.getAuthor(),
            request.getPublisher()
        );
        book.setDescription(request.getDescription());
        book.setCoverUrl(request.getCoverUrl());
        book.setTotalPages(request.getTotalPages());
        book.setMainGenre(request.getMainGenre());
        book.setPubDate(request.getPubDate());
        return book;
    }
}
```

```java
// UserMapper.java
@Mapper(componentModel = "spring")
public interface UserMapper {
    
    // RequestDTO → Entity 변환
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)  // Service에서 암호화
    @Mapping(target = "role", constant = "USER")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "failedLoginCount", constant = "0")
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(RegistrationRequest request);
    
    // Entity → ResponseDTO 변환
    @Mapping(target = "id", source = "id")
    @Mapping(target = "loginId", source = "loginId")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "status", source = "status")
    RegisterResponse toRegisterResponse(User user);
    
    // Entity → LoginResponse 변환
    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    @Mapping(target = "user", source = "user")
    LoginResponse toLoginResponse(User user, String accessToken, String refreshToken);
    
    // Entity → LoginIdRetrievalResponse 변환
    @Mapping(target = "loginId", source = "loginId")
    @Mapping(target = "email", source = "email")
    LoginIdRetrievalResponse toLoginIdRetrievalResponse(User user);
    
    // Entity → PasswordResetResponse 변환
    @Mapping(target = "message", constant = "비밀번호가 성공적으로 변경되었습니다.")
    @Mapping(target = "loginId", source = "loginId")
    PasswordResetResponse toPasswordResetResponse(User user);
    
    // Entity → UserProfileResponse 변환
    @Mapping(target = "id", source = "id")
    @Mapping(target = "loginId", source = "loginId")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "role", expression = "java(user.getRole().name())")
    @Mapping(target = "status", expression = "java(user.getStatus().name())")
    UserProfileResponse toUserProfileResponse(User user);
}
```

---

## 전체 흐름 비교

### 현재 흐름

```
클라이언트
  ↓ RequestDTO
Controller
  ↓ RequestDTO (변환 없음)
Service
  ↓ RequestDTO → Entity 변환 (Service 내부)
  ↓ Entity 작업
  ↓ Entity 반환
Controller
  ↓ Entity → ResponseDTO 변환 (Controller 내부)
  ↓ ResponseDTO
클라이언트
```

### 변경 후 흐름

```
클라이언트
  ↓ RequestDTO
Controller
  ↓ RequestDTO
Mapper (RequestDTO → Entity)
  ↓ Entity
Service
  ↓ Entity 작업
  ↓ Entity 반환
Mapper (Entity → ResponseDTO)
  ↓ ResponseDTO
Controller
  ↓ ResponseDTO
클라이언트
```

---

## 패키지 구조 변경

### 변경 전
```
server/
  ├── controller/
  │   └── v1/
  │       ├── AuthController.java
  │       ├── BookShelfController.java
  │       └── ...
  └── service/
      ├── AuthService.java
      ├── BookService.java
      └── ...
```

### 변경 후
```
server/
  ├── controller/
  │   └── v1/
  │       ├── AuthController.java
  │       ├── BookShelfController.java
  │       └── ...
  ├── mapper/                    # ✅ 새로 추가
  │   ├── UserMapper.java
  │   ├── BookMapper.java
  │   └── ...
  └── service/
      ├── AuthService.java
      ├── BookService.java
      └── ...
```

---

## MapStruct 설정

### pom.xml 추가

```xml
<properties>
    <org.mapstruct.version>1.5.5.Final</org.mapstruct.version>
</properties>

<dependencies>
    <!-- MapStruct -->
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>${org.mapstruct.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <source>17</source>
                <target>17</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>${org.mapstruct.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## 주요 변경 사항 요약

### 1. Controller 계층
- ✅ **변경 전**: Entity → ResponseDTO 변환 수행
- ✅ **변경 후**: 변환 없음, Mapper만 호출

**제거되는 코드**:
- `convertToShelfBook()` 같은 헬퍼 메서드
- `new ResponseDTO(entity)` 같은 직접 생성 코드
- `stream().map()` 변환 로직

**추가되는 코드**:
- `@Autowired private BookMapper bookMapper;`
- `bookMapper.toEntity(request)`
- `bookMapper.toResponse(entity)`

### 2. Service 계층
- ✅ **변경 전**: RequestDTO를 받아 Entity로 변환
- ✅ **변경 후**: Entity만 받고 Entity만 반환

**제거되는 코드**:
- `public UserShelfBook addBookToShelf(String loginId, BookAdditionRequest request)`
- `createBookFromRequest(request)` 같은 변환 메서드

**변경되는 코드**:
- `public UserShelfBook addBookToShelf(UserShelfBook userBook)`
- 모든 메서드가 Entity 기반으로 변경

### 3. Mapper 계층 (신규)
- ✅ **위치**: `server.mapper` 패키지
- ✅ **구현**: MapStruct 인터페이스
- ✅ **역할**: 모든 DTO ↔ Entity 변환

**주요 Mapper**:
- `UserMapper`: User 관련 변환
- `BookMapper`: Book 관련 변환

---

## 구체적인 변경 예시

### 예시 1: BookShelfController.addBookToShelf()

#### 변경 전
```java
public ApiResponse<BookAdditionResponse> addBookToShelf(
    @Valid @RequestBody BookAdditionRequest request) {
    
    // Service 호출 (RequestDTO 전달)
    UserShelfBook userBook = bookService.addBookToShelf(loginId, request);
    
    // Controller에서 변환
    BookAdditionResponse response = new BookAdditionResponse(
        "책이 내 서재에 추가되었습니다.",
        userBook.getBookId(),
        request.getTitle(),
        userBook.getCategory()
    );
    
    return ApiResponse.success(response);
}
```

#### 변경 후
```java
public ApiResponse<BookAdditionResponse> addBookToShelf(
    @Valid @RequestBody BookAdditionRequest request) {
    
    // Mapper로 RequestDTO → Entity 변환
    UserShelfBook userBookEntity = bookMapper.toUserShelfBookEntity(request, loginId);
    
    // Service 호출 (Entity 전달)
    UserShelfBook savedUserBook = bookService.addBookToShelf(userBookEntity);
    
    // Mapper로 Entity → ResponseDTO 변환
    BookAdditionResponse response = bookMapper.toBookAdditionResponse(savedUserBook);
    
    return ApiResponse.success(response);
}
```

### 예시 2: BookService.addBookToShelf()

#### 변경 전
```java
public UserShelfBook addBookToShelf(String loginId, BookAdditionRequest request) {
    // RequestDTO 사용
    User user = userRepository.findActiveUserByLoginId(loginId)
        .orElseThrow(...);
    
    if (bookRepository.existsByIsbn(request.getIsbn())) {
        // ...
    }
    
    Book book = createBookFromRequest(request);  // RequestDTO → Entity 변환
    UserShelfBook userBook = new UserShelfBook(user, book, request.getCategory());
    setCategorySpecificFields(userBook, request);  // RequestDTO 사용
    
    return userBookRepository.save(userBook);
}
```

#### 변경 후
```java
public UserShelfBook addBookToShelf(UserShelfBook userBook) {
    // Entity만 사용
    User user = userBook.getUser();
    
    if (bookRepository.existsByIsbn(userBook.getBook().getIsbn())) {
        // ...
    }
    
    // 비즈니스 로직 처리 (Entity 기반)
    validateCategorySpecificFields(userBook);
    
    return userBookRepository.save(userBook);
}
```

### 예시 3: BookShelfController.getMyShelf()

#### 변경 전
```java
public ApiResponse<MyShelfResponse> getMyShelf(...) {
    List<UserShelfBook> userBooks = bookService.getMyShelf(loginId, category, sortBy);
    
    // Controller에서 변환
    List<MyShelfResponse.ShelfBook> shelfBooks = userBooks.stream()
        .map(this::convertToShelfBook)  // 헬퍼 메서드
        .collect(Collectors.toList());
    
    MyShelfResponse response = new MyShelfResponse(shelfBooks, shelfBooks.size());
    return ApiResponse.success(response);
}

// 헬퍼 메서드
private MyShelfResponse.ShelfBook convertToShelfBook(UserShelfBook userBook) {
    // 수동 변환 로직
    // ...
}
```

#### 변경 후
```java
public ApiResponse<MyShelfResponse> getMyShelf(...) {
    List<UserShelfBook> userBooks = bookService.getMyShelf(loginId, category, sortBy);
    
    // Mapper로 변환
    MyShelfResponse response = bookMapper.toMyShelfResponse(userBooks);
    
    return ApiResponse.success(response);
}

// ❌ convertToShelfBook() 헬퍼 메서드 제거
```

### 예시 4: AuthController.signup()

#### 변경 전
```java
public ApiResponse<RegisterResponse> signup(
    @RequestBody RegistrationRequest request) {
    
    // Client RequestDTO → CommandDTO 변환
    UserCreationCommand command = new UserCreationCommand(
        request.getLoginId(),
        request.getEmail(),
        request.getName(),
        request.getPassword()
    );
    
    // Service 호출 (CommandDTO)
    UserResult userResult = authService.register(command);
    
    // ResultDTO → Client ResponseDTO 변환
    RegisterResponse response = new RegisterResponse(userResult);
    
    return ApiResponse.success(response);
}
```

#### 변경 후
```java
public ApiResponse<RegisterResponse> signup(
    @RequestBody RegistrationRequest request) {
    
    // Mapper로 RequestDTO → Entity 변환
    User userEntity = userMapper.toEntity(request);
    
    // Service 호출 (Entity)
    User savedUser = authService.register(userEntity);
    
    // Mapper로 Entity → ResponseDTO 변환
    RegisterResponse response = userMapper.toRegisterResponse(savedUser);
    
    return ApiResponse.success(response);
}
```

### 예시 5: AuthService.register()

#### 변경 전
```java
public UserResult register(UserCreationCommand command) {
    User user = executeRegister(command);
    return toUserResult(user);  // Entity → ResultDTO 변환
}

private User executeRegister(UserCreationCommand command) {
    // CommandDTO 사용
    if (userRepository.existsByLoginId(command.getLoginId())) {
        // ...
    }
    // ...
    User user = new User(
        command.getLoginId(),
        command.getEmail(),
        command.getName(),
        encodedPassword
    );
    return userRepository.save(user);
}

private UserResult toUserResult(User user) {
    // Entity → ResultDTO 변환
    return new UserResult(...);
}
```

#### 변경 후
```java
public User register(User user) {
    // Entity만 사용
    if (userRepository.existsByLoginId(user.getLoginId())) {
        throw new IllegalArgumentException(...);
    }
    
    if (userRepository.existsByEmail(user.getEmail())) {
        throw new IllegalArgumentException(...);
    }
    
    // 비밀번호 검증 및 암호화
    passwordValidator.validate(user.getPasswordHash());
    String encodedPassword = passwordEncoder.encode(user.getPasswordHash());
    user.setPasswordHash(encodedPassword);
    
    // 비즈니스 로직 처리
    return userRepository.save(user);
}

// ❌ toUserResult() 메서드 제거 (Mapper로 대체)
```

---

## Mapper 인터페이스 상세 설계

### UserMapper

```java
@Mapper(componentModel = "spring")
public interface UserMapper {
    
    // ========== RequestDTO → Entity ==========
    
    /**
     * RegistrationRequest → User Entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)  // Service에서 암호화
    @Mapping(target = "role", constant = "USER")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "failedLoginCount", constant = "0")
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(RegistrationRequest request);
    
    /**
     * LoginRequest → User Entity (부분 매핑)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "failedLoginCount", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(LoginRequest request);
    
    // ========== Entity → ResponseDTO ==========
    
    /**
     * User Entity → RegisterResponse
     */
    RegisterResponse toRegisterResponse(User user);
    
    /**
     * User Entity → LoginResponse
     */
    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    @Mapping(target = "user", source = "user")
    LoginResponse toLoginResponse(User user, String accessToken, String refreshToken);
    
    /**
     * User Entity → LoginIdRetrievalResponse
     */
    LoginIdRetrievalResponse toLoginIdRetrievalResponse(User user);
    
    /**
     * User Entity → PasswordResetResponse
     */
    @Mapping(target = "message", constant = "비밀번호가 성공적으로 변경되었습니다.")
    PasswordResetResponse toPasswordResetResponse(User user);
    
    /**
     * User Entity → UserProfileResponse
     */
    @Mapping(target = "role", expression = "java(user.getRole().name())")
    @Mapping(target = "status", expression = "java(user.getStatus().name())")
    UserProfileResponse toUserProfileResponse(User user);
}
```

### BookMapper

```java
@Mapper(componentModel = "spring")
public interface BookMapper {
    
    // ========== RequestDTO → Entity ==========
    
    /**
     * BookAdditionRequest → UserShelfBook Entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)  // Controller에서 설정
    @Mapping(target = "book", expression = "java(createBookFromRequest(request))")
    @Mapping(target = "category", source = "request.category")
    @Mapping(target = "expectation", source = "request.expectation")
    @Mapping(target = "readingStartDate", source = "request.readingStartDate")
    @Mapping(target = "readingProgress", source = "request.readingProgress")
    @Mapping(target = "purchaseType", source = "request.purchaseType")
    @Mapping(target = "readingFinishedDate", source = "request.readingFinishedDate")
    @Mapping(target = "rating", source = "request.rating")
    @Mapping(target = "review", source = "request.review")
    @Mapping(target = "categoryManuallySet", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserShelfBook toUserShelfBookEntity(BookAdditionRequest request);
    
    /**
     * BookDetailUpdateRequest → UserShelfBook Entity (부분 업데이트)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "book", ignore = true)
    @Mapping(target = "category", source = "request.category")
    @Mapping(target = "expectation", source = "request.expectation")
    @Mapping(target = "readingStartDate", source = "request.readingStartDate")
    @Mapping(target = "readingProgress", source = "request.readingProgress")
    @Mapping(target = "purchaseType", source = "request.purchaseType")
    @Mapping(target = "readingFinishedDate", source = "request.readingFinishedDate")
    @Mapping(target = "rating", source = "request.rating")
    @Mapping(target = "review", source = "request.review")
    @Mapping(target = "categoryManuallySet", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateUserShelfBookFromRequest(@MappingTarget UserShelfBook userBook, BookDetailUpdateRequest request);
    
    // ========== Entity → ResponseDTO ==========
    
    /**
     * UserShelfBook Entity → BookAdditionResponse
     */
    @Mapping(target = "message", constant = "책이 내 서재에 추가되었습니다.")
    @Mapping(target = "bookId", source = "userBook.bookId")
    @Mapping(target = "title", source = "userBook.book.title")
    @Mapping(target = "category", source = "userBook.category")
    BookAdditionResponse toBookAdditionResponse(UserShelfBook userBook);
    
    /**
     * UserShelfBook Entity 리스트 → MyShelfResponse
     */
    @Mapping(target = "books", source = "userBooks")
    @Mapping(target = "totalCount", expression = "java(userBooks.size())")
    MyShelfResponse toMyShelfResponse(List<UserShelfBook> userBooks);
    
    /**
     * UserShelfBook Entity → ShelfBook
     */
    @Mapping(target = "userBookId", source = "id")
    @Mapping(target = "bookId", source = "bookId")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "lastReadPage", source = "lastReadPage")
    @Mapping(target = "lastReadAt", source = "lastReadAt")
    @Mapping(target = "addedAt", source = "addedAt")
    @Mapping(target = "isbn", source = "book.isbn")
    @Mapping(target = "title", source = "book.title")
    @Mapping(target = "author", source = "book.author")
    @Mapping(target = "publisher", source = "book.publisher")
    @Mapping(target = "description", source = "book.description")
    @Mapping(target = "coverUrl", source = "book.coverUrl")
    @Mapping(target = "totalPages", source = "book.totalPages")
    @Mapping(target = "mainGenre", source = "book.mainGenre")
    @Mapping(target = "pubDate", source = "book.pubDate")
    MyShelfResponse.ShelfBook toShelfBook(UserShelfBook userBook);
    
    // ========== 내부 헬퍼 메서드 ==========
    
    /**
     * BookAdditionRequest → Book Entity 변환
     */
    default Book createBookFromRequest(BookAdditionRequest request) {
        Book book = new Book(
            request.getIsbn(),
            request.getTitle(),
            request.getAuthor(),
            request.getPublisher()
        );
        book.setDescription(request.getDescription());
        book.setCoverUrl(request.getCoverUrl());
        book.setTotalPages(request.getTotalPages());
        book.setMainGenre(request.getMainGenre());
        book.setPubDate(request.getPubDate());
        return book;
    }
}
```

---

## 변경 작업 체크리스트

### Phase 1: MapStruct 설정
- [ ] `pom.xml`에 MapStruct 의존성 추가
- [ ] `maven-compiler-plugin` 설정 추가

### Phase 2: Mapper 인터페이스 생성
- [ ] `UserMapper` 인터페이스 생성
- [ ] `BookMapper` 인터페이스 생성
- [ ] 필요한 매핑 메서드 정의

### Phase 3: Controller 수정
- [ ] `AuthController`: CommandDTO 변환 제거, Mapper 사용
- [ ] `BookShelfController`: Entity 변환 제거, Mapper 사용
- [ ] `BookSearchController`: 변환 로직 확인 및 Mapper 적용
- [ ] `UserController`: Entity 변환 제거, Mapper 사용

### Phase 4: Service 수정
- [ ] `AuthService`: 모든 메서드를 Entity 기반으로 변경
- [ ] `BookService`: RequestDTO 파라미터를 Entity로 변경
- [ ] Service 간 통신이 Entity 기반인지 확인

### Phase 5: CommandDTO/ResultDTO 삭제
- [ ] `commandDTO` 패키지 삭제
- [ ] `resultDTO` 패키지 삭제
- [ ] 관련 import 문 정리

### Phase 6: ValidationService 처리
- [ ] `UserValidationService`: RequestDTO 기반으로 변경 또는 삭제
- [ ] `BookValidationService`: RequestDTO 기반으로 변경 또는 삭제

---

## 장점

### 1. 책임 분리
- ✅ Controller: HTTP 요청/응답 처리만 담당
- ✅ Mapper: 변환 로직만 담당
- ✅ Service: 비즈니스 로직만 담당

### 2. 코드 일관성
- ✅ 모든 변환이 Mapper를 통해 수행
- ✅ 변환 로직이 한 곳에 집중
- ✅ 유지보수 용이

### 3. 타입 안정성
- ✅ MapStruct가 컴파일 타임에 검증
- ✅ 런타임 오류 감소

### 4. 성능
- ✅ MapStruct가 생성한 코드는 최적화됨
- ✅ 리플렉션 사용 없음

### 5. 테스트 용이성
- ✅ Mapper 단위 테스트 가능
- ✅ Service는 Entity만 테스트

---

## 주의사항

### 1. 복잡한 변환 로직
- MapStruct로 표현하기 어려운 복잡한 변환은 `default` 메서드 사용
- 예: `createBookFromRequest()` 같은 복잡한 변환

### 2. 부분 업데이트
- `@MappingTarget` 사용하여 기존 Entity에 부분 업데이트
- 예: `updateUserShelfBookFromRequest()`

### 3. 컨텍스트 정보 전달
- `@Context` 파라미터 사용하여 추가 정보 전달
- 예: `loginId` 같은 컨텍스트 정보

### 4. 순환 참조
- Entity 간 순환 참조가 있는 경우 `@Mapping(target = "...", ignore = true)` 사용

---

## 결론

### 구조 변경 요약

1. **Controller**: 변환 작업 제거, Mapper만 호출
2. **Mapper**: 모든 DTO ↔ Entity 변환 담당 (MapStruct 사용)
3. **Service**: Entity만 사용 (DTO 사용 안 함)

### 변경 범위

- **높은 영향**: AuthService, AuthController (CommandDTO/ResultDTO 사용)
- **중간 영향**: BookService, BookShelfController (RequestDTO 직접 사용)
- **낮은 영향**: BookSearchController, UserController (변경 최소)

### 예상 작업량

- **Mapper 생성**: 중간 (MapStruct 인터페이스 정의)
- **Service 수정**: 높음 (모든 메서드 시그니처 변경)
- **Controller 수정**: 중간 (변환 로직 제거, Mapper 호출 추가)

---

**작성일**: 2024년
**버전**: 1.0

