# Primary DB 중지 및 Secondary DB Read Failover 테스트 가이드

## 목적
Primary DB를 중지하고 Secondary DB에서 읽기 작업이 정상적으로 작동하는지 테스트합니다.

## 사전 준비

### 1. 현재 DB 상태 확인

#### Primary DB 상태 확인 (포트 3306)
```bash
# Windows PowerShell
netstat -an | findstr "3306"

# 또는 MySQL 접속 테스트
mysql -u root -pYenapark1000 -h localhost -P 3306 -e "SELECT 1;"
```

#### Secondary DB 상태 확인 (포트 3307)
```bash
# Docker 컨테이너 상태 확인
docker ps | findstr secondary-db

# 또는 MySQL 접속 테스트
mysql -u root -pYenapark1000 -h localhost -P 3307 -e "SELECT 1;"
```

### 2. 애플리케이션 로그 확인 준비
애플리케이션 로그에서 다음 메시지를 확인할 수 있도록 준비:
- `Primary DB 읽기 실패, Secondary DB로 전환`
- `Secondary DB 읽기 성공 (Failover)`

## 테스트 절차

### 방법 1: MySQL 서비스 중지 (Windows)

#### 1-1. MySQL 서비스 확인
```powershell
# MySQL 서비스 이름 확인
Get-Service | Where-Object {$_.DisplayName -like "*MySQL*"}

# 또는
sc query | findstr MySQL
```

#### 1-2. MySQL 서비스 중지
```powershell
# 방법 A: PowerShell 사용
Stop-Service -Name "MySQL80"  # 서비스 이름은 실제 이름에 맞게 변경

# 방법 B: 서비스 관리자 사용
# Win + R → services.msc → MySQL 서비스 찾기 → 중지

# 방법 C: 명령 프롬프트 사용
net stop MySQL80
```

#### 1-3. Primary DB 연결 확인 (실패해야 함)
```bash
mysql -u root -pYenapark1000 -h localhost -P 3306 -e "SELECT 1;"
# 예상 결과: ERROR 2003 (HY000): Can't connect to MySQL server on 'localhost' (10061)
```

#### 1-4. Secondary DB 연결 확인 (성공해야 함)
```bash
mysql -u root -pYenapark1000 -h localhost -P 3307 -e "SELECT 1;"
# 예상 결과: 정상 연결 및 쿼리 실행
```

### 방법 2: MySQL 포트 차단 (방화벽 사용)

#### 2-1. Windows 방화벽 규칙 추가 (포트 3306 차단)
```powershell
# 인바운드 규칙 추가 (포트 3306 차단)
New-NetFirewallRule -DisplayName "Block MySQL Primary DB" -Direction Inbound -LocalPort 3306 -Protocol TCP -Action Block

# 또는 고급 방화벽 사용
# Win + R → wf.msc → 인바운드 규칙 → 새 규칙 → 포트 → 3306 → 차단
```

#### 2-2. Primary DB 연결 확인 (실패해야 함)
```bash
mysql -u root -pYenapark1000 -h localhost -P 3306 -e "SELECT 1;"
# 예상 결과: 연결 타임아웃 또는 거부
```

#### 2-3. 테스트 완료 후 방화벽 규칙 제거
```powershell
# 방화벽 규칙 제거
Remove-NetFirewallRule -DisplayName "Block MySQL Primary DB"
```

### 방법 3: MySQL 설정 파일 수정 (임시 비활성화)

#### 3-1. MySQL 설정 파일 찾기
```bash
# 일반적인 위치
# C:\ProgramData\MySQL\MySQL Server 8.0\my.ini
# 또는
# C:\Program Files\MySQL\MySQL Server 8.0\my.ini
```

#### 3-2. 포트 변경 (임시)
```ini
# my.ini 파일에서
[mysqld]
port=33060  # 원래 포트 3306에서 다른 포트로 변경
```

#### 3-3. MySQL 서비스 재시작
```powershell
Restart-Service -Name "MySQL80"
```

#### 3-4. 테스트 완료 후 원래 포트로 복구
```ini
# my.ini 파일에서
[mysqld]
port=3306  # 원래 포트로 복구
```

```powershell
Restart-Service -Name "MySQL80"
```

## 테스트 실행

### 1. 애플리케이션 실행
```bash
# Spring Boot 애플리케이션 실행
cd 분산2_프로젝트
./mvnw spring-boot:run
```

### 2. 읽기 작업 테스트

#### 2-1. 메모 조회 테스트
```bash
# API 호출 (인증 토큰 필요)
curl -X GET "http://localhost:8080/api/v1/memos/today-flow" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

#### 2-2. 서재 조회 테스트
```bash
# API 호출
curl -X GET "http://localhost:8080/api/v1/user/books" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

#### 2-3. 사용자 정보 조회 테스트
```bash
# API 호출
curl -X GET "http://localhost:8080/api/v1/users/me" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

### 3. 애플리케이션 로그 확인

#### 예상 로그 메시지
```
[INFO] Primary DB 읽기 실패, Secondary DB로 전환
[INFO] Secondary DB 읽기 성공 (Failover)
```

#### 로그 확인 방법
```bash
# 애플리케이션 콘솔 출력 확인
# 또는 로그 파일 확인 (logback 설정에 따라)
```

## 검증 사항

### ✅ 성공 기준
1. Primary DB 연결 실패 확인
2. Secondary DB 연결 성공 확인
3. 애플리케이션에서 읽기 작업 정상 수행
4. 로그에 "Secondary DB 읽기 성공 (Failover)" 메시지 확인
5. API 응답이 정상적으로 반환됨

### ❌ 실패 시 확인 사항
1. Secondary DB가 실행 중인지 확인
   ```bash
   docker ps | findstr secondary-db
   ```
2. Secondary DB 포트(3307)가 열려있는지 확인
   ```bash
   netstat -an | findstr "3307"
   ```
3. 애플리케이션 설정 확인
   - `application.yml`에서 Secondary DB 설정 확인
   - 포트 3307이 올바르게 설정되어 있는지 확인
4. 애플리케이션 로그에서 에러 메시지 확인

## 테스트 완료 후 복구

### Primary DB 재시작
```powershell
# MySQL 서비스 시작
Start-Service -Name "MySQL80"

# 또는
net start MySQL80
```

### Primary DB 연결 확인
```bash
mysql -u root -pYenapark1000 -h localhost -P 3306 -e "SELECT 1;"
```

### 애플리케이션 재시작 (필요한 경우)
Primary DB가 정상 작동하는지 확인하기 위해 애플리케이션을 재시작할 수 있습니다.

## 주의사항

1. **데이터 일관성**: Primary DB와 Secondary DB 간 데이터 동기화 상태를 확인하세요.
2. **쓰기 작업**: 이 테스트는 읽기 작업만 테스트합니다. 쓰기 작업은 Primary DB가 필요합니다.
3. **복구 시간**: Primary DB를 중지한 상태로 오래 두지 마세요. 데이터 불일치가 발생할 수 있습니다.
4. **프로덕션 환경**: 이 테스트는 개발 환경에서만 수행하세요.

## 추가 테스트 시나리오

### 시나리오 1: Primary DB 일시 중지 후 복구
1. Primary DB 중지
2. 읽기 작업 테스트 (Secondary DB 사용)
3. Primary DB 재시작
4. 읽기 작업 테스트 (Primary DB 사용)

### 시나리오 2: Primary DB와 Secondary DB 모두 실패
1. Primary DB 중지
2. Secondary DB 중지 (`docker stop reading-tracker-secondary-db`)
3. 읽기 작업 테스트 (예상: `DatabaseUnavailableException` 발생)

### 시나리오 3: Primary DB 복구 후 자동 전환
1. Primary DB 중지
2. 읽기 작업 테스트 (Secondary DB 사용)
3. Primary DB 재시작
4. 다음 읽기 작업은 자동으로 Primary DB로 전환됨 (코드 로직에 따라)

## 참고 자료

- [DualMasterReadService.java](../../src/main/java/com/readingtracker/server/service/read/DualMasterReadService.java)
- [application.yml](../../src/main/resources/application.yml)
- [SCENARIO2_DUAL_MASTER_SYNC_TEST.md](./SCENARIO2_DUAL_MASTER_SYNC_TEST.md)


