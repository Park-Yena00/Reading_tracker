# 외부 API 통합 및 데이터 무결성 해결 방안 분석

## 질문

**MapStruct 기반 구조로 수정한 이후, 외부 API를 사용해서 데이터 무결성 문제를 해결할 수 있도록 코드 수정이 유연하게 가능한가?**

---

## 데이터 무결성 문제 정의

### 문제 상황

**시나리오**: 한 사용자가 동시에 웹과 앱으로 로그인하여 특정 데이터를 수정

#### 1. Lost Update (업데이트 손실) 문제
```
시간 순서:
1. 웹: 데이터 조회 (version=1, readingProgress=50)
2. 앱: 데이터 조회 (version=1, readingProgress=50)
3. 앱: 데이터 수정 (readingProgress=80) → DB 저장 완료 (version=2)
4. 웹: 이전 데이터 기반으로 수정 (readingProgress=60) → DB 저장 (version=2 덮어쓰기)
5. 결과: 앱의 수정사항(readingProgress=80)이 손실됨
```

#### 2. Dirty Read (더티 리드) 문제
```
시간 순서:
1. 앱: 데이터 수정 시작 (트랜잭션 시작)
2. 웹: 데이터 조회 → 아직 커밋되지 않은 데이터 읽음
3. 앱: 트랜잭션 롤백
4. 결과: 웹이 잘못된 데이터를 기반으로 작업 수행
```

---

## 현재 아키텍처 분석

### 현재 구조 (MapStruct 적용 후 예상)

```
┌─────────────────────────────────────┐
│  Controller (Client ↔ Server 경계)  │
│  - RequestDTO 받음                   │
│  - ResponseDTO 반환                  │
└─────────────────────────────────────┘
           ↓ RequestDTO ↑ ResponseDTO
┌─────────────────────────────────────┐
│  Mapper (Controller ↔ Service 경계) │
│  - RequestDTO → Entity               │
│  - Entity → ResponseDTO              │
└─────────────────────────────────────┘
           ↓ Entity ↑ Entity
┌─────────────────────────────────────┐
│  Service (서버 내부)                  │
│  - Entity만 사용                     │
│  - @Transactional                    │
│  - 비즈니스 로직 처리                │
└─────────────────────────────────────┘
           ↓ Entity ↑ Entity
┌─────────────────────────────────────┐
│  Repository (Server ↔ DBMS 경계)     │
│  - JPA EntityManager                 │
│  - DB 작업                           │
└─────────────────────────────────────┘
```

### 현재 트랜잭션 관리

```java
@Service
@Transactional  // 클래스 레벨 트랜잭션
public class BookService {
    
    @Transactional(readOnly = true)  // 읽기 전용
    public List<UserShelfBook> getMyShelf(...) { ... }
    
    public UserShelfBook updateBookDetail(...) {
        // 쓰기 트랜잭션
        // ...
    }
}
```

---

## 외부 API 통합 가능성 분석

### ✅ **예, 외부 API 통합이 유연하게 가능합니다.**

### 이유

#### 1. Service 계층의 독립성
- **Service는 Entity만 사용**: DTO와 분리되어 있음
- **비즈니스 로직 집중**: 외부 API 호출 로직 추가 용이
- **의존성 주입**: 외부 API 클라이언트를 Service에 주입 가능

#### 2. 계층 분리의 장점
- **Controller 변경 불필요**: 외부 API 통합 시 Controller는 영향 없음
- **Mapper 변경 불필요**: DTO-Entity 변환 로직은 그대로 유지
- **Service만 수정**: 외부 API 통합 로직을 Service에 추가

#### 3. 확장 가능한 구조
- **인터페이스 기반 설계**: 외부 API를 인터페이스로 추상화 가능
- **AOP 적용 가능**: 트랜잭션, 로깅, 외부 API 호출 등을 AOP로 처리 가능
- **이벤트 기반 통합**: Spring Events를 통한 비동기 통합 가능

---

## 외부 API 통합 포인트

### 옵션 1: Service 계층에서 직접 호출 (권장)

#### 구조
```
Controller → Mapper → Service → [외부 API] → Repository → DB
```

#### 예시 코드 구조
```java
@Service
@Transactional
public class BookService {
    
    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private ExternalApiClient externalApiClient;  // ✅ 외부 API 클라이언트
    
    public UserShelfBook updateBookDetail(String loginId, Long userBookId, 
                                         BookDetailUpdateRequest request) {
        // 1. Entity 조회
        UserShelfBook userBook = userBookRepository.findById(userBookId)
            .orElseThrow(...);
        
        // 2. 외부 API 호출 (데이터 무결성 검증/락 획득)
        LockResult lockResult = externalApiClient.acquireLock(
            userBookId, 
            userBook.getVersion()  // Optimistic Lock 버전
        );
        
        if (!lockResult.isSuccess()) {
            throw new ConcurrentModificationException("다른 클라이언트에서 수정 중입니다.");
        }
        
        try {
            // 3. 데이터 수정
            updateFields(userBook, request);
            
            // 4. DB 저장
            UserShelfBook saved = userBookRepository.save(userBook);
            
            // 5. 외부 API에 변경사항 알림
            externalApiClient.notifyUpdate(saved.getId(), saved.getVersion());
            
            return saved;
        } finally {
            // 6. 락 해제
            externalApiClient.releaseLock(lockResult.getLockId());
        }
    }
}
```

#### 장점
- **명확한 흐름**: Service에서 모든 비즈니스 로직 관리
- **트랜잭션 통합**: 외부 API 호출을 트랜잭션 내에서 관리 가능
- **에러 처리 용이**: Service에서 통합 에러 처리

#### 단점
- **동기 호출**: 외부 API 응답 대기 필요
- **트랜잭션 시간 증가**: 외부 API 호출 시간만큼 트랜잭션 유지

---

### 옵션 2: AOP를 통한 인터셉터 방식

#### 구조
```
Controller → Mapper → Service → [AOP Interceptor] → [외부 API] → Repository → DB
```

#### 예시 코드 구조
```java
// AOP Aspect
@Aspect
@Component
public class DataIntegrityAspect {
    
    @Autowired
    private ExternalApiClient externalApiClient;
    
    @Around("@annotation(RequiresLock)")
    public Object handleLock(ProceedingJoinPoint joinPoint) throws Throwable {
        // 메서드 파라미터에서 Entity 추출
        Object[] args = joinPoint.getArgs();
        UserShelfBook userBook = extractUserBook(args);
        
        // 외부 API로 락 획득
        LockResult lockResult = externalApiClient.acquireLock(
            userBook.getId(), 
            userBook.getVersion()
        );
        
        try {
            // 원래 메서드 실행
            return joinPoint.proceed();
        } finally {
            // 락 해제
            externalApiClient.releaseLock(lockResult.getLockId());
        }
    }
}

// Service
@Service
@Transactional
public class BookService {
    
    @RequiresLock  // ✅ AOP 어노테이션
    public UserShelfBook updateBookDetail(...) {
        // 비즈니스 로직만 집중
        // 외부 API 호출은 AOP가 처리
    }
}
```

#### 장점
- **관심사 분리**: 비즈니스 로직과 외부 API 호출 분리
- **재사용성**: 여러 Service 메서드에 적용 가능
- **유지보수성**: 외부 API 변경 시 AOP만 수정

#### 단점
- **복잡도 증가**: AOP 설정 및 디버깅 어려움
- **파라미터 추출 복잡**: 메서드 파라미터에서 Entity 추출 필요

---

### 옵션 3: 이벤트 기반 비동기 통합

#### 구조
```
Controller → Mapper → Service → Repository → DB
                                    ↓
                            [Event Publisher]
                                    ↓
                            [외부 API Listener]
```

#### 예시 코드 구조
```java
// Service
@Service
@Transactional
public class BookService {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    public UserShelfBook updateBookDetail(...) {
        // 1. 데이터 수정 및 저장
        UserShelfBook saved = userBookRepository.save(userBook);
        
        // 2. 이벤트 발행 (비동기)
        eventPublisher.publishEvent(
            new BookUpdatedEvent(saved.getId(), saved.getVersion())
        );
        
        return saved;
    }
}

// 이벤트 리스너
@Component
public class ExternalApiEventListener {
    
    @Autowired
    private ExternalApiClient externalApiClient;
    
    @Async
    @EventListener
    public void handleBookUpdated(BookUpdatedEvent event) {
        // 외부 API에 변경사항 알림 (비동기)
        externalApiClient.notifyUpdate(event.getBookId(), event.getVersion());
    }
}
```

#### 장점
- **비동기 처리**: 외부 API 호출이 트랜잭션에 영향 없음
- **느슨한 결합**: Service와 외부 API 완전 분리
- **확장성**: 여러 리스너 추가 가능

#### 단점
- **일관성 보장 어려움**: 비동기이므로 즉시 반영 보장 불가
- **에러 처리 복잡**: 비동기 에러 처리 필요

---

## 데이터 무결성 해결 방안

### 1. Optimistic Locking (낙관적 잠금)

#### Entity에 버전 필드 추가
```java
@Entity
public class UserShelfBook {
    
    @Id
    private Long id;
    
    @Version  // ✅ Optimistic Lock 버전 필드
    private Long version;
    
    // ... 기타 필드
}
```

#### Service에서 버전 체크
```java
@Service
@Transactional
public class BookService {
    
    @Autowired
    private ExternalApiClient externalApiClient;
    
    public UserShelfBook updateBookDetail(String loginId, Long userBookId, 
                                         BookDetailUpdateRequest request) {
        // 1. Entity 조회 (버전 포함)
        UserShelfBook userBook = userBookRepository.findById(userBookId)
            .orElseThrow(...);
        
        Long currentVersion = userBook.getVersion();
        
        // 2. 외부 API로 최신 버전 확인
        Long latestVersion = externalApiClient.getLatestVersion(userBookId);
        
        if (!currentVersion.equals(latestVersion)) {
            // 최신 데이터 다시 조회
            userBook = userBookRepository.findById(userBookId)
                .orElseThrow(...);
        }
        
        // 3. 데이터 수정
        updateFields(userBook, request);
        
        // 4. 저장 시 버전 체크 (JPA가 자동 처리)
        return userBookRepository.save(userBook);
    }
}
```

---

### 2. Pessimistic Locking (비관적 잠금)

#### 외부 API를 통한 락 관리
```java
@Service
@Transactional
public class BookService {
    
    @Autowired
    private ExternalApiClient externalApiClient;
    
    public UserShelfBook updateBookDetail(...) {
        // 1. 외부 API로 락 획득
        LockResult lockResult = externalApiClient.acquireLock(userBookId);
        
        if (!lockResult.isSuccess()) {
            throw new LockAcquisitionException("다른 클라이언트에서 수정 중입니다.");
        }
        
        try {
            // 2. Entity 조회 (락이 걸린 상태)
            UserShelfBook userBook = userBookRepository.findById(userBookId)
                .orElseThrow(...);
            
            // 3. 데이터 수정 및 저장
            updateFields(userBook, request);
            return userBookRepository.save(userBook);
            
        } finally {
            // 4. 락 해제
            externalApiClient.releaseLock(lockResult.getLockId());
        }
    }
}
```

---

### 3. 외부 API를 통한 변경사항 알림

#### 실시간 동기화
```java
@Service
@Transactional
public class BookService {
    
    @Autowired
    private ExternalApiClient externalApiClient;
    
    public UserShelfBook updateBookDetail(...) {
        // 1. 데이터 수정 및 저장
        UserShelfBook saved = userBookRepository.save(userBook);
        
        // 2. 외부 API에 변경사항 알림
        externalApiClient.notifyUpdate(
            saved.getId(),
            saved.getVersion(),
            saved.getUpdatedAt()
        );
        
        return saved;
    }
    
    @Transactional(readOnly = true)
    public UserShelfBook getBookDetail(Long userBookId) {
        // 1. 외부 API로 최신 변경사항 확인
        UpdateInfo latestUpdate = externalApiClient.getLatestUpdate(userBookId);
        
        // 2. 로컬 데이터와 비교
        UserShelfBook localBook = userBookRepository.findById(userBookId)
            .orElseThrow(...);
        
        if (latestUpdate.getVersion() > localBook.getVersion()) {
            // 최신 데이터 다시 조회 또는 갱신 필요 알림
            throw new StaleDataException("데이터가 최신화되었습니다. 다시 조회해주세요.");
        }
        
        return localBook;
    }
}
```

---

## MapStruct 구조와의 호환성

### ✅ MapStruct 구조는 외부 API 통합에 유리합니다.

#### 이유

1. **Service 계층의 순수성**
   - Service는 Entity만 사용하므로 외부 API 통합이 명확함
   - DTO 변환 로직과 분리되어 있어 외부 API 호출 추가 용이

2. **Controller 변경 불필요**
   - 외부 API 통합 시 Controller는 영향 없음
   - Mapper도 변경 불필요

3. **테스트 용이성**
   - 외부 API를 Mock으로 대체하여 테스트 가능
   - Service 계층만 테스트하면 됨

---

## 권장 통합 방식

### 단계별 접근

#### 1단계: 기본 통합 (Service 계층 직접 호출)
```java
@Service
@Transactional
public class BookService {
    
    @Autowired
    private ExternalApiClient externalApiClient;
    
    public UserShelfBook updateBookDetail(...) {
        // 외부 API 호출
        // 비즈니스 로직
        // DB 저장
    }
}
```

#### 2단계: 고급 통합 (AOP 또는 이벤트)
- 필요에 따라 AOP 또는 이벤트 기반으로 확장
- 기존 코드 변경 최소화

---

## 결론

### ✅ **외부 API 통합이 유연하게 가능합니다.**

**이유**:
1. **Service 계층의 독립성**: Entity만 사용하므로 외부 API 통합 포인트 명확
2. **계층 분리**: Controller와 Mapper는 영향 없음
3. **확장 가능한 구조**: AOP, 이벤트 등 다양한 방식 적용 가능
4. **테스트 용이성**: Mock을 통한 테스트 가능

**권장 방식**:
- **초기**: Service 계층에서 직접 호출
- **확장**: 필요 시 AOP 또는 이벤트 기반으로 확장

**데이터 무결성 해결**:
- **Optimistic Locking**: Entity에 `@Version` 추가
- **외부 API 락 관리**: 외부 API를 통한 락 획득/해제
- **변경사항 알림**: 외부 API를 통한 실시간 동기화

---

**작성일**: 2024년
**버전**: 1.0

