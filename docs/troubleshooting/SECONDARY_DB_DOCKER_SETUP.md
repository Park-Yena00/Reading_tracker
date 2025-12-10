# Secondary DB Docker Compose 설정 가이드

> **작성일**: 2025-12-09  
> **목적**: Secondary DB를 Docker Compose로 실행하기 위한 설정 가이드  
> **범위**: MySQL 8.0 컨테이너 설정 및 Master-Master 복제 준비

---

## 개요

Secondary DB는 Docker Compose를 통해 MySQL 8.0 컨테이너로 실행됩니다. Primary DB(로컬 노트북 MySQL)와 Master-Master 복제를 구성하여 데이터 일관성을 유지합니다.

---

## Docker Compose 설정

### 서비스 구성

`docker-compose.yml`에 다음 서비스가 포함됩니다:

1. **Redis**: 세션 및 캐시 관리
2. **Secondary DB**: MySQL 8.0 (포트 3307)

### Secondary DB 설정

```yaml
secondary-db:
  image: mysql:8.0
  container_name: reading-tracker-secondary-db
  ports:
    - "3307:3306"  # 호스트 포트 3307 → 컨테이너 포트 3306
  environment:
    MYSQL_ROOT_PASSWORD: Yenapark1000
    MYSQL_DATABASE: reading_tracker
    MYSQL_USER: reading_tracker
    MYSQL_PASSWORD: Yenapark1000
    TZ: Asia/Seoul
  volumes:
    - secondary-db-data:/var/lib/mysql
    - ./mysql/init:/docker-entrypoint-initdb.d
  command: 
    - --character-set-server=utf8mb4
    - --collation-server=utf8mb4_unicode_ci
    - --default-time-zone=+09:00
    - --server-id=2
    - --log-bin=mysql-bin
    - --binlog-format=ROW
    - --gtid-mode=ON
    - --enforce-gtid-consistency=ON
  restart: unless-stopped
  networks:
    - reading-tracker-network
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-pYenapark1000"]
    interval: 10s
    timeout: 5s
    retries: 5
```

### 주요 설정 설명

- **포트**: 3307 (Primary DB는 3306 사용)
- **데이터베이스**: `reading_tracker`
- **문자셋**: utf8mb4 (한글 지원)
- **타임존**: Asia/Seoul (UTC+9)
- **서버 ID**: 2 (Primary DB는 1)
- **Binary Log**: Master-Master 복제를 위한 설정
- **GTID**: Global Transaction Identifier 활성화

---

## 실행 방법

### 1. Docker Compose 실행

**주의**: 최신 Docker Desktop에서는 `docker-compose` (하이픈 포함) 대신 `docker compose` (하이픈 없이)를 사용합니다.

```bash
cd 분산2_프로젝트

# 방법 1: docker compose (권장, 최신 Docker Desktop)
docker compose up -d

# 방법 2: docker-compose (구버전 또는 별도 설치 필요)
docker-compose up -d
```

**PowerShell에서 실행 시**:
```powershell
cd 분산2_프로젝트
docker compose up -d
```

### 2. 컨테이너 상태 확인

```bash
# docker compose 사용
docker compose ps

# 또는 docker-compose 사용
docker-compose ps
```

**예상 결과**:
```
NAME                          STATUS          PORTS
reading-tracker-redis         Up             0.0.0.0:6379->6379/tcp
reading-tracker-secondary-db  Up (healthy)   0.0.0.0:3307->3306/tcp
```

### 3. 로그 확인

```bash
# Secondary DB 로그 확인
docker compose logs -f secondary-db

# 모든 서비스 로그 확인
docker compose logs -f
```

### 4. 데이터베이스 연결 확인

```bash
# 컨테이너 내부에서 MySQL 접속
docker compose exec secondary-db mysql -u root -pYenapark1000

# 또는 호스트에서 직접 접속 (포트 3307)
mysql -h localhost -P 3307 -u root -pYenapark1000
```

---

## 초기 데이터베이스 설정

### 1. 데이터베이스 생성 확인

컨테이너 시작 시 `reading_tracker` 데이터베이스가 자동으로 생성됩니다.

### 2. Flyway 마이그레이션 실행

Secondary DB에도 Primary DB와 동일한 스키마가 필요합니다. 

**방법 1: 애플리케이션 실행 시 자동 마이그레이션**

애플리케이션 설정에서 Secondary DB에 대한 Flyway 마이그레이션을 활성화할 수 있습니다 (현재는 Primary DB만 마이그레이션).

**방법 2: 수동 마이그레이션**

Primary DB의 스키마를 Secondary DB에 복사:

```bash
# Primary DB 스키마 덤프
mysqldump -h localhost -P 3306 -u root -pYenapark1000 \
  --no-data --routines --triggers reading_tracker > schema.sql

# Secondary DB에 스키마 적용
mysql -h localhost -P 3307 -u root -pYenapark1000 reading_tracker < schema.sql
```

**방법 3: Docker 초기화 스크립트**

`mysql/init/` 디렉토리에 초기화 SQL 스크립트를 추가하면 컨테이너 시작 시 자동 실행됩니다.

---

## Master-Master 복제 설정 (선택사항)

### 주의사항

Master-Master 복제는 데이터 일관성 문제가 발생할 수 있으므로, 현재는 **Custom Dual Write** 방식을 사용합니다.

### Custom Dual Write vs Master-Master 복제

**현재 구현 (Custom Dual Write)**:
- Primary DB에 먼저 쓰기
- 성공 시 Secondary DB에 쓰기
- Secondary 실패 시 Primary 보상 트랜잭션
- 애플리케이션 레벨에서 순서 보장

**Master-Master 복제 (미구현)**:
- MySQL 레벨에서 자동 복제
- 양방향 복제로 인한 충돌 가능성
- 복잡한 충돌 해결 로직 필요

### 향후 Master-Master 복제 설정 시 참고

만약 향후 Master-Master 복제를 구현한다면:

1. **Primary DB 설정** (`my.cnf`):
   ```ini
   server-id=1
   log-bin=mysql-bin
   binlog-format=ROW
   gtid-mode=ON
   enforce-gtid-consistency=ON
   ```

2. **Secondary DB 설정** (Docker Compose에 이미 포함):
   ```yaml
   command: 
     - --server-id=2
     - --log-bin=mysql-bin
     - --binlog-format=ROW
     - --gtid-mode=ON
     - --enforce-gtid-consistency=ON
   ```

3. **복제 사용자 생성**:
   ```sql
   CREATE USER 'replication'@'%' IDENTIFIED BY 'replication_password';
   GRANT REPLICATION SLAVE ON *.* TO 'replication'@'%';
   FLUSH PRIVILEGES;
   ```

---

## 문제 해결

### 1. docker-compose 명령어를 인식하지 못하는 경우

**오류**: `'docker-compose' 용어가 cmdlet, 함수, 스크립트 파일 또는 실행할 수 있는 프로그램 이름으로 인식되지 않습니다`

**해결 방법**:

1. **docker compose 사용** (하이픈 없이, 권장):
   ```powershell
   docker compose up -d
   ```

2. **Docker Desktop 설치 확인**:
   - Docker Desktop이 설치되어 있는지 확인
   - Docker Desktop이 실행 중인지 확인 (시스템 트레이 아이콘)

3. **Docker 버전 확인**:
   ```powershell
   docker --version
   docker compose version
   ```

4. **docker-compose 별도 설치** (필요한 경우):
   - [Docker Compose 공식 설치 가이드](https://docs.docker.com/compose/install/) 참고
   - 또는 Docker Desktop 재설치

### 2. 컨테이너가 시작되지 않는 경우

```bash
# 로그 확인
docker compose logs secondary-db

# 컨테이너 재시작
docker compose restart secondary-db

# 컨테이너 완전 재생성
docker compose down
docker compose up -d
```

### 3. 포트 충돌

포트 3307이 이미 사용 중인 경우:

```powershell
# 포트 사용 확인 (Windows PowerShell)
netstat -ano | findstr :3307

# 또는
Get-NetTCPConnection -LocalPort 3307

# docker-compose.yml에서 포트 변경
ports:
  - "3308:3306"  # 다른 포트 사용
```

그리고 `application.yml`의 Secondary DB URL도 변경:
```yaml
secondary:
  url: jdbc:mysql://localhost:3308/reading_tracker?...
```

### 4. 데이터베이스 연결 실패

**애플리케이션에서 Secondary DB 연결 실패 시**:

1. 컨테이너 상태 확인:
   ```powershell
   docker compose ps secondary-db
   ```

2. 컨테이너 내부에서 MySQL 접속 테스트:
   ```powershell
   docker compose exec secondary-db mysql -u root -pYenapark1000
   ```

3. 네트워크 확인:
   ```powershell
   docker network inspect reading-tracker_reading-tracker-network
   ```

### 4. 데이터 영구 저장 확인

볼륨이 제대로 마운트되었는지 확인:

```bash
# 볼륨 목록 확인
docker volume ls

# 볼륨 상세 정보
docker volume inspect 분산2_프로젝트_secondary-db-data
```

### 5. 컨테이너 완전 삭제 및 재생성

데이터를 초기화하고 다시 시작:

```powershell
# 컨테이너 및 볼륨 삭제
docker compose down -v

# 재시작
docker compose up -d
```

---

## 환경 변수 설정

### Docker Compose 환경 변수

`.env` 파일을 생성하여 비밀번호를 관리할 수 있습니다:

```env
# .env 파일
SECONDARY_DB_PASSWORD=Yenapark1000
PRIMARY_DB_PASSWORD=Yenapark1000
```

`docker-compose.yml`에서 사용:

```yaml
environment:
  MYSQL_ROOT_PASSWORD: ${SECONDARY_DB_PASSWORD:-Yenapark1000}
```

---

## 애플리케이션 설정 확인

### application.yml

Secondary DB 연결 설정이 올바른지 확인:

```yaml
spring:
  datasource:
    secondary:
      url: jdbc:mysql://localhost:3307/reading_tracker?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
      username: root
      password: ${SECONDARY_DB_PASSWORD:Yenapark1000}
```

### 연결 테스트

애플리케이션 실행 후 로그에서 Secondary DB 연결 확인:

```
[INFO] HikariPool-2 - Starting...
[INFO] HikariPool-2 - Start completed.
```

---

## 주의사항

1. **데이터 백업**: Secondary DB 데이터도 정기적으로 백업
2. **포트 충돌**: Primary DB(3306)와 Secondary DB(3307) 포트 확인
3. **비밀번호 보안**: 프로덕션 환경에서는 환경 변수 사용
4. **볼륨 관리**: `docker-compose down -v` 실행 시 데이터 삭제됨

---

## 참고 자료

- [Docker Compose 공식 문서](https://docs.docker.com/compose/)
- [MySQL Docker 공식 이미지](https://hub.docker.com/_/mysql)
- [MySQL Master-Master 복제 가이드](https://dev.mysql.com/doc/refman/8.0/en/replication.html)

---

**문서 버전**: 1.0  
**최종 업데이트**: 2025-12-09  
**작성자**: Development Team

