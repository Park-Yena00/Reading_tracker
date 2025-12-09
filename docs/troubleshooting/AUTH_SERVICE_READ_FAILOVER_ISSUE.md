# AuthService Read Failover 미적용 문제 해결

> **작성일**: 2025-12-09  
> **문제**: Secondary DB 중단 시 로그인 실패  
> **원인**: AuthService의 Read 작업에 Dual Read Failover 미적용  
> **상태**: ✅ 해결 완료

---

## 문제 진단

### 증상

Secondary DB를 중단한 상태에서 로그인 시도 시 로그인 실패가 발생했습니다.  
Primary DB는 정상 실행 중이었지만, 로그인이 실패했습니다.

### 현재 Read 흐름 분석

#### 1. AuthService의 문제점

`AuthService`는 `DualMasterReadService`를 사용하지 않고 있습니다:

- `userRepository.findByLoginId()` 직접 호출
- Primary DB가 다운된 경우 사용자 조회 실패
- Secondary DB로 자동 전환되지 않음

#### 2. Read 작업이 Dual Read Failover로 감싸지지 않은 메서드들

다음 메서드들이 `DualMasterReadService.readWithFailover()`를 사용하지 않습니다:

1. **`executeLogin()`** (140줄)
   - `findByLoginId()` (142줄) - 사용자 조회

2. **`executeRegister()`** (66줄)
   - `existsByLoginId()` (68줄) - 로그인 ID 중복 확인
   - `existsByEmail()` (72줄) - 이메일 중복 확인

3. **`executeFindLoginId()`** (201줄)
   - `findActiveUserByEmailAndName()` (203줄) - 아이디 찾기

4. **`executeVerifyAccount()`** (225줄)
   - `findActiveUserByLoginIdAndEmail()` (227줄) - 계정 확인

5. **`executeResetPassword()`** (264줄)
   - `findById()` (275줄) - 사용자 조회
   - `findValidToken()` (266줄) - 토큰 조회 (PasswordResetTokenRepository)

#### 3. DualMasterReadService의 동작 방식

현재 `DualMasterReadService.readWithFailover()`는 다음과 같이 동작합니다:

1. **Primary DB에서 시도**
   - Primary DB가 정상이면 Primary에서 읽기 성공
   
2. **Primary 실패 시 Secondary로 전환**
   - Primary DB가 다운되었거나 연결 실패 시
   - 자동으로 Secondary DB에서 읽기 시도
   
3. **둘 다 실패 시 예외 발생**
   - Primary와 Secondary 모두 실패한 경우에만 `DatabaseUnavailableException` 발생

이 동작 방식은 사용자 요구사항과 일치합니다:
- ✅ Primary DB가 실행 중이면 Primary에서 읽기
- ✅ Primary DB가 다운되었을 때만 Secondary에서 읽기
- ✅ 둘 다 다운되었을 때만 읽기 실패

---

## 수정 계획

### 1. AuthService에 DualMasterReadService 주입

```java
@Autowired
private DualMasterReadService dualMasterReadService;
```

### 2. 모든 Read 작업을 Dual Read Failover로 감싸기

#### 2.1 executeRegister() 메서드

```java
// 기존
if (userRepository.existsByLoginId(user.getLoginId())) {
    throw new IllegalArgumentException(ErrorCode.DUPLICATE_LOGIN_ID.getMessage());
}

if (userRepository.existsByEmail(user.getEmail())) {
    throw new IllegalArgumentException(ErrorCode.DUPLICATE_EMAIL.getMessage());
}

// 수정 후
if (dualMasterReadService.readWithFailover(() -> 
    userRepository.existsByLoginId(user.getLoginId()))) {
    throw new IllegalArgumentException(ErrorCode.DUPLICATE_LOGIN_ID.getMessage());
}

if (dualMasterReadService.readWithFailover(() -> 
    userRepository.existsByEmail(user.getEmail()))) {
    throw new IllegalArgumentException(ErrorCode.DUPLICATE_EMAIL.getMessage());
}
```

#### 2.2 executeLogin() 메서드

```java
// 기존
User user = userRepository.findByLoginId(loginId)
    .orElseThrow(() -> new IllegalArgumentException(ErrorCode.USER_NOT_FOUND.getMessage()));

// 수정 후
User user = dualMasterReadService.readWithFailover(() -> 
    userRepository.findByLoginId(loginId))
    .orElseThrow(() -> new IllegalArgumentException(ErrorCode.USER_NOT_FOUND.getMessage()));
```

#### 2.3 executeFindLoginId() 메서드

```java
// 기존
User user = userRepository.findActiveUserByEmailAndName(email, name)
    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다."));

// 수정 후
User user = dualMasterReadService.readWithFailover(() -> 
    userRepository.findActiveUserByEmailAndName(email, name))
    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다."));
```

#### 2.4 executeVerifyAccount() 메서드

```java
// 기존
User user = userRepository.findActiveUserByLoginIdAndEmail(loginId, email)
    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다."));

// 수정 후
User user = dualMasterReadService.readWithFailover(() -> 
    userRepository.findActiveUserByLoginIdAndEmail(loginId, email))
    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다."));
```

#### 2.5 executeResetPassword() 메서드

```java
// 기존
PasswordResetToken tokenEntity = passwordResetTokenRepository
    .findValidToken(resetToken, LocalDateTime.now())
    .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 토큰입니다."));

User user = userRepository.findById(userId)
    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

// 수정 후
PasswordResetToken tokenEntity = dualMasterReadService.readWithFailover(() -> 
    passwordResetTokenRepository.findValidToken(resetToken, LocalDateTime.now()))
    .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 토큰입니다."));

User user = dualMasterReadService.readWithFailover(() -> 
    userRepository.findById(userId))
    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
```

### 3. PasswordResetTokenRepository의 Read 작업 확인

`PasswordResetTokenRepository`의 `findValidToken()` 메서드도 Read 작업이므로 `DualMasterReadService.readWithFailover()`로 감싸야 합니다.

---

## 아키텍처 준수 사항

### 1. FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md

문서에 따르면:
- ✅ **모든 Read 작업**에 `DualMasterReadService.readWithFailover()` 적용 필요
- ✅ AuthService의 Read 작업도 예외 없이 적용되어야 함

### 2. DualMasterReadService 동작 원칙

- ✅ **Primary 우선**: Primary DB에서 먼저 읽기 시도
- ✅ **Secondary Failover**: Primary 실패 시 자동으로 Secondary로 전환
- ✅ **둘 다 실패 시 예외**: Primary와 Secondary 모두 실패한 경우에만 `DatabaseUnavailableException` 발생

---

## 수정 완료 체크리스트

- [x] `DualMasterReadService` 주입 추가 ✅
- [x] `executeRegister()` - `existsByLoginId()` Read Failover 적용 ✅
- [x] `executeRegister()` - `existsByEmail()` Read Failover 적용 ✅
- [x] `executeLogin()` - `findByLoginId()` Read Failover 적용 ✅
- [x] `executeFindLoginId()` - `findActiveUserByEmailAndName()` Read Failover 적용 ✅
- [x] `executeVerifyAccount()` - `findActiveUserByLoginIdAndEmail()` Read Failover 적용 ✅
- [x] `executeResetPassword()` - `findValidToken()` Read Failover 적용 ✅
- [x] `executeResetPassword()` - `findById()` Read Failover 적용 ✅
- [x] 컴파일 오류 확인 ✅
- [ ] 테스트 검증 (수동 테스트 필요)

---

## 테스트 시나리오

### 시나리오 1: Primary DB 정상, Secondary DB 중단
- **예상 결과**: Primary DB에서 읽기 성공, 로그인 정상 작동

### 시나리오 2: Primary DB 중단, Secondary DB 정상
- **예상 결과**: Secondary DB로 자동 전환, 로그인 정상 작동

### 시나리오 3: Primary DB와 Secondary DB 모두 중단
- **예상 결과**: `DatabaseUnavailableException` 발생, 로그인 실패

---

## 참고 문서

- [FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md](../fault-tolerance/FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md)
- [ARCHITECTURE.md](../architecture/ARCHITECTURE.md)

