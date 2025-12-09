# Secondary DB 실패 시 500 에러 발생 문제

> **작성일**: 2025-12-09  
> **문제**: Secondary DB 중단 시 로그인 실패 및 500 에러 발생  
> **원인**: `DatabaseWriteException`이 `GlobalExceptionHandler`에서 적절히 처리되지 않음  
> **상태**: 🔄 분석 완료, 수정 계획 수립 중

---

## 문제 진단

### 증상

Secondary DB를 중단한 상태에서 로그인 시도 시 다음 오류가 발생합니다:

**브라우저 F12 콘솔**:
```
Failed to load resource: the server responded with a status of 500 ()
[AuthHelper] 로그인 실패: Error: 서버 내부 오류가 발생했습니다
```

**서버 로그**:
```
ERROR ... DualMasterWriteService : Secondary DB 쓰기 실패, Primary에 보상 트랜잭션 실행
INFO  ... DualMasterWriteService : 보상 트랜잭션 실행 성공
com.readingtracker.server.common.exception.DatabaseWriteException: Secondary DB 쓰기 실패, Primary 보상 트랜잭션 실행됨
```

### 현재 상황 분석

#### 1. Dual Write 로직 동작 ✅

- Primary DB에 쓰기 성공 (로그인 성공 처리)
- Secondary DB에 쓰기 실패 (Secondary DB가 중단되어 있음)
- 보상 트랜잭션이 성공적으로 실행되어 Primary DB의 데이터를 롤백
- 데이터 일관성은 유지됨

#### 2. 예외 처리 문제 ❌

**현재 구현**:
- `DualMasterWriteService`에서 Secondary 실패 시 `DatabaseWriteException` 발생
- `GlobalExceptionHandler`에서 `DatabaseWriteException`을 처리하지 않음
- `RuntimeException` 핸들러로 처리되어 500 에러 반환

**문제점**:
- Secondary DB 실패는 예상 가능한 장애 상황 (Fault Tolerance 시나리오)
- 보상 트랜잭션이 성공했으므로 데이터 일관성은 유지됨
- 하지만 사용자에게는 500 에러가 발생하여 로그인이 실패한 것으로 보임
- 사용자 경험이 저하됨

#### 3. 아키텍처 문서 요구사항

`FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md`에 따르면:

> **Write 작업 (10% 사용)**:
> - **Phase 1**: Primary DB에 먼저 실행
> - **Phase 2**: 성공 시 Secondary DB에도 동일 작업 실행
> - **실패 처리**: 하나의 DB에서 실패 시 양쪽 모두 롤백
> - 사용자에게는 try-catch exception 처리로 실패 알림

**현재 구현은 아키텍처 요구사항을 준수하고 있으나**, 사용자에게 더 명확한 메시지를 제공해야 합니다.

---

## 원인 분석

### 근본 원인

1. **`GlobalExceptionHandler`에 `DatabaseWriteException` 핸들러 부재**
   - `DatabaseWriteException`이 `RuntimeException`을 상속하므로 `handleRuntimeException()`으로 처리됨
   - 500 에러와 "서버 내부 오류가 발생했습니다" 메시지 반환
   - 사용자에게는 장애 상황임을 알 수 없음

2. **Secondary DB 실패는 예상 가능한 장애 상황**
   - Fault Tolerance 아키텍처의 일부
   - 보상 트랜잭션이 성공했으므로 데이터 일관성은 유지됨
   - 하지만 사용자에게는 실패로 보임

3. **사용자 경험 저하**
   - 500 에러는 시스템 오류로 인식됨
   - "일시적 장애" 또는 "잠시 후 다시 시도해주세요" 같은 메시지가 더 적절함

---

## 수정 계획

### 1. `ErrorCode`에 새로운 에러 코드 추가

**목적**: Secondary DB 실패를 명확히 구분하기 위한 에러 코드 추가

**추가할 에러 코드**:
```java
// 데이터베이스 관련
DATABASE_WRITE_FAILED("DATABASE_WRITE_FAILED", "데이터 저장에 실패했습니다. 잠시 후 다시 시도해주세요."),
DATABASE_TEMPORARY_UNAVAILABLE("DATABASE_TEMPORARY_UNAVAILABLE", "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요."),
```

### 2. `GlobalExceptionHandler`에 `DatabaseWriteException` 핸들러 추가

**목적**: Secondary DB 실패 시 사용자에게 적절한 메시지 제공

**구현 내용**:
- `DatabaseWriteException`을 전용 핸들러로 처리
- 보상 트랜잭션이 성공한 경우: "일시적 장애" 메시지 반환 (503 Service Unavailable 또는 500)
- 보상 트랜잭션이 실패한 경우: "시스템 오류" 메시지 반환 (500 Internal Server Error)

**HTTP 상태 코드 선택**:
- **503 Service Unavailable**: 일시적 장애 상황에 적합
- **500 Internal Server Error**: 시스템 오류 상황에 적합

**권장**: 보상 트랜잭션이 성공한 경우 503, 실패한 경우 500

### 3. `DatabaseWriteException`에 보상 트랜잭션 성공 여부 정보 추가 (선택사항)

**목적**: 보상 트랜잭션 성공 여부를 예외에 포함하여 핸들러에서 구분 가능하도록 함

**구현 내용**:
- `DatabaseWriteException`에 `compensationSucceeded` 필드 추가
- `DualMasterWriteService`에서 예외 발생 시 보상 트랜잭션 성공 여부를 포함

**장점**: 핸들러에서 보상 트랜잭션 성공 여부에 따라 다른 메시지 제공 가능

**단점**: 예외 클래스 수정 필요

---

## 수정 파일 목록

1. **`분산2_프로젝트/src/main/java/com/readingtracker/server/common/constant/ErrorCode.java`**
   - `DATABASE_WRITE_FAILED` 또는 `DATABASE_TEMPORARY_UNAVAILABLE` 에러 코드 추가

2. **`분산2_프로젝트/src/main/java/com/readingtracker/server/common/exception/GlobalExceptionHandler.java`**
   - `DatabaseWriteException` 핸들러 추가
   - 보상 트랜잭션 성공 여부에 따른 적절한 메시지 및 HTTP 상태 코드 반환

3. **`분산2_프로젝트/src/main/java/com/readingtracker/server/common/exception/DatabaseWriteException.java`** (선택사항)
   - `compensationSucceeded` 필드 추가
   - 생성자 수정

4. **`분산2_프로젝트/src/main/java/com/readingtracker/server/service/write/DualMasterWriteService.java`** (선택사항)
   - `DatabaseWriteException` 발생 시 보상 트랜잭션 성공 여부를 포함하도록 수정

---

## 수정 상세 계획

### 옵션 A: 간단한 구현 (권장)

**방법**: `GlobalExceptionHandler`에 `DatabaseWriteException` 핸들러만 추가

**장점**:
- 구현 간단
- 기존 코드 수정 최소화
- 빠른 적용 가능

**단점**:
- 보상 트랜잭션 성공 여부를 구분할 수 없음
- 모든 Secondary 실패에 대해 동일한 메시지 제공

**구현 예시**:
```java
@ExceptionHandler(DatabaseWriteException.class)
public ResponseEntity<ApiResponse<Void>> handleDatabaseWriteException(DatabaseWriteException ex) {
    ErrorResponse errorResponse = new ErrorResponse();
    errorResponse.setCode(ErrorCode.DATABASE_TEMPORARY_UNAVAILABLE.getCode());
    errorResponse.setMessage(ErrorCode.DATABASE_TEMPORARY_UNAVAILABLE.getMessage());
    
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error(errorResponse));
}
```

### 옵션 B: 상세한 구현 (선택사항)

**방법**: `DatabaseWriteException`에 보상 트랜잭션 성공 여부를 포함하고, 핸들러에서 구분하여 처리

**장점**:
- 보상 트랜잭션 성공/실패에 따라 다른 메시지 제공 가능
- 사용자에게 더 정확한 정보 제공

**단점**:
- 예외 클래스 수정 필요
- 구현 복잡도 증가

**구현 예시**:
```java
// DatabaseWriteException.java
public class DatabaseWriteException extends RuntimeException {
    private final boolean compensationSucceeded;
    
    public DatabaseWriteException(String message, Throwable cause, boolean compensationSucceeded) {
        super(message, cause);
        this.compensationSucceeded = compensationSucceeded;
    }
    
    public boolean isCompensationSucceeded() {
        return compensationSucceeded;
    }
}

// GlobalExceptionHandler.java
@ExceptionHandler(DatabaseWriteException.class)
public ResponseEntity<ApiResponse<Void>> handleDatabaseWriteException(DatabaseWriteException ex) {
    ErrorResponse errorResponse = new ErrorResponse();
    
    if (ex.isCompensationSucceeded()) {
        // 보상 트랜잭션 성공: 일시적 장애
        errorResponse.setCode(ErrorCode.DATABASE_TEMPORARY_UNAVAILABLE.getCode());
        errorResponse.setMessage(ErrorCode.DATABASE_TEMPORARY_UNAVAILABLE.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(errorResponse));
    } else {
        // 보상 트랜잭션 실패: 시스템 오류
        errorResponse.setCode(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
        errorResponse.setMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(errorResponse));
    }
}
```

---

## 권장 해결 방안

### 최종 권장: 옵션 A (간단한 구현)

**이유**:
1. **아키텍처 문서 준수**: Secondary DB 실패는 "사용자에게는 try-catch exception 처리로 실패 알림"으로 명시되어 있음
2. **구현 간단성**: 기존 코드 수정 최소화
3. **사용자 경험**: "일시적 장애" 메시지로 충분히 명확함
4. **복잡도 감소**: 보상 트랜잭션 성공 여부를 구분할 필요 없음 (보상 트랜잭션 실패는 Recovery Queue로 처리됨)

**구현 내용**:
1. `ErrorCode`에 `DATABASE_TEMPORARY_UNAVAILABLE` 추가
2. `GlobalExceptionHandler`에 `DatabaseWriteException` 핸들러 추가
3. 503 Service Unavailable 상태 코드 반환
4. "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요." 메시지 제공

---

## 참고 문서

- [FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md](../fault-tolerance/FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md)
- [DUAL_WRITE_IMPLEMENTATION_ISSUES.md](../fault-tolerance/DUAL_WRITE_IMPLEMENTATION_ISSUES.md)
- [ARCHITECTURE.md](../architecture/ARCHITECTURE.md)

