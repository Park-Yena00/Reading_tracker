# Redis 기능 테스트 가이드

## 개요

본 문서는 Redis를 활용한 다음 기능들을 테스트하는 방법을 설명합니다:
1. **멱등성 키 관리**: 메모 생성 시 중복 요청 방지
2. **세션/인증 토큰 관리**: RefreshToken Redis 저장 및 관리
3. **빈번한 캐싱**: 태그 데이터 및 내 서재 정보 캐싱

모든 테스트는 **추가 소프트웨어 설치 없이** Redis CLI와 HTTP 요청/응답 로그를 통해 **가시적으로 확인**할 수 있습니다.

---

## 테스트 환경 준비

### 1. Redis 컨테이너 실행 확인

```bash
# Redis 컨테이너가 실행 중인지 확인
docker ps | grep reading-tracker-redis

# Redis CLI 접속
docker exec -it reading-tracker-redis redis-cli
```

### 2. Spring Boot 애플리케이션 실행

애플리케이션을 실행하고 다음 로그를 확인합니다:
- `RedisConnectionFactory` 초기화 로그
- Redis 연결 성공 메시지

### 3. 테스트 도구

- **HTTP 요청**: 브라우저 개발자 도구 Console (권장), Postman, curl
- **Redis CLI**: `docker exec -it reading-tracker-redis redis-cli`
- **애플리케이션 로그**: 콘솔 출력

> **참고**: 본 문서의 모든 HTTP 요청은 **브라우저 개발자 도구 Console에서 바로 실행 가능한 JavaScript `fetch` 코드**로 제공됩니다. 각 테스트 섹션에서 "브라우저 Console에서 실행" 부분을 복사하여 사용하세요.

### 4. 명령어 실행 위치 구분

본 문서에서 사용하는 코드 블록은 실행 위치에 따라 구분됩니다:

- **````bash` 블록**: **터미널(또는 Cursor 터미널)**에서 실행
  - `docker exec -it ...` 명령어: 터미널에서 실행
  - Redis CLI 명령어 (`KEYS`, `GET`, `TTL` 등): Redis CLI 접속 후 실행
- **````javascript` 블록**: **브라우저 개발자 도구 Console**에서 실행
- **````http` 블록**: HTTP 요청 형식 (참고용, 직접 실행 불가)

**Redis CLI 사용 방법:**
1. 터미널에서 `docker exec -it reading-tracker-redis redis-cli` 실행
2. Redis CLI 프롬프트(`127.0.0.1:6379>`)가 나타나면 Redis 명령어 입력
3. 종료하려면 `exit` 또는 `Ctrl+C` 입력

---

## 테스트 1: 멱등성 키 관리

### 목적
동일한 `Idempotency-Key`로 여러 번 요청해도 동일한 응답이 반환되고, 중복 메모 생성이 방지되는지 확인합니다.

### 테스트 시나리오

#### 1-1. 멱등성 키를 포함한 메모 생성 요청

**요청 1 (처음 요청)**
```http
POST /api/v1/memos
Authorization: Bearer {access_token}
Idempotency-Key: test-idempotency-key-001
Content-Type: application/json

{
  "userBookId": 1,
  "page": 10,
  "content": "첫 번째 메모 내용",
  "tagCodes": ["impressive-quote"]
}
```

**예상 응답 (201 Created)**
```json
{
  "ok": true,
  "data": {
    "id": 123,
    "userBookId": 1,
    "page": 10,
    "content": "첫 번째 메모 내용",
    "tagCodes": ["impressive-quote"],
    "createdAt": "2024-01-01T10:00:00"
  }
}
```

**Redis 확인 (Redis CLI)**

> **실행 위치**: 아래 명령어는 **터미널**에서 실행합니다.

```bash
# 1단계: 터미널에서 Redis CLI 접속
docker exec -it reading-tracker-redis redis-cli

# 2단계: Redis CLI 프롬프트(127.0.0.1:6379>)가 나타나면 아래 명령어 실행
# 멱등성 키 확인
KEYS idempotency:memo:*

# 특정 키 조회
GET idempotency:memo:test-idempotency-key-001

# 결과 예시:
# {
#   "status": "COMPLETED",
#   "response": { ... },
#   "createdAt": "2024-01-01T10:00:00"
# }
```

#### 1-2. 동일한 멱등성 키로 재요청

**요청 2 (동일한 Idempotency-Key로 재요청)**
```http
POST /api/v1/memos
Authorization: Bearer {access_token}
Idempotency-Key: test-idempotency-key-001
Content-Type: application/json

{
  "userBookId": 1,
  "page": 20,
  "content": "두 번째 메모 내용 (다른 내용)",
  "tagCodes": ["question"]
}
```

**예상 응답 (200 OK) - 첫 번째 요청과 동일한 응답 반환**
```json
{
  "ok": true,
  "data": {
    "id": 123,  // 동일한 ID
    "userBookId": 1,
    "page": 10,  // 첫 번째 요청의 값
    "content": "첫 번째 메모 내용",  // 첫 번째 요청의 값
    "tagCodes": ["impressive-quote"],  // 첫 번째 요청의 값
    "createdAt": "2024-01-01T10:00:00"
  }
}
```

**확인 사항:**
- ✅ 응답이 첫 번째 요청과 **완전히 동일**함
- ✅ DB에 **새로운 메모가 생성되지 않음** (메모 ID 123만 존재)
- ✅ 애플리케이션 로그에 "캐시된 응답 반환" 메시지 없음 (정상 동작)

**Redis 확인**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# 터미널에서 Redis CLI 접속 (아직 접속하지 않았다면)
docker exec -it reading-tracker-redis redis-cli

# Redis CLI 내부에서 실행
# 동일한 키가 여전히 존재하는지 확인
GET idempotency:memo:test-idempotency-key-001

# TTL 확인 (24시간 = 86400초)
TTL idempotency:memo:test-idempotency-key-001
# 예상 결과: 86399 (약 24시간)
```

#### 1-2-1. 브라우저 개발자 도구를 사용한 동일한 멱등성 키 재요청

> **참고**: 일반적으로 웹 UI에서 메모 작성 버튼을 클릭하면 클라이언트 코드가 매번 새로운 UUID를 생성하여 `Idempotency-Key`로 사용합니다. 따라서 동일한 멱등성 키로 재요청하려면 브라우저 개발자 도구를 사용해야 합니다.

**방법 1: Network 탭에서 요청 복사 및 Console에서 재실행**

1. **브라우저 개발자 도구 열기** (F12 또는 우클릭 → 검사)
2. **Network 탭** 이동
3. **메모 작성 요청** 실행 (웹 UI에서 메모 작성 버튼 클릭)
4. **Network 탭에서 해당 요청 선택** (예: `memos` 요청)
5. **우클릭 → Copy → Copy as fetch** 선택
6. **Console 탭**으로 이동
7. **복사한 코드를 붙여넣고 실행** (첫 번째 요청 완료)
8. **동일한 `Idempotency-Key`를 유지한 채로 재실행**:
   ```javascript
   // 첫 번째 요청 (복사한 내용)
   fetch('http://localhost:8080/api/v1/memos', {
     method: 'POST',
     headers: {
       'Authorization': 'Bearer {access_token}',
       'Idempotency-Key': 'test-idempotency-key-001',  // 동일한 키
       'Content-Type': 'application/json'
     },
     body: JSON.stringify({
       "userBookId": 1,
       "page": 10,
       "content": "첫 번째 메모 내용",
       "tagCodes": ["impressive-quote"]
     })
   }).then(r => r.json()).then(console.log);
   
   // 동일한 키로 재요청 (수동으로 실행)
   fetch('http://localhost:8080/api/v1/memos', {
     method: 'POST',
     headers: {
       'Authorization': 'Bearer {access_token}',
       'Idempotency-Key': 'test-idempotency-key-001',  // 동일한 키 유지
       'Content-Type': 'application/json'
     },
     body: JSON.stringify({
       "userBookId": 1,
       "page": 20,  // 내용이 달라도 동일한 응답 반환
       "content": "다른 내용",
       "tagCodes": ["question"]
     })
   }).then(r => r.json()).then(console.log);
   ```

**확인 사항:**
- ✅ 두 번째 요청의 응답이 첫 번째 요청과 **완전히 동일**함
- ✅ Console에 동일한 메모 ID와 내용이 출력됨
- ✅ Network 탭에서 두 번째 요청의 응답 상태가 200 OK임 (201 Created가 아님)

**방법 2: Console에서 직접 fetch 요청 작성**

1. **Console 탭**에서 직접 fetch 요청 작성
2. **동일한 `Idempotency-Key`를 명시적으로 지정**:
   ```javascript
   // 첫 번째 요청
   const idempotencyKey = 'test-idempotency-key-001';
   const accessToken = 'Bearer {실제_토큰_값}';
   
   fetch('http://localhost:8080/api/v1/memos', {
     method: 'POST',
     headers: {
       'Authorization': accessToken,
       'Idempotency-Key': idempotencyKey,
       'Content-Type': 'application/json'
     },
     body: JSON.stringify({
       "userBookId": 1,
       "page": 10,
       "content": "첫 번째 메모",
       "tagCodes": ["impressive-quote"]
     })
   }).then(r => r.json()).then(data => {
     console.log('첫 번째 응답:', data);
   });
   
   // 동일한 키로 재요청 (바로 실행 가능)
   setTimeout(() => {
     fetch('http://localhost:8080/api/v1/memos', {
       method: 'POST',
       headers: {
         'Authorization': accessToken,
         'Idempotency-Key': idempotencyKey,  // 동일한 키
         'Content-Type': 'application/json'
       },
       body: JSON.stringify({
         "userBookId": 1,
         "page": 20,
         "content": "두 번째 메모 (다른 내용)",
         "tagCodes": ["question"]
       })
     }).then(r => r.json()).then(data => {
       console.log('두 번째 응답 (동일해야 함):', data);
     });
   }, 1000);
   ```

**추천 방법:**
- **방법 1 (Copy as fetch)** 또는 **방법 2 (직접 작성)**을 권장합니다
- 이 방법들은 `Idempotency-Key`를 명시적으로 제어할 수 있어 테스트가 확실합니다

#### 1-3. 다른 멱등성 키로 요청

**요청 3 (다른 Idempotency-Key)**
```http
POST /api/v1/memos
Authorization: Bearer {access_token}
Idempotency-Key: test-idempotency-key-002
Content-Type: application/json

{
  "userBookId": 1,
  "page": 30,
  "content": "세 번째 메모 내용",
  "tagCodes": ["question"]
}
```

**예상 응답 (201 Created) - 새로운 메모 생성**
```json
{
  "ok": true,
  "data": {
    "id": 124,  // 새로운 ID
    "userBookId": 1,
    "page": 30,
    "content": "세 번째 메모 내용",
    "tagCodes": ["question"],
    "createdAt": "2024-01-01T10:01:00"
  }
}
```

**Redis 확인**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# Redis CLI 내부에서 실행
# 두 개의 멱등성 키가 모두 존재하는지 확인
KEYS idempotency:memo:*

# 결과:
# 1) "idempotency:memo:test-idempotency-key-001"
# 2) "idempotency:memo:test-idempotency-key-002"
```

### 테스트 체크리스트

- [ ] 첫 번째 요청 시 메모가 정상적으로 생성됨
- [ ] Redis에 `idempotency:memo:{key}` 형태로 저장됨
- [ ] 동일한 키로 재요청 시 첫 번째 응답과 동일한 응답 반환
- [ ] DB에 중복 메모가 생성되지 않음
- [ ] 다른 키로 요청 시 새로운 메모가 생성됨
- [ ] TTL이 24시간으로 설정되어 있음

---

## 테스트 2: 세션/인증 토큰 관리

### 목적
RefreshToken이 Redis에 저장되고, 토큰 무효화가 정상적으로 작동하는지 확인합니다.

### 테스트 시나리오

#### 2-1. 로그인 후 RefreshToken 저장 확인

**요청: 로그인**

**HTTP 요청 형식:**
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "loginId": "testuser",
  "password": "password123",
  "deviceId": "device-001",
  "deviceName": "Test Device",
  "platform": "WEB"
}
```

**브라우저 Console에서 실행:**
```javascript
// 브라우저 개발자 도구 Console에서 실행
fetch('http://localhost:8080/api/v1/auth/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    "loginId": "testuser",
    "password": "password123",
    "deviceId": "device-001",
    "deviceName": "Test Device",
    "platform": "WEB"
  })
})
.then(r => r.json())
.then(data => {
  console.log('로그인 성공:', data);
  console.log('AccessToken:', data.data.accessToken);
  console.log('RefreshToken:', data.data.refreshToken);
  // 토큰을 변수에 저장 (나중에 사용)
  window.testAccessToken = data.data.accessToken;
  window.testRefreshToken = data.data.refreshToken;
})
.catch(error => {
  console.error('로그인 실패:', error);
});
```

**예상 응답**
```json
{
  "ok": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

**Redis 확인**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# 터미널에서 Redis CLI 접속 (아직 접속하지 않았다면)
docker exec -it reading-tracker-redis redis-cli

# Redis CLI 내부에서 실행
# RefreshToken 확인
KEYS refresh_token:*

# 특정 RefreshToken 조회
GET refresh_token:{refreshToken값}

# 결과 예시:
# {
#   "userId": 1,
#   "deviceId": "device-001",
#   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
#   "expiresAt": "2024-01-08T10:00:00",
#   "revoked": false,
#   "createdAt": "2024-01-01T10:00:00"
# }

# 사용자/디바이스별 토큰 목록 확인
KEYS refresh_tokens:user:1:device:*

# 토큰 목록 조회
SMEMBERS refresh_tokens:user:1:device:device-001
```

#### 2-2. RefreshToken으로 새 토큰 발급 (Token Rotation)

RefreshToken으로 새 토큰을 발급받는 방법은 여러 가지가 있습니다:

**방법 1: 브라우저 개발자 도구에서 직접 API 호출 (권장)**

1. **브라우저 개발자 도구 열기** (F12)
2. **Console 탭**으로 이동
3. **로그인 시 받은 RefreshToken을 변수에 저장** (이미 로그인했다면 localStorage나 쿠키에서 확인)
4. **RefreshToken API 직접 호출**:
   ```javascript
   // 로그인 시 받은 RefreshToken (예시)
   const refreshToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...';  // 실제 RefreshToken 값
   const deviceId = 'device-001';  // 로그인 시 사용한 deviceId
   
   // RefreshToken으로 새 토큰 발급
   fetch('http://localhost:8080/api/v1/auth/refresh', {
     method: 'POST',
     headers: {
       'Content-Type': 'application/json'
     },
     body: JSON.stringify({
       "refreshToken": refreshToken,
       "deviceId": deviceId
     })
   })
   .then(r => r.json())
   .then(data => {
     console.log('새 토큰 발급 성공:', data);
     console.log('새 AccessToken:', data.data.accessToken);
     console.log('새 RefreshToken:', data.data.refreshToken);
   })
   .catch(error => {
     console.error('토큰 갱신 실패:', error);
   });
   ```

**방법 2: 브라우저를 껐다가 다시 접속**

> **참고**: 이 방법은 프론트엔드 코드가 자동으로 RefreshToken을 사용하여 새 토큰을 발급하는 경우에만 작동합니다.

1. **로그인 후 브라우저를 완전히 종료** (모든 탭 닫기)
2. **브라우저를 다시 실행하고 웹사이트 접속**
3. **프론트엔드 코드가 자동으로**:
   - localStorage나 쿠키에서 RefreshToken 조회
   - `/api/v1/auth/refresh` API 호출
   - 새 AccessToken과 RefreshToken 받아서 저장
4. **Network 탭에서 자동 호출된 refresh 요청 확인**

**방법 3: AccessToken 만료 후 자동 갱신 (프론트엔드 자동 처리)**

1. **로그인 후 AccessToken이 만료될 때까지 대기** (또는 AccessToken 만료 시간을 짧게 설정)
2. **API 요청 시 401 Unauthorized 응답 발생**
3. **프론트엔드 코드가 자동으로**:
   - RefreshToken을 사용하여 새 토큰 발급
   - 실패한 요청을 새 AccessToken으로 재시도
4. **Network 탭에서 자동 호출된 refresh 요청 확인**

**방법 4: Postman 또는 curl 사용**

```bash
# curl 예시
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "deviceId": "device-001"
  }'
```

**요청 예시 (방법 1 사용 시)**
```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "{이전에 받은 refreshToken}",
  "deviceId": "device-001"
}
```

**예상 응답**
```json
{
  "ok": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",  // 새로운 토큰
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",  // 새로운 토큰
    "tokenType": "Bearer",
    "expiresIn": 3600000
  }
}
```

**Redis 확인 (토큰 발급 직후)**
```bash
# 이전 RefreshToken이 revoked=true로 변경되었는지 확인
GET refresh_token:{이전_refreshToken}

# 결과:
# {
#   "userId": 1,
#   "deviceId": "device-001",
#   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
#   "expiresAt": "2024-01-08T10:00:00",
#   "revoked": true,  # 무효화됨
#   "createdAt": "2024-01-01T10:00:00"
# }

# 새로운 RefreshToken이 저장되었는지 확인
GET refresh_token:{새로운_refreshToken}

# 결과:
# {
#   "userId": 1,
#   "deviceId": "device-001",
#   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
#   "expiresAt": "2024-01-08T10:00:00",
#   "revoked": false,  # 유효함
#   "createdAt": "2024-01-01T10:01:00"
# }

# 사용자/디바이스별 토큰 목록 확인
SMEMBERS refresh_tokens:user:1:device:device-001
# 결과: 새로운 RefreshToken만 포함됨 (이전 토큰은 revoked 상태이지만 목록에는 남아있을 수 있음)
```

**확인 사항:**
- ✅ 이전 RefreshToken이 `revoked=true`로 변경됨
- ✅ 새로운 RefreshToken이 Redis에 저장됨
- ✅ 새로운 AccessToken과 RefreshToken이 응답으로 반환됨
- ✅ 동일한 RefreshToken으로 재요청 시 오류 발생 (이미 사용됨)

#### 2-3. 로그아웃 시 토큰 무효화 확인

**요청: 로그아웃 (또는 토큰 무효화 API 호출)**

**HTTP 요청 형식:**
```http
POST /api/v1/auth/logout
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "deviceId": "device-001"
}
```

**브라우저 Console에서 실행:**
```javascript
// 브라우저 개발자 도구 Console에서 실행
// 먼저 로그인하여 accessToken을 받아야 함
const accessToken = window.testAccessToken || 'Bearer {실제_토큰_값}';
const deviceId = 'device-001';

fetch('http://localhost:8080/api/v1/auth/logout', {
  method: 'POST',
  headers: {
    'Authorization': accessToken,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    "deviceId": deviceId
  })
})
.then(r => r.json())
.then(data => {
  console.log('로그아웃 성공:', data);
})
.catch(error => {
  console.error('로그아웃 실패:', error);
});
```

**Redis 확인**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# Redis CLI 내부에서 실행
# 해당 디바이스의 모든 토큰이 무효화되었는지 확인
KEYS refresh_token:*

# 각 토큰의 revoked 상태 확인
GET refresh_token:{token1}
GET refresh_token:{token2}

# 사용자/디바이스별 토큰 목록이 삭제되었는지 확인
KEYS refresh_tokens:user:1:device:device-001
# 결과: (empty list or set) - 삭제됨
```

### 테스트 체크리스트

- [ ] 로그인 후 RefreshToken이 Redis에 저장됨
- [ ] `refresh_token:{token}` 형태로 저장됨
- [ ] `refresh_tokens:user:{userId}:device:{deviceId}` 형태로 토큰 목록 저장됨
- [ ] Token Rotation 시 이전 토큰이 `revoked=true`로 변경됨
- [ ] 새로운 토큰이 Redis에 저장됨
- [ ] 로그아웃 시 해당 디바이스의 모든 토큰이 무효화됨
- [ ] TTL이 RefreshToken 만료 시간(7일)으로 설정되어 있음

---

## 테스트 3: 빈번한 캐싱 - 태그 데이터

### 목적
태그 데이터가 Redis에 캐싱되고, Purger-Driven Invalidation이 정상적으로 작동하는지 확인합니다.

### 테스트 시나리오

#### 3-1. 태그 데이터 조회 및 캐싱 확인

**요청 1: 모든 태그 조회**

**HTTP 요청 형식:**
```http
GET /api/v1/tags
```

**브라우저 Console에서 실행:**
```javascript
// 브라우저 개발자 도구 Console에서 실행
fetch('http://localhost:8080/api/v1/tags')
  .then(r => r.json())
  .then(data => {
    console.log('태그 데이터:', data);
    console.log('태그 개수:', data.data.length);
  })
  .catch(error => {
    console.error('태그 조회 실패:', error);
  });
```

**예상 응답**
```json
{
  "ok": true,
  "data": [
    {
      "id": 1,
      "category": "TYPE",
      "code": "impressive-quote",
      "sortOrder": 1,
      "isActive": true
    },
    {
      "id": 2,
      "category": "TYPE",
      "code": "question",
      "sortOrder": 2,
      "isActive": true
    },
    ...
  ]
}
```

**Redis 확인**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# Redis CLI 내부에서 실행
# 태그 캐시 확인
KEYS tags:*

# 모든 태그 캐시 조회
GET tags:all

# 카테고리별 태그 캐시 조회
GET tags:type
GET tags:topic

# TTL 확인 (7일 = 604800초)
TTL tags:all
# 예상 결과: 604799 (약 7일)
```

#### 3-2. 캐시된 데이터 재조회 (캐시 히트 확인)

**요청 2: 동일한 요청 재전송**

**HTTP 요청 형식:**
```http
GET /api/v1/tags
```

**브라우저 Console에서 실행:**
```javascript
// 브라우저 개발자 도구 Console에서 실행 (동일한 요청 재전송)
fetch('http://localhost:8080/api/v1/tags')
  .then(r => r.json())
  .then(data => {
    console.log('캐시된 태그 데이터:', data);
    console.log('응답 시간 확인: Network 탭에서 확인 가능');
  })
  .catch(error => {
    console.error('태그 조회 실패:', error);
  });
```

**확인 사항:**
- ✅ 응답이 동일함
- ✅ 애플리케이션 로그에 DB 쿼리 로그가 없음 (캐시에서 조회)
- ✅ 응답 시간이 첫 번째 요청보다 빠름

**Redis 확인**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# Redis CLI 내부에서 실행
# TTL이 감소했는지 확인 (시간이 지났다면)
TTL tags:all
```

#### 3-3. 카테고리별 태그 조회

**요청 3: TYPE 카테고리 태그 조회**

**HTTP 요청 형식:**
```http
GET /api/v1/tags?category=TYPE
```

**브라우저 Console에서 실행:**
```javascript
// 브라우저 개발자 도구 Console에서 실행
// TYPE 카테고리 태그 조회
fetch('http://localhost:8080/api/v1/tags?category=TYPE')
  .then(r => r.json())
  .then(data => {
    console.log('TYPE 카테고리 태그:', data);
    console.log('TYPE 태그 개수:', data.data.length);
  })
  .catch(error => {
    console.error('태그 조회 실패:', error);
  });

// TOPIC 카테고리 태그 조회
fetch('http://localhost:8080/api/v1/tags?category=TOPIC')
  .then(r => r.json())
  .then(data => {
    console.log('TOPIC 카테고리 태그:', data);
    console.log('TOPIC 태그 개수:', data.data.length);
  })
  .catch(error => {
    console.error('태그 조회 실패:', error);
  });
```

**Redis 확인**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# Redis CLI 내부에서 실행
# TYPE 카테고리 캐시 확인
GET tags:type

# TTL 확인
TTL tags:type
```

#### 3-4. 캐시 무효화 (Purger-Driven Invalidation)

**수동 캐시 무효화 (개발자가 태그 DB 변경 후)**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# Redis CLI 내부에서 실행
# Redis CLI에서 직접 삭제 (실제로는 TagService.invalidateTagCache() 호출)
DEL tags:all
DEL tags:type
DEL tags:topic
```

**또는 애플리케이션 코드에서 호출:**
```java
// TagService.invalidateTagCache() 메서드 호출
// 또는 관리자 API를 통해 호출
```

**요청 4: 캐시 무효화 후 재조회**

**HTTP 요청 형식:**
```http
GET /api/v1/tags
```

**브라우저 Console에서 실행:**
```javascript
// 브라우저 개발자 도구 Console에서 실행
// 캐시 무효화 후 재조회 (Redis에서 캐시를 삭제한 후 실행)
fetch('http://localhost:8080/api/v1/tags')
  .then(r => r.json())
  .then(data => {
    console.log('캐시 무효화 후 태그 데이터:', data);
    console.log('새로운 캐시가 Redis에 저장되었는지 확인 필요');
  })
  .catch(error => {
    console.error('태그 조회 실패:', error);
  });
```

**확인 사항:**
- ✅ DB에서 최신 데이터를 조회함
- ✅ Redis에 새로운 캐시가 저장됨

**Redis 확인**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# Redis CLI 내부에서 실행
# 새로운 캐시가 저장되었는지 확인
GET tags:all

# TTL이 다시 7일로 설정되었는지 확인
TTL tags:all
```

### 테스트 체크리스트

- [ ] 첫 번째 요청 시 태그 데이터가 Redis에 캐싱됨
- [ ] `tags:all`, `tags:type`, `tags:topic` 형태로 저장됨
- [ ] TTL이 7일로 설정되어 있음
- [ ] 두 번째 요청 시 캐시에서 조회됨 (DB 쿼리 없음)
- [ ] 캐시 무효화 후 재조회 시 DB에서 최신 데이터 조회
- [ ] 새로운 캐시가 저장됨

---

## 테스트 4: 빈번한 캐싱 - 내 서재 정보

### 목적
내 서재 정보가 Redis에 캐싱되고, Write-Through 패턴이 정상적으로 작동하는지 확인합니다.

### 테스트 시나리오

#### 4-1. 내 서재 조회 및 캐싱 확인

**요청 1: 내 서재 조회**

**HTTP 요청 형식:**
```http
GET /api/v1/user/books
Authorization: Bearer {access_token}
```

**브라우저 Console에서 실행:**
```javascript
// 브라우저 개발자 도구 Console에서 실행
// 먼저 로그인하여 accessToken을 받아야 함
const accessToken = window.testAccessToken || 'Bearer {실제_토큰_값}';

fetch('http://localhost:8080/api/v1/user/books', {
  method: 'GET',
  headers: {
    'Authorization': accessToken
  }
})
.then(r => r.json())
.then(data => {
  console.log('내 서재 데이터:', data);
  console.log('책 개수:', data.data.books.length);
})
.catch(error => {
  console.error('내 서재 조회 실패:', error);
});
```

**예상 응답**
```json
{
  "ok": true,
  "data": {
    "books": [
      {
        "id": 1,
        "bookId": 10,
        "category": "ToRead",
        ...
      },
      ...
    ]
  }
}
```

**Redis 확인**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# Redis CLI 내부에서 실행
# 내 서재 캐시 확인
KEYS my_shelf:user:*

# 특정 사용자의 캐시 확인
KEYS my_shelf:user:1:*

# 예시:
# my_shelf:user:1:sort:TITLE
# my_shelf:user:1:category:ToRead:sort:TITLE

# 캐시 조회
GET my_shelf:user:1:sort:TITLE

# TTL 확인 (5분 = 300초)
TTL my_shelf:user:1:sort:TITLE
# 예상 결과: 299 (약 5분)
```

#### 4-2. 캐시된 데이터 재조회

**요청 2: 동일한 요청 재전송**

**HTTP 요청 형식:**
```http
GET /api/v1/user/books
Authorization: Bearer {access_token}
```

**브라우저 Console에서 실행:**
```javascript
// 브라우저 개발자 도구 Console에서 실행 (동일한 요청 재전송)
const accessToken = window.testAccessToken || 'Bearer {실제_토큰_값}';

fetch('http://localhost:8080/api/v1/user/books', {
  method: 'GET',
  headers: {
    'Authorization': accessToken
  }
})
.then(r => r.json())
.then(data => {
  console.log('캐시된 내 서재 데이터:', data);
  console.log('응답 시간 확인: Network 탭에서 확인 가능');
})
.catch(error => {
  console.error('내 서재 조회 실패:', error);
});
```

**확인 사항:**
- ✅ 응답이 동일함
- ✅ 애플리케이션 로그에 DB 쿼리 로그가 없음 (캐시에서 조회)
- ✅ 응답 시간이 첫 번째 요청보다 빠름

#### 4-3. 책 추가 시 캐시 무효화 (Write-Through)

**요청 3: 내 서재에 책 추가**

**HTTP 요청 형식:**
```http
POST /api/v1/user/books
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "isbn": "9781234567890",
  "title": "테스트 책",
  "author": "테스트 저자",
  "category": "ToRead",
  ...
}
```

**브라우저 Console에서 실행:**
```javascript
// 브라우저 개발자 도구 Console에서 실행
// 먼저 로그인하여 accessToken을 받아야 함
const accessToken = window.testAccessToken || 'Bearer {실제_토큰_값}';

fetch('http://localhost:8080/api/v1/user/books', {
  method: 'POST',
  headers: {
    'Authorization': accessToken,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    "isbn": "9781234567890",
    "title": "테스트 책",
    "author": "테스트 저자",
    "category": "ToRead",
    "publisher": "테스트 출판사",
    "totalPages": 300,
    "publishedDate": "2024-01-01"
  })
})
.then(r => r.json())
.then(data => {
  console.log('책 추가 성공:', data);
  console.log('추가된 책 ID:', data.data.id);
  console.log('캐시가 즉시 무효화되었는지 Redis에서 확인 필요');
})
.catch(error => {
  console.error('책 추가 실패:', error);
});
```

**예상 응답 (201 Created)**
```json
{
  "ok": true,
  "data": {
    "id": 100,
    "bookId": 50,
    "category": "ToRead",
    ...
  }
}
```

**Redis 확인 (즉시 확인)**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# Redis CLI 내부에서 실행
# 캐시가 즉시 삭제되었는지 확인
KEYS my_shelf:user:1:*

# 결과: (empty list or set) - 모든 캐시가 삭제됨
```

**요청 4: 캐시 무효화 후 재조회**

**HTTP 요청 형식:**
```http
GET /api/v1/user/books
Authorization: Bearer {access_token}
```

**브라우저 Console에서 실행:**
```javascript
// 브라우저 개발자 도구 Console에서 실행
// 캐시 무효화 후 재조회 (책 추가/삭제/수정 후 실행)
const accessToken = window.testAccessToken || 'Bearer {실제_토큰_값}';

fetch('http://localhost:8080/api/v1/user/books', {
  method: 'GET',
  headers: {
    'Authorization': accessToken
  }
})
.then(r => r.json())
.then(data => {
  console.log('캐시 무효화 후 내 서재 데이터:', data);
  console.log('새로운 캐시가 Redis에 저장되었는지 확인 필요');
})
.catch(error => {
  console.error('내 서재 조회 실패:', error);
});
```

**확인 사항:**
- ✅ DB에서 최신 데이터를 조회함 (새로 추가된 책 포함)
- ✅ Redis에 새로운 캐시가 저장됨

**Redis 확인**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# Redis CLI 내부에서 실행
# 새로운 캐시가 저장되었는지 확인
GET my_shelf:user:1:sort:TITLE

# 새로 추가된 책이 포함되어 있는지 확인
# (캐시 데이터에 새 책 정보가 있어야 함)
```

#### 4-4. 책 삭제 시 캐시 무효화

**요청 5: 내 서재에서 책 제거**

**HTTP 요청 형식:**
```http
DELETE /api/v1/user/books/100
Authorization: Bearer {access_token}
```

**브라우저 Console에서 실행:**
```javascript
// 브라우저 개발자 도구 Console에서 실행
// 먼저 로그인하여 accessToken을 받아야 함
const accessToken = window.testAccessToken || 'Bearer {실제_토큰_값}';
const userBookId = 100; // 삭제할 책의 userBookId

fetch(`http://localhost:8080/api/v1/user/books/${userBookId}`, {
  method: 'DELETE',
  headers: {
    'Authorization': accessToken
  }
})
.then(r => r.json())
.then(data => {
  console.log('책 삭제 성공:', data);
  console.log('캐시가 즉시 무효화되었는지 Redis에서 확인 필요');
})
.catch(error => {
  console.error('책 삭제 실패:', error);
});
```

**Redis 확인 (즉시 확인)**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# Redis CLI 내부에서 실행
# 캐시가 즉시 삭제되었는지 확인
KEYS my_shelf:user:1:*

# 결과: (empty list or set) - 모든 캐시가 삭제됨
```

#### 4-5. 책 상태 변경 시 캐시 무효화

**요청 6: 책 읽기 상태 변경**

**HTTP 요청 형식:**
```http
PUT /api/v1/user/books/50/category?category=Reading
Authorization: Bearer {access_token}
```

**브라우저 Console에서 실행:**
```javascript
// 브라우저 개발자 도구 Console에서 실행
// 먼저 로그인하여 accessToken을 받아야 함
const accessToken = window.testAccessToken || 'Bearer {실제_토큰_값}';
const userBookId = 50; // 상태를 변경할 책의 userBookId
const category = 'Reading'; // 새로운 카테고리 (ToRead, Reading, AlmostFinished, Finished)

fetch(`http://localhost:8080/api/v1/user/books/${userBookId}/category?category=${category}`, {
  method: 'PUT',
  headers: {
    'Authorization': accessToken
  }
})
.then(r => r.json())
.then(data => {
  console.log('책 상태 변경 성공:', data);
  console.log('캐시가 즉시 무효화되었는지 Redis에서 확인 필요');
})
.catch(error => {
  console.error('책 상태 변경 실패:', error);
});
```

**Redis 확인 (즉시 확인)**

> **실행 위치**: 아래 명령어는 **터미널에서 Redis CLI 접속 후** 실행합니다.

```bash
# Redis CLI 내부에서 실행
# 캐시가 즉시 삭제되었는지 확인
KEYS my_shelf:user:1:*

# 결과: (empty list or set) - 모든 캐시가 삭제됨
```

### 테스트 체크리스트

- [ ] 첫 번째 요청 시 내 서재 데이터가 Redis에 캐싱됨
- [ ] `my_shelf:user:{userId}:sort:{sortBy}` 형태로 저장됨
- [ ] TTL이 5분으로 설정되어 있음
- [ ] 두 번째 요청 시 캐시에서 조회됨 (DB 쿼리 없음)
- [ ] 책 추가 시 **즉시** 모든 캐시가 삭제됨 (Write-Through)
- [ ] 책 삭제 시 **즉시** 모든 캐시가 삭제됨
- [ ] 책 상태 변경 시 **즉시** 모든 캐시가 삭제됨
- [ ] 캐시 무효화 후 재조회 시 DB에서 최신 데이터 조회
- [ ] 새로운 캐시가 저장됨

---

## 통합 테스트 시나리오

### 시나리오: 전체 플로우 테스트

1. **로그인** → RefreshToken Redis 저장 확인
2. **태그 조회** → 태그 캐시 저장 확인
3. **내 서재 조회** → 내 서재 캐시 저장 확인
4. **메모 생성 (멱등성 키 포함)** → 멱등성 키 저장 확인
5. **동일한 멱등성 키로 재요청** → 동일한 응답 반환 확인
6. **책 추가** → 내 서재 캐시 무효화 확인
7. **내 서재 재조회** → 새로운 캐시 저장 확인
8. **토큰 갱신** → 이전 토큰 무효화, 새 토큰 저장 확인

### Redis 전체 데이터 확인

```bash
# 모든 Redis 키 확인
KEYS *

# 카테고리별 확인
KEYS idempotency:memo:*
KEYS refresh_token:*
KEYS refresh_tokens:*
KEYS tags:*
KEYS my_shelf:*

# 각 키의 TTL 확인
TTL {key}
```

---

## 문제 해결

### Redis 연결 실패
- Redis 컨테이너가 실행 중인지 확인: `docker ps | grep redis`
- `application-dev.yml`의 Redis 설정 확인
- 네트워크 연결 확인: `docker exec -it reading-tracker-redis redis-cli PING`

### 캐시가 저장되지 않음
- 애플리케이션 로그에서 Redis 연결 오류 확인
- Redis 메모리 사용량 확인: `docker exec -it reading-tracker-redis redis-cli INFO memory`
- `maxmemory-policy` 설정 확인

### TTL이 예상과 다름
- Redis 서버 시간 확인: `docker exec -it reading-tracker-redis redis-cli TIME`
- 애플리케이션 로그에서 TTL 설정 확인

---

## 참고 사항

- 모든 테스트는 **로컬 개발 환경**에서 수행합니다
- Redis 데이터는 **컨테이너 재시작 시 삭제**될 수 있습니다 (영속성 설정 확인)
- 프로덕션 환경에서는 **Redis 영속성(AOF+RDB)**이 활성화되어 있어야 합니다
- 테스트 후 불필요한 데이터는 정리하는 것을 권장합니다:
  ```bash
  # 특정 패턴의 키 삭제 (주의: 실제 데이터 삭제됨)
  docker exec -it reading-tracker-redis redis-cli --eval "return redis.call('del', unpack(redis.call('keys', ARGV[1])))" 0 "test-*"
  ```

