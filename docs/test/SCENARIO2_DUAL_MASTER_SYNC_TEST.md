# 시나리오 2: MySQL 이중화 및 양방향 동기화 테스트 가이드

> **목적**: MySQL Dual Master 구성에서 Custom Dual Write 및 Read Failover 기능을 시각적으로 검증  
> **필요 도구**: MySQL 8.0 Command Line Client, 서버 Console, 웹 UI, 브라우저(F12 개발자 도구)  
> **예상 소요 시간**: 약 1시간

---

## 📋 테스트 전 준비사항

### 1. MySQL 데이터베이스 설정 확인

#### 1-1. application.yml 파일에서 DB 연결 정보 확인
**서버 Console 또는 IDE에서 확인**:
- 파일 위치: `src/main/resources/application.yml`
- 다음 설정 확인:
  ```yaml
  spring:
    datasource:
      primary:
        url: jdbc:mysql://localhost:3306/reading_tracker
        username: root
        password: [비밀번호]
      secondary:
        url: jdbc:mysql://localhost:3307/reading_tracker
        username: root
        password: [비밀번호]
  ```
- Primary DB 포트: 일반적으로 `3306`
- Secondary DB 포트: 일반적으로 `3307` (또는 설정에 따라 다를 수 있음)

#### 1-2. MySQL 서버 실행 상태 확인
**CMD 또는 PowerShell에서 실행**:
```cmd
# MySQL 서비스 상태 확인 (Windows)
sc query MySQL80

# 또는 MySQL 프로세스 확인
tasklist | findstr mysql

# 포트 3306과 3307이 열려있는지 확인
netstat -an | findstr "3306"
netstat -an | findstr "3307"
```

**확인 사항**:
- 포트 3306 (Primary DB)이 `LISTENING` 상태인지 확인
- 포트 3307 (Secondary DB)이 `LISTENING` 상태인지 확인
- **중요**: Secondary DB는 별도의 MySQL 인스턴스가 필요합니다. 포트 3307에서 MySQL이 실행되고 있지 않다면 Secondary DB를 설정해야 합니다.

#### 1-3. Primary DB 접속 테스트
**MySQL Command Line Client에서 실행**:
```sql
-- Primary DB 접속 (포트 3306)
mysql -u root -p -h localhost -P 3306

-- 비밀번호 입력 (예: Yenapark1000)
-- 접속 성공 시 다음 명령어 실행
SHOW DATABASES;
USE reading_tracker;
SHOW TABLES;
SELECT COUNT(*) FROM memo;
```

**확인 사항**:
- Primary DB 접속이 성공하는지 확인
- `reading_tracker` 데이터베이스가 존재하는지 확인
- 필요한 테이블들이 존재하는지 확인

#### 1-4. Secondary DB 설정 확인 및 접속 테스트

**⚠️ 중요**: Secondary DB는 별도의 MySQL 인스턴스가 필요합니다. 

**방법 : Docker를 사용한 Secondary DB 설정 (권장 - 간단한 방법)**

현재 Redis를 Docker로 관리하고 있으므로, Secondary DB도 Docker를 사용하여 구성하는 것이 가장 논리적이고 효율적입니다. Primary DB가 호스트에서 직접 실행되고, Secondary DB가 Docker 컨테이너에서 실행되는 것은 현대적인 마이크로서비스 또는 분산 환경에서 매우 흔하며, 아키텍처상 아무런 문제가 없습니다.

**아키텍처 관점**: 애플리케이션은 두 DB가 **다른 포트(3306/3307)**에서 독립적으로 실행된다는 사실만 중요하게 여깁니다. Docker는 이 독립적인 인스턴스를 격리된 방식으로 설정하는 가장 깔끔한 방법입니다.

**테스트 관점**: Docker 컨테이너를 사용하면 Secondary DB 장애 시뮬레이션 (예: `docker stop mysql-secondary`)을 손쉽게 할 수 있어, 구현하신 Dual Write 및 Read Failover 테스트를 완벽하게 검증할 수 있습니다.

#### 🚀 Secondary DB 설정 및 연결 가이드 (3단계)

**단계 1: Secondary DB Docker 인스턴스 실행**

**CMD 또는 PowerShell에서 실행**:
```cmd
# Secondary DB Docker 컨테이너 실행
docker run --name mysql-secondary ^
  -e MYSQL_ROOT_PASSWORD=Yenapark1000 ^
  -e MYSQL_DATABASE=reading_tracker ^
  -p 3307:3306 ^
  -d mysql:8.0

# Docker 컨테이너 실행 확인
docker ps

# 컨테이너 로그 확인 (선택)
docker logs mysql-secondary
```

**확인 사항**:
- `docker ps` 명령어로 `mysql-secondary` 컨테이너가 `Up` 상태인지 확인
- 포트 매핑이 `0.0.0.0:3307->3306/tcp`로 표시되는지 확인

**단계 2: application.yml 설정 확인 및 수정**

**서버 Console 또는 IDE에서 확인**:
- 파일 위치: `src/main/resources/application.yml`
- Secondary DB 비밀번호를 실제 비밀번호로 설정:

```yaml
spring:
  datasource:
    secondary:
      url: jdbc:mysql://localhost:3307/reading_tracker?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
      username: root
      password: Yenapark1000  # 환경 변수 대신 직접 설정 (또는 ${SECONDARY_DB_PASSWORD:Yenapark1000})
```

**또는 환경 변수로 설정** (권장):
```cmd
# PowerShell에서
$env:SECONDARY_DB_PASSWORD="Yenapark1000"

# CMD에서
set SECONDARY_DB_PASSWORD=Yenapark1000
```

**단계 3: 초기 데이터 동기화 (Initial Synchronization)**

**⚠️ 중요**: Secondary DB는 빈 데이터베이스로 시작하므로, Primary DB의 기존 데이터를 Secondary DB로 복사해야 합니다. 이를 **초기 동기화(Initial Synchronization)** 또는 **Bulk Load**라고 합니다.

**3-1. Primary DB 데이터 덤프**

**CMD 또는 PowerShell에서 실행**:
```cmd
# Primary DB에서 데이터 덤프 (스키마 + 데이터)
mysqldump -u root -pYenapark1000 -h localhost -P 3306 reading_tracker > primary_dump.sql

# 또는 비밀번호를 입력하도록 하려면
mysqldump -u root -p -h localhost -P 3306 reading_tracker > primary_dump.sql
```

**3-2. Secondary DB에 데이터 복원**

**CMD 또는 PowerShell에서 실행**:
```cmd
# Secondary DB에 데이터 복원
mysql -u root -pYenapark1000 -h localhost -P 3307 reading_tracker < primary_dump.sql

# 또는 비밀번호를 입력하도록 하려면
mysql -u root -p -h localhost -P 3307 reading_tracker < primary_dump.sql
```

**3-3. 데이터 동기화 확인**

**MySQL Command Line Client에서 확인**:
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
-- Secondary DB에서
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
- 주요 테이블의 데이터가 일치하는지 확인

**3-4. 샘플 데이터 비교 (선택)**

**MySQL Command Line Client에서 실행**:
```sql
-- Primary DB에서
SELECT id, user_id, content, memo_start_time FROM memo ORDER BY id DESC LIMIT 5;
```

```sql
-- Secondary DB에서
SELECT id, user_id, content, memo_start_time FROM memo ORDER BY id DESC LIMIT 5;
```

**확인 사항**:
- 두 DB의 샘플 데이터가 완전히 일치하는지 확인

**💡 초기 동기화 완료 후**

초기 동기화가 완료되면, 이후 발생하는 모든 신규 CUD(Create, Update, Delete) 작업은 Dual Write 로직을 통해 자동으로 양쪽 DB에 동시에 반영됩니다.

**⚠️ 주의사항**:
- Primary DB의 기존 데이터를 삭제하지 마세요. 초기 동기화는 Primary DB의 데이터를 Secondary DB로 복사하는 작업입니다.
- ID가 연속적이지 않아도 문제없습니다. Primary DB의 현재 상태를 그대로 Secondary DB에 복사하면 됩니다.


**접속 실패 시 확인 사항**:
- `ERROR 2003 (HY000): Can't connect to MySQL server on 'localhost:3307' (10061)`
  - **원인**: 포트 3307에서 MySQL 서버가 실행되고 있지 않음
  - **해결**: Secondary DB를 설정하거나 Docker를 사용하여 실행

**Secondary DB 비밀번호 확인**:
- `application.yml`에서 `SECONDARY_DB_PASSWORD` 환경 변수 확인
- 환경 변수가 설정되지 않았다면 기본값 `root` 사용
- Primary DB 비밀번호와 동일하게 설정하려면:
  ```yaml
  spring:
    datasource:
      secondary:
        password: ${SECONDARY_DB_PASSWORD:Yenapark1000}
  ```
  또는 환경 변수로 설정:
  ```cmd
  set SECONDARY_DB_PASSWORD=Yenapark1000
  ```

**확인 사항**:
- Secondary DB 접속이 성공하는지 확인
- `reading_tracker` 데이터베이스가 존재하는지 확인 (없다면 생성 필요)
- 필요한 테이블들이 존재하는지 확인 (없다면 Primary DB에서 스키마 복사 필요)

### 2. 서버 실행 확인

#### 2-1. Spring Boot 서버 시작
**서버 Console 또는 IDE에서**:
- Spring Boot 애플리케이션 실행
- 또는 CMD에서:
  ```cmd
  cd [프로젝트_루트_경로]
  mvn spring-boot:run
  ```

#### 2-2. 서버 시작 로그 확인
**서버 Console에서 다음 로그 확인**:
```
[INFO] Started ReadingTrackerApplication in X.XXX seconds
[INFO] HikariPool-1 - Starting...
[INFO] HikariPool-1 - Start completed.
[INFO] HikariPool-2 - Starting...
[INFO] HikariPool-2 - Start completed.
```

**확인 사항**:
- `HikariPool-1` (Primary DB) 연결 성공 로그 확인
- `HikariPool-2` (Secondary DB) 연결 성공 로그 확인
- 에러 메시지가 없는지 확인

#### 2-3. 서버 헬스체크 확인
**브라우저 또는 CMD에서**:
```cmd
# 헬스체크 엔드포인트 호출
curl http://localhost:8080/api/v1/health

# 또는 브라우저에서 접속
# http://localhost:8080/api/v1/health
```

**확인 사항**:
- HTTP 200 OK 응답 확인
- 서버가 정상적으로 실행 중인지 확인

### 3. MySQL Command Line Client 준비

#### 3-1. Primary DB 접속
**MySQL Command Line Client에서 실행**:
```sql
-- Primary DB 접속
mysql -u root -p -h localhost -P 3306

-- 비밀번호 입력 후 다음 명령어 실행
USE reading_tracker;

-- 현재 데이터 확인
SELECT COUNT(*) AS memo_count FROM memo;
SELECT COUNT(*) AS user_count FROM users;
SELECT COUNT(*) AS book_count FROM books;
```

#### 3-2. Secondary DB 접속 (새 창 또는 새 연결)
**MySQL Command Line Client 새 창에서 실행**:
```sql
-- Secondary DB 접속
mysql -u root -p -h localhost -P 3307

-- 비밀번호 입력 후 다음 명령어 실행
USE reading_tracker;

-- 현재 데이터 확인
SELECT COUNT(*) AS memo_count FROM memo;
SELECT COUNT(*) AS user_count FROM users;
SELECT COUNT(*) AS book_count FROM books;
```

**확인 사항**:
- Primary와 Secondary DB의 데이터 개수가 일치하는지 확인 (정상 상태)
- 또는 최소한 두 DB 모두 접속이 가능한지 확인

#### 3-3. DB 연결 정보 확인 (선택)
**MySQL Command Line Client에서 실행**:
```sql
-- Primary DB에서
SHOW VARIABLES LIKE 'port';
SELECT DATABASE();

-- Secondary DB에서
SHOW VARIABLES LIKE 'port';
SELECT DATABASE();
```

**확인 사항**:
- Primary DB 포트: `3306`
- Secondary DB 포트: `3307` (또는 설정에 따라 다를 수 있음)
- 현재 사용 중인 데이터베이스: `reading_tracker`

### 4. 테스트 계정 준비

#### 4-1. 웹 UI에서 로그인
**브라우저에서**:
1. 웹 애플리케이션 접속: `http://localhost:8080`
2. 로그인 페이지에서 테스트 계정으로 로그인
3. 로그인 성공 확인

#### 4-2. 내 서재에 책 등록 확인
**웹 UI에서**:
1. 내 서재 페이지로 이동
2. 책이 등록되어 있는지 확인
3. 책이 없다면 책 검색 후 등록

#### 4-3. DB에서 사용자 및 책 정보 확인 (선택)
**MySQL Command Line Client에서 실행**:
```sql
-- Primary DB에서
SELECT id, login_id, name FROM users WHERE login_id = '[테스트_계정_로그인ID]';

-- 사용자 ID 확인 후
SELECT ub.id, ub.user_id, b.title 
FROM user_books ub 
JOIN books b ON ub.book_id = b.id 
WHERE ub.user_id = [사용자_ID];
```

**확인 사항**:
- 사용자 계정이 DB에 존재하는지 확인
- 사용자의 서재에 책이 등록되어 있는지 확인
- 최소 1개 이상의 책이 등록되어 있어야 테스트 가능

---

## 테스트 1: Happy Path - Primary/Secondary 동시 쓰기 성공 확인

### 목적
정상적인 상황에서 Dual Write가 올바르게 작동하고, Primary와 Secondary DB의 데이터가 일치하는지 확인

### 단계별 테스트 절차

#### 1단계: 메모 작성 전 DB 상태 확인
1. **MySQL Command Line Client에서 Primary DB 접속**
   ```sql
   mysql -u root -p -h localhost -P 3306
   USE reading_tracker;
   ```

2. **Primary DB에서 현재 메모 개수 확인**
   ```sql
   SELECT COUNT(*) FROM memo;
   SELECT MAX(id) FROM memo;
   ```

3. **MySQL Command Line Client에서 Secondary DB 접속** (새 창 또는 새 연결)
   ```sql
   mysql -u root -p -h localhost -P 3307
   USE reading_tracker;
   ```

4. **Secondary DB에서 현재 메모 개수 확인**
   ```sql
   SELECT COUNT(*) FROM memo;
   SELECT MAX(id) FROM memo;
   ```

5. **확인 사항**
   - Primary와 Secondary DB의 메모 개수가 일치하는지 확인
   - 최대 ID가 일치하는지 확인

#### 2단계: 웹 UI에서 메모 작성
1. **웹 브라우저에서 메모 작성 페이지 열기**
2. **메모 내용 입력** (예: "Dual Write 테스트 메모")
3. **저장** 버튼 클릭

#### 3단계: 서버 Console에서 Dual Write 로그 확인
1. **서버 Console 창 확인**
   - 다음과 같은 로그가 순차적으로 나타나는지 확인:
     ```
     [MemoController] POST /api/v1/memos 요청 수신
     [MemoService] createMemo 호출
     [DualMasterWriteService] Primary DB 쓰기 시작
     [DualMasterWriteService] Primary DB 쓰기 성공: memoId=xxx
     [DualMasterWriteService] Secondary DB 쓰기 시작
     [DualMasterWriteService] Secondary DB 쓰기 성공: memoId=xxx
     [DualMasterWriteService] Dual Write 완료: memoId=xxx
     ```

2. **확인 사항**
   - Primary DB 쓰기가 먼저 실행되는지 확인
   - Primary DB 쓰기 성공 후 Secondary DB 쓰기가 실행되는지 확인
   - 두 DB 모두 성공하는지 확인

#### 4단계: Primary DB에서 데이터 확인
1. **MySQL Command Line Client에서 Primary DB 접속**
   ```sql
   SELECT * FROM memo WHERE id = (SELECT MAX(id) FROM memo);
   ```

2. **확인 사항**
   - 방금 작성한 메모가 Primary DB에 저장되었는지 확인
   - 다음 필드들이 올바르게 저장되었는지 확인:
     - `id`: 메모 ID
     - `user_id`: 사용자 ID
     - `book_id`: 책 ID
     - `content`: 메모 내용
     - `memo_start_time`: 작성 시간

#### 5단계: Secondary DB에서 데이터 확인
1. **MySQL Command Line Client에서 Secondary DB 접속**
   ```sql
   SELECT * FROM memo WHERE id = (SELECT MAX(id) FROM memo);
   ```

2. **확인 사항**
   - 방금 작성한 메모가 Secondary DB에도 저장되었는지 확인
   - Primary DB와 Secondary DB의 모든 필드가 완전히 일치하는지 확인:
     ```sql
     -- Primary DB에서
     SELECT id, user_id, book_id, content, memo_start_time FROM memo WHERE id = xxx;
     
     -- Secondary DB에서
     SELECT id, user_id, book_id, content, memo_start_time FROM memo WHERE id = xxx;
     ```
   - 두 결과가 완전히 일치하는지 확인

#### 6단계: memo_tags 테이블 확인
1. **Primary DB에서**
   ```sql
   SELECT * FROM memo_tags WHERE memo_id = (SELECT MAX(id) FROM memo);
   ```

2. **Secondary DB에서**
   ```sql
   SELECT * FROM memo_tags WHERE memo_id = (SELECT MAX(id) FROM memo);
   ```

3. **확인 사항**
   - Primary와 Secondary DB의 `memo_tags` 데이터가 일치하는지 확인

---

## 테스트 2: Secondary Write Failure - 보상 트랜잭션 확인

### 목적
Secondary DB 쓰기 실패 시 Primary DB의 보상 트랜잭션이 올바르게 실행되어 데이터 불일치를 방지하는지 확인

### 단계별 테스트 절차

#### 1단계: Secondary DB 연결 차단 준비
1. **MySQL Command Line Client에서 Secondary DB 접속**
   ```sql
   mysql -u root -p -h localhost -P 3307
   ```

2. **Secondary DB 사용자 권한 확인**
   ```sql
   SELECT User, Host FROM mysql.user WHERE User = 'root';
   ```

#### 2단계: Secondary DB 비밀번호 변경 (임시 차단)
1. **MySQL Command Line Client에서 Secondary DB 접속**
   ```sql
   ALTER USER 'root'@'localhost' IDENTIFIED BY 'wrong_password';
   FLUSH PRIVILEGES;
   ```

   **주의**: 이 방법은 실제로 Secondary DB 연결을 차단합니다. 테스트 후 원래 비밀번호로 복구해야 합니다.

2. **또는 application.yml에서 Secondary DB 연결 정보를 잘못된 값으로 임시 변경**
   ```yaml
   spring:
     datasource:
       secondary:
         url: jdbc:mysql://localhost:3307/reading_tracker_wrong
         username: wrong_user
         password: wrong_password
   ```
   - 서버 재시작 필요

#### 3단계: 웹 UI에서 메모 작성 시도
1. **웹 브라우저에서 메모 작성 페이지 열기**
2. **메모 내용 입력** (예: "Secondary 실패 테스트 메모")
3. **저장** 버튼 클릭

#### 4단계: 서버 Console에서 보상 트랜잭션 로그 확인
1. **서버 Console 창 확인**
   - 다음과 같은 로그가 순차적으로 나타나는지 확인:
     ```
     [MemoController] POST /api/v1/memos 요청 수신
     [MemoService] createMemo 호출
     [DualMasterWriteService] Primary DB 쓰기 시작
     [DualMasterWriteService] Primary DB 쓰기 성공: memoId=xxx
     [DualMasterWriteService] Secondary DB 쓰기 시작
     [DualMasterWriteService] Secondary DB 쓰기 실패: ...
     [DualMasterWriteService] 보상 트랜잭션 실행 시작: memoId=xxx
     [DualMasterWriteService] 보상 트랜잭션 실행 성공: memoId=xxx (Primary에서 DELETE)
     [DualMasterWriteService] DatabaseWriteException 발생
     ```

2. **확인 사항**
   - Primary DB 쓰기는 성공했는지 확인
   - Secondary DB 쓰기가 실패했는지 확인
   - 보상 트랜잭션이 실행되었는지 확인
   - 보상 트랜잭션이 성공했는지 확인

#### 5단계: Primary DB에서 데이터 확인 (보상 트랜잭션 검증)
1. **MySQL Command Line Client에서 Primary DB 접속**
   ```sql
   SELECT * FROM memo WHERE id = (SELECT MAX(id) FROM memo);
   ```

2. **확인 사항**
   - **데이터가 없어야 함** (보상 트랜잭션으로 DELETE됨)
   - 또는 최대 ID가 이전과 동일한지 확인 (새로운 메모가 생성되지 않았음)

#### 6단계: 웹 UI에서 에러 메시지 확인
1. **웹 브라우저에서 확인**
   - 에러 메시지가 표시되는지 확인
   - 메모가 저장되지 않았는지 확인

#### 7단계: Secondary DB 연결 복구
1. **MySQL Command Line Client에서 Secondary DB 접속**
   ```sql
   ALTER USER 'root'@'localhost' IDENTIFIED BY '원래_비밀번호';
   FLUSH PRIVILEGES;
   ```

2. **또는 application.yml을 원래 설정으로 복구**
   - 서버 재시작

---

## 테스트 3: 보상 트랜잭션 실패 - Recovery Queue 발행 확인

### 목적
보상 트랜잭션이 실패할 경우 Recovery Queue에 이벤트가 발행되는지 확인

### 단계별 테스트 절차

#### 1단계: Primary DB 연결 차단 준비
1. **application.yml에서 Primary DB 연결 정보를 잘못된 값으로 임시 변경**
   ```yaml
   spring:
     datasource:
       primary:
         url: jdbc:mysql://localhost:3306/reading_tracker_wrong
         username: wrong_user
         password: wrong_password
   ```
   - 서버 재시작 필요

   **주의**: 이 방법은 Primary DB 연결을 차단하므로, 보상 트랜잭션도 실패하게 됩니다.

#### 2단계: Secondary DB 연결 차단
1. **application.yml에서 Secondary DB 연결 정보를 잘못된 값으로 임시 변경**
   ```yaml
   spring:
     datasource:
       secondary:
         url: jdbc:mysql://localhost:3307/reading_tracker_wrong
         username: wrong_user
         password: wrong_password
   ```
   - 서버 재시작

#### 3단계: 웹 UI에서 메모 작성 시도
1. **웹 브라우저에서 메모 작성 페이지 열기**
2. **메모 내용 입력** (예: "보상 트랜잭션 실패 테스트")
3. **저장** 버튼 클릭

#### 4단계: 서버 Console에서 Recovery Queue 발행 로그 확인
1. **서버 Console 창 확인**
   - 다음과 같은 로그가 순차적으로 나타나는지 확인:
     ```
     [MemoController] POST /api/v1/memos 요청 수신
     [MemoService] createMemo 호출
     [DualMasterWriteService] Primary DB 쓰기 시작
     [DualMasterWriteService] Primary DB 쓰기 성공: memoId=xxx
     [DualMasterWriteService] Secondary DB 쓰기 시작
     [DualMasterWriteService] Secondary DB 쓰기 실패: ...
     [DualMasterWriteService] 보상 트랜잭션 실행 시작: memoId=xxx
     [DualMasterWriteService] 보상 트랜잭션 실행 실패: ...
     [DualMasterWriteService] CRITICAL: 보상 트랜잭션 실패로 인한 데이터 불일치 발생
     [DualMasterWriteService] Recovery Queue에 이벤트 발행: entityType=Memo, entityId=xxx
     [RecoveryQueueService] 이벤트 발행 완료: action=SECONDARY_SYNC_RETRY
     ```

2. **확인 사항**
   - Primary DB 쓰기는 성공했는지 확인
   - Secondary DB 쓰기가 실패했는지 확인
   - 보상 트랜잭션이 실패했는지 확인
   - Recovery Queue에 이벤트가 발행되었는지 확인

#### 5단계: Primary DB에서 데이터 확인
1. **MySQL Command Line Client에서 Primary DB 접속** (원래 설정으로 복구 후)
   ```sql
   SELECT * FROM memo WHERE id = (SELECT MAX(id) FROM memo);
   ```

2. **확인 사항**
   - **데이터가 존재해야 함** (보상 트랜잭션이 실패했으므로)
   - 이는 데이터 불일치 상태임

#### 6단계: DB 연결 복구
1. **application.yml을 원래 설정으로 복구**
   - 서버 재시작

---

## 테스트 4: CompensationRecoveryWorker 자동 복구 확인

### 목적
Recovery Queue에 발행된 이벤트를 CompensationRecoveryWorker가 처리하여 데이터를 복구하는지 확인

### 단계별 테스트 절차

#### 1단계: Recovery Queue에 이벤트 발행 (테스트 3 참조)
1. **보상 트랜잭션 실패 시나리오 실행** (테스트 3의 1-5단계 참조)
2. **Recovery Queue에 이벤트가 발행되었는지 확인**

#### 2단계: CompensationRecoveryWorker 실행 대기
1. **서버 Console에서 스케줄러 실행 대기**
   - `CompensationRecoveryWorker`는 1분마다 실행됩니다 (`@Scheduled(fixedDelay = 60000)`)
   - 또는 서버 Console에서 다음과 같은 로그 확인:
     ```
     [CompensationRecoveryWorker] 복구 큐 처리 시작: 1 개 이벤트
     [CompensationRecoveryWorker] Secondary 동기화 재시도 시작: entityType=Memo, entityId=xxx
     [CompensationRecoveryWorker] Secondary DB에서 데이터 삭제 시도
     [CompensationRecoveryWorker] Secondary 동기화 재시도 성공: entityType=Memo, entityId=xxx, deletedRows=1
     [CompensationRecoveryWorker] 복구 완료: entityType=Memo, entityId=xxx
     ```

2. **확인 사항**
   - Worker가 1분마다 실행되는지 확인
   - 이벤트를 처리하는지 확인
   - Secondary DB에서 데이터를 삭제하는지 확인

#### 3단계: Secondary DB에서 데이터 확인
1. **MySQL Command Line Client에서 Secondary DB 접속**
   ```sql
   SELECT * FROM memo WHERE id = xxx;
   ```

2. **확인 사항**
   - **데이터가 없어야 함** (Worker가 삭제했으므로)
   - 또는 이미 데이터가 없었다면 로그에서 "이미 정리되었거나 존재하지 않음" 메시지 확인

#### 4단계: Primary DB에서 데이터 확인
1. **MySQL Command Line Client에서 Primary DB 접속**
   ```sql
   SELECT * FROM memo WHERE id = xxx;
   ```

2. **확인 사항**
   - 데이터가 여전히 존재하는지 확인 (Primary DB는 변경되지 않음)
   - 이는 의도된 동작입니다 (Secondary DB만 정리)

---

## 테스트 5: Read Failover - Primary DB 장애 시 Secondary DB로 전환 확인

### 목적
Primary DB 장애 시 Read Failover가 올바르게 작동하여 Secondary DB에서 데이터를 읽을 수 있는지 확인

### 단계별 테스트 절차

#### 1단계: 정상 상태에서 메모 작성
1. **웹 UI에서 메모 작성** (테스트 1 참조)
2. **Primary와 Secondary DB 모두에 데이터가 저장되었는지 확인**

#### 2단계: Primary DB 연결 차단
1. **application.yml에서 Primary DB 연결 정보를 잘못된 값으로 임시 변경**
   ```yaml
   spring:
     datasource:
       primary:
         url: jdbc:mysql://localhost:3306/reading_tracker_wrong
         username: wrong_user
         password: wrong_password
   ```
   - 서버 재시작

#### 3단계: 웹 UI에서 메모 조회
1. **웹 브라우저에서 메모 목록 페이지 열기**
2. **메모 목록이 정상적으로 표시되는지 확인**

#### 4단계: 서버 Console에서 Read Failover 로그 확인
1. **서버 Console 창 확인**
   - 다음과 같은 로그가 순차적으로 나타나는지 확인:
     ```
     [MemoController] GET /api/v1/memos/books/{userBookId} 요청 수신
     [MemoService] getAllBookMemos 호출
     [DualMasterReadService] Primary DB 읽기 시도
     [DualMasterReadService] Primary DB 읽기 실패: ...
     [DualMasterReadService] Primary DB 읽기 실패, Secondary DB로 전환
     [DualMasterReadService] Secondary DB 읽기 시도
     [DualMasterReadService] Secondary DB 읽기 성공
     [MemoService] 메모 목록 반환: count=xxx
     ```

2. **확인 사항**
   - Primary DB 읽기가 실패했는지 확인
   - Secondary DB로 자동 전환되었는지 확인
   - Secondary DB 읽기가 성공했는지 확인
   - 메모 목록이 정상적으로 반환되었는지 확인

#### 5단계: 웹 UI에서 메모 확인
1. **웹 브라우저에서 확인**
   - 메모 목록이 정상적으로 표시되는지 확인
   - 메모 내용이 올바른지 확인
   - 에러 메시지가 나타나지 않는지 확인

#### 6단계: Primary DB 연결 복구
1. **application.yml을 원래 설정으로 복구**
   - 서버 재시작

---

## 테스트 6: AlertService 연동 확인 (최대 재시도 횟수 초과)

### 목적
CompensationRecoveryWorker가 최대 재시도 횟수(10회)를 초과하여 실패할 경우 AlertService가 알림을 발송하는지 확인

### 단계별 테스트 절차

#### 1단계: Recovery Queue에 이벤트 발행
1. **보상 트랜잭션 실패 시나리오 실행** (테스트 3 참조)
2. **Recovery Queue에 이벤트가 발행되었는지 확인**

#### 2단계: Secondary DB 연결을 계속 차단
1. **application.yml에서 Secondary DB 연결 정보를 잘못된 값으로 유지**
   - 서버 재시작

#### 3단계: CompensationRecoveryWorker 재시도 모니터링
1. **서버 Console에서 Worker 실행 로그 확인**
   - Worker가 1분마다 실행되는지 확인
   - 각 실행마다 다음과 같은 로그 확인:
     ```
     [CompensationRecoveryWorker] 복구 큐 처리 시작: 1 개 이벤트
     [CompensationRecoveryWorker] Secondary 동기화 재시도 시작: entityType=Memo, entityId=xxx
     [CompensationRecoveryWorker] 복구 재시도 실패: entityId=xxx, retryCount=1
     [CompensationRecoveryWorker] 재시도 큐에 다시 추가: entityId=xxx
     ```

2. **재시도 횟수 확인**
   - `retryCount`가 점진적으로 증가하는지 확인
   - 최대 10회까지 재시도되는지 확인

#### 4단계: 최대 재시도 횟수 초과 시 AlertService 알림 확인
1. **서버 Console에서 10회 재시도 후 로그 확인**
   - 다음과 같은 로그가 나타나는지 확인:
     ```
     [CompensationRecoveryWorker] 복구 재시도 실패: entityId=xxx, retryCount=10
     [CompensationRecoveryWorker] CRITICAL: 복구 작업 최대 재시도 횟수 초과: entityType=Memo, entityId=xxx, retryCount=10, 수동 개입 필요
     [AlertService] CRITICAL ALERT: 복구 작업 최대 재시도 횟수 초과 - [상세 메시지]
     [CompensationRecoveryWorker] 이벤트를 실패 상태로 표시: entityId=xxx
     ```

2. **확인 사항**
   - 최대 재시도 횟수(10회)를 초과했는지 확인
   - CRITICAL 로그가 기록되었는지 확인
   - AlertService가 호출되었는지 확인
   - 알림 메시지에 다음 정보가 포함되었는지 확인:
     - Entity Type
     - Entity ID
     - Action
     - Target DB
     - Failure Time
     - Error Message

#### 5단계: Secondary DB 연결 복구
1. **application.yml을 원래 설정으로 복구**
   - 서버 재시작

---

## 테스트 7: DELETE 작업 시 Secondary Cleanup 확인

### 목적
DELETE 작업에서 Secondary DB 삭제 실패 시 Recovery Queue에 이벤트가 발행되고, CompensationRecoveryWorker가 이를 처리하는지 확인

### 단계별 테스트 절차

#### 1단계: 정상 상태에서 메모 작성
1. **웹 UI에서 메모 작성** (테스트 1 참조)
2. **Primary와 Secondary DB 모두에 데이터가 저장되었는지 확인**

#### 2단계: Secondary DB 연결 차단
1. **application.yml에서 Secondary DB 연결 정보를 잘못된 값으로 임시 변경**
   ```yaml
   spring:
     datasource:
       secondary:
         url: jdbc:mysql://localhost:3307/reading_tracker_wrong
         username: wrong_user
         password: wrong_password
   ```
   - 서버 재시작

#### 3단계: 웹 UI에서 메모 삭제
1. **웹 브라우저에서 메모 삭제**
   - 메모 카드에서 **삭제** 버튼 클릭
   - 확인 대화상자에서 **확인** 클릭

#### 4단계: 서버 Console에서 DELETE_SECONDARY_CLEANUP 이벤트 발행 확인
1. **서버 Console 창 확인**
   - 다음과 같은 로그가 순차적으로 나타나는지 확인:
     ```
     [MemoController] DELETE /api/v1/memos/{memoId} 요청 수신
     [MemoService] deleteMemo 호출
     [DualMasterWriteService] Primary DB 쓰기 시작 (DELETE)
     [DualMasterWriteService] Primary DB 쓰기 성공: memoId=xxx
     [DualMasterWriteService] Secondary DB 쓰기 시작 (DELETE)
     [DualMasterWriteService] Secondary DB 쓰기 실패: ...
     [DualMasterWriteService] DELETE_SECONDARY_CLEANUP 이벤트 발행: memoId=xxx
     [RecoveryQueueService] 이벤트 발행 완료: action=DELETE_SECONDARY_CLEANUP
     ```

2. **확인 사항**
   - Primary DB에서 DELETE가 성공했는지 확인
   - Secondary DB에서 DELETE가 실패했는지 확인
   - DELETE_SECONDARY_CLEANUP 이벤트가 발행되었는지 확인

#### 5단계: Primary DB에서 데이터 확인
1. **MySQL Command Line Client에서 Primary DB 접속**
   ```sql
   SELECT * FROM memo WHERE id = xxx;
   ```

2. **확인 사항**
   - **데이터가 없어야 함** (Primary DB에서 삭제됨)

#### 6단계: Secondary DB에서 데이터 확인 (복구 전)
1. **MySQL Command Line Client에서 Secondary DB 접속** (원래 설정으로 복구 후)
   ```sql
   SELECT * FROM memo WHERE id = xxx;
   ```

2. **확인 사항**
   - **데이터가 여전히 존재해야 함** (Secondary DB에서 삭제 실패)

#### 7단계: CompensationRecoveryWorker 실행 대기
1. **서버 Console에서 Worker 실행 대기** (1분)
2. **로그 확인**:
   ```
   [CompensationRecoveryWorker] 복구 큐 처리 시작: 1 개 이벤트
   [CompensationRecoveryWorker] DELETE_SECONDARY_CLEANUP 이벤트 처리 시작: memoId=xxx
   [CompensationRecoveryWorker] Secondary DB에서 memo_tags 삭제: memoId=xxx
   [CompensationRecoveryWorker] Secondary DB에서 memo 삭제: memoId=xxx
   [CompensationRecoveryWorker] Secondary 유령 데이터 정리 성공: entityType=Memo, entityId=xxx, deletedRows=1
   ```

#### 8단계: Secondary DB에서 데이터 확인 (복구 후)
1. **MySQL Command Line Client에서 Secondary DB 접속**
   ```sql
   SELECT * FROM memo WHERE id = xxx;
   ```

2. **확인 사항**
   - **데이터가 없어야 함** (Worker가 삭제했으므로)

#### 9단계: Secondary DB 연결 복구
1. **application.yml을 원래 설정으로 복구**
   - 서버 재시작

---

## 테스트 결과 확인 체크리스트

### ✅ 시나리오 2 구현 검증 항목

- [ ] Happy Path: Primary/Secondary 동시 쓰기 성공
- [ ] Primary와 Secondary DB의 데이터 일치 확인
- [ ] Secondary Write Failure: 보상 트랜잭션 실행
- [ ] 보상 트랜잭션 실패: Recovery Queue 발행
- [ ] CompensationRecoveryWorker: 자동 복구 작동
- [ ] Read Failover: Primary 장애 시 Secondary로 전환
- [ ] AlertService: 최대 재시도 횟수 초과 시 알림 발송
- [ ] DELETE 작업: Secondary Cleanup 이벤트 발행 및 처리

---

## 문제 해결 가이드

### 문제 1: Primary DB 연결 실패
- **확인 사항**: application.yml의 Primary DB 설정 확인
- **확인 사항**: MySQL 서버가 실행 중인지 확인
- **해결 방법**: MySQL 서버 재시작 또는 연결 정보 수정

### 문제 2: Secondary DB 연결 실패
- **확인 사항**: application.yml의 Secondary DB 설정 확인
- **확인 사항**: Secondary DB 포트가 올바른지 확인 (기본: 3307)
- **해결 방법**: Secondary DB 서버 재시작 또는 연결 정보 수정

### 문제 3: 보상 트랜잭션이 실행되지 않음
- **확인 사항**: 서버 Console에서 Secondary DB 쓰기 실패 로그 확인
- **확인 사항**: DualMasterWriteService의 보상 트랜잭션 로직 확인
- **해결 방법**: 서버 재시작 후 다시 시도

### 문제 4: CompensationRecoveryWorker가 실행되지 않음
- **확인 사항**: 서버 Console에서 스케줄러 로그 확인
- **확인 사항**: `@EnableScheduling` 어노테이션이 설정 클래스에 있는지 확인
- **해결 방법**: 서버 재시작 후 1분 대기

### 문제 5: Read Failover가 작동하지 않음
- **확인 사항**: 서버 Console에서 Primary DB 읽기 실패 로그 확인
- **확인 사항**: DualMasterReadService의 Failover 로직 확인
- **해결 방법**: 서버 재시작 후 다시 시도

---

## Primary DB 중단 및 보상 트랜잭션 롤백 과정 확인

### Primary DB 중단 방법

Primary DB는 로컬 노트북에서 MySQL로 구성되어 있으므로, 다음 방법으로 중단할 수 있습니다:

#### 방법 1: Windows 서비스 중지 (권장)

**CMD 또는 PowerShell에서 실행 (관리자 권한 필요)**:
```cmd
# MySQL 서비스 중지
net stop MySQL80

# 또는
sc stop MySQL80

# 서비스 상태 확인
sc query MySQL80
```

**서비스 상태 확인**:
- `STATE`가 `STOPPED`로 표시되면 중단 성공
- `STATE`가 `RUNNING`이면 아직 실행 중

#### 방법 2: MySQL 프로세스 종료

**CMD 또는 PowerShell에서 실행**:
```cmd
# MySQL 프로세스 확인
tasklist | findstr mysql

# MySQL 프로세스 종료 (PID 확인 후)
taskkill /PID [PID번호] /F

# 또는 모든 MySQL 프로세스 종료
taskkill /IM mysqld.exe /F
```

**주의사항**:
- 이 방법은 데이터 손실 위험이 있으므로 테스트 환경에서만 사용
- 프로덕션 환경에서는 절대 사용하지 마세요

#### Primary DB 재시작 방법

**CMD 또는 PowerShell에서 실행 (관리자 권한 필요)**:
```cmd
# MySQL 서비스 시작
net start MySQL80

# 또는
sc start MySQL80

# 서비스 상태 확인
sc query MySQL80
```

**확인 사항**:
- `STATE`가 `RUNNING`으로 표시되면 재시작 성공
- 서버 Console에서 Primary DB 연결 성공 로그 확인

---

### 보상 트랜잭션 롤백 과정 확인 방법

Secondary DB 쓰기 실패 시 Primary DB에서 보상 트랜잭션이 실행되어 데이터가 롤백됩니다. 이 과정을 실시간으로 확인할 수 있는 방법은 다음과 같습니다:

#### 사전 준비

1. **두 개의 MySQL Command Line Client 창 열기**
   - 창 1: Primary DB 모니터링용 (롤백 전/후 데이터 확인)
   - 창 2: Secondary DB 상태 확인용 (선택사항)

2. **Primary DB 접속**
   ```sql
   -- Primary DB 접속 (포트 3306)
   mysql -u root -p -h localhost -P 3306
   
   -- 비밀번호 입력 후
   USE reading_tracker;
   ```

#### 테스트 시나리오: 메모 작성 시 Secondary DB 실패

**단계 1: 롤백 전 데이터 확인 쿼리 준비**

Primary DB 모니터링 창에서 다음 쿼리를 준비합니다:

```sql
-- 현재 메모 개수 확인
SELECT COUNT(*) as memo_count FROM memo;

-- 최근 생성된 메모 확인 (최신 5개)
SELECT id, user_id, user_book_id, page_number, content, memo_start_time, created_at 
FROM memo 
ORDER BY created_at DESC 
LIMIT 5;

-- 특정 사용자의 메모 확인 (테스트용)
SELECT id, user_id, user_book_id, page_number, content, memo_start_time, created_at 
FROM memo 
WHERE user_id = [사용자ID]
ORDER BY created_at DESC;
```

**단계 2: Secondary DB 중단**

**PowerShell 또는 CMD에서 실행**:
```cmd
# Secondary DB Docker 컨테이너 중지
docker stop mysql-secondary

# 또는 Secondary DB가 별도 MySQL 인스턴스인 경우
# 해당 MySQL 서비스를 중지
```

**단계 3: 웹 UI에서 메모 작성**

1. 웹 브라우저에서 로그인
2. "오늘의 흐름" 화면에서 메모 작성 시도
3. 서버 Console에서 다음 로그 확인:
   ```
   ERROR ... DualMasterWriteService : Secondary DB 쓰기 실패, Primary에 보상 트랜잭션 실행
   INFO  ... DualMasterWriteService : 보상 트랜잭션 실행 성공
   ```

**단계 4: 롤백 전 데이터 확인 (보상 트랜잭션 실행 직전)**

메모 작성 직후, 보상 트랜잭션이 실행되기 전에 Primary DB 모니터링 창에서 다음 쿼리를 실행합니다:

```sql
-- 롤백 전: 최근 생성된 메모 확인
SELECT id, user_id, user_book_id, page_number, content, memo_start_time, created_at 
FROM memo 
ORDER BY created_at DESC 
LIMIT 5;

-- 롤백 전: 메모 개수 확인
SELECT COUNT(*) as memo_count_before_rollback FROM memo;
```

**예상 결과**:
- 새로 작성한 메모가 Primary DB에 존재함
- 메모 개수가 증가함

**단계 5: 롤백 후 데이터 확인 (보상 트랜잭션 실행 직후)**

보상 트랜잭션이 실행된 후 (서버 Console에서 "보상 트랜잭션 실행 성공" 로그 확인 후), Primary DB 모니터링 창에서 동일한 쿼리를 다시 실행합니다:

```sql
-- 롤백 후: 최근 생성된 메모 확인
SELECT id, user_id, user_book_id, page_number, content, memo_start_time, created_at 
FROM memo 
ORDER BY created_at DESC 
LIMIT 5;

-- 롤백 후: 메모 개수 확인
SELECT COUNT(*) as memo_count_after_rollback FROM memo;
```

**예상 결과**:
- 새로 작성한 메모가 Primary DB에서 삭제됨 (롤백됨)
- 메모 개수가 롤백 전과 동일함 (또는 감소함)

#### 실시간 모니터링 스크립트 (선택사항)

Primary DB 모니터링을 자동화하려면 다음 스크립트를 사용할 수 있습니다:

**PowerShell 스크립트 (monitor-primary-db.ps1)**:
```powershell
# Primary DB 모니터링 스크립트
# 사용법: .\monitor-primary-db.ps1

$mysqlCmd = "mysql -u root -pYenapark1000 -h localhost -P 3306 reading_tracker"

while ($true) {
    Write-Host "`n=== $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -ForegroundColor Cyan
    Write-Host "메모 개수:" -ForegroundColor Yellow
    & $mysqlCmd -e "SELECT COUNT(*) as memo_count FROM memo;"
    
    Write-Host "`n최근 메모 5개:" -ForegroundColor Yellow
    & $mysqlCmd -e "SELECT id, user_id, LEFT(content, 50) as content_preview, created_at FROM memo ORDER BY created_at DESC LIMIT 5;"
    
    Start-Sleep -Seconds 2
}
```

**사용 방법**:
1. 위 스크립트를 `monitor-primary-db.ps1` 파일로 저장
2. PowerShell에서 실행: `.\monitor-primary-db.ps1`
3. 메모 작성 시도 시 실시간으로 데이터 변화 확인

#### 다른 엔티티 타입 확인 방법

메모 외의 다른 엔티티(예: User, UserShelfBook)에 대한 보상 트랜잭션도 동일한 방법으로 확인할 수 있습니다:

**User 테이블 확인**:
```sql
-- 롤백 전/후 사용자 확인
SELECT id, login_id, email, name, status, last_login_at, updated_at 
FROM users 
ORDER BY updated_at DESC 
LIMIT 5;
```

**UserShelfBook 테이블 확인**:
```sql
-- 롤백 전/후 사용자 책 확인
SELECT id, user_id, book_id, category, reading_progress, updated_at 
FROM user_books 
ORDER BY updated_at DESC 
LIMIT 5;
```

#### 주의사항

1. **타이밍 이슈**
   - 보상 트랜잭션은 매우 빠르게 실행되므로, 롤백 전 데이터 확인이 어려울 수 있습니다
   - 가능하면 자동화 스크립트를 사용하거나, 쿼리를 미리 준비해두고 빠르게 실행하세요

2. **트랜잭션 격리 수준**
   - MySQL의 기본 격리 수준(REPEATABLE READ)에서는 다른 세션에서 커밋된 데이터를 즉시 볼 수 없을 수 있습니다
   - 필요시 `SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;` 설정

3. **Secondary DB 복구**
   - 테스트 완료 후 반드시 Secondary DB를 재시작하세요:
     ```cmd
     docker start mysql-secondary
     ```

---

## 주의사항

1. **DB 연결 차단 테스트 후 반드시 복구**
   - 테스트 완료 후 application.yml을 원래 설정으로 복구해야 합니다.
   - 그렇지 않으면 서버가 정상적으로 작동하지 않습니다.

2. **테스트 데이터 정리**
   - 테스트로 생성한 메모는 테스트 완료 후 삭제하는 것을 권장합니다.
   - MySQL Command Line Client에서:
     ```sql
     DELETE FROM memo WHERE content LIKE '%테스트%';
     ```

3. **서버 재시작**
   - application.yml을 수정한 경우 반드시 서버를 재시작해야 합니다.

4. **Primary DB 중단 테스트 주의**
   - Primary DB를 중단한 후 반드시 재시작하세요
   - 프로덕션 환경에서는 절대 사용하지 마세요

---

**테스트 완료 후**: 모든 테스트가 통과하면 시나리오 2 구현이 완료된 것으로 확인됩니다.

