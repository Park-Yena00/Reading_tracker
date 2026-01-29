# Spring Boot 애플리케이션 시작 실패 에러 분석

## ✅ 실제 발생한 에러 분석 결과

### 에러 종류

**에러 유형**: `NoSuchBeanDefinitionException` (Bean 정의 없음)  
**핵심 에러 메시지**: 
```
No qualifying bean of type 'org.springframework.transaction.PlatformTransactionManager' available: 
expected at least 1 bean which qualifies as autowire candidate. 
Dependency annotations: {@org.springframework.beans.factory.annotation.Autowired(required=true), 
@org.springframework.beans.factory.annotation.Qualifier("secondaryTransactionManager")}
```

**에러 체인**:
1. `authController` 생성 실패
2. → `authService` 생성 실패  
3. → `jwtService` 생성 실패
4. → `dualMasterWriteService` 생성 실패
5. → **`secondaryTransactionManager` 빈을 찾을 수 없음** ← **핵심 원인**

이 에러는 **Spring Boot 애플리케이션이 시작(Startup) 단계에서 Bean 주입 실패**로 인해 발생했습니다.

---

## 🔍 원인 분석

### 실제 원인

**문제점**: `DualMasterWriteService` 클래스에서 `secondaryTransactionManager` 빈을 **필수(`required=true`)로 주입**받으려고 했지만, Secondary DB가 비활성화되어 있어 해당 빈이 생성되지 않았습니다.

**설정 확인**:
- `application.yml`: `spring.datasource.secondary.enabled: ${SECONDARY_DB_ENABLED:false}` (기본값: false)
- `DualMasterDataSourceConfig.java`: `secondaryTransactionManager` 빈은 `@ConditionalOnProperty(name = "spring.datasource.secondary.enabled", havingValue = "true")` 조건이 있어 Secondary DB가 활성화되지 않으면 생성되지 않음
- `DualMasterWriteService.java`: `@Autowired(required=true)` 기본값으로 `secondaryTransactionManager`를 필수 주입으로 요구

**비교**: `DualMasterReadService`는 `@Autowired(required = false)`로 설정되어 있어 Secondary DB가 없어도 정상 동작함

---

## ✅ 해결 방법

### 해결책: `DualMasterWriteService` 수정

`DualMasterWriteService`의 `secondaryTransactionManager`와 `secondaryJdbcTemplate`에 `required = false`를 추가하고, Secondary DB가 없을 때는 Primary만 사용하도록 로직을 수정했습니다.

**수정 내용**:
1. `@Autowired(required = false)` 추가
2. Secondary DB가 없을 때 early return 처리

**수정된 코드**:
```java
@Autowired(required = false)
@Qualifier("secondaryTransactionManager")
private PlatformTransactionManager secondaryTxManager;

@Autowired(required = false)
@Qualifier("secondaryJdbcTemplate")
private JdbcTemplate secondaryJdbcTemplate;
```

**로직 추가**:
```java
// Secondary DB가 설정되지 않은 경우 Primary만 사용하고 성공 반환
if (secondaryTxManager == null || secondaryJdbcTemplate == null) {
    log.info("Secondary DB가 설정되지 않음. Primary DB만 사용합니다.");
    return primaryResult;
}
```

---

## 📝 참고: 기타 가능한 원인들 (이번 에러와 무관)

다음은 이번 에러와는 무관하지만, 향후 발생할 수 있는 다른 원인들입니다:

### 1. **데이터베이스 연결 실패**

**원인**:
- MySQL 서버가 실행되지 않음 (`localhost:3306`)
- 데이터베이스 `reading_tracker`가 존재하지 않음
- 데이터베이스 접속 정보 오류 (사용자명, 비밀번호)

**확인 방법**:
```bash
# MySQL 서버 상태 확인
mysql -u root -p -e "SHOW DATABASES;"

# reading_tracker 데이터베이스 존재 확인
mysql -u root -p -e "USE reading_tracker;"
```

**해결 방법**:
- MySQL 서버 시작
- 데이터베이스 생성: `CREATE DATABASE reading_tracker CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
- `application.yml`의 비밀번호 확인 (`PRIMARY_DB_PASSWORD` 환경변수 또는 기본값 `Yenapark1000`)

---

### 2. **Redis 연결 실패**

**원인**:
- Redis 서버가 실행되지 않음 (`localhost:6379`)
- Redis 연결 설정 오류

**확인 방법**:
```bash
# Redis 서버 상태 확인 (Windows)
redis-cli ping
# 응답: PONG (정상), 연결 실패 시 에러 메시지

# 또는 PowerShell에서
Test-NetConnection -ComputerName localhost -Port 6379
```

**해결 방법**:
- Redis 서버 시작
- `application-dev.yml`의 Redis 설정 확인 (host, port, password)

---

### 3. **Flyway 마이그레이션 실패**

**원인**:
- 데이터베이스 마이그레이션 스크립트 실행 실패
- 이전 마이그레이션과 충돌
- 체크섬 불일치

**확인 방법**:
```bash
# Flyway 마이그레이션 상태 확인
mvn flyway:info
```

**해결 방법**:
- `application.yml`에서 `flyway.repair-on-migrate: true` 설정 확인 (이미 설정됨)
- 필요시 수동으로 마이그레이션 복구: `mvn flyway:repair`

---

### 4. **포트 충돌**

**원인**:
- 8080 포트가 이미 다른 프로세스에서 사용 중

**확인 방법**:
```powershell
# Windows PowerShell
netstat -ano | findstr :8080
```

**해결 방법**:
- 다른 프로세스 종료 또는 `application.yml`에서 포트 변경

---

### 5. **환경 변수 또는 설정 파일 오류**

**원인**:
- 필수 환경 변수 누락
- 설정 파일 문법 오류
- 프로파일 설정 오류

**확인 방법**:
- `.env` 파일 존재 여부 확인
- `application.yml`, `application-dev.yml` 문법 검증

---

## 상세 에러 로그 확인 방법

현재 에러 메시지는 요약본이므로, 실제 원인을 파악하려면 **상세 로그**를 확인해야 합니다:

### 방법 1: 상세 스택 트레이스 확인
```bash
mvn spring-boot:run -e
```

### 방법 2: 디버그 모드로 실행
```bash
mvn spring-boot:run -X
```

### 방법 3: 특정 프로파일 지정하여 실행
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## 권장 해결 순서

1. **MySQL 서버 상태 확인 및 시작**
   ```bash
   # MySQL 서비스 시작 (Windows)
   net start MySQL80
   # 또는 MySQL Workbench에서 확인
   ```

2. **Redis 서버 상태 확인 및 시작**
   ```bash
   # Redis 서버 시작 (설치 경로에 따라 다름)
   redis-server
   ```

3. **데이터베이스 및 스키마 확인**
   ```sql
   CREATE DATABASE IF NOT EXISTS reading_tracker 
   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

4. **상세 에러 로그 확인**
   ```bash
   mvn spring-boot:run -e
   ```

5. **포트 충돌 확인**
   ```powershell
   netstat -ano | findstr :8080
   ```

---

## 예상되는 실제 에러 메시지

상세 로그를 확인하면 다음과 같은 메시지 중 하나가 나타날 가능성이 높습니다:

- `Communications link failure` → MySQL 연결 실패
- `Unable to connect to Redis` → Redis 연결 실패
- `Flyway migration failed` → 마이그레이션 실패
- `Port 8080 is already in use` → 포트 충돌
- `Bean creation failed` → 설정 오류

---

## ✅ 해결 완료

**수정 파일**: `src/main/java/com/readingtracker/server/service/write/DualMasterWriteService.java`

**변경 사항**:
- `secondaryTransactionManager`와 `secondaryJdbcTemplate`에 `required = false` 추가
- Secondary DB가 없을 때 Primary만 사용하도록 early return 로직 추가

이제 Secondary DB가 비활성화되어 있어도 애플리케이션이 정상적으로 시작됩니다.

---

## 참고사항

- **Secondary DB**는 `enabled: false`로 설정되어 있어 선택적 구성 요소입니다
- **Flyway**는 `repair-on-migrate: true`로 설정되어 있어 자동 복구 시도합니다
- **Redis**는 필수 구성 요소이므로 연결 실패 시 애플리케이션 시작 불가합니다
- **이번 에러는 Flyway 마이그레이션과 무관**하며, Bean 주입 설정 문제였습니다


