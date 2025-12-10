# Docker Compose 명령어 인식 오류 해결 가이드

> **작성일**: 2025-12-09  
> **목적**: PowerShell에서 `docker-compose` 명령어를 인식하지 못하는 오류 해결  
> **오류**: `'docker-compose' 용어가 cmdlet, 함수, 스크립트 파일 또는 실행할 수 있는 프로그램 이름으로 인식되지 않습니다`

---

## 오류 메시지

```
docker-compose : 'docker-compose' 용어가 cmdlet, 함수, 스크립트 파일 또는 실행할 수 있는 프로그램 이름으로 인식되지 않습니다.
위치 줄:1 문자:1
+ docker-compose up -d
+ ~~~~~~~~~~~~~~
    + CategoryInfo          : ObjectNotFound: (docker-compose:String) [], CommandNotFoundException
    + FullyQualifiedErrorId : CommandNotFoundException
```

---

## 원인 분석

### 오류 유형 1: docker-compose 명령어 인식 실패

최신 Docker Desktop (버전 20.10 이상)에서는 `docker-compose` (하이픈 포함) 대신 `docker compose` (하이픈 없이, Docker CLI의 서브커맨드)를 사용합니다.

- **구버전**: `docker-compose` (별도 설치 필요)
- **신버전**: `docker compose` (Docker CLI에 내장)

### 오류 유형 2: docker 명령어 자체 인식 실패

`docker` 명령어 자체를 인식하지 못하는 경우:

1. **Docker Desktop이 설치되지 않음**
2. **Docker Desktop이 실행되지 않음**
3. **PATH 환경 변수에 Docker 경로가 없음**
4. **PowerShell 세션을 재시작하지 않음** (설치 후)

---

## 해결 방법

### 방법 1: docker compose 사용 (하이픈 없이) - 권장

최신 Docker Desktop을 사용하는 경우:

```powershell
# 하이픈 없이 사용
docker compose up -d

# 다른 명령어도 동일하게
docker compose ps
docker compose logs -f
docker compose down
```

### 방법 2: Docker Desktop 설치 및 실행 확인

#### 2.1 Docker Desktop 설치 확인

1. **시작 메뉴에서 확인**:
   - 시작 메뉴에서 "Docker Desktop" 검색
   - 설치되어 있지 않으면 [Docker Desktop 다운로드](https://www.docker.com/products/docker-desktop/)

2. **설치 경로 확인**:
   - 기본 설치 경로: `C:\Program Files\Docker\Docker\`
   - `docker.exe` 파일 존재 확인: `C:\Program Files\Docker\Docker\resources\bin\docker.exe`

#### 2.2 Docker Desktop 실행 확인

1. **시스템 트레이 확인**:
   - 시스템 트레이(작업 표시줄 오른쪽)에서 Docker 아이콘 확인
   - 아이콘이 없으면 Docker Desktop 실행

2. **작업 관리자 확인**:
   - 작업 관리자(Ctrl+Shift+Esc) → 프로세스 탭
   - "Docker Desktop" 프로세스 확인

3. **Docker Desktop 수동 실행**:
   - 시작 메뉴 → Docker Desktop 실행
   - 또는 `C:\Program Files\Docker\Docker\Docker Desktop.exe` 실행

#### 2.3 Docker 버전 확인

Docker Desktop이 실행된 후:

```powershell
docker --version
docker compose version
```

**예상 결과**:
```
Docker version 24.0.0, build abc123
Docker Compose version v2.23.0
```

**오류 발생 시**: Docker Desktop이 설치되지 않았거나 실행되지 않은 상태입니다.

### 방법 3: docker-compose 별도 설치 (구버전 호환)

`docker-compose` (하이픈 포함)를 사용해야 하는 경우:

#### 3.1 pip를 통한 설치 (Python 필요)

```powershell
pip install docker-compose
```

#### 3.2 직접 다운로드

1. [Docker Compose 릴리스 페이지](https://github.com/docker/compose/releases)에서 다운로드
2. `docker-compose.exe`를 PATH에 추가

#### 3.3 Chocolatey를 통한 설치

```powershell
choco install docker-compose
```

---

## 명령어 비교

### docker compose (신버전, 권장)

```powershell
# 서비스 시작
docker compose up -d

# 서비스 중지
docker compose down

# 상태 확인
docker compose ps

# 로그 확인
docker compose logs -f

# 특정 서비스 재시작
docker compose restart secondary-db

# 볼륨 포함 삭제
docker compose down -v
```

### docker-compose (구버전)

```powershell
# 서비스 시작
docker-compose up -d

# 서비스 중지
docker-compose down

# 상태 확인
docker-compose ps

# 로그 확인
docker-compose logs -f

# 특정 서비스 재시작
docker-compose restart secondary-db

# 볼륨 포함 삭제
docker-compose down -v
```

---

## 빠른 확인 체크리스트

문제 해결 전 확인 사항:

- [ ] Docker Desktop이 설치되어 있는가?
  - 시작 메뉴에서 "Docker Desktop" 검색
  - 또는 `C:\Program Files\Docker\Docker\` 경로 확인
- [ ] Docker Desktop이 실행 중인가?
  - 시스템 트레이 아이콘 확인
  - 작업 관리자에서 Docker 프로세스 확인
- [ ] PowerShell을 재시작했는가?
  - Docker Desktop 설치/실행 후 PowerShell 재시작 필요
- [ ] `docker --version` 명령어가 작동하는가?
  - 작동하지 않으면 PATH 환경 변수 확인
- [ ] `docker compose version` 명령어가 작동하는가?
  - Docker Desktop이 정상 실행되어야 함

---

## 권장 사용법

**최신 Docker Desktop 사용 시**:

```powershell
# 프로젝트 디렉토리로 이동
cd 분산2_프로젝트

# 서비스 시작 (하이픈 없이)
docker compose up -d

# 상태 확인
docker compose ps

# 로그 확인
docker compose logs -f secondary-db
```

---

## 추가 문제 해결

### Docker Desktop이 설치되어 있지 않은 경우

1. **Docker Desktop 다운로드**:
   - [Docker Desktop 다운로드 페이지](https://www.docker.com/products/docker-desktop/)
   - Windows용 설치 파일 다운로드

2. **설치 과정**:
   - 설치 파일 실행
   - 설치 옵션에서 "Use WSL 2 instead of Hyper-V" 선택 (Windows 10/11)
   - 설치 완료 후 시스템 재부팅

3. **설치 후 확인**:
   - Docker Desktop 실행
   - 시스템 트레이에서 Docker 아이콘 확인
   - PowerShell 새로 열기
   - `docker --version` 명령어 테스트

### Docker Desktop이 실행되지 않는 경우

1. **작업 관리자 확인**:
   - Ctrl+Shift+Esc로 작업 관리자 열기
   - "Docker Desktop" 프로세스 확인
   - 멈춰있는 프로세스가 있으면 종료

2. **Docker Desktop 재시작**:
   - 시스템 트레이에서 Docker 아이콘 우클릭
   - "Quit Docker Desktop" 선택
   - 잠시 대기 후 Docker Desktop 다시 실행

3. **시스템 재부팅**:
   - 위 방법이 작동하지 않으면 시스템 재부팅

### Docker Desktop 설치 후에도 명령어가 인식되지 않는 경우

1. **PowerShell 완전 재시작**:
   - 모든 PowerShell 창 닫기
   - PowerShell 새로 열기 (관리자 권한 권장)

2. **환경 변수 새로고침**:
   ```powershell
   # 현재 세션에서 PATH 새로고침
   $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
   docker --version
   ```

3. **직접 경로로 실행 테스트**:
   ```powershell
   & "C:\Program Files\Docker\Docker\resources\bin\docker.exe" --version
   ```
   
   이 명령어가 작동하면 PATH 문제입니다.

4. **시스템 재부팅**:
   - 환경 변수 변경 후 시스템 재부팅이 가장 확실한 방법입니다.

### PATH 환경 변수 문제

Docker가 PATH에 추가되지 않은 경우:

#### 방법 1: PowerShell 세션 재시작

Docker Desktop 설치 후 PowerShell을 완전히 종료하고 다시 시작:

1. 모든 PowerShell 창 닫기
2. PowerShell 새로 열기
3. `docker --version` 명령어 다시 시도

#### 방법 2: 환경 변수 수동 추가

1. **시스템 속성** → **고급** → **환경 변수**
2. **시스템 변수** → **Path** 편집
3. Docker Desktop 설치 경로 추가:
   - `C:\Program Files\Docker\Docker\resources\bin`
   - 또는 `C:\Program Files\Docker\Docker\resources\cli-plugins`
4. **확인** 클릭
5. PowerShell 재시작

#### 방법 3: PowerShell에서 임시 PATH 추가

현재 세션에서만 사용:

```powershell
$env:Path += ";C:\Program Files\Docker\Docker\resources\bin"
docker --version
```

#### 방법 4: Docker Desktop 재설치

위 방법들이 작동하지 않으면:

1. Docker Desktop 완전 제거
2. 시스템 재부팅
3. Docker Desktop 재설치
4. 설치 완료 후 시스템 재부팅
5. PowerShell 새로 열기

---

## 참고 자료

- [Docker Compose 공식 문서](https://docs.docker.com/compose/)
- [Docker Desktop 설치 가이드](https://docs.docker.com/desktop/install/windows-install/)
- [Docker Compose 명령어 참조](https://docs.docker.com/compose/reference/)

---

**문서 버전**: 1.0  
**최종 업데이트**: 2025-12-09  
**작성자**: Development Team

