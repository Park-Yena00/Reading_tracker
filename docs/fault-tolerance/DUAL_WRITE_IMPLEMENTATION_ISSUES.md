# MySQL 이중화 구현 시 기존 기능 영향 분석

> **목적**: MySQL 이중화(Custom Dual Write 및 Read Failover) 구현 시 기존 기능에 미치는 영향 분석 및 해결 방안 제시  
> **범위**: 기존 Service 메서드, Repository 패턴, 트랜잭션 관리, 성능, 비즈니스 로직  
> **최종 업데이트**: 2024년

---

## 📋 목차

1. [개요](#개요)
2. [주요 문제점](#주요-문제점)
3. [상세 분석](#상세-분석)
4. [권장 해결 방안](#권장-해결-방안)
5. [결론](#결론)

---

## 개요

본 문서는 [FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md](./FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md)에 따라 MySQL 이중화 로직을 구현할 때, 기존 기능에 미치는 영향을 분석하고 해결 방안을 제시합니다.

### 분석 범위

- 기존 Service 메서드 구조
- Repository 패턴 (JPA Repository)
- 트랜잭션 관리 방식 (`@Transactional`)
- 성능 영향
- 복잡한 비즈니스 로직
- 부수 효과 (Redis 캐시 등)

### 분석 결과 요약

**⚠️ 부정적 영향이 있습니다**

주요 원인:
1. **JPA Repository vs JdbcTemplate 불일치** (심각)
2. **@Transactional 어노테이션 충돌** (심각)
3. **성능 저하 가능성** (중간)
4. **복잡한 비즈니스 로직과의 통합 어려움** (중간)
5. **Redis 캐시 무효화 타이밍 문제** (낮음)
6. **트랜잭션 격리 수준** (낮음)

---

## 주요 문제점

### 문제점 요약

| 문제점 | 심각도 | 영향 범위 | 해결 난이도 |
|--------|--------|-----------|-------------|
| JPA Repository vs JdbcTemplate 불일치 | 🔴 심각 | 모든 Write/Read 메서드 | 높음 |
| @Transactional 어노테이션 충돌 | 🔴 심각 | 모든 Service 클래스 | 높음 |
| 성능 저하 | 🟡 중간 | 모든 Write 작업 | 중간 |
| 복잡한 비즈니스 로직 통합 | 🟡 중간 | 일부 복잡한 메서드 | 중간 |
| Redis 캐시 무효화 타이밍 | 🟢 낮음 | BookService | 낮음 |
| 트랜잭션 격리 수준 | 🟢 낮음 | 동시성 제어 | 낮음 |

---

## 상세 분석

### 1. JPA Repository vs JdbcTemplate 불일치 🔴 **심각**

#### 문제 설명

**로드맵 설계**:
- `DualMasterWriteService.writeWithDualWrite()`: `Function<JdbcTemplate, T>` 파라미터 사용
- `DualMasterReadService.readWithFailover()`: `Function<JdbcTemplate, T>` 파라미터 사용

**현재 코드 현황**:
- 모든 Service가 **JPA Repository** 사용
  - `memoRepository.save()`
  - `memoRepository.findById()`
  - `userShelfBookRepository.save()`
  - 등등...

**기술적 불일치**:
- JPA Repository는 내부적으로 `EntityManager`를 사용
- `EntityManager`는 **하나의 DataSource**에만 연결됨
- Dual Write를 위해서는 Primary/Secondary 각각의 독립적인 DataSource 필요

#### 영향

1. **기존 코드 대폭 수정 필요**
   - 모든 Service 메서드의 Repository 호출을 JdbcTemplate + SQL로 변경
   - 비즈니스 로직 대폭 수정 필요

2. **개발 생산성 저하**
   - JPA의 자동 쿼리 생성 기능 사용 불가
   - 관계 매핑(@OneToMany, @ManyToOne 등) 자동 처리 불가
   - 수동 SQL 작성 필요

3. **코드 복잡도 증가**
   - SQL 쿼리 직접 작성
   - ResultSet 매핑 수동 처리
   - 오류 가능성 증가

#### 근본 원인 분석

이 문제는 **ORM(JPA)의 트랜잭션 관리 철학과 Dual Write의 아키텍처 요구사항 간의 근본적인 충돌**에서 비롯됩니다.

**JPA/Hibernate의 기본 동작 방식**:
- JPA/Hibernate는 기본적으로 단일 `EntityManager` 인스턴스와 이와 연결된 **단일 DataSource**를 사용
- 이는 단일 트랜잭션 범위 내에서 작동
- `DualMasterDataSourceConfig.java` 파일에서 Primary와 Secondary를 분리하여 `EntityManagerFactory`를 설정한 것은 잘 하셨으나, `MemoService` 같은 비즈니스 로직에서 `@Autowired`로 주입받는 `memoRepository`는 기본적으로 `@Primary` 설정된 Primary DB에만 연결됩니다

**Dual Write의 요구사항**:
- Dual Write 로직을 구현하려면 단일 함수(`writeWithDualWrite()`) 내에서 두 개의 독립적인 데이터 소스에 접근해야 함
- JPA의 단일 트랜잭션 관리 모델이 이를 직접적으로 지원하지 않음
- 따라서 문제 1번의 문제가 심각한 로직 불일치를 유발합니다

#### 해결 방안: 옵션 A와 옵션 C의 결합 (최종 선택)

세 가지 옵션 중, **"옵션 C: 하이브리드 접근"**을 기반으로 하되, 비즈니스 로직의 편의성(JPA의 장점)을 최대한 살리는 방향으로 **"옵션 A: JPA Repository 분리"**를 부분적으로 적용합니다.

**전략 요약**:

1. **Write (Dual Write) 영역**: 하이브리드 (JPA + JdbcTemplate) 접근
   - `DualMasterWriteService`에서 Primary에는 JPA Repository를 사용
   - Secondary에는 JdbcTemplate을 사용
   - 복구 트랜잭션 실행 등 복잡한 Dual Write 로직은 Native SQL을 실행할 수 있는 JdbcTemplate이 가장 유연하고 제어가 용이합니다

2. **Read (Failover) 영역**: JPA Repository 유지 (옵션 A 확장)
   - `DualMasterReadService` 내에서 Primary용 Repository와 Secondary용 Repository를 모두 주입받아 사용
   - 조회(Read)는 비즈니스 로직의 복잡성이 낮고, JPA의 객체 매핑(Entity Mapping) 이점을 포기할 이유가 적기 때문입니다

---

### Write 로직 구현 상세

`writeWithDualWrite()` 함수 구현의 난이도를 낮추고 JPA와의 충돌을 피하기 위해, 다음과 같은 세부 전략을 제안합니다.

#### 1. Secondary Write에 대한 JdbcTemplate 전환

**전략**:
- Primary DB에 대한 쓰기(핵심 트랜잭션)는 JPA Repository를 사용하여 ORM의 이점을 유지
- Secondary DB에 대한 쓰기는 JdbcTemplate을 사용하도록 변경

**JdbcTemplate 설정**:
- `DualMasterDataSourceConfig.java`에서 Secondary DB를 위한 별도의 `JdbcTemplate` Bean을 생성하고 이를 `DualMasterWriteService`에 주입
- 이때 JdbcTemplate을 사용하는 Secondary DB에서의 Write 작업은 `NamedParameterJdbcTemplate`와 `BeanPropertySqlParameterSource`를 사용합니다

**⚠️ 중요: NamedParameterJdbcTemplate 필수 사용**

기본 `JdbcTemplate`을 사용할 경우, SQL 쿼리는 `?` (물음표)를 파라미터 Placeholder로 사용합니다.

```sql
-- SQL문 예시
INSERT INTO memo (id, title, content) VALUES (?, ?, ?);
```

이 SQL문 예시처럼 파라미터를 배열로 넘기면, 배열의 순서가 SQL의 `?` 순서와 1:1로 매칭됩니다. 이는 컴파일 에러나 런타임 예외가 아닌, **데이터베이스의 오염(Silent Data Corruption)**으로 이어지기 때문에 디버깅이 매우 어렵습니다. 이 문제가 Dual Write와 Recovery 로직에 발생하면 시스템의 일관성이 심각하게 훼손됩니다.

**해결책: NamedParameterJdbcTemplate 사용**

`NamedParameterJdbcTemplate`는 `?` 대신 `:파라미터이름` 형식의 Placeholder를 사용합니다. 파라미터는 `Map` 또는 `SqlParameterSource` 객체를 통해 이름으로 전달되므로, 순서에 전혀 의존하지 않습니다.

**BeanPropertySqlParameterSource 활용**

`BeanPropertySqlParameterSource`의 역할:
- 엔티티(예: `Memo` 객체)를 넘기면, 이 객체의 getter 메서드를 자동으로 호출하여 파라미터 Map을 생성
- 개발자는 수많은 필드를 일일이 `param.addValue("title", memo.getTitle())`처럼 매핑할 필요가 없음
- 파라미터 누락이나 오타 실수를 줄일 수 있음

**구현 요구사항**:
- `DualMasterDataSourceConfig.java`에 Secondary DB 전용 `NamedParameterJdbcTemplate` Bean 추가
- Dual Write의 핵심인 Secondary DB 쓰기 로직과 데이터 복구 로직은 반드시 이 조합(`NamedParameterJdbcTemplate` + `BeanPropertySqlParameterSource`)을 사용하여 구현

**설정 예시**:
```java
@Configuration
public class DualMasterDataSourceConfig {
    
    // ... Primary/Secondary DataSource 설정 ...
    
    @Bean
    public NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate() {
        return new NamedParameterJdbcTemplate(secondaryDataSource());
    }
}
```

#### 2. PlatformTransactionManager Bean 설정 (필수)

**필수성**:

Primary DB는 JPA를, Secondary DB는 JDBC Template을 사용하고 있는 지금 구조에서, 각 DB를 위해 두 개의 독립적인 `PlatformTransactionManager` Bean 설정이 필수적으로 필요합니다.

**필수적인 이유**:

1. **다른 데이터베이스 (Isolation)**
   - 두 DB가 논리적으로 독립적인 트랜잭션을 가져야 합니다
   - Spring은 주입된 `PlatformTransactionManager`를 통해 해당 DB의 트랜잭션을 제어합니다

2. **다른 기술 스택 (Type)**
   - JPA와 JDBC는 트랜잭션을 관리하는 방식 자체가 다르기 때문에, 각 기술에 맞는 전용 트랜잭션 매니저가 필요합니다

3. **수동 제어 (Dual Write Logic)**
   - `DualMasterWriteService`는 두 개의 독립적인 트랜잭션 매니저(`primaryTransactionManager`, `secondaryTransactionManager`)를 주입받아, 각각에 대한 `TransactionTemplate`을 생성하고 이를 통해 Primary DB 커밋 후 Secondary DB 커밋을 시도하는 수동적인 Dual Write 로직을 구현하게 됩니다

**구현 요구사항**:
- `DualMasterDataSourceConfig.java`에 Primary DB용 `PlatformTransactionManager` Bean 추가
- `DualMasterDataSourceConfig.java`에 Secondary DB용 `PlatformTransactionManager` Bean 추가
- 각각 `@Qualifier` 어노테이션을 사용하여 명확히 구분
- `DualMasterWriteService`에서 두 트랜잭션 매니저를 주입받아 사용

**설정 예시**:
```java
@Configuration
public class DualMasterDataSourceConfig {
    
    // ... Primary/Secondary DataSource 설정 ...
    
    @Bean
    @Primary
    public PlatformTransactionManager primaryTransactionManager() {
        return new DataSourceTransactionManager(primaryDataSource());
    }
    
    @Bean
    public PlatformTransactionManager secondaryTransactionManager() {
        return new DataSourceTransactionManager(secondaryDataSource());
    }
}
```

**DualMasterWriteService에서의 사용 예시**:
```java
@Service
public class DualMasterWriteService {
    
    @Autowired
    @Qualifier("primaryTransactionManager")
    private PlatformTransactionManager primaryTxManager;
    
    @Autowired
    @Qualifier("secondaryTransactionManager")
    private PlatformTransactionManager secondaryTxManager;
    
    public <T> T writeWithDualWrite(...) {
        // Primary 트랜잭션
        TransactionTemplate primaryTx = new TransactionTemplate(primaryTxManager);
        T primaryResult = primaryTx.execute(status -> {
            // Primary DB 쓰기 (JPA Repository 사용)
        });
        
        // Secondary 트랜잭션
        TransactionTemplate secondaryTx = new TransactionTemplate(secondaryTxManager);
        try {
            secondaryTx.execute(status -> {
                // Secondary DB 쓰기 (JdbcTemplate 사용)
            });
        } catch (Exception e) {
            // 보상 트랜잭션 실행
        }
        
        return primaryResult;
    }
}
```

#### 3. 보상 트랜잭션(Compensation)의 정확한 구현

**문제 상황**:
- Dual Write가 실패했을 때 실행되는 보상 트랜잭션은 Primary DB의 변경 사항을 롤백해야 함
- MySQL 이중화 구조에 따르면 Primary DB (JPA) 트랜잭션과 Secondary DB (JdbcTemplate) 트랜잭션을 분리해서 관리
- Primary 쓰기 성공 후 Secondary 쓰기가 실패하면, Primary DB의 데이터는 이미 커밋되었을 가능성이 높음

**해결 전략: @Transactional 활용 및 Primary 트랜잭션의 활용**

Secondary 쓰기까지 전체를 하나의 `@Transactional` 경계로 묶고, Secondary 쓰기가 실패하면 Primary 쓰기까지 롤백되도록 설계합니다. 이를 위해 Primary 트랜잭션을 활용해야 합니다.

1. **@Transactional을 writeWithDualWrite 메서드에 적용**
   - `@Transactional`을 `writeWithDualWrite` 메서드에 적용하여 JPA의 트랜잭션 관리 시스템이 Primary DB의 작업을 관리하게 합니다
   - Secondary DB 작업은 트랜잭션 매니저를 타지 않기 때문에, try-catch 블록 내에서 명시적으로 처리해야 합니다

2. **예외 처리 전략**
   - Secondary DB에 쓰기가 실패했을 때 try-catch 구문에서 예외를 던지면 이 예외를 **@Transactional**이 감지하여, Primary DB에 대해 강제 롤백을 지시합니다
   - 한편 Primary DB에 쓰기 작업이 실패한 경우는 Primary DB의 트랜잭션이 시작조차 되지 않거나 실패하면, Service 메서드가 아예 Secondary DB 쓰기 단계로 진입하지 못하고 즉시 예외가 던져져 사용자에게 실패를 알립니다
   - 즉, 발생한 예외는 Service 계층을 빠져나가 Controller 계층의 catch 블록에서 포착됩니다
   - 그리고 Controller는 사용자에게 "저장에 실패했습니다. 다시 시도해주세요." 알림을 반환합니다

3. **Controller 계층에서의 최종 예외 처리**
   - 방금 설명한 저장 실패 알림에 대해서, 최종적인 try-catch 예외 처리는 **Controller 계층**에서 이뤄져야 합니다
   - Service (비즈니스 로직) 계층에서는 절대로 try-catch로 예외를 잡아먹으면 안 됩니다
   - Secondary 쓰기가 실패했을 때 `RuntimeException`을 다시 던져야만 (re-throw), Spring의 `@Transactional` 기능이 이를 감지하고 Primary DB의 쓰기를 자동으로 롤백할 수 있습니다
   - 따라서 클라이언트(사용자)와 직접 통신하는 Controller 계층에서 try-catch 예외 처리를 해야 합니다
   - Service 계층에서 올라온 예외를 여기서 최종적으로 잡아 사용자에게 친화적인 메시지(HTTP Status Code, 에러 메시지)로 변환해야 합니다

**예외 시나리오**:
- **Primary DB에서부터 쓰기가 실패한 경우**: 예외를 던져 Controller를 통해 사용자에게 저장 실패 알림을 반환
- **Primary DB에서는 쓰기가 성공했지만 Secondary DB에서는 쓰기가 실패한 경우**: 예외를 던져 Controller를 통해 사용자에게 저장 실패 알림을 반환

#### 4. 비상 복구 로깅 메커니즘 (Emergency Recovery Logging)

**극히 예외적인 상황**:
- Primary DB에서는 쓰기가 성공했지만 Secondary DB에서는 쓰기가 실패한 경우
- Primary DB의 트랜잭션 롤백이 실패하는 경우가 있음
- 트랜잭션 관리자가 롤백을 요청했으나 DB 시스템의 비정상적인 상태로 인해 롤백이 실패하고 Primary DB에 데이터가 커밋되는 **'극히 예외적인 경우'**

**복구 액션**:
- 일단 사용자에게 저장 실패 알림을 반환
- 시스템 관리자가 수동으로 Primary DB 삭제 (Delete)를 수행
  - 사용자에게 전달된 '실패' 응답과 시스템 상태를 일치시키기 위함
  - 사용자가 재시도할 경우 발생할 수 있는 데이터 중복 저장(Duplicate) 문제를 원천적으로 방지하기 위함

이때 시스템 관리자가 수동으로 Primary DB에서 해당 데이터를 삭제하기 위해서는 해당 데이터가 '무엇'인지 알아야할 필요가 있습니다. Primary DB 롤백 실패 시, 관리자가 삭제해야 할 '잔여 데이터'를 정확히 식별하기 위해서는 비상 복구 로깅(Emergency Recovery Logging) 메커니즘이 필수적입니다. 이 로그는 Primary DB 커밋과 Secondary DB 쓰기 실패 사이에 발생하는 최후의 방어선입니다.

**비상 복구 로깅 메커니즘의 목적**:
- Primary DB에 커밋된 데이터가 Secondary DB 불일치로 인해 롤백되어야 함을 증명하는 기록
- Primary/Secondary DB와 독립적이며, 고가용성을 갖춘 저장소로, 현재 사용중인 MySQL DB에 새로운 테이블을 만들어서 사용하겠습니다

**로그 레코드 생성 시점**:
- Primary DB 커밋 완료 직후
- Secondary DB 쓰기 요청 직전 또는 실패 감지 직후
- 최대한 Secondary 실패가 확정되기 전에 기록하여 기록 실패 가능성을 최소화

**로그 레코드 필수 컬럼**:

관리자가 삭제 대상을 명확히 식별하고 복구 액션을 취할 수 있도록, 로그 레코드에는 다음의 핵심 식별자가 반드시 포함되어야 합니다.

1. **`primary_record_id` (필수)**
   - 이 식별자는 Primary DB에 성공적으로 커밋된 레코드의 고유 ID (PK)로, 관리자가 Primary DB에서 삭제할 대상을 조회하기 위한 직접적인 키입니다

2. **`transaction_attempt_id`**
   - 전체 분산 트랜잭션 시도의 고유 ID (UUID)
   - 이 컬럼은 중복 복구 방지 및 로그 흐름 추적하는 용도입니다

3. **`failure_timestamp`**
   - Secondary DB 쓰기가 실패로 확인된 정확한 시점에 대한 컬럼입니다
   - 잔여 데이터의 발생 시점을 확인하고, 복구 우선순위를 결정하는 데 사용합니다

4. **`target_table_name`**
   - Primary DB에서 해당 레코드가 저장된 테이블명
   - 이 컬럼은 복구 스크립트 실행 시 테이블 경로를 지정합니다

5. **`status`**
   - `PENDING_DELETE` (초기 상태), `DELETED_BY_ADMIN` (복구 완료 후)를 저장합니다
   - 이 컬럼은 복구 작업의 현재 진행 상태를 관리합니다

**관리자 수동 복구 프로세스**:

이 비상 복구 로깅 메커니즘에 따라 관리자는 주기적으로 비상 복구 로그 저장소를 모니터링하거나, 시스템 장애 알림에 따라 다음 단계를 수동으로 수행합니다.

**단계 A: 잔여 데이터 식별 (Identification)**

A단계에서 먼저 관리자는 복구 로그 저장소에서 `status = PENDING_DELETE`인 모든 로그 레코드를 조회합니다. 이후 각 레코드에서 식별자인 `primary_record_id`와 `target_table_name`을 추출합니다.

**단계 B: Primary DB 데이터 확인 (Verification)**

A 단계를 거친 관리자는 단계B에 따라 다음 작업을 수행합니다. 추출된 식별자를 사용하여 Primary DB에서 해당 데이터가 실제로 존재하는지 (롤백이 실패했는지) 미리 확인합니다.

**단계 C: 수동 삭제 실행 (Manual Deletion)**
- 확인된 데이터를 Primary DB에서 직접 DELETE 쿼리로 제거
- 복구 완료 후 로그 레코드의 `status`를 `DELETED_BY_ADMIN`으로 업데이트

---

### Read 로직 구현 상세

한편, Read 로직 구현을 위한 상세 제안은 다음과 같습니다.

Primary/Secondary를 위한 Repository를 분리하는 옵션 A를 적용하여 Primary와 Secondary에 대한 읽기(Read)를 명확히 구분해야 합니다.

#### Repository 분리 전략

**구현 방법**:

1. **Repository 분리**:
   - Primary용 (`PrimaryMemoRepository`)와 Secondary용 (`SecondaryMemoRepository`) 두 개의 Repository 인터페이스를 만듭니다
   - 각각 동일한 메서드 시그니처를 가지되, 다른 `EntityManagerFactory`를 사용

2. **설정 변경**:
   - `DualMasterDataSourceConfig.java`에 두 Repository가 각각 Primary/Secondary `EntityManagerFactory`를 바라보도록 설정되어야 합니다
   - (현재 파일에는 이미 두 개의 `@EnableJpaRepositories`가 설정되어 있어, 이 방식이 지원됩니다)

**구현 예시**:
```java
// Primary Repository
@Repository
public interface PrimaryMemoRepository extends JpaRepository<Memo, Long> {
    List<Memo> findByUserIdAndUserShelfBookId(Long userId, Long userBookId);
}

// Secondary Repository
@Repository
public interface SecondaryMemoRepository extends JpaRepository<Memo, Long> {
    List<Memo> findByUserIdAndUserShelfBookId(Long userId, Long userBookId);
}

// DualMasterReadService에서 사용
@Service
public class DualMasterReadService {
    
    @Autowired
    private PrimaryMemoRepository primaryMemoRepository;
    
    @Autowired
    private SecondaryMemoRepository secondaryMemoRepository;
    
    public <T> T readWithFailover(Function<PrimaryMemoRepository, T> readOperation) {
        try {
            // Primary에서 시도
            return readOperation.apply(primaryMemoRepository);
        } catch (Exception e) {
            log.warn("Primary DB 읽기 실패, Secondary DB로 전환", e);
            // Secondary에서 시도
            try {
                return readOperation.apply(secondaryMemoRepository);
            } catch (Exception e2) {
                log.error("Secondary DB 읽기도 실패", e2);
                throw new DatabaseUnavailableException("모든 DB 접근 실패", e2);
            }
        }
    }
}
```

---

### 참고: 기존 옵션들

#### 옵션 A: JPA Repository 유지 + Repository 인스턴스 다중화

**방법**:
- Primary/Secondary 각각의 EntityManagerFactory 생성
- `PrimaryMemoRepository`, `SecondaryMemoRepository` 생성
- 각각 다른 EntityManagerFactory 사용

**장점**:
- 기존 JPA Repository 코드 유지 가능
- JPA의 편의 기능 계속 사용

**단점**:
- Repository 인스턴스 중복 생성
- EntityManagerFactory 다중화로 메모리 사용량 증가
- 코드 복잡도 증가

**적용 범위**: Read 작업에 적용 (최종 선택)

#### 옵션 B: JdbcTemplate으로 전환

**방법**:
- 모든 Repository 호출을 JdbcTemplate + SQL로 변경
- 수동 쿼리 작성 및 ResultSet 매핑

**장점**:
- 로드맵 설계와 일치
- 명시적 SQL 제어 가능

**단점**:
- 기존 코드 대폭 수정 필요
- 개발 생산성 저하
- SQL 작성 및 유지보수 부담

**적용 범위**: Write 작업의 Secondary DB에만 부분 적용 (최종 선택)

#### 옵션 C: 하이브리드 접근

**방법**:
- **Read 작업**: JPA Repository 유지, Failover는 Service 레벨에서 처리
- **Write 작업**: JdbcTemplate 사용 (Dual Write 구현)

**장점**:
- Read 작업은 기존 코드 최대한 유지
- Write 작업만 JdbcTemplate으로 전환
- 영향 범위 최소화

**단점**:
- Read/Write 패턴 불일치
- 일관성 문제 가능

**적용 범위**: 전체 전략의 기본 틀로 사용 (최종 선택)

---

### 2. @Transactional 어노테이션 충돌 🔴 **심각**

#### 문제 설명

**현재 코드 현황**:
- 대부분의 Service 클래스에 클래스 레벨 `@Transactional` 선언
  ```java
  @Service
  @Transactional  // 클래스 레벨
  public class MemoService {
      // ...
  }
  ```
- 일부 메서드에 `@Transactional(readOnly = true)` 오버라이드

**로드맵 설계**:
- Primary/Secondary 각각의 독립적인 `PlatformTransactionManager` 필요
- `TransactionTemplate`을 사용한 수동 트랜잭션 관리

**충돌 원인**:

@Transactional 충돌의 근본 원인은 Dual Write 구조 그 자체에 있습니다.

1. **Spring의 @Transactional 제약사항**:
   - Spring의 `@Transactional`은 기본적으로 **하나의 PlatformTransactionManager 인스턴스**에만 바인딩됩니다
   - Dual Write 요구사항에 따르면, Primary DB 커밋 (Primary TM 필요)과 Secondary DB 커밋 (Secondary TM 필요)을 순차적으로 독립 관리해야 합니다

2. **DualMasterWriteService의 내부 동작 방식**:
   - `DualMasterWriteService`가 도입되더라도, 이 Service 내부에서 **두 개의 다른 DB에 대한 독립적인 트랜잭션 경계(Commit/Rollback)**를 생성해야 한다는 사실은 변하지 않습니다
   - 따라서 `DualMasterWriteService`는 내부적으로 다음과 같이 동작해야 합니다:
     1. Primary DB 트랜잭션 시작 (Primary TM 사용)
     2. Primary DB 작업 수행 및 커밋
     3. Secondary DB 트랜잭션 시작 (Secondary TM 사용)
     4. Secondary DB 작업 수행 및 커밋

3. **외부 트랜잭션과의 충돌**:
   - 만약 해당 비즈니스 로직 서비스 메서드에 `@Transactional`이 남아 있다면, Spring AOP가 `MemoService` 호출 시 기본 TM을 사용하여 외부 트랜잭션을 생성하게 됩니다
   - 이럴 경우, `DualMasterWriteService` 내부에서 수동으로 Primary/Secondary 트랜잭션을 관리하려고 할 때, 이미 외부 트랜잭션이 존재하기 때문에 Spring의 트랜잭션 전파 규칙(Propagation Rule)에 따라 의도한 대로 독립적인 트랜잭션이 동작하지 않거나 예상치 못한 충돌을 일으키게 됩니다

4. **문제 1과 문제 2의 연관성**:
   - "문제 1의 해결 방법", 즉 `DualMasterWriteService`를 도입하여 Dual Write 로직을 캡슐화하는 것 자체가 문제 2를 해결하기 위한 **필수적인 구현 방식(옵션 A)**을 강제하게 됩니다
   - 따라서 Dual Write가 필요한 Service 메서드는 반드시 외부 트랜잭션을 제거해야 합니다

#### 영향

1. **트랜잭션 관리 방식 전면 변경 필요**
   - 클래스 레벨 `@Transactional` 제거 또는 조건부 처리
   - 수동 트랜잭션 관리로 전환

2. **기존 코드 수정 필요**
   - 모든 Service 메서드의 트랜잭션 관리 방식 변경
   - `@Transactional` 제거 후 `TransactionTemplate` 사용

3. **트랜잭션 경계 명시적 관리 필요**
   - 트랜잭션 시작/종료를 명시적으로 관리
   - 롤백 처리 로직 추가

#### 해결 방안

**결론: 옵션 A가 필수적입니다**

문제 1의 해결 방안인 `DualMasterWriteService`의 도입은 곧 문제 2의 해결 방안 중 **옵션 A(@Transactional 제거 + 수동 트랜잭션 관리)를 채택하는 것을 의미**합니다.

##### 필수 해결 방안: @Transactional 제거 + 수동 트랜잭션 관리

**필수성**:
- `DualMasterWriteService`가 내부에서 두 개의 독립적인 트랜잭션을 관리하려면, 외부 트랜잭션이 존재해서는 안 됩니다
- 상위 비즈니스 서비스 클래스에 `@Transactional`이 남아있으면 두 개의 독립적인 트랜잭션 관리가 불가능해집니다
- 따라서 트랜잭션 관리의 근본적인 책임이 Dual Write 로직을 독립적으로 처리해야 하는 하위 서비스(`DualMasterWriteService`)로 위임되었기 때문에, 상위 서비스에서는 `@Transactional`을 제거해야 합니다

**적용 대상**:
- 상위 비즈니스 서비스 클래스: `BookService`, `JwtService`, `MemoService`, `UserService`, `AuthService`, `UserDeviceService`
- 클래스 레벨 `@Transactional` 제거
- 트랜잭션 관리 책임을 `DualMasterWriteService`로 완전히 위임

**구현 방법**:
- 클래스 레벨 `@Transactional` 제거
- `DualMasterWriteService`와 `DualMasterReadService`에서 `TransactionTemplate` 사용
- 기존 Service 메서드에서 `@Transactional` 제거

**장점**:
- 명시적 트랜잭션 관리
- Primary/Secondary 각각의 트랜잭션 제어 가능
- 외부 트랜잭션과의 충돌 방지
- 독립적인 트랜잭션 경계 보장

**구현 예시**:
```java
@Service
// @Transactional 제거 (필수)
public class MemoService {
    
    @Autowired
    private DualMasterWriteService writeService;
    
    // @Transactional 제거 (필수)
    // 트랜잭션 관리는 DualMasterWriteService 내부에서 처리
    public Memo createMemo(User user, Memo memo) {
        return writeService.writeWithDualWrite(
            // writeWithDualWrite 내부에서 TransactionTemplate 사용
            // 1. Primary DB 트랜잭션 시작 (Primary TM 사용)
            // 2. Primary DB 작업 수행 및 커밋
            // 3. Secondary DB 트랜잭션 시작 (Secondary TM 사용)
            // 4. Secondary DB 작업 수행 및 커밋
            jdbcTemplate -> { /* ... */ },
            savedMemo -> { /* ... */ }
        );
    }
}
```

**중요 사항**:
- 로직 개선 이후에도 Dual Write가 필요한 모든 Service 클래스와 메서드는 해당 개선 사항(클래스 레벨 `@Transactional` 제거)을 적용해야 합니다
- 이는 트랜잭션 관리의 근본적인 책임이 Dual Write 로직을 독립적으로 처리해야 하는 하위 서비스(`DualMasterWriteService`)로 위임되었기 때문입니다

#### Service 클래스별 @Transactional 적용 방안

Dual Write 아키텍처 도입에 따른 `@Transactional` 적용 방안을 각 Service 클래스의 주요 기능에 따라 세 가지 범주로 나누어 분석했습니다.

##### 범주 1: Dual Write가 필요한 핵심 Write/Update 서비스 (클래스 레벨 @Transactional 제거 필수)

이 범주에 속하는 서비스들은 Primary DB와 Secondary DB 모두에 데이터를 동기화해야 하는 쓰기(Write) 작업을 포함하고 있습니다. 따라서 클래스 레벨 `@Transactional`은 반드시 제거되어야 합니다.

**적용 대상**: `BookService`, `MemoService`, `AuthService`, `UserDeviceService`

**적용 사항**:

1. **클래스 레벨**: `@Transactional` 어노테이션을 완전히 제거합니다

2. **Write 메서드**: `DualMasterWriteService`를 호출하도록 수정합니다. 이 서비스 내부에서 Primary/Secondary 트랜잭션을 수동으로 관리합니다

3. **Read 메서드**: Read 메서드에만 명시적으로 `@Transactional(readOnly = true)`를 붙여 기존의 단일 DB Read 트랜잭션 기능을 유지합니다

**구현 예시**:
```java
@Service
// @Transactional 제거 (필수)
public class MemoService {
    
    @Autowired
    private DualMasterWriteService writeService;
    
    // Write 메서드: DualMasterWriteService 사용
    public Memo createMemo(User user, Memo memo) {
        return writeService.writeWithDualWrite(/* ... */);
    }
    
    // Read 메서드: @Transactional(readOnly = true) 명시적 선언
    @Transactional(readOnly = true)
    public List<Memo> getAllBookMemos(User user, Long userBookId) {
        return memoRepository.findByUserIdAndUserShelfBookId(
            user.getId(), userBookId
        );
    }
}
```

##### 범주 2: Write/Read가 혼재되거나 Write는 있으나 ReadOnly가 기본인 서비스 (클래스 레벨 @Transactional 제거 권고)

`UserService`는 현재 `readOnly = true`로 선언되어 있지만, 비밀번호 변경이나 프로필 업데이트 같은 Write 기능도 반드시 포함합니다. 일관성을 위해 클래스 레벨 선언을 제거하고 메서드 레벨로 관리하는 것이 가장 안전합니다.

**적용 대상**: `UserService`

**현재 상태**: `@Transactional(readOnly = true)` 클래스 레벨 선언

**적용 사항**:

1. **클래스 레벨**: `@Transactional(readOnly = true)` 제거

2. **Read 메서드**: `@Transactional(readOnly = true)`로 재선언

3. **Write 메서드**: `DualMasterWriteService` 사용

**구현 예시**:
```java
@Service
// @Transactional(readOnly = true) 제거
public class UserService {
    
    @Autowired
    private DualMasterWriteService writeService;
    
    // Read 메서드: @Transactional(readOnly = true) 재선언
    @Transactional(readOnly = true)
    public boolean isLoginIdDuplicate(String loginId) {
        return userRepository.existsByLoginId(loginId);
    }
    
    // Write 메서드: DualMasterWriteService 사용
    public User updateUserProfile(User user, UpdateProfileRequest request) {
        return writeService.writeWithDualWrite(/* ... */);
    }
}
```

##### 범주 3: 순수 Read 또는 Primary DB만 사용하는 유틸리티성 서비스 (변경 불필요 가능성 높음)

`JwtService`는 토큰 생성/검증 등 유틸리티 역할을 주로 수행하며, 일반적으로 Primary DB에 대한 쓰기 작업을 수행하지 않거나 매우 미미합니다.

**적용 대상**: `JwtService`

**적용 사항**:

- 만약 DB Write가 없다면 트랜잭션 제거/유지 무방
- 만약 사용자 정보 조회만 한다면 `@Transactional(readOnly = true)`로 변경하는 것이 더 정확합니다
- 향후 Dual Write가 필요한 Write 작업이 추가될 경우, 범주 1의 적용 사항을 따릅니다

#### 개별 메서드 레벨 @Transactional 처리 방안

위 비즈니스 로직 서비스 클래스들 이외에도 `@Transactional`이 선언된 개별 메서드에 대해서도 다음 개선 사항을 적용해야 합니다.

클래스 레벨의 `@Transactional`을 제거했더라도, 특정 메서드에 오버라이드 목적으로 `@Transactional`이 이미 선언되어 있을 수 있기 때문입니다.

**처리 방안**:

1. **Dual Write를 수행하는 메서드들**:
   - `@Transactional` 또는 `@Transactional(propagation = ...)`을 반드시 제거합니다
   - 해당 메서드는 `DualMasterWriteService.executeDualWrite()`를 호출하도록 로직을 변경합니다

2. **Read Only 작업을 수행하는 메서드들**:
   - `@Transactional(readOnly = true)`를 유지 또는 추가합니다
   - 이 트랜잭션은 Primary DB에 대한 Read 트랜잭션 역할을 합니다

3. **단일 DB Write (예외적인 경우)**:
   - 해당 로직이 Dual Write로 전환되어야 한다면 `@Transactional`을 제거하거나 로직 전환을 수행합니다

#### 하위 레이어: DualMasterWriteService 및 DualMasterReadService 구현

이 두 핵심 서비스의 구현체 내부에서 Primary/Secondary DB에 대한 수동 트랜잭션 관리 로직이 완벽하게 구현되어야 합니다. Dual Write 아키텍처는 Service 레이어뿐만 아니라 전반적인 DB 접근 및 예외 처리 방식에 영향을 미치기 때문입니다.

##### DualMasterWriteService 구현 요구사항

**역할**: Primary/Secondary 동시 쓰기 관리

**구현 요구사항**:
- `TransactionTemplate`을 사용하여 두 개의 독립적인 트랜잭션을 순차적으로 수행 (Primary Commit → Secondary Commit)
- Primary 실패 시 Secondary 롤백
- Secondary 실패 시 Primary 롤백(보상 트랜잭션) 로직 구현

##### DualMasterReadService 구현 요구사항

**역할**: Read Failover 관리

**구현 요구사항**:
- Primary DB 접근 실패 시 Secondary DB로 전환하는 로직 구현
- 각 DB에 대한 트랜잭션 경계 설정

##### 패키지 구조 제안

`DualMasterWriteService`와 `DualMasterReadService`는 별도의 패키지(core 패키지) 하위에 두어 상위 비즈니스 로직 클래스들과 분리해야 합니다.

**이유**:
- 이 두 서비스는 비즈니스 로직을 처리하는 것이 아니라, 데이터베이스 접근 및 트랜잭션 관리라는 인프라스트럭처 및 핵심 메커니즘을 처리하는 역할을 합니다
- 따라서 일반적인 `MemoService`나 `BookService`와 같은 도메인 서비스와 함께 두면 안 됩니다

**권장 패키지 구조**:

```
com.readingtracker.server
├── service
│   ├── BookService.java
│   ├── MemoService.java
│   └── ...
├── core                    // 새로 생성
│   ├── DualMasterWriteService.java
│   └── DualMasterReadService.java
└── config
    └── DualTransactionConfiguration.java  // Primary/Secondary DB 설정
```

**설정 파일 위치**:
- `server.config.DualTransactionConfiguration.java`: Primary/Secondary DB의 DataSource, EntityManagerFactory, 그리고 두 개의 `PlatformTransactionManager` Bean을 정의합니다

**Repository 패키지 구조**:
- `dbms.primary.repository`: Primary DB의 리포지토리
- `dbms.secondary.repository`: Secondary DB의 리포지토리
- 각 DB의 리포지토리를 별도 패키지로 관리하여, JpaRepository와 JdbcTemplate 기반 리포지토리를 명확히 구분합니다

---

### 3. 성능 저하 🟡 **중간**

> **⚠️ 구현 불필요**: 본 프로젝트는 비기능 품질 구현이 목적이며 실제 배포가 아닙니다. 데모 시연만 가능하면 되므로, 작은 데이터(메모 10개 정도) 환경에서는 성능 저하 문제가 체감되지 않습니다. 따라서 성능 최적화 관련 구현은 불필요합니다.

#### 문제 설명

**Dual Write 특성**:
- 모든 Write 작업이 **Primary → Secondary 순차 실행**
- 네트워크 지연 시간 **2배** (Primary + Secondary)
- 트랜잭션 처리 시간 증가

**현재 코드 현황**:
- 단일 DB 작업으로 빠른 응답 시간
- 네트워크 지연 최소화

#### 영향

1. **응답 시간 증가**
   - Primary Write: 기존 시간
   - Secondary Write: 추가 시간
   - 총 응답 시간 = Primary 시간 + Secondary 시간
   - 특히 Secondary DB가 느린 경우 심각한 성능 저하

2. **동시 처리량 감소**
   - 각 요청이 두 번의 DB 작업 수행
   - DB 연결 풀 사용량 증가
   - 처리량 감소

3. **사용자 경험 저하**
   - 느린 응답 시간으로 인한 사용자 불만
   - 특히 모바일 환경에서 체감 지연 증가

#### 해결 방안

##### 옵션 A: 비동기 Dual Write (권장)

**방법**:
- Primary Write는 **동기** 처리 (응답 대기)
- Secondary Write는 **비동기** 처리 (백그라운드)
- Primary 성공 시 즉시 응답 반환

**장점**:
- 응답 시간 최소화 (Primary 시간만)
- 사용자 경험 향상
- Secondary 실패 시에도 Primary는 유지 (일관성 약간 완화)

**단점**:
- 일시적 데이터 불일치 가능 (Secondary 동기화 완료 전)
- 비동기 처리 복잡도 증가
- Secondary 실패 시 복구 메커니즘 필요

**구현 예시**:
```java
public <T> T writeWithDualWriteAsync(...) {
    // Phase 1: Primary에 쓰기 (동기)
    T primaryResult = writeToPrimary(...);
    
    // Phase 2: Secondary에 쓰기 (비동기)
    CompletableFuture.runAsync(() -> {
        try {
            writeToSecondary(...);
        } catch (Exception e) {
            // Secondary 실패 시 보상 트랜잭션
            executeCompensation(primaryResult);
        }
    });
    
    // Primary 성공 시 즉시 반환
    return primaryResult;
}
```

##### 옵션 B: 성능 모니터링 및 최적화

**방법**:
- DB 연결 풀 크기 조정
- Secondary DB 성능 최적화
- 타임아웃 설정
- 네트워크 최적화

**장점**:
- 기존 동기 방식 유지
- 일관성 보장

**단점**:
- 근본적인 성능 문제 해결 어려움
- Secondary DB 성능에 의존

---

### 4. 복잡한 비즈니스 로직과의 충돌 🟡 **중간**

> **⚠️ 반드시 개선 필요**: 해당 문제는 Primary와 Secondary DB 간의 데이터 유실/불일치 위험을 내포하므로 반드시 개선해야 합니다.

#### 문제 설명

**복잡한 메서드 예시**:

1. **`MemoService.closeBook()`**:
   - 여러 Repository 호출
   - 복잡한 비즈니스 로직 (카테고리 자동 변경, 날짜 설정 등)
   - 여러 단계의 데이터 수정

2. **`BookService.addBookToShelf()`**:
   - Book 테이블 조회/생성
   - UserShelfBook 저장
   - Redis 캐시 무효화

**Dual Write 요구사항**:
- 모든 단계를 Primary/Secondary에 **동일하게** 실행
- 보상 트랜잭션도 모든 단계를 역순으로 실행

#### 영향

1. **Dual Write 구현 복잡도 증가**
   - 복잡한 메서드를 Primary/Secondary에 동일하게 실행하기 어려움
   - 보상 트랜잭션 로직 복잡도 증가

2. **부수 효과 처리 어려움**
   - Redis 캐시 무효화 같은 부수 효과를 Primary/Secondary 각각 처리해야 함
   - 타이밍 문제 발생 가능

3. **테스트 복잡도 증가**
   - 다양한 시나리오 테스트 필요
   - 디버깅 어려움

#### 해결 방안

##### 옵션 A: 메서드 단위로 Dual Write 래핑

**방법**:
- 복잡한 비즈니스 로직을 하나의 함수로 묶어 `writeWithDualWrite()`에 전달
- 보상 트랜잭션도 하나의 함수로 구현

**장점**:
- 기존 비즈니스 로직 최대한 유지
- Dual Write 로직과 분리

**단점**:
- 보상 트랜잭션 로직 복잡도 증가
- 모든 단계를 역순으로 실행해야 함

**구현 예시**:
```java
public void closeBook(User user, Long userBookId, CloseBookRequest request) {
    writeService.writeWithDualWrite(
        // 복잡한 비즈니스 로직을 하나의 함수로
        jdbcTemplate -> {
            // 1. UserShelfBook 조회
            // 2. 진행률 업데이트
            // 3. 카테고리 자동 변경
            // 4. 날짜 설정
            // 5. 저장
            return executeCloseBook(user, userBookId, request);
        },
        // 보상 트랜잭션: 모든 단계를 역순으로
        savedBook -> {
            // 1. 원래 상태로 복구
            // 2. 관련 데이터 복구
            restoreOriginalState(savedBook);
        }
    );
}
```

##### 옵션 B: 트랜잭션 경계 재설계 (최종 선택)

**방법**:
- 복잡한 메서드를 여러 단계로 분리
- 각 단계를 Dual Write로 처리

**장점**:
- 각 단계별 보상 트랜잭션 명확
- 디버깅 용이

**단점**:
- 기존 코드 대폭 수정 필요
- 단계 간 의존성 관리 복잡

#### 개선이 반드시 필요한 이유

##### 1. 보상 트랜잭션의 복잡성과 위험성

현재 Primary와 Secondary가 동기 방식으로 작동한다고 가정했습니다. 어떤 메서드 내에서 5단계의 DB 작업이 순차적으로 발생하는 상황이라고 가정합시다.

이때 Primary DB에서는 5단계가 모두 성공했는데, Secondary DB에서 3단계 작업 도중 실패했다고 가정합시다. 그 결과, 사용자에게 성공 응답을 줄 수 없으므로, Primary DB의 5단계 작업을 모두 원래 상태로 되돌려야 합니다 (보상 트랜잭션).

이 과정에서 5단계를 거친 복잡한 비즈니스 로직을 완벽하게 역순으로 되돌리는 `restoreOriginalState()` 함수를 구현하는 것은 매우 어렵고, 작은 버그라도 치명적인 데이터 불일치를 초래할 수 있습니다. 이는 프로젝트 크기에 비례하지 않는 구조적 위험입니다.

##### 2. Side Effect (부수 효과) 처리의 위험

`BookService.addBookToShelf()` 예시처럼 Redis 캐시 무효화와 같은 DB 작업 외의 부수 효과가 섞여 있다면, Dual Write 트랜잭션 경계가 더욱 복잡해집니다.

- **정상 케이스**: 'Primary DB 커밋 성공 → Secondary DB 커밋 성공 → Redis 무효화 (성공)' 하는 경우는 별다른 문제가 없습니다.

- **문제 케이스**: 그러나 만약 'Primary DB 커밋 성공 → Secondary DB 실패 → Primary 롤백 → 이미 성공한 Redis 무효화는 어떻게 처리할 것인가?' 라는 상황은 문제입니다.

따라서 Dual Write의 책임은 오직 두 DB에 대한 쓰기 동기화여야 합니다. 캐시 무효화 같은 부수 효과가 섞이면 트랜잭션 관리의 복잡도가 기하급수적으로 늘어납니다.

#### 최종 해결 방안: 옵션 B의 철학과 코드 분리 조합

이를 해결하기 위해서 옵션 B의 철학과 코드 분리를 조합해서 사용하겠습니다. 즉 DB 쓰기 작업의 단위를 가능한 한 작고 원자적(Atomic)으로 만드는 것입니다.

##### 1. Dual Write 로직의 역할을 명확하게

`writeWithDualWrite()`는 오직 하나의 원자적인 DB 쓰기 명령만을 처리하도록 설계하고, 비즈니스 로직은 이를 조립(Compose)하는 역할만 해야 합니다.

**구현 원칙**:

1. **비즈니스 서비스 계층**: 복잡한 메서드 (`closeBook`)를 여러 개의 작고 독립적인 단위로 분리합니다.

2. **DB 접근 계층 (Repository/Dao)**: 각 작은 단위는 단 하나의 `writeWithDualWrite()` 호출을 통해 DB에 반영됩니다.

**구현 예시**:

```java
// 개선된 CloseBook 로직 (비즈니스 계층)
public void closeBook(User user, Long userBookId, CloseBookRequest request) {
    
    // 1. UserShelfBook 상태 업데이트 (원자적 쓰기 1)
    userShelfBookRepository.updateStatus(userBookId, "CLOSED"); 
    // <-- 이 내부에서 Dual Write 호출
    
    // 2. 카테고리 자동 변경 로직 (읽기 + 원자적 쓰기 2)
    Category newCategory = determineNewCategory(userBookId);
    categoryRepository.updateCategory(userBookId, newCategory); 
    // <-- 이 내부에서 Dual Write 호출
    
    // 3. Redis 캐시 무효화 (DB 트랜잭션 외부로 분리)
    cacheService.invalidateUserShelfBook(userBookId);
}

// Repository 계층 (Dual Write 래핑)
// 이제 writeWithDualWrite()가 처리하는 로직은 매우 단순해지고 보상 트랜잭션도 간단해집니다.
public void updateStatus(Long userBookId, String status) {
    writeService.writeWithDualWrite(
        // Primary/Secondary에 업데이트 쿼리 실행
        jdbcTemplate -> executeUpdate(userBookId, status),
        
        // 보상 트랜잭션: 실패 시 상태를 "원래 상태"로 간단히 되돌림
        savedObject -> restoreStatus(userBookId, savedObject.originalStatus) 
    );
}
```

**장점**:
- 각 `writeWithDualWrite()` 호출이 단일 원자적 작업만 처리하므로 보상 트랜잭션이 단순해집니다
- 부수 효과(Redis 캐시 등)는 DB 트랜잭션 외부로 분리되어 트랜잭션 관리 복잡도가 감소합니다
- 각 단계가 독립적이므로 디버깅과 테스트가 용이합니다
- 작은 단위로 분리되어 데이터 불일치 위험이 크게 감소합니다

---

### 5. Redis 캐시 무효화 타이밍 문제 🟢 **낮음**

> **✅ 문제 4번의 개선 사항 적용 시 자연스럽게 해소됨**: 문제 4번의 최종 해결 방안(부수 효과를 DB 트랜잭션 외부로 분리)을 적용하면, Redis 캐시 무효화가 `writeWithDualWrite()` 완전 성공 후에만 실행되므로 문제 5번의 핵심 문제(캐시와 DB 불일치)가 자연스럽게 해소됩니다. 별도의 해결 방안 구현이 불필요합니다.

#### 문제 설명

**현재 코드**:
```java
public UserShelfBook addBookToShelf(UserShelfBook userShelfBook) {
    // ... 저장 로직 ...
    UserShelfBook saved = userBookRepository.save(userShelfBook);
    
    // 캐시 무효화
    invalidateMyShelfCache(userShelfBook.getUserId());
    
    return saved;
}
```

**Dual Write 시나리오**:
- Primary Write 성공 → 캐시 무효화
- Secondary Write 실패 → 보상 트랜잭션으로 Primary 롤백
- **문제**: 캐시는 이미 무효화됨, 하지만 DB는 롤백됨

#### 영향

1. **캐시와 DB 불일치**
   - 캐시가 무효화되어 재조회 시 DB에서 데이터 없음
   - 사용자에게 일시적 오류 표시 가능

2. **데이터 일관성 문제**
   - 캐시 무효화 타이밍과 DB 롤백 타이밍 불일치

#### 해결 방안

##### 옵션 A: Secondary 성공 후 캐시 무효화

**방법**:
- Primary/Secondary 모두 성공한 후에만 캐시 무효화
- 보상 트랜잭션 실행 시에는 캐시 무효화하지 않음

**장점**:
- 캐시와 DB 일관성 보장
- 간단한 구현

**단점**:
- Secondary 실패 시 캐시가 오래된 데이터 유지
- 일시적 불일치 가능

**구현 예시**:
```java
public UserShelfBook addBookToShelf(UserShelfBook userShelfBook) {
    return writeService.writeWithDualWrite(
        jdbcTemplate -> {
            return userBookRepository.save(userShelfBook);
        },
        savedBook -> {
            // 보상 트랜잭션 (캐시 무효화 없음)
            userBookRepository.delete(savedBook);
        }
    );
    // Dual Write 성공 후에만 캐시 무효화
    invalidateMyShelfCache(userShelfBook.getUserId());
}
```

##### 옵션 B: 보상 트랜잭션 시 캐시 복구

**방법**:
- 보상 트랜잭션 실행 시 캐시에 원래 데이터 복구
- 또는 캐시 무효화 후 재조회

**장점**:
- 캐시 일관성 보장

**단점**:
- 복잡도 증가
- 성능 오버헤드

---

### 6. 트랜잭션 격리 수준 🟢 **낮음**

> **⚠️ 개선할 필요 없음 (감수하고 가야 하는 문제)**: 데모 시연/소규모 프로젝트 환경에서는 발생 빈도가 극도로 낮아 현재 시점에 구현의 복잡도를 감수할 필요가 없습니다. 또한 문제 4번의 개선(원자적 쓰기 단위 분리) 덕분에 위험도가 대폭 감소하여, 트랜잭션의 길이가 짧아져 동시성 충돌 발생 시간이 줄었습니다. 따라서 별도의 해결 방안 구현은 불필요합니다.

#### 문제 설명

**Dual Write 특성**:
- Primary와 Secondary가 **서로 다른 트랜잭션**으로 실행
- 동시성 제어가 어려움

**현재 코드**:
- 단일 DB 트랜잭션으로 격리 수준 보장

#### 영향

1. **동시성 문제 가능성**
   - 동시 요청 시 Primary와 Secondary에서 읽은 데이터가 다를 수 있음
   - 낮은 확률이지만 발생 가능

2. **일관성 약간 완화**
   - Primary와 Secondary 간 일시적 불일치 가능

#### 해결 방안

##### 옵션 A: 트랜잭션 격리 수준 명시적 설정

**방법**:
- Primary/Secondary 각각의 트랜잭션 격리 수준 설정
- `READ_COMMITTED` 또는 `REPEATABLE_READ` 사용

**장점**:
- 동시성 문제 최소화

**단점**:
- 성능 약간 저하 가능

##### 옵션 B: 낙관적/비관적 락 사용

**방법**:
- JPA의 `@Version` 어노테이션 사용 (낙관적 락)
- 또는 SELECT FOR UPDATE 사용 (비관적 락)

**장점**:
- 동시성 제어 강화

**단점**:
- 구현 복잡도 증가
- 성능 오버헤드

---

## 권장 해결 방안

### 단계별 접근 전략

#### Phase 1: Read Failover 먼저 구현 (영향 최소화)

**목표**: Read 작업의 장애 허용 기능 추가

**방법**:
- JPA Repository 유지
- Service 레벨에서 Primary 실패 시 Secondary로 재시도
- `@Transactional(readOnly = true)` 유지

**장점**:
- 기존 코드 최소한 수정
- 영향 범위 제한
- 점진적 전환 가능

**구현 예시**:
```java
@Service
@Transactional(readOnly = true)  // 유지
public class MemoService {
    
    @Autowired
    private MemoRepository memoRepository; // 기존 유지
    
    @Autowired
    private DualMasterReadService readService;
    
    public List<Memo> getAllBookMemos(User user, Long userBookId) {
        // Service 레벨에서 Failover 처리
        return readService.readWithFailover(() -> {
            // 기존 Repository 호출 유지
            return memoRepository.findByUserIdAndUserShelfBookId(
                user.getId(), userBookId
            );
        });
    }
}
```

#### Phase 2: Write Dual Write 구현 (점진적 전환)

**목표**: Write 작업의 Dual Write 기능 추가

**방법**:
- 복잡한 메서드부터 점진적 전환
- JPA Repository → JdbcTemplate 하이브리드 접근
- 또는 Repository 인스턴스 다중화

**우선순위**:
1. 단순한 Write 메서드 (예: `createMemo`, `deleteMemo`)
2. 중간 복잡도 메서드 (예: `updateMemo`)
3. 복잡한 메서드 (예: `closeBook`, `addBookToShelf`)

**구현 예시**:
```java
@Service
// @Transactional 제거 (Write 메서드용)
public class MemoService {
    
    @Autowired
    private DualMasterWriteService writeService;
    
    // 단순한 Write 메서드부터 전환
    public Memo createMemo(User user, Memo memo) {
        return writeService.writeWithDualWrite(
            jdbcTemplate -> {
                // JdbcTemplate으로 직접 SQL 실행
                String sql = "INSERT INTO memos (...) VALUES (...)";
                // ...
            },
            savedMemo -> {
                // 보상 트랜잭션
                String deleteSql = "DELETE FROM memos WHERE id = ?";
                // ...
            }
        );
    }
}
```

#### Phase 3: 최적화 및 모니터링

**목표**: 성능 최적화 및 안정성 향상

**방법**:
- 비동기 Dual Write 고려
- 성능 모니터링 및 튜닝
- 장애 시나리오 테스트

---

### 하이브리드 접근 방식 (최종 권장)

#### Read 작업: JPA Repository + Service 레벨 Failover

```java
@Service
@Transactional(readOnly = true)
public class MemoService {
    
    @Autowired
    private MemoRepository memoRepository; // 기존 유지
    
    @Autowired
    private DualMasterReadService readService;
    
    public List<Memo> getAllBookMemos(User user, Long userBookId) {
        return readService.readWithFailover(() -> {
            // 기존 Repository 호출 유지
            return memoRepository.findByUserIdAndUserShelfBookId(
                user.getId(), userBookId
            );
        });
    }
}
```

#### Write 작업: JdbcTemplate + Dual Write

```java
@Service
// @Transactional 제거
public class MemoService {
    
    @Autowired
    private DualMasterWriteService writeService;
    
    public Memo createMemo(User user, Memo memo) {
        return writeService.writeWithDualWrite(
            jdbcTemplate -> {
                // JdbcTemplate으로 직접 SQL 실행
                // 또는 Repository를 Primary/Secondary 각각 생성하여 사용
            },
            savedMemo -> {
                // 보상 트랜잭션
            }
        );
    }
}
```

---

## 결론

### 요약

MySQL 이중화 구현 시 **부정적 영향이 있습니다**. 주요 원인:

1. **JPA Repository vs JdbcTemplate 불일치** (심각)
2. **@Transactional 어노테이션 충돌** (심각)
3. **성능 저하 가능성** (중간)
4. **복잡한 비즈니스 로직과의 통합 어려움** (중간)
5. **Redis 캐시 무효화 타이밍 문제** (낮음)
6. **트랜잭션 격리 수준** (낮음)

### 권장 접근 방식

1. **단계적 구현**: Read Failover → Write Dual Write
2. **하이브리드 접근**: Read는 JPA Repository 유지, Write는 JdbcTemplate 사용
3. **점진적 전환**: 단순한 메서드부터 복잡한 메서드로 확장
4. **성능 최적화**: 비동기 Dual Write 고려

### 다음 단계

1. **Phase 1 검토**: Read Failover 구현 방안 상세 설계
2. **Phase 2 검토**: Write Dual Write 구현 방안 상세 설계
3. **프로토타입**: 작은 규모로 프로토타입 구현 및 검증
4. **테스트 계획**: 영향 범위별 테스트 시나리오 작성

---

**문서 버전**: 1.0  
**최종 업데이트**: 2024년  
**작성자**: Development Team

