# Fault Tolerance 테스트 가이드 (로컬 환경)

> **목적**: 배포 없이 로컬 환경에서 네트워크 장애를 시뮬레이션하고, 시스템의 장애 허용 능력을 테스트  
> **환경**: Windows 10/11 (PowerShell), Linux, macOS 모두 지원

---

## 📋 목차

1. [개요](#개요)
2. [로컬 환경에서 네트워크 장애 시뮬레이션 방법](#로컬-환경에서-네트워크-장애-시뮬레이션-방법)
3. [Windows 환경 도구](#windows-환경-도구)
4. [크로스 플랫폼 도구](#크로스-플랫폼-도구)
5. [Docker를 이용한 네트워크 시뮬레이션](#docker를-이용한-네트워크-시뮬레이션)
6. [프록시 서버를 이용한 장애 시뮬레이션](#프록시-서버를-이용한-장애-시뮬레이션)
7. [모니터링 및 로깅](#모니터링-및-로깅)
8. [테스트 시나리오](#테스트-시나리오)
9. [실제 구현 예제](#실제-구현-예제)

---

## 개요

### 배포 없이 가능한 장애 시뮬레이션

✅ **가능한 것들:**
- 네트워크 연결 끊김 (Connection Loss)
- 네트워크 지연 (Latency)
- 패킷 손실 (Packet Loss)
- 대역폭 제한 (Bandwidth Throttling)
- 타임아웃 (Timeout)
- 서버 다운 시뮬레이션
- 간헐적 연결 문제

### 테스트 시나리오

1. **네트워크 끊김**: 서버와 클라이언트 간 연결 완전 차단
2. **지연 시간 증가**: 느린 네트워크 환경 시뮬레이션
3. **패킷 손실**: 불안정한 네트워크 환경
4. **서버 응답 없음**: 서버 다운 또는 무응답
5. **재연결 테스트**: 네트워크 복구 후 자동 재연결

---

## 로컬 환경에서 네트워크 장애 시뮬레이션 방법

### 방법 1: 서버 중지/시작 (가장 간단)

```powershell
# Windows PowerShell에서

# 1. Spring Boot 서버 중지 (작업 관리자 또는 Ctrl+C)
# 또는 포트 차단
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# 2. 서버 다시 시작
mvn spring-boot:run

# 3. 웹사이트/앱에서 API 호출 시도 → 네트워크 에러 확인
```

**장점:**
- ✅ 설정 불필요
- ✅ 즉시 테스트 가능
- ✅ 실제 서버 다운 상황과 유사

**단점:**
- ⚠️ 수동으로 제어해야 함
- ⚠️ 지연 시간이나 패킷 손실은 시뮬레이션 불가

---

## Windows 환경 도구

### 1. Clumsy (추천) ⭐

**다운로드**: https://jagt.github.io/clumsy/

#### 특징
- ✅ GUI 인터페이스로 쉽게 사용
- ✅ 실시간 네트워크 장애 주입
- ✅ 특정 포트만 필터링 가능
- ✅ 무료

#### 사용 방법

1. **설치 및 실행**
   ```powershell
   # Clumsy 다운로드 후 실행
   # GitHub에서 최신 릴리즈 다운로드
   ```

2. **설정 예시**

   ```
   필터 설정:
   outbound and tcp.DstPort == 8080
   
   또는 모든 트래픽:
   outbound
   ```

3. **장애 유형 설정**
   - **Lag (지연)**: 100ms, 500ms, 1000ms 등
   - **Drop (패킷 손실)**: 10%, 50%, 100%
   - **Throttle (대역폭 제한)**: 100KB/s, 10KB/s 등
   - **Corrupt (패킷 손상)**: 5%, 10%
   - **Duplicate (패킷 중복)**: 5%

#### 테스트 시나리오

**시나리오 1: 네트워크 지연**
```
Filter: outbound and tcp.DstPort == 8080
Lag: 500ms
Drop: 0%
```

**시나리오 2: 패킷 손실**
```
Filter: outbound and tcp.DstPort == 8080
Lag: 0ms
Drop: 20%
```

**시나리오 3: 완전 차단**
```
Filter: outbound and tcp.DstPort == 8080
Drop: 100%
```

#### 주의사항
- 관리자 권한으로 실행 필요
- 방화벽 설정 확인

---

### 2. Windows Firewall을 이용한 포트 차단

```powershell
# PowerShell (관리자 권한 필요)

# 1. 아웃바운드 연결 차단
New-NetFirewallRule -DisplayName "Block Port 8080" `
    -Direction Outbound `
    -LocalPort 8080 `
    -Protocol TCP `
    -Action Block

# 2. 인바운드 연결 차단
New-NetFirewallRule -DisplayName "Block Port 8080 Inbound" `
    -Direction Inbound `
    -LocalPort 8080 `
    -Protocol TCP `
    -Action Block

# 3. 규칙 삭제 (테스트 후)
Remove-NetFirewallRule -DisplayName "Block Port 8080"
Remove-NetFirewallRule -DisplayName "Block Port 8080 Inbound"
```

---

### 3. Windows 네트워크 어댑터 비활성화

```powershell
# PowerShell (관리자 권한 필요)

# 네트워크 어댑터 확인
Get-NetAdapter

# 특정 어댑터 비활성화 (네트워크 완전 차단)
Disable-NetAdapter -Name "Wi-Fi" -Confirm:$false

# 다시 활성화
Enable-NetAdapter -Name "Wi-Fi" -Confirm:$false
```

**주의**: 실제 인터넷 연결이 끊어지므로 주의!

---

## 크로스 플랫폼 도구

### 1. Charles Proxy (유료, 평가판 30일)

**다운로드**: https://www.charlesproxy.com/

#### 특징
- ✅ 강력한 HTTP 프록시 도구
- ✅ 네트워크 지연, 차단 시뮬레이션
- ✅ 요청/응답 모니터링
- ✅ Breakpoint로 요청 중단 가능

#### 사용 방법

1. **Charles 실행**
   - HTTP 프록시로 설정 (포트 8888)
   - 브라우저/앱에서 프록시 설정

2. **Throttling 설정**
   ```
   Proxy → Throttle Settings
   - Enable Throttling 체크
   - Bandwidth: 56kbps (느린 연결)
   - Latency: 500ms (지연)
   ```

3. **Breakpoint 설정**
   ```
   Proxy → Breakpoints
   - Enable Breakpoints
   - 특정 URL 선택하여 요청 중단
   ```

---

### 2. mitmproxy (무료, 오픈소스)

**설치**: 
```bash
# Windows (pip)
pip install mitmproxy

# 또는
choco install mitmproxy
```

#### 사용 방법

```bash
# 기본 실행
mitmproxy

# 특정 포트로 실행
mitmproxy -p 8080

# 인터셉트 모드로 특정 요청 차단
# mitmproxy 웹 인터페이스에서 설정
```

---

### 3. Postman / Thunder Client (API 테스트)

**Postman**: https://www.postman.com/  
**Thunder Client**: VS Code 확장

#### 네트워크 에러 시뮬레이션

1. **Timeout 설정**
   ```javascript
   // Postman Pre-request Script
   pm.request.timeout = 1000; // 1초 타임아웃
   ```

2. **에러 응답 시뮬레이션**
   - Postman Mock Server 사용
   - 500, 503, 504 등 에러 응답 반환

3. **요청 중단**
   - 네트워크 끊김 시뮬레이션
   - AbortController 사용

---

## Docker를 이용한 네트워크 시뮬레이션

### Docker Compose로 장애 시뮬레이션 환경 구성

#### docker-compose.yml

```yaml
version: '3.8'

services:
  # Spring Boot 서버
  spring-boot-server:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/reading_tracker
    depends_on:
      - db
    networks:
      - app-network

  # MySQL 데이터베이스
  db:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=root
      - MYSQL_DATABASE=reading_tracker
    ports:
      - "3306:3306"
    networks:
      - app-network

  # Toxiproxy (네트워크 장애 시뮬레이터)
  toxiproxy:
    image: ghcr.io/shopify/toxiproxy:latest
    ports:
      - "8474:8474"  # Toxiproxy API
      - "8081:8081"  # 프록시된 서버 포트
    networks:
      - app-network
```

### Toxiproxy 사용

**Toxiproxy**: https://github.com/shopify/toxiproxy

#### 설치 및 실행

```bash
# Docker로 실행
docker run -d --name toxiproxy -p 8474:8474 -p 8081:8081 \
  ghcr.io/shopify/toxiproxy

# 또는 docker-compose 사용
docker-compose up toxiproxy -d
```

#### 프록시 생성 및 장애 주입

```bash
# 1. 프록시 생성 (Spring Boot 서버를 프록시)
curl -X POST http://localhost:8474/proxies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "spring-boot-proxy",
    "listen": "0.0.0.0:8081",
    "upstream": "spring-boot-server:8080"
  }'

# 2. 지연 시간 추가 (500ms)
curl -X POST http://localhost:8474/proxies/spring-boot-proxy/toxics \
  -H "Content-Type: application/json" \
  -d '{
    "name": "latency",
    "type": "latency",
    "attributes": {
      "latency": 500
    }
  }'

# 3. 패킷 손실 (10%)
curl -X POST http://localhost:8474/proxies/spring-boot-proxy/toxics \
  -H "Content-Type: application/json" \
  -d '{
    "name": "loss",
    "type": "timeout",
    "attributes": {
      "timeout": 0
    }
  }'

# 4. 완전 차단
curl -X DELETE http://localhost:8474/proxies/spring-boot-proxy/toxics/latency
curl -X POST http://localhost:8474/proxies/spring-boot-proxy/toxics \
  -H "Content-Type: application/json" \
  -d '{
    "name": "down",
    "type": "down"
  }'

# 5. 복구
curl -X DELETE http://localhost:8474/proxies/spring-boot-proxy/toxics/down

# 6. 프록시 삭제
curl -X DELETE http://localhost:8474/proxies/spring-boot-proxy
```

#### 클라이언트 설정

```javascript
// 웹 클라이언트에서 프록시된 포트 사용
const API_BASE_URL = 'http://localhost:8081/api/v1'; // 8081로 변경
```

---

## 프록시 서버를 이용한 장애 시뮬레이션

### Node.js 프록시 서버 (커스텀)

간단한 프록시 서버를 만들어서 네트워크 장애를 시뮬레이션할 수 있습니다.

#### proxy-server.js

```javascript
const http = require('http');
const httpProxy = require('http-proxy-middleware');
const express = require('express');

const app = express();
const TARGET_SERVER = 'http://localhost:8080';

// 장애 설정
let config = {
  enabled: false,
  latency: 0,
  dropRate: 0, // 0-100
  timeout: false
};

// 프록시 미들웨어
const proxy = httpProxy.createProxyMiddleware({
  target: TARGET_SERVER,
  changeOrigin: true,
  onProxyReq: (proxyReq, req, res) => {
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
    
    // 지연 시간 적용
    if (config.latency > 0) {
      setTimeout(() => {
        // 실제 프록시 요청
      }, config.latency);
    }
    
    // 패킷 손실 시뮬레이션
    if (config.dropRate > 0) {
      const random = Math.random() * 100;
      if (random < config.dropRate) {
        res.status(503).json({ error: 'Service Unavailable (Simulated)' });
        return;
      }
    }
    
    // 타임아웃 시뮬레이션
    if (config.timeout) {
      setTimeout(() => {
        res.status(504).json({ error: 'Gateway Timeout (Simulated)' });
      }, 5000);
      return;
    }
  },
  onError: (err, req, res) => {
    console.error('Proxy error:', err);
    res.status(503).json({ error: 'Service Unavailable' });
  }
});

app.use('/api', proxy);

// 설정 변경 엔드포인트
app.get('/proxy/config', (req, res) => {
  res.json(config);
});

app.post('/proxy/config', express.json(), (req, res) => {
  config = { ...config, ...req.body };
  res.json({ message: 'Config updated', config });
});

app.listen(8081, () => {
  console.log('Proxy server running on http://localhost:8081');
  console.log('Target server:', TARGET_SERVER);
});
```

#### 사용 방법

```bash
# 1. 의존성 설치
npm init -y
npm install express http-proxy-middleware

# 2. 프록시 서버 실행
node proxy-server.js

# 3. 설정 변경 (PowerShell)
# 지연 시간 500ms 추가
Invoke-RestMethod -Uri http://localhost:8081/proxy/config `
  -Method POST `
  -Body (@{latency=500} | ConvertTo-Json) `
  -ContentType "application/json"

# 패킷 손실 20%
Invoke-RestMethod -Uri http://localhost:8081/proxy/config `
  -Method POST `
  -Body (@{dropRate=20} | ConvertTo-Json) `
  -ContentType "application/json"

# 완전 차단
Invoke-RestMethod -Uri http://localhost:8081/proxy/config `
  -Method POST `
  -Body (@{enabled=false; timeout=true} | ConvertTo-Json) `
  -ContentType "application/json"

# 복구
Invoke-RestMethod -Uri http://localhost:8081/proxy/config `
  -Method POST `
  -Body (@{enabled=true; latency=0; dropRate=0; timeout=false} | ConvertTo-Json) `
  -ContentType "application/json"
```

---

## 모니터링 및 로깅

### 1. 브라우저 DevTools Network 탭

**사용 방법:**
1. F12 → Network 탭 열기
2. **Offline 모드** 활성화
   ```
   Network 탭 → Throttling → Offline
   ```
3. **느린 네트워크 시뮬레이션**
   ```
   Network 탭 → Throttling → Slow 3G / Fast 3G
   ```
4. **요청 차단**
   ```
   Network 탭 → 특정 요청 우클릭 → Block request URL
   ```

### 2. Spring Boot Actuator

**의존성 추가** (`pom.xml`):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**설정** (`application.yml`):
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,loggers
  endpoint:
    health:
      show-details: always
```

**모니터링 엔드포인트**:
```
http://localhost:8080/actuator/health
http://localhost:8080/actuator/metrics
http://localhost:8080/actuator/prometheus
```

### 3. 로깅 설정

**application.yml**:
```yaml
logging:
  level:
    root: INFO
    com.readingtracker: DEBUG
    org.springframework.web: DEBUG
    org.springframework.security: DEBUG
  file:
    name: logs/reading-tracker.log
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

---

## 테스트 시나리오

### 시나리오 1: 네트워크 완전 차단

**목적**: 서버와의 연결이 완전히 끊겼을 때의 동작 확인

**방법**:
```powershell
# Clumsy 사용
# Filter: outbound and tcp.DstPort == 8080
# Drop: 100%

# 또는 Windows Firewall
New-NetFirewallRule -DisplayName "Block 8080" `
    -Direction Outbound -LocalPort 8080 -Protocol TCP -Action Block
```

**확인 사항**:
- ✅ 에러 메시지 표시
- ✅ 재시도 로직 동작
- ✅ 사용자에게 명확한 피드백
- ✅ 앱이 크래시하지 않음

---

### 시나리오 2: 느린 네트워크 (지연)

**목적**: 느린 네트워크에서의 사용자 경험 확인

**방법**:
```powershell
# Clumsy 사용
# Lag: 1000ms
# Drop: 0%
```

**확인 사항**:
- ✅ 로딩 인디케이터 표시
- ✅ 타임아웃 설정 적절
- ✅ 사용자가 취소 가능

---

### 시나리오 3: 간헐적 연결 문제

**목적**: 연결이 끊겼다 다시 연결되는 상황

**방법**:
```powershell
# Clumsy 사용
# Drop: 30-50%
# 또는 프록시 서버에서 dropRate 설정
```

**확인 사항**:
- ✅ 자동 재시도
- ✅ 백오프(Exponential Backoff) 전략
- ✅ 부분적 실패 처리

---

### 시나리오 4: 서버 다운

**목적**: 서버가 완전히 응답하지 않을 때

**방법**:
```powershell
# Spring Boot 서버 중지
# 또는 포트 차단
```

**확인 사항**:
- ✅ 연결 오류 처리
- ✅ 재연결 시도
- ✅ 오프라인 모드 전환 (선택사항)

---

## 실제 구현 예제

### JavaScript: 네트워크 장애 처리 개선

```javascript
// services/api-client.js (개선된 버전)
class ApiClient {
    constructor(baseURL) {
        this.baseURL = baseURL;
        this.maxRetries = 3;
        this.retryDelay = 1000; // 1초
        this.timeout = 10000; // 10초
    }

    async request(endpoint, options = {}) {
        const url = `${this.baseURL}${endpoint}`;
        const token = tokenManager.getAccessToken();

        const config = {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                ...(token && { 'Authorization': `Bearer ${token}` }),
                ...options.headers
            },
            signal: AbortSignal.timeout(this.timeout) // 타임아웃 추가
        };

        if (config.body && typeof config.body === 'object') {
            config.body = JSON.stringify(config.body);
        }

        // 재시도 로직
        for (let attempt = 0; attempt <= this.maxRetries; attempt++) {
            try {
                const response = await fetch(url, config);

                // 401 에러 처리 (토큰 갱신)
                if (response.status === 401) {
                    const refreshed = await tokenManager.refreshToken();
                    if (refreshed) {
                        config.headers['Authorization'] = `Bearer ${tokenManager.getAccessToken()}`;
                        continue; // 재시도
                    } else {
                        authState.logout();
                        throw new Error('인증이 만료되었습니다.');
                    }
                }

                return await this.handleResponse(response);
            } catch (error) {
                // 네트워크 에러 처리
                if (error.name === 'AbortError') {
                    if (attempt < this.maxRetries) {
                        console.warn(`Request timeout, retrying... (${attempt + 1}/${this.maxRetries})`);
                        await this.delay(this.retryDelay * Math.pow(2, attempt)); // Exponential backoff
                        continue;
                    }
                    throw new Error('요청 시간이 초과되었습니다. 네트워크 연결을 확인해주세요.');
                }

                // 네트워크 연결 오류
                if (error.message.includes('Failed to fetch') || 
                    error.message.includes('NetworkError')) {
                    if (attempt < this.maxRetries) {
                        console.warn(`Network error, retrying... (${attempt + 1}/${this.maxRetries})`);
                        await this.delay(this.retryDelay * Math.pow(2, attempt));
                        continue;
                    }
                    throw new Error('네트워크 연결에 실패했습니다. 인터넷 연결을 확인해주세요.');
                }

                // 다른 에러는 즉시 throw
                throw error;
            }
        }
    }

    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    async handleResponse(response) {
        // ... 기존 코드 ...
    }
}
```

### UI: 네트워크 상태 표시

```javascript
// utils/network-monitor.js
class NetworkMonitor {
    constructor() {
        this.isOnline = navigator.onLine;
        this.listeners = [];
        this.init();
    }

    init() {
        window.addEventListener('online', () => {
            this.isOnline = true;
            this.notifyListeners(true);
        });

        window.addEventListener('offline', () => {
            this.isOnline = false;
            this.notifyListeners(false);
        });
    }

    subscribe(callback) {
        this.listeners.push(callback);
        return () => {
            this.listeners = this.listeners.filter(cb => cb !== callback);
        };
    }

    notifyListeners(isOnline) {
        this.listeners.forEach(callback => callback(isOnline));
    }
}

export const networkMonitor = new NetworkMonitor();
```

```javascript
// UI 컴포넌트에서 사용
import { networkMonitor } from '../utils/network-monitor.js';

networkMonitor.subscribe((isOnline) => {
    if (!isOnline) {
        showNotification('오프라인 상태입니다. 네트워크 연결을 확인해주세요.', 'error');
    } else {
        showNotification('네트워크 연결이 복구되었습니다.', 'success');
    }
});
```

---

## 요약

### 배포 없이 가능한 것들

✅ **네트워크 장애 시뮬레이션**
- Clumsy (Windows)
- Charles Proxy
- Toxiproxy (Docker)
- 커스텀 프록시 서버

✅ **서버 다운 시뮬레이션**
- 서버 중지
- 포트 차단 (Windows Firewall)

✅ **모니터링**
- 브라우저 DevTools
- Spring Boot Actuator
- 로깅

✅ **테스트 자동화**
- 스크립트로 장애 주입
- 재시도 로직 테스트
- 에러 처리 검증

### 추천 도구

1. **Windows 환경**: Clumsy (가장 간단)
2. **크로스 플랫폼**: Toxiproxy (Docker)
3. **API 테스트**: Postman / Thunder Client
4. **모니터링**: 브라우저 DevTools + Spring Boot Actuator

---

## 다음 단계

1. **Clumsy 설치** 및 기본 테스트
2. **API 클라이언트에 재시도 로직 추가**
3. **에러 처리 개선**
4. **네트워크 상태 모니터링 UI 추가**
5. **자동화된 테스트 스크립트 작성**

