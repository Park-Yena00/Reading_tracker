# Secondary DB Docker 설정 가이드

> **목적**: Docker를 사용하여 Secondary DB를 설정하고 Primary DB와 초기 동기화  
> **예상 소요 시간**: 약 10분

---

## 📋 사전 준비사항

- Docker Desktop이 설치되어 있고 실행 중이어야 합니다
- Primary DB가 포트 3306에서 실행 중이어야 합니다
- Primary DB 비밀번호: `Yenapark1000`

### Docker 실행 상태 확인 방법

**방법 1: Docker 명령어로 확인 (가장 확실한 방법)**

**CMD 또는 PowerShell에서 실행**:
```cmd
# Docker 버전 확인 (설치 여부 확인)
docker --version

# Docker 실행 중인 컨테이너 확인 (실행 상태 확인)
docker ps

# 모든 컨테이너 확인 (중지된 것 포함)
docker ps -a
```

**확인 사항**:
- `docker --version`이 버전 정보를 출력하면 Docker가 설치되어 있습니다
- `docker ps`가 에러 없이 실행되면 Docker Desktop이 실행 중입니다
- 에러 메시지가 나타나면:
  - `Cannot connect to the Docker daemon`: Docker Desktop이 실행되지 않음
  - `'docker' 용어가 인식되지 않습니다`: Docker가 설치되지 않았거나 PATH에 없음

**방법 2: Docker Desktop GUI 확인**

1. 작업 표시줄에서 Docker 아이콘 확인
   - Docker Desktop이 실행 중이면 작업 표시줄에 고래 아이콘이 표시됩니다
   - 아이콘을 클릭하면 Docker Desktop 창이 열립니다

2. Docker Desktop 창에서 확인
   - "Docker Desktop is running" 메시지 확인
   - 실행 중인 컨테이너 목록 확인

**방법 3: 작업 관리자에서 확인**

1. `Ctrl + Shift + Esc`로 작업 관리자 열기
2. "프로세스" 탭에서 다음 프로세스 확인:
   - `Docker Desktop`
   - `com.docker.backend`
   - `dockerd`

**방법 4: 서비스 상태 확인 (Windows)**

**PowerShell에서 실행**:
```powershell
# Docker 관련 서비스 확인
Get-Service | Where-Object {$_.Name -like "*docker*"}
```

**CMD에서 실행**:
```cmd
sc query com.docker.service
```

**Docker가 실행되지 않은 경우**:

1. **Docker Desktop 설치 여부 확인**:
   - 시작 메뉴에서 "Docker Desktop" 검색
   - 검색 결과가 없다면 Docker Desktop이 설치되지 않았을 수 있습니다

2. **Docker Desktop 시작**:
   - 시작 메뉴에서 "Docker Desktop" 검색 후 실행
   - 또는 작업 표시줄의 Docker 아이콘 클릭
   - Docker Desktop이 시작되면 작업 표시줄에 고래 아이콘이 나타납니다

3. **PowerShell 재시작**:
   - Docker Desktop을 시작한 후 PowerShell을 완전히 종료하고 다시 열기
   - 또는 새 PowerShell 창 열기
   - Docker Desktop이 시작되면 환경 변수가 업데이트되므로 PowerShell을 재시작해야 합니다

4. **Docker Desktop이 시작되지 않는 경우**:
   - 컴퓨터 재시작
   - Docker Desktop 재설치
   - Windows 기능에서 WSL 2 활성화 확인

**⚠️ 중요**: Docker Desktop을 시작한 후에는 **PowerShell을 재시작**해야 `docker` 명령어를 사용할 수 있습니다. Docker Desktop이 시작되면 환경 변수가 업데이트되지만, 이미 열려있는 PowerShell 세션에는 반영되지 않습니다.

**💡 빠른 해결 방법 (PowerShell 재시작 없이)**:

PowerShell을 재시작하지 않고 현재 세션에서 바로 Docker를 사용하려면 다음 명령어를 실행하세요:

```powershell
# 현재 PowerShell 세션에 Docker 경로 추가
$env:PATH += ";C:\Program Files\Docker\Docker\resources\bin"

# 확인
docker --version
```

이 명령어는 현재 PowerShell 세션에만 적용됩니다. 새 PowerShell 창을 열면 다시 재시작하거나 위 명령어를 실행해야 합니다.

---

## 🚀 단계별 설정 가이드

### 단계 1: Secondary DB Docker 컨테이너 실행

**CMD 또는 PowerShell에서 실행**:

```cmd
docker run --name mysql-secondary -e MYSQL_ROOT_PASSWORD=Yenapark1000 -e MYSQL_DATABASE=reading_tracker -p 3307:3306 -d mysql:8.0
```

**확인**:
```cmd
docker ps
```

`mysql-secondary` 컨테이너가 `Up` 상태인지 확인합니다.

---

### 단계 2: application.yml 설정 확인

`src/main/resources/application.yml` 파일이 다음처럼 설정되어 있는지 확인:

```yaml
spring:
  datasource:
    secondary:
      url: jdbc:mysql://localhost:3307/reading_tracker?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
      username: root
      password: ${SECONDARY_DB_PASSWORD:Yenapark1000}
```

이미 업데이트되어 있습니다.

---

### 단계 3: Primary DB 데이터 덤프

**PowerShell에서 실행** (UTF-8 인코딩 보장):

```powershell
# UTF-8 코드 페이지 설정 후 덤프 생성
cmd /c "chcp 65001 >nul && mysqldump -u root -pYenapark1000 -h localhost -P 3306 --default-character-set=utf8mb4 --skip-extended-insert reading_tracker > primary_dump.sql"
```

또는 비밀번호를 입력하도록 하려면:

```powershell
cmd /c "chcp 65001 >nul && mysqldump -u root -p -h localhost -P 3306 --default-character-set=utf8mb4 --skip-extended-insert reading_tracker > primary_dump.sql"
```

**확인**: `primary_dump.sql` 파일이 생성되었는지 확인합니다.

```powershell
Test-Path primary_dump.sql
(Get-Item primary_dump.sql).Length
```

---

### 단계 4: Secondary DB에 데이터 복원

**PowerShell에서 실행** (Docker 컨테이너 내부에서 실행):

```powershell
# 1. 덤프 파일을 컨테이너로 복사
docker cp primary_dump.sql mysql-secondary:/tmp/dump.sql

# 2. 컨테이너 내부에서 복원 실행
docker exec mysql-secondary bash -c "mysql -u root -pYenapark1000 reading_tracker < /tmp/dump.sql"
```

또는 비밀번호를 입력하도록 하려면:

```powershell
docker cp primary_dump.sql mysql-secondary:/tmp/dump.sql
docker exec -it mysql-secondary bash
# 컨테이너 내부에서:
mysql -u root -p reading_tracker < /tmp/dump.sql
exit
```

**⚠️ 중요**: PowerShell의 리다이렉션 연산자(`<`)는 Docker 명령어와 함께 사용할 수 없습니다. 따라서 덤프 파일을 컨테이너로 복사한 후 컨테이너 내부에서 실행하는 방법을 사용해야 합니다.

**확인**: 에러 메시지가 없는지 확인합니다.

---

### 단계 5: 데이터 동기화 확인

**MySQL Command Line Client에서 실행**:

```sql
-- Primary DB에서
mysql -u root -p -h localhost -P 3306
USE reading_tracker;
SELECT COUNT(*) AS memo_count FROM memo;
SELECT COUNT(*) AS user_count FROM users;
SELECT COUNT(*) AS book_count FROM books;
SELECT MAX(id) AS max_memo_id FROM memo;
```

```sql
-- Secondary DB에서 (새 창)
mysql -u root -p -h localhost -P 3307
USE reading_tracker;
SELECT COUNT(*) AS memo_count FROM memo;
SELECT COUNT(*) AS user_count FROM users;
SELECT COUNT(*) AS book_count FROM books;
SELECT MAX(id) AS max_memo_id FROM memo;
```

**확인 사항**:
- Primary와 Secondary DB의 데이터 개수가 일치하는지 확인
- Primary와 Secondary DB의 최대 ID가 일치하는지 확인

---

### 단계 6: 서버 재시작 및 연결 확인

1. **Spring Boot 서버 재시작**
2. **서버 Console에서 다음 로그 확인**:
   ```
   [INFO] HikariPool-1 - Start completed.  (Primary DB)
   [INFO] HikariPool-2 - Start completed.  (Secondary DB)
   ```

**확인 사항**:
- 두 DB 모두 연결 성공 로그가 나타나는지 확인
- 에러 메시지가 없는지 확인

---

## ✅ 설정 완료

이제 Secondary DB가 Docker로 실행 중이며, Primary DB와 초기 동기화가 완료되었습니다.

이후 발생하는 모든 신규 CUD(Create, Update, Delete) 작업은 Dual Write 로직을 통해 자동으로 양쪽 DB에 동시에 반영됩니다.

---

## 🔧 문제 해결

### 문제 1: Docker 명령어가 인식되지 않음
- **해결**: Docker Desktop이 실행 중인지 확인
- **해결**: PowerShell을 관리자 권한으로 실행

### 문제 2: 포트 3307이 이미 사용 중
- **해결**: 기존 컨테이너 제거 후 재생성
  ```cmd
  docker stop mysql-secondary
  docker rm mysql-secondary
  docker run --name mysql-secondary -e MYSQL_ROOT_PASSWORD=Yenapark1000 -e MYSQL_DATABASE=reading_tracker -p 3307:3306 -d mysql:8.0
  ```

### 문제 3: mysqldump 명령어가 인식되지 않음
- **해결**: MySQL이 PATH에 추가되어 있는지 확인
- **해결**: MySQL 설치 경로에서 직접 실행

### 문제 4: 데이터 복원 실패
- **해결**: Secondary DB 컨테이너가 실행 중인지 확인 (`docker ps`)
- **해결**: 덤프 파일 경로가 올바른지 확인

---

**설정 완료 후**: `SCENARIO2_DUAL_MASTER_SYNC_TEST.md` 문서의 테스트를 진행하세요.

