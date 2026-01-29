# Secondary NamedParameterJdbcTemplate Bean Not Found 에러 해결

> **작성일**: 2026-01-12  
> **문제**: 애플리케이션 시작 시 `secondaryNamedParameterJdbcTemplate` 빈을 찾을 수 없음  
> **원인**: Secondary DB가 비활성화된 상태에서 필수 의존성으로 주입 시도  
> **상태**: 🔍 분석 완료

---

## 문제 진단

### 증상

애플리케이션 시작 시 다음과 같은 에러가 발생합니다:

```
org.springframework.beans.factory.NoSuchBeanDefinitionException: 
No qualifying bean of type 'org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate' 
available: expected at least 1 bean which qualifies as autowire candidate. 
Dependency annotations: {
    @org.springframework.beans.factory.annotation.Autowired(required=true), 
    @org.springframework.beans.factory.annotation.Qualifier("secondaryNamedParameterJdbcTemplate")
}
```

### 에러 발생 위치

에러는 다음 의존성 체인을 통해 발생합니다:

1. `authController` → `authService` 주입 시도
2. `authService` → `jwtService` 주입 시도
3. `jwtService` → `secondaryNamedParameterJdbcTemplate` 주입 실패 ❌

---

## 원인 분석

### 1. 빈 생성 조건부 설정

`DualMasterDataSourceConfig.java`에서 `secondaryNamedParameterJdbcTemplate` 빈은 조건부로 생성됩니다:

```java
@Bean
@ConditionalOnProperty(
    name = "spring.datasource.secondary.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
public NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate(
        @Qualifier("secondaryDataSource") DataSource secondaryDataSource) {
    return new NamedParameterJdbcTemplate(secondaryDataSource);
}
```

**핵심 포인트:**
- `spring.datasource.secondary.enabled=true`일 때만 빈이 생성됩니다
- `matchIfMissing = false`이므로 속성이 없으면 빈이 생성되지 않습니다

### 2. 설정 파일 확인

`application.yml`에서 Secondary DB 설정:

```yaml
spring:
  datasource:
    secondary:
      enabled: ${SECONDARY_DB_ENABLED:false}  # 기본값: false
      url: jdbc:mysql://localhost:3307/reading_tracker?...
      username: root
      password: ${SECONDARY_DB_PASSWORD:Yenapark1000}
      driver-class-name: com.mysql.cj.jdbc.Driver
```

**문제점:**
- `SECONDARY_DB_ENABLED` 환경 변수가 설정되지 않았거나 `false`인 경우
- 기본값이 `false`이므로 Secondary DB가 비활성화됩니다
- 결과적으로 `secondaryNamedParameterJdbcTemplate` 빈이 생성되지 않습니다

**실제 상황:**
- Secondary DB는 Docker Desktop을 통해 활성화되어 있음
- 하지만 `SECONDARY_DB_ENABLED` 환경 변수가 설정되지 않아 기본값 `false`가 적용됨
- 이로 인해 빈이 생성되지 않아 애플리케이션 시작 실패

### 3. 필수 의존성 주입 시도

`JwtService.java`에서 필수 의존성으로 주입을 시도합니다:

```java
@Autowired
@Qualifier("secondaryNamedParameterJdbcTemplate")
private NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
```

**문제점:**
- `@Autowired(required = true)`가 기본값입니다
- 빈이 없으면 애플리케이션 시작이 실패합니다
- `JwtService`는 `saveUserDeviceWithDualWrite()`와 `updateUserDeviceWithDualWrite()` 메서드에서 이 빈을 사용합니다

### 4. 영향받는 서비스들

다음 서비스들도 동일한 문제가 발생할 수 있습니다:

- `JwtService` (52-53줄)
- `AuthService` (57-58줄)
- `BookService` (52-53줄)
- `MemoService` (60줄)
- `UserDeviceService` (38-39줄)
- `CompensationRecoveryWorker` (53-54줄)
- `SecondaryUserDao` (28-29줄)
- 기타 Secondary DAO 클래스들

### 5. 프로파일별 설정 파일의 영향 분석

**질문:** `application-dev.yml`과 `application-prod.yml`에 Secondary DB 설정이 없는 것이 에러에 영향을 미치는가?

**답변:** 아니요, 직접적인 영향은 없습니다.

#### 5.1 Spring Boot 설정 상속 메커니즘

Spring Boot는 다음과 같이 설정을 병합합니다:

1. `application.yml` (기본 설정)
2. `application-{profile}.yml` (프로파일별 설정, 오버라이드/병합)

**중요한 점:**
- 설정 경로가 정확히 일치해야 오버라이드됩니다
- 프로파일별 파일에 없는 설정은 기본 설정이 그대로 사용됩니다

#### 5.2 현재 설정 구조

**application.yml:**
```yaml
spring:
  datasource:
    primary:
      url: jdbc:mysql://localhost:3306/...
    secondary:
      enabled: ${SECONDARY_DB_ENABLED:false}  # ← 이 설정이 핵심
      url: jdbc:mysql://localhost:3307/...
```

**application-dev.yml:**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/...  # ← primary/secondary 구분 없음
    # secondary 설정 없음
```

**application-prod.yml:**
```yaml
spring:
  datasource:
    url: ${DB_URL}  # ← primary/secondary 구분 없음
    # secondary 설정 없음
```

#### 5.3 설정 병합 결과

프로파일이 `dev`일 때:

1. `spring.datasource.url` (application-dev.yml)
   - `spring.datasource.primary.url`과 경로가 다르므로 오버라이드되지 않음
   - 별도 속성으로 병합됨

2. `spring.datasource.secondary.*` (application-dev.yml에 없음)
   - application.yml의 설정이 그대로 사용됨
   - `enabled: ${SECONDARY_DB_ENABLED:false}` 적용

3. 최종 결과:
   ```
   spring.datasource.primary.url = application.yml의 값
   spring.datasource.secondary.enabled = ${SECONDARY_DB_ENABLED:false}  ← 이 값이 사용됨
   spring.datasource.secondary.url = application.yml의 값
   ```

#### 5.4 결론

- 프로파일별 파일에 `spring.datasource.secondary.*` 설정이 없으면 `application.yml`의 설정이 그대로 사용됩니다
- 현재 `application.yml`의 `secondary.enabled: ${SECONDARY_DB_ENABLED:false}`가 적용됩니다
- 프로파일별 파일이 있어도 없어도, `SECONDARY_DB_ENABLED` 환경 변수가 없으면 `enabled: false`가 되어 빈이 생성되지 않습니다

**따라서 에러의 직접적인 원인은:**
- `application.yml`의 `enabled: ${SECONDARY_DB_ENABLED:false}` 설정
- `SECONDARY_DB_ENABLED` 환경 변수가 설정되지 않음

프로파일별 설정 파일의 부재는 원인이 아닙니다.

#### 5.5 프로파일별 설정 파일에 Secondary DB 설정 정의가 필수인가?

**답변:** 필수는 아닙니다.

**이유:**
- 프로파일별 파일에 없는 설정은 `application.yml`의 설정을 그대로 사용합니다
- 따라서 `application-dev.yml`과 `application-prod.yml`에 `spring.datasource.secondary.*`가 없어도 `application.yml`의 설정이 적용됩니다

**프로파일별 설정이 필요한 경우:**
- 프로파일별로 다른 Secondary DB URL/인증 정보를 사용할 때
- 프로파일별로 다른 기본값(`enabled` 기본값 등)이 필요할 때
- 설정을 명시적으로 관리하고 싶을 때

현재는 `application.yml`의 설정만으로도 충분합니다.

---

## 해결 방안

### 방안 1: Secondary DB 활성화 (권장)

Secondary DB를 사용할 계획이라면, Secondary DB를 활성화하는 것이 가장 적절한 해결책입니다.

#### 1.1 환경 변수 설정

**Windows PowerShell:**
```powershell
$env:SECONDARY_DB_ENABLED="true"
```

**Linux/Mac:**
```bash
export SECONDARY_DB_ENABLED=true
```

#### 1.2 application.yml 직접 수정

**옵션 A: 항상 활성화**
```yaml
spring:
  datasource:
    secondary:
      enabled: true  # false에서 true로 변경
```

**옵션 B: 환경 변수 사용 (권장)**
```yaml
spring:
  datasource:
    secondary:
      enabled: ${SECONDARY_DB_ENABLED:true}  # 기본값을 true로 변경
```

**옵션 B의 장점:**
- 기본적으로 Secondary DB가 활성화됨 (Docker Desktop으로 실행 중인 경우 자동 연결)
- 테스트 시 환경 변수 `SECONDARY_DB_ENABLED=false`로 비활성화 가능
- 코드 수정 없이 설정만으로 제어 가능

#### 1.3 사전 확인 사항

Secondary DB를 활성화하기 전에 다음을 확인해야 합니다:

1. **Secondary DB 서버 실행 확인**
   - 포트 3307에서 MySQL이 실행 중이어야 합니다
   - `docker-compose.yml` 또는 별도 MySQL 인스턴스 확인

2. **연결 정보 확인**
   - URL, username, password가 올바른지 확인
   - 네트워크 연결 가능 여부 확인

3. **데이터베이스 스키마 확인**
   - Secondary DB에 필요한 테이블이 생성되어 있는지 확인

### 방안 2: 선택적 의존성 주입 (Secondary DB 미사용 시)

Secondary DB를 사용하지 않을 계획이라면, 의존성을 선택적으로 변경하고 null 체크를 추가해야 합니다.

#### 2.1 JwtService 수정 예시

```java
@Autowired(required = false)
@Qualifier("secondaryNamedParameterJdbcTemplate")
private NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
```

#### 2.2 사용 부분에 null 체크 추가

```java
private UserDevice saveUserDeviceWithDualWrite(UserDevice device) {
    return dualMasterWriteService.writeWithDualWrite(
        // Primary: JPA Repository 사용
        () -> userDeviceRepository.save(device),
        
        // Secondary: JdbcTemplate 사용 (null 체크 추가)
        (jdbcTemplate, savedDevice) -> {
            if (secondaryNamedParameterJdbcTemplate == null) {
                // Secondary DB가 비활성화된 경우 로깅만 수행
                System.out.println("[JwtService] Secondary DB가 비활성화되어 Dual Write를 건너뜁니다.");
                return null;
            }
            
            String insertDeviceSql = "INSERT INTO user_devices ...";
            // ... 나머지 코드
            secondaryNamedParameterJdbcTemplate.update(insertDeviceSql, deviceParams);
            return null;
        },
        // ... 나머지 코드
    );
}
```

#### 2.3 영향받는 모든 서비스 수정 필요

다음 서비스들도 동일하게 수정해야 합니다:
- `JwtService`
- `AuthService`
- `BookService`
- `MemoService`
- `UserDeviceService`
- `CompensationRecoveryWorker`
- 모든 Secondary DAO 클래스들

**주의사항:**
- 이 방법은 Dual Write 기능이 비활성화되므로, Secondary DB 동기화가 이루어지지 않습니다
- Primary DB 장애 시 데이터 복구가 어려울 수 있습니다

### 방안 3: 조건부 빈 생성 정책 변경

`DualMasterDataSourceConfig`에서 빈 생성 정책을 변경할 수 있지만, 이는 권장되지 않습니다:

```java
@Bean
@ConditionalOnProperty(
    name = "spring.datasource.secondary.enabled", 
    havingValue = "true", 
    matchIfMissing = true  // 변경: false → true
)
```

**문제점:**
- Secondary DB 연결 실패 시에도 빈이 생성을 시도할 수 있습니다
- 연결 실패 시 예외 처리가 복잡해집니다
- 의도하지 않은 동작을 유발할 수 있습니다

---

## 권장 사항

### 현재 상황에 따른 권장 사항

1. **Secondary DB를 사용할 계획인 경우**
   - ✅ **방안 1 권장**: Secondary DB를 활성화하고 연결을 확인
   - Secondary DB가 정상 작동하면 Dual Write 기능이 정상 동작합니다

2. **Secondary DB를 사용하지 않을 계획인 경우**
   - ✅ **방안 2 권장**: 모든 서비스에서 선택적 의존성으로 변경
   - Dual Write 기능을 비활성화하고 Primary DB만 사용

3. **개발 환경에서만 Secondary DB를 비활성화하고 싶은 경우**
   - 프로파일별 설정을 사용하여 개발 환경에서는 `enabled: false`, 운영 환경에서는 `enabled: true`로 설정

4. **Secondary DB를 사용하되, 테스트 시 비활성화가 필요한 경우**
   - ✅ **권장**: `enabled: ${SECONDARY_DB_ENABLED:true}`로 설정
   - 기본값을 `true`로 설정하여 일반적으로 Secondary DB 사용
   - 테스트 시 환경 변수 `SECONDARY_DB_ENABLED=false`로 비활성화 가능

### 프로파일별 설정 예시

```yaml
# application-dev.yml
spring:
  datasource:
    secondary:
      enabled: false  # 개발 환경에서는 비활성화

# application-prod.yml
spring:
  datasource:
    secondary:
      enabled: true  # 운영 환경에서는 활성화
```

### Secondary DB 테스트 시 비활성화 방법

Dual Write/Read 기능을 테스트하기 위해 Secondary DB를 비활성화해야 하는 경우:

#### 환경 변수로 비활성화

**Windows PowerShell:**
```powershell
# Secondary DB 비활성화
$env:SECONDARY_DB_ENABLED="false"

# 애플리케이션 실행
mvn spring-boot:run
```

**Linux/Mac:**
```bash
export SECONDARY_DB_ENABLED=false
mvn spring-boot:run
```

#### Docker Compose로 Secondary DB 중지

```bash
# Secondary DB만 중지 (Primary는 계속 실행)
docker-compose stop secondary-db

# 또는 특정 컨테이너만 중지
docker stop <secondary-db-container-name>
```

**주의사항:**
- Secondary DB를 중지하면 연결 실패가 발생할 수 있습니다
- `DualMasterDataSourceConfig`의 `initializationFailTimeout=-1` 설정으로 인해 애플리케이션은 시작되지만, 실제 사용 시점에 연결 실패가 발생합니다

#### 테스트 시나리오별 권장 방법

**시나리오 1: Primary DB 장애 테스트 (Secondary DB 활성화)**
```powershell
$env:SECONDARY_DB_ENABLED="true"  # 또는 설정하지 않음 (기본값 true)
# Primary DB 중지
# Secondary DB로 자동 전환 확인
```

**시나리오 2: Secondary DB 비활성화 테스트**
```powershell
$env:SECONDARY_DB_ENABLED="false"
# 애플리케이션 실행
# Secondary DB 빈이 생성되지 않음
# Dual Write 기능이 비활성화됨
```

**시나리오 3: Secondary DB 연결 실패 테스트**
```powershell
$env:SECONDARY_DB_ENABLED="true"
# Docker에서 Secondary DB 중지
# 애플리케이션은 시작되지만 Secondary DB 연결 실패
# Dual Write 시도 시 실패 처리 확인
```

---

## 참고 사항

### Dual Write 아키텍처

현재 시스템은 Dual Master 아키텍처를 사용하고 있습니다:

- **Primary DB**: JPA Repository를 통한 주요 쓰기 작업
- **Secondary DB**: JdbcTemplate을 통한 이중 쓰기 작업
- **목적**: Primary DB 장애 시 Secondary DB로 자동 전환하여 고가용성 확보

### Secondary DB 설정 확인 방법

Secondary DB가 정상적으로 설정되어 있는지 확인:

```bash
# MySQL 연결 테스트
mysql -h localhost -P 3307 -u root -p reading_tracker

# 또는 Docker를 사용하는 경우
docker ps | grep mysql
```

---

## 핵심 원인 요약

### 에러 발생의 근본 원인

1. **설정 문제:**
   - `application.yml`의 `enabled: ${SECONDARY_DB_ENABLED:false}` 설정
   - 환경 변수 `SECONDARY_DB_ENABLED`가 설정되지 않아 기본값 `false` 적용

2. **빈 생성 조건:**
   - `DualMasterDataSourceConfig`에서 `spring.datasource.secondary.enabled=true`일 때만 빈 생성
   - `enabled=false`이므로 빈이 생성되지 않음

3. **필수 의존성 주입:**
   - `JwtService` 등에서 `@Autowired(required=true)`로 필수 주입 시도
   - 빈이 없어서 애플리케이션 시작 실패

### 해결 방안 요약

**가장 권장하는 해결책:**
```yaml
# application.yml
spring:
  datasource:
    secondary:
      enabled: ${SECONDARY_DB_ENABLED:true}  # 기본값을 true로 변경
```

**이유:**
- Secondary DB가 Docker Desktop을 통해 활성화되어 있으므로 기본값을 `true`로 설정
- 테스트 시 환경 변수로 `SECONDARY_DB_ENABLED=false` 설정하여 비활성화 가능
- 코드 수정 없이 설정만으로 제어 가능

### 프로파일별 설정 파일 관련

- `application-dev.yml`과 `application-prod.yml`에 Secondary DB 설정이 없어도 에러에 직접적인 영향 없음
- Spring Boot 설정 상속 메커니즘에 의해 `application.yml`의 설정이 그대로 사용됨
- 프로파일별 설정 파일에 Secondary DB 설정을 정의하는 것은 필수가 아님

---

## 관련 파일

- `src/main/java/com/readingtracker/server/config/DualMasterDataSourceConfig.java`
- `src/main/java/com/readingtracker/server/service/JwtService.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`
