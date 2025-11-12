# CommandDTO/ResultDTO 삭제 영향 분석

## 개요

현재 프로젝트에서 CommandDTO와 ResultDTO를 삭제하고 RequestDTO/ResponseDTO만 사용하는 구조로 변경할 때의 영향 범위를 분석한 문서입니다.

---

## 현재 CommandDTO/ResultDTO 사용 현황

### 1. 사용하는 곳

#### AuthService
- **CommandDTO 사용**:
  - `UserCreationCommand` - 회원가입
  - `LoginCommand` - 로그인
  - `LoginIdRetrievalCommandDTO` - 아이디 찾기
  - `AccountVerificationCommand` - 계정 확인
  - `PasswordResetCommand` - 비밀번호 재설정

- **ResultDTO 사용**:
  - `UserResult` - 사용자 정보 반환
  - `LoginIdRetrievalResult` - 아이디 찾기 결과
  - `AuthService.LoginResult` (내부 클래스) - 로그인 결과

#### AuthController
- Client RequestDTO → CommandDTO 변환 수행
- ResultDTO → Client ResponseDTO 변환 수행

#### ValidationService (사용되지 않음)
- `UserValidationService`: CommandDTO를 파라미터로 받지만 **실제로 호출되지 않음**
- `BookValidationService`: CommandDTO를 파라미터로 받지만 **실제로 호출되지 않음**

### 2. 사용하지 않는 곳

#### BookService
- ✅ Client RequestDTO를 직접 사용
- ✅ Entity를 직접 반환
- CommandDTO/ResultDTO 사용 안 함

#### BookShelfController
- ✅ Client RequestDTO를 직접 Service에 전달
- ✅ Entity를 직접 받아 ResponseDTO로 변환
- CommandDTO/ResultDTO 사용 안 함

#### BookSearchController
- ✅ Client RequestDTO를 직접 Service에 전달
- ✅ Service가 ResponseDTO를 직접 반환
- CommandDTO/ResultDTO 사용 안 함

#### UserController
- ✅ Entity를 직접 받아 ResponseDTO로 변환
- CommandDTO/ResultDTO 사용 안 함

---

## 삭제 시 영향 범위

### ✅ 삭제 가능한 항목 (문제 없음)

#### 1. BookValidationService
- **현재 상태**: CommandDTO를 파라미터로 받지만 **호출되지 않음**
- **삭제 영향**: 없음 (사용되지 않음)
- **조치**: 삭제 가능 또는 RequestDTO로 변경

#### 2. UserValidationService
- **현재 상태**: CommandDTO를 파라미터로 받지만 **호출되지 않음**
- **삭제 영향**: 없음 (사용되지 않음)
- **조치**: 삭제 가능 또는 RequestDTO로 변경

### ⚠️ 수정이 필요한 항목

#### 1. AuthService (전면 수정 필요)

**현재 구조**:
```java
public UserResult register(UserCreationCommand command) {
    User user = executeRegister(command);
    return toUserResult(user);
}
```

**변경 후 구조**:
```java
public User register(RegistrationRequest request) {
    // CommandDTO 대신 RequestDTO 직접 사용
    User user = executeRegister(request);
    return user;  // Entity 반환 (Mapper로 변환)
}
```

**수정 필요 메서드**:
- `register(UserCreationCommand)` → `register(RegistrationRequest)`
- `login(LoginCommand)` → `login(LoginRequest)`
- `findLoginIdByEmailAndName(LoginIdRetrievalCommandDTO)` → `findLoginIdByEmailAndName(LoginIdRetrievalRequest)`
- `verifyAccountForPasswordReset(AccountVerificationCommand)` → `verifyAccountForPasswordReset(AccountVerificationRequest)`
- `resetPassword(PasswordResetCommand)` → `resetPassword(PasswordResetRequest)`

**ResultDTO 제거**:
- `UserResult` 반환 → `User` Entity 반환
- `LoginIdRetrievalResult` 반환 → `User` Entity 반환
- `AuthService.LoginResult` 내부 클래스 → 별도 ResponseDTO로 변경

#### 2. AuthController (전면 수정 필요)

**현재 구조**:
```java
public ApiResponse<RegisterResponse> signup(@RequestBody RegistrationRequest request) {
    // Client RequestDTO → CommandDTO 변환
    UserCreationCommand command = new UserCreationCommand(...);
    
    // Service 호출 (CommandDTO 사용)
    UserResult userResult = authService.register(command);
    
    // ResultDTO → Client ResponseDTO 변환
    RegisterResponse response = new RegisterResponse(userResult);
    return ApiResponse.success(response);
}
```

**변경 후 구조**:
```java
public ApiResponse<RegisterResponse> signup(@RequestBody RegistrationRequest request) {
    // Service 호출 (RequestDTO 직접 사용)
    User user = authService.register(request);
    
    // Entity → ResponseDTO 변환 (Mapper 사용)
    RegisterResponse response = userMapper.toRegisterResponse(user);
    return ApiResponse.success(response);
}
```

**수정 필요 메서드**:
- `signup()` - CommandDTO 변환 제거
- `login()` - CommandDTO 변환 제거, LoginResult 처리 변경
- `findLoginId()` - CommandDTO 변환 제거
- `verifyAccount()` - CommandDTO 변환 제거
- `resetPassword()` - CommandDTO 변환 제거

#### 3. Mapper 클래스 추가 필요

**새로 생성해야 할 Mapper 클래스**:
- `UserMapper`: Entity ↔ RequestDTO/ResponseDTO 변환
  - `toEntity(RegistrationRequest)` - RequestDTO → Entity
  - `toRegisterResponse(User)` - Entity → ResponseDTO
  - `toLoginResponse(User, TokenResult)` - Entity → LoginResponseDTO
  - `toLoginIdRetrievalResponse(User)` - Entity → LoginIdRetrievalResponseDTO
  - `toPasswordResetResponse(User)` - Entity → PasswordResetResponseDTO

---

## 삭제 가능 여부 판단

### ✅ 시스템 전체적으로 실행 가능

**이유**:
1. **BookService 계열**: 이미 RequestDTO/ResponseDTO만 사용 중
2. **AuthService**: 수정하면 RequestDTO/ResponseDTO로 전환 가능
3. **ValidationService**: 사용되지 않으므로 삭제 가능
4. **의존성**: CommandDTO/ResultDTO에 의존하는 코드는 제한적

### ⚠️ 필요한 작업

#### 1. 필수 작업
- **AuthService 메서드 시그니처 변경**: CommandDTO → RequestDTO
- **AuthService 반환 타입 변경**: ResultDTO → Entity
- **AuthController 변환 로직 제거**: CommandDTO/ResultDTO 변환 제거
- **Mapper 클래스 생성**: Entity ↔ RequestDTO/ResponseDTO 변환

#### 2. 선택 작업
- **ValidationService 수정 또는 삭제**: 사용되지 않으므로 삭제 가능
- **LoginResult 내부 클래스 처리**: 별도 ResponseDTO로 분리 고려

---

## Mapper 클래스 설계 제안

### 구조 제안

```
server/
  └── mapper/
      ├── UserMapper.java
      └── BookMapper.java
```

### UserMapper 예시

```java
@Component
public class UserMapper {
    
    // RequestDTO → Entity
    public User toEntity(RegistrationRequest request) {
        return new User(
            request.getLoginId(),
            request.getEmail(),
            request.getName(),
            null  // password는 Service에서 암호화
        );
    }
    
    // Entity → ResponseDTO
    public RegisterResponse toRegisterResponse(User user) {
        return new RegisterResponse(
            user.getId(),
            user.getLoginId(),
            user.getEmail(),
            user.getName(),
            user.getRole(),
            user.getStatus()
        );
    }
    
    // Entity → LoginResponse
    public LoginResponse toLoginResponse(User user, String accessToken, String refreshToken) {
        return new LoginResponse(
            accessToken,
            refreshToken,
            toUserInfo(user)
        );
    }
    
    // Entity → LoginIdRetrievalResponse
    public LoginIdRetrievalResponse toLoginIdRetrievalResponse(User user) {
        return new LoginIdRetrievalResponse(
            user.getLoginId(),
            user.getEmail()
        );
    }
    
    // Entity → PasswordResetResponse
    public PasswordResetResponse toPasswordResetResponse(User user) {
        return new PasswordResetResponse(
            "비밀번호가 성공적으로 변경되었습니다.",
            user.getLoginId()
        );
    }
    
    // Entity → UserInfo (내부 클래스)
    private LoginResponse.UserInfo toUserInfo(User user) {
        return new LoginResponse.UserInfo(
            user.getId(),
            user.getLoginId(),
            user.getEmail(),
            user.getName(),
            user.getRole(),
            user.getStatus()
        );
    }
}
```

---

## 변경 작업 체크리스트

### Phase 1: Mapper 클래스 생성
- [ ] `UserMapper` 생성
- [ ] `BookMapper` 생성 (필요시)

### Phase 2: AuthService 수정
- [ ] `register()` 메서드: `UserCreationCommand` → `RegistrationRequest`
- [ ] `login()` 메서드: `LoginCommand` → `LoginRequest`, 반환 타입 변경
- [ ] `findLoginIdByEmailAndName()` 메서드: `LoginIdRetrievalCommandDTO` → `LoginIdRetrievalRequest`
- [ ] `verifyAccountForPasswordReset()` 메서드: `AccountVerificationCommand` → `AccountVerificationRequest`
- [ ] `resetPassword()` 메서드: `PasswordResetCommand` → `PasswordResetRequest`
- [ ] `toUserResult()` 메서드 제거 (Mapper로 대체)

### Phase 3: AuthController 수정
- [ ] `signup()`: CommandDTO 변환 제거, Mapper 사용
- [ ] `login()`: CommandDTO 변환 제거, LoginResult 처리 변경
- [ ] `findLoginId()`: CommandDTO 변환 제거, Mapper 사용
- [ ] `verifyAccount()`: CommandDTO 변환 제거
- [ ] `resetPassword()`: CommandDTO 변환 제거, Mapper 사용

### Phase 4: ValidationService 처리
- [ ] `UserValidationService`: RequestDTO로 변경 또는 삭제
- [ ] `BookValidationService`: RequestDTO로 변경 또는 삭제

### Phase 5: CommandDTO/ResultDTO 삭제
- [ ] `commandDTO` 패키지 전체 삭제
- [ ] `resultDTO` 패키지 전체 삭제
- [ ] import 문 정리

---

## 주의사항

### 1. LoginResult 내부 클래스 처리
- 현재 `AuthService.LoginResult`는 내부 클래스로 정의됨
- CommandDTO/ResultDTO 삭제 시 별도 ResponseDTO로 분리 필요
- 또는 `LoginResponse`를 직접 사용

### 2. PasswordValidator
- 현재 `PasswordValidator`는 별도 유틸리티로 존재
- RequestDTO에서 직접 검증 가능하므로 문제 없음

### 3. 검증 로직 위치
- 현재: ValidationService에서 CommandDTO 검증 (사용되지 않음)
- 변경 후: RequestDTO에 `@Valid` 어노테이션 추가 또는 Service에서 직접 검증

---

## 결론

### ✅ 삭제 가능
- 시스템 전체적으로 실행 가능
- 필요한 작업은 명확함
- Mapper 클래스 도입으로 변환 로직 일원화 가능

### ⚠️ 필요한 작업
1. **Mapper 클래스 생성** (필수)
2. **AuthService 메서드 시그니처 변경** (필수)
3. **AuthController 변환 로직 제거** (필수)
4. **ValidationService 처리** (선택)

### 📋 권장 작업 순서
1. Mapper 클래스 생성
2. AuthService 수정
3. AuthController 수정
4. ValidationService 처리
5. CommandDTO/ResultDTO 삭제

---

**작성일**: 2024년
**버전**: 1.0

