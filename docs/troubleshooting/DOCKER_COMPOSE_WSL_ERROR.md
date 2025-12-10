# Docker Compose WSL 오류 해결 가이드

> **작성일**: 2025-12-09  
> **목적**: Docker Compose 실행 시 발생하는 WSL 관련 오류 해결  
> **오류**: `context deadline exceeded` (WSL 명령 타임아웃)

---

## 오류 메시지

```
bootstrapping in the main distro: listing WSL distros: running WSL command wsl.exe C:\WINDOWS\System32\wsl.exe -l -v --all: context deadline exceeded
```

---

## 원인 분석

이 오류는 Docker Desktop이 WSL(Windows Subsystem for Linux)과 통신하는 과정에서 타임아웃이 발생한 것입니다. 주요 원인:

1. **WSL이 제대로 설치되지 않음**
2. **WSL 배포판이 실행되지 않음**
3. **Docker Desktop과 WSL 간의 통신 문제**
4. **WSL 서비스가 중지됨**

---

## 해결 방법

### 방법 1: WSL 상태 확인 및 재시작

#### 1.1 WSL 배포판 목록 확인

PowerShell 또는 명령 프롬프트에서 실행:

```powershell
wsl --list --verbose
```

또는:

```powershell
wsl -l -v
```

**예상 결과**:
```
  NAME            STATE           VERSION
* Ubuntu-22.04    Running         2
  docker-desktop  Running         2
  docker-desktop-data Running     2
```

#### 1.2 WSL 배포판이 중지된 경우

WSL 배포판이 `Stopped` 상태인 경우:

```powershell
# 특정 배포판 시작
wsl --distribution Ubuntu-22.04

# 또는 기본 배포판 시작
wsl
```

#### 1.3 WSL 서비스 재시작

관리자 권한 PowerShell에서 실행:

```powershell
# WSL 종료
wsl --shutdown

# 잠시 대기 후 다시 확인
wsl --list --verbose
```

---

### 방법 2: Docker Desktop 설정 확인

#### 2.1 Docker Desktop WSL 2 통합 확인

1. Docker Desktop 실행
2. **Settings** (톱니바퀴 아이콘) 클릭
3. **Resources** → **WSL Integration** 선택
4. 다음 확인:
   - ✅ **Enable integration with my default WSL distro** 체크
   - ✅ 사용 중인 WSL 배포판 활성화 (예: Ubuntu-22.04)

#### 2.2 Docker Desktop 재시작

1. Docker Desktop 완전 종료
2. 작업 관리자에서 `Docker Desktop` 프로세스 확인 및 종료
3. Docker Desktop 다시 시작

---

### 방법 3: WSL 재설치 (필요한 경우)

#### 3.1 WSL 업데이트

관리자 권한 PowerShell에서 실행:

```powershell
# WSL 업데이트
wsl --update

# 기본 버전을 WSL 2로 설정
wsl --set-default-version 2
```

#### 3.2 WSL 배포판 재설치

기존 배포판이 손상된 경우:

```powershell
# 배포판 목록 확인
wsl --list --verbose

# 배포판 내보내기 (백업)
wsl --export Ubuntu-22.04 C:\backup\ubuntu.tar

# 배포판 등록 해제
wsl --unregister Ubuntu-22.04

# 배포판 재설치
wsl --install -d Ubuntu-22.04

# 또는 백업에서 복원
wsl --import Ubuntu-22.04 C:\WSL\Ubuntu-22.04 C:\backup\ubuntu.tar
```

---

### 방법 4: Docker Desktop WSL 백엔드 비활성화 (임시 해결책)

WSL 통합이 계속 문제가 되는 경우, Docker Desktop을 Hyper-V 모드로 실행:

1. Docker Desktop **Settings**
2. **General** 탭
3. **Use the WSL 2 based engine** 체크 해제
4. Docker Desktop 재시작

**주의**: 이 방법은 성능이 저하될 수 있으며, WSL 2를 사용하는 것이 권장됩니다.

---

### 방법 5: 시스템 재부팅

위 방법들이 작동하지 않는 경우:

1. 모든 WSL 배포판 종료:
   ```powershell
   wsl --shutdown
   ```

2. Docker Desktop 완전 종료

3. 시스템 재부팅

4. 재부팅 후:
   - Docker Desktop 시작
   - WSL 배포판 시작 확인
   - Docker Compose 실행

---

## Secondary DB 실행 방법

### 정상적인 실행 순서

1. **WSL 확인**:
   ```powershell
   wsl --list --verbose
   ```

2. **Docker Desktop 실행 및 확인**:
   - Docker Desktop이 정상 실행되었는지 확인
   - 시스템 트레이 아이콘 확인

3. **Docker Compose 실행**:
   ```bash
   cd 분산2_프로젝트
   docker-compose up -d
   ```

4. **컨테이너 상태 확인**:
   ```bash
   docker-compose ps
   ```

5. **로그 확인**:
   ```bash
   docker-compose logs -f
   ```

---

## 예방 조치

### 1. WSL 정기 업데이트

```powershell
wsl --update
```

### 2. Docker Desktop 정기 업데이트

Docker Desktop을 최신 버전으로 유지

### 3. WSL 메모리 제한 설정

`.wslconfig` 파일 생성 (`C:\Users\{사용자명}\.wslconfig`):

```ini
[wsl2]
memory=4GB
processors=2
swap=2GB
```

---

## 추가 문제 해결

### WSL 명령이 응답하지 않는 경우

1. **작업 관리자 확인**:
   - `wsl.exe` 프로세스가 멈춰있는지 확인
   - 멈춰있으면 강제 종료 후 재시도

2. **Windows 기능 확인**:
   - **제어판** → **프로그램** → **Windows 기능 켜기/끄기**
   - ✅ **Windows Subsystem for Linux** 체크 확인
   - ✅ **가상 머신 플랫폼** 체크 확인

3. **Windows 업데이트**:
   - Windows를 최신 버전으로 업데이트

---

## 참고 자료

- [Docker Desktop WSL 2 백엔드 문서](https://docs.docker.com/desktop/wsl/)
- [WSL 공식 문서](https://docs.microsoft.com/ko-kr/windows/wsl/)
- [Docker Compose 문서](https://docs.docker.com/compose/)

---

## 빠른 체크리스트

문제 해결 전 확인 사항:

- [ ] WSL이 설치되어 있는가? (`wsl --version`)
- [ ] WSL 배포판이 실행 중인가? (`wsl --list --verbose`)
- [ ] Docker Desktop이 실행 중인가?
- [ ] Docker Desktop의 WSL Integration이 활성화되어 있는가?
- [ ] Windows 기능에서 WSL이 활성화되어 있는가?
- [ ] 시스템을 재부팅했는가?

---

**문서 버전**: 1.0  
**최종 업데이트**: 2025-12-09  
**작성자**: Development Team


