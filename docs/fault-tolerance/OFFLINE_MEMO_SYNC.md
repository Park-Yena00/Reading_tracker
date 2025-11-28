# 오프라인 메모 작성 및 동기화 설계

> **목적**: 네트워크가 없는 환경에서 메모를 작성하고, 네트워크 복구 시 자동으로 서버에 동기화하는 기능 구현  
> **비기능 품질**: Fault Tolerance (장애 허용)  
> **시나리오**: 오프라인 상태에서 메모 작성 → 로컬 저장 → 네트워크 복구 시 자동 동기화

---

## 📋 목차

1. [시나리오 분석](#시나리오-분석)
2. [해결 방법 개요](#해결-방법-개요)
3. [기술 스택 및 아키텍처](#기술-스택-및-아키텍처)
4. [데이터 모델](#데이터-모델)
5. [구현 방법](#구현-방법)
6. [동기화 전략](#동기화-전략)
7. [충돌 해결](#충돌-해결)
8. [에러 처리](#에러-처리)
9. [구현 단계별 가이드](#구현-단계별-가이드)
10. [테스트 방법](#테스트-방법)

---

## 시나리오 분석

### 요구사항

1. **오프라인 환경에서 메모 작성**
   - 네트워크가 없어도 메모 작성 가능
   - 모든 메모는 내 서재(userBookId)에 저장된 책에 대해서만 작성 가능
   - 내용 손실 없이 UI에 즉시 표시

2. **로컬 저장**
   - 서버에 저장할 수 없으므로 로컬 저장소에 임시 저장
   - 브라우저 재시작 후에도 데이터 유지

3. **자동 동기화**
   - 네트워크 재연결 시 자동으로 서버에 동기화
   - 데이터 손실 없이 모든 메모 저장
   - 동기화 상태 UI 표시

### 제약사항

- **메모 작성 조건**: `userBookId`가 내 서재에 존재하는 책이어야 함
- **데이터 유효성**: 서버와 동일한 검증 규칙 적용 필요
  - `userBookId`: 필수
  - `pageNumber`: 필수, 1 이상
  - `content`: 필수, 최대 5000자
  - `tags`: 선택 (문자열 리스트)
  - `memoStartTime`: 필수

### 고려사항

1. **로컬 ID 생성**: 서버 ID는 서버에서 생성되므로 임시 ID 필요
2. **동기화 순서**: 오프라인 메모들은 작성 시간 순으로 동기화
3. **중복 방지**: 동일한 메모가 중복 저장되지 않도록 보장
4. **부분 실패**: 일부 메모만 동기화 실패 시 재시도 로직

### 관련 시나리오

**멀티 디바이스 오프라인 동기화**: 여러 디바이스(웹, 모바일 앱)에서 오프라인 상태로 작성한 메모가 네트워크 복구 시 모든 디바이스에서 동기화되어 무결성을 유지하는 시나리오에 대해서는 [멀티 디바이스 오프라인 동기화 설계](./MULTI_DEVICE_SYNC.md) 문서를 참조하세요.

---

## 해결 방법 개요

### Offline-First 접근법

**핵심 원칙**:
1. **로컬 우선**: 항상 로컬 저장소에 먼저 저장
2. **백그라운드 동기화**: 네트워크 상태와 무관하게 동작
3. **낙관적 업데이트**: 즉시 UI 업데이트, 나중에 서버 동기화
4. **재시도 메커니즘**: 동기화 실패 시 자동 재시도

### 아키텍처 플로우

```
[사용자 메모 작성]
        ↓
[로컬 저장소에 저장] ← IndexedDB
        ↓
[UI 즉시 업데이트]
        ↓
[네트워크 상태 확인]
        ├─ 온라인 → [동기화 큐에 추가] → [서버로 전송]
        └─ 오프라인 → [대기 상태 표시]
                            ↓
                    [네트워크 재연결 감지]
                            ↓
                    [대기 중인 메모 동기화]
                            ↓
                    [서버 응답 처리]
                            ├─ 성공 → [로컬 메모 업데이트 (서버 ID)]
                            └─ 실패 → [재시도 큐에 추가]
```

### 네트워크 연결 감지 및 자동 동기화 메커니즘

#### 1. 네트워크 상태 감지 방법

**핵심 원리:**
- **`navigator.onLine` API**: 현재 브라우저의 온라인/오프라인 상태를 확인
- **`online` / `offline` 이벤트**: 네트워크 상태 변경 시 자동으로 이벤트 발생
- **헬스체크 (선택사항)**: 실제 서버 연결 가능 여부를 확인

**감지 메커니즘:**

```javascript
// 1. 초기 상태 확인
const isOnline = navigator.onLine;  // boolean: true/false

// 2. 이벤트 리스너 등록
window.addEventListener('online', () => {
    // 네트워크 연결 복구 감지
    console.log('네트워크가 연결되었습니다!');
});

window.addEventListener('offline', () => {
    // 네트워크 연결 끊김 감지
    console.log('네트워크가 끊어졌습니다!');
});
```

**작동 원리:**
1. 브라우저가 시스템의 네트워크 어댑터 상태를 모니터링
2. 네트워크 연결이 감지되면 `online` 이벤트 자동 발생
3. 네트워크 연결이 끊어지면 `offline` 이벤트 자동 발생
4. 이벤트 발생 시 등록된 콜백 함수 실행

#### 2. 네트워크 복구 감지 시 자동 동기화 플로우

**단계별 처리 과정:**

```
[Step 1] 네트워크 연결 감지
    ↓
    브라우저: 'online' 이벤트 발생
    ↓
    NetworkMonitor.onNetworkOnline() 호출
    ↓
    
[Step 2] 네트워크 안정화 대기
    ↓
    setTimeout(1000ms) - 네트워크 안정화 대기
    (너무 빠른 동기화 시도 방지)
    ↓
    
[Step 3] 대기 중인 메모 조회
    ↓
    IndexedDB에서 syncStatus = 'pending'인 메모들 조회
    ↓
    memoStartTime 기준 정렬 (작성 순서 보장)
    ↓
    
[Step 4] 순차 동기화 실행
    ↓
    for each (대기 중인 메모) {
        [4-1] 동기화 상태 업데이트
            - syncStatus: 'pending' → 'syncing'
            ↓
        [4-2] 서버 API 호출
            POST /api/v1/memos
            Request Body: MemoCreateRequest
            ↓
        [4-3] 서버 응답 처리
            ├─ 성공 (200 OK)
            │   ↓
            │   - 서버에서 생성된 ID 받음
            │   - 로컬 메모에 serverId 저장
            │   - syncStatus: 'syncing' → 'synced'
            │   - 동기화 큐 항목 status: 'SUCCESS'
            │
            └─ 실패 (4xx/5xx 또는 네트워크 오류)
                ↓
                - syncStatus: 'syncing' → 'failed'
                - 동기화 큐에 에러 기록
                - 재시도 로직 트리거 (Exponential Backoff)
    }
    ↓
    
[Step 5] 동기화 완료 후 처리
    ↓
    - 성공한 메모: IndexedDB 업데이트 (serverId 설정)
    - 실패한 메모: 재시도 큐에 추가
    - UI 업데이트 (동기화 상태 표시)
```

#### 3. 실제 데이터 전달 과정

**로컬 저장소 → 서버 → DB 흐름:**

```javascript
// [로컬 저장소 (IndexedDB)]
{
  localId: "550e8400-e29b-41d4-a716-446655440000",
  userBookId: 123,
  pageNumber: 50,
  content: "메모 내용",
  tags: ["태그1", "태그2"],
  memoStartTime: "2024-01-01T10:30:00Z",
  syncStatus: "pending"
}
    ↓
// [동기화 요청 생성]
{
  type: "CREATE",
  localMemoId: "550e8400-e29b-41d4-a716-446655440000",
  data: {
    userBookId: 123,
    pageNumber: 50,
    content: "메모 내용",
    tags: ["태그1", "태그2"],
    memoStartTime: "2024-01-01T10:30:00Z"
  }
}
    ↓
// [HTTP 요청: POST /api/v1/memos]
fetch('http://localhost:8080/api/v1/memos', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer <access_token>'
  },
  body: JSON.stringify({
    userBookId: 123,
    pageNumber: 50,
    content: "메모 내용",
    tags: ["태그1", "태그2"],
    memoStartTime: "2024-01-01T10:30:00Z"
  })
})
    ↓
// [서버 처리]
Spring Boot Controller → Service → Repository → MySQL
    ↓
// [서버 응답]
{
  "ok": true,
  "data": {
    "id": 456,  // 서버에서 생성된 ID
    "userBookId": 123,
    "content": "메모 내용",
    ...
    "createdAt": "2024-01-01T10:35:00Z"
  }
}
    ↓
// [로컬 저장소 업데이트]
{
  localId: "550e8400-e29b-41d4-a716-446655440000",
  serverId: 456,  // ← 서버 ID 저장
  ...
  syncStatus: "synced"  // ← 동기화 완료
}
```

#### 4. 네트워크 감지 신뢰성 보장

**문제점:**
- `navigator.onLine`은 네트워크 어댑터 상태만 확인
- 실제 서버 연결 가능 여부와 다를 수 있음
- 예: Wi-Fi 연결되어 있지만 인터넷 접속 불가

**해결 방법:**

**Option 1: 기본 방식 (간단)**
```javascript
// navigator.onLine만 사용
// 빠르고 가볍지만 완벽하지 않을 수 있음
window.addEventListener('online', () => {
    // 네트워크 복구 감지
    onNetworkOnline();
});
```

**Option 2: 헬스체크 추가 (권장)** ⭐
```javascript
// utils/network-monitor.js (개선된 버전)
class NetworkMonitor {
    async onNetworkOnline() {
        // 1초 대기 (네트워크 안정화)
        await this.delay(1000);
        
        // 2. 실제 서버 연결 가능 여부 확인 (헬스체크)
        const isServerReachable = await this.checkServerHealth();
        
        if (isServerReachable) {
            // 서버에 실제로 연결 가능 → 동기화 시작
            await offlineMemoService.syncPendingMemos();
        } else {
            // 네트워크는 연결되었지만 서버 접근 불가
            console.warn('네트워크는 연결되었지만 서버에 접근할 수 없습니다.');
            // 재시도 예약
            setTimeout(() => this.onNetworkOnline(), 5000);
        }
    }
    
    /**
     * 서버 헬스체크 (실제 연결 가능 여부 확인)
     */
    async checkServerHealth() {
        try {
            // 간단한 HEAD 요청으로 서버 응답 확인
            const response = await fetch('http://localhost:8080/api/v1/health', {
                method: 'HEAD',
                signal: AbortSignal.timeout(3000)  // 3초 타임아웃
            });
            return response.ok;
        } catch (error) {
            console.error('서버 헬스체크 실패:', error);
            return false;
        }
    }
}
```

**Option 3: 동기화 시도 시 실제 연결 확인**
```javascript
// 동기화 시도 시 실제 API 호출로 연결 가능 여부 확인
// 실패 시 자동으로 재시도 큐에 추가
// (현재 구현 방식)
```

#### 5. 네트워크 감지 및 동기화 통합 플로우

**전체 시스템 동작 흐름:**

```
┌─────────────────────────────────────────────────────┐
│  [사용자 액션]                                       │
│  오프라인 상태에서 메모 작성                         │
└─────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────┐
│  [로컬 저장]                                         │
│  1. IndexedDB에 메모 저장                           │
│  2. syncStatus = 'pending' 설정                     │
│  3. 동기화 큐에 항목 추가                           │
│  4. UI 즉시 업데이트                                │
└─────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────┐
│  [네트워크 상태 감지 대기]                           │
│  NetworkMonitor가 online 이벤트 감지 대기           │
└─────────────────────────────────────────────────────┘
                    ↓
        [사용자가 네트워크 연결]
                    ↓
┌─────────────────────────────────────────────────────┐
│  [네트워크 연결 감지]                                │
│  1. 브라우저: 'online' 이벤트 발생                   │
│  2. NetworkMonitor.onNetworkOnline() 호출          │
│  3. navigator.onLine = true 확인                    │
└─────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────┐
│  [네트워크 안정화 대기]                              │
│  setTimeout(1000ms) - 안정화 대기                   │
└─────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────┐
│  [대기 중인 메모 조회]                               │
│  1. IndexedDB에서 syncStatus='pending' 조회        │
│  2. memoStartTime 기준 정렬                         │
│  3. 동기화할 메모 목록 생성                         │
└─────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────┐
│  [순차 동기화 실행]                                  │
│  for each 메모:                                     │
│    1. syncStatus → 'syncing'                        │
│    2. POST /api/v1/memos 요청                       │
│    3. 서버 응답 대기                                │
│       ├─ 성공 → serverId 저장, 'synced' 설정       │
│       └─ 실패 → 'failed' 설정, 재시도 큐 추가      │
└─────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────┐
│  [서버 처리]                                         │
│  1. MemoController.createMemo()                     │
│  2. MemoService.createMemo()                        │
│  3. MemoRepository.save()                           │
│  4. MySQL 데이터베이스에 저장                       │
│  5. 서버 생성 ID 반환                               │
└─────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────┐
│  [로컬 저장소 업데이트]                              │
│  1. IndexedDB에서 해당 localId 찾기                 │
│  2. serverId 필드 업데이트                          │
│  3. syncStatus → 'synced' 업데이트                  │
│  4. 동기화 큐 항목 → 'SUCCESS' 업데이트             │
└─────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────┐
│  [UI 업데이트]                                       │
│  동기화 상태 아이콘 변경:                            │
│  ⏳ 대기 중 → 🔄 동기화 중 → ✓ 동기화 완료         │
└─────────────────────────────────────────────────────┘
```

#### 6. 네트워크 감지 이벤트 예제

**실제 코드에서의 동작:**

```javascript
// 앱 시작 시 NetworkMonitor 초기화
const networkMonitor = new NetworkMonitor();

// 네트워크 연결 감지 시 자동 실행되는 흐름:

// 1. 사용자가 Wi-Fi 연결 또는 네트워크 케이블 연결
//    ↓
// 2. 브라우저가 'online' 이벤트 발생
window.addEventListener('online', () => {
    // ↓
    // 3. NetworkMonitor.onNetworkOnline() 자동 호출
    networkMonitor.onNetworkOnline();
});

// 4. 1초 후 (안정화 대기)
setTimeout(async () => {
    // ↓
    // 5. 대기 중인 메모 동기화 시작
    await offlineMemoService.syncPendingMemos();
}, 1000);

// 6. IndexedDB에서 대기 중인 메모 조회
const pendingMemos = await dbManager.getPendingMemos();
// 결과: [{ localId: "...", content: "...", ... }, ...]

// 7. 각 메모를 순차적으로 동기화
for (const memo of pendingMemos) {
    // 7-1. 동기화 상태 업데이트
    memo.syncStatus = 'syncing';
    await dbManager.saveMemo(memo);
    
    // 7-2. 서버 API 호출
    const response = await apiClient.post('/memos', {
        userBookId: memo.userBookId,
        pageNumber: memo.pageNumber,
        content: memo.content,
        tags: memo.tags,
        memoStartTime: memo.memoStartTime
    });
    
    // 7-3. 서버 응답으로 로컬 메모 업데이트
    await dbManager.updateMemoWithServerId(memo.localId, response.data.id);
    // IndexedDB 업데이트: serverId = 456, syncStatus = 'synced'
}
```

#### 7. 네트워크 감지의 한계 및 보완책

**한계점:**
1. `navigator.onLine`은 네트워크 어댑터 상태만 확인 (실제 인터넷 연결과 다를 수 있음)
2. 일부 환경에서는 `online` 이벤트가 지연되거나 발생하지 않을 수 있음
3. 서버가 다운되어 있으면 네트워크는 연결되어도 동기화 불가

**보완책:**

**1. 주기적 헬스체크 (Polling)**
```javascript
// 주기적으로 서버 연결 가능 여부 확인
setInterval(async () => {
    if (navigator.onLine) {
        const isServerReachable = await checkServerHealth();
        if (isServerReachable) {
            // 대기 중인 메모가 있으면 동기화 시도
            const pendingCount = await getPendingMemoCount();
            if (pendingCount > 0) {
                await offlineMemoService.syncPendingMemos();
            }
        }
    }
}, 30000); // 30초마다 확인
```

**2. 수동 동기화 버튼 제공**
```javascript
// 사용자가 수동으로 동기화 시도 가능
function manualSync() {
    offlineMemoService.syncPendingMemos();
}
```

**3. 동기화 실패 시 자동 재시도**
```javascript
// 이미 구현됨: Exponential Backoff 재시도
// 5초, 10초, 20초 후 자동 재시도
```

---

## 기술 스택 및 아키텍처

### 클라이언트 측 기술

#### 1. 로컬 저장소 선택

**Option 1: IndexedDB (추천)** ⭐
- ✅ 대용량 데이터 저장 가능
- ✅ 비동기 API (논블로킹)
- ✅ 구조화된 데이터 저장
- ✅ 복잡한 쿼리 지원
- ⚠️ 구현 복잡도 높음

**Option 2: LocalStorage**
- ✅ 간단한 API
- ✅ 동기 API
- ❌ 용량 제한 (~5-10MB)
- ❌ 구조화된 데이터 저장 제한
- ⚠️ 대량 데이터 처리 시 성능 저하

**결정**: **IndexedDB 사용** (용량 제한 없고, 메모가 많아질 수 있으므로)

#### 2. 동기화 큐 관리

```javascript
// 동기화 큐 구조
{
  id: "sync-queue-item-id",        // 고유 ID
  type: "CREATE",                   // 작업 타입 (CREATE, UPDATE, DELETE)
  localMemoId: "local-memo-id",    // 로컬 임시 ID
  data: MemoCreateRequest,          // 요청 데이터
  status: "PENDING",                // PENDING, SYNCING, SUCCESS, FAILED
  retryCount: 0,                    // 재시도 횟수
  createdAt: Date,                  // 생성 시간
  lastRetryAt: Date                 // 마지막 재시도 시간
}
```

#### 3. 네트워크 상태 감지

- `navigator.onLine` API
- `online` / `offline` 이벤트 리스너
- 주기적인 헬스체크 (선택사항)

### 서버 측 처리

#### 현재 API 구조 (변경 불필요)

```
POST /api/v1/memos
Request: MemoCreateRequest
Response: MemoResponse (서버 생성 ID 포함)
```

**서버 변경사항**: 없음 (기존 API 그대로 사용)

---

## 데이터 모델

### 로컬 메모 저장 구조

```javascript
// IndexedDB 스키마: offline_memos
{
  localId: string,              // 로컬 임시 ID (UUID)
  serverId: number | null,      // 서버 ID (동기화 후 설정)
  userBookId: number,           // 사용자 책 ID
  pageNumber: number,           // 페이지 번호
  content: string,              // 메모 내용
  tags: string[],               // 태그 리스트
  memoStartTime: string,        // ISO 8601 형식
  syncStatus: string,           // "pending" | "syncing" | "synced" | "failed"
  createdAt: string,            // 로컬 생성 시간
  updatedAt: string,            // 로컬 수정 시간
  syncQueueId: string | null    // 동기화 큐 항목 ID
}
```

### 동기화 큐 저장 구조

```javascript
// IndexedDB 스키마: sync_queue
{
  id: string,                   // 고유 ID (UUID)
  type: string,                 // "CREATE" | "UPDATE" | "DELETE"
  localMemoId: string,          // 로컬 메모 ID
  data: object,                 // 요청 데이터 (MemoCreateRequest 등)
  status: string,               // "PENDING" | "SYNCING" | "SUCCESS" | "FAILED"
  retryCount: number,           // 재시도 횟수
  error: string | null,         // 에러 메시지
  createdAt: string,            // 생성 시간
  lastRetryAt: string | null    // 마지막 재시도 시간
}
```

---

## 구현 방법

### 1. IndexedDB 초기화 및 스키마 정의

```javascript
// storage/indexeddb-manager.js
class IndexedDBManager {
    constructor() {
        this.dbName = 'reading-tracker';
        this.version = 1;
        this.db = null;
    }

    async init() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(this.dbName, this.version);

            request.onerror = () => reject(request.error);
            request.onsuccess = () => {
                this.db = request.result;
                resolve(this.db);
            };

            request.onupgradeneeded = (event) => {
                const db = event.target.result;

                // offline_memos 테이블
                if (!db.objectStoreNames.contains('offline_memos')) {
                    const memoStore = db.createObjectStore('offline_memos', {
                        keyPath: 'localId'
                    });
                    memoStore.createIndex('syncStatus', 'syncStatus', { unique: false });
                    memoStore.createIndex('userBookId', 'userBookId', { unique: false });
                    memoStore.createIndex('memoStartTime', 'memoStartTime', { unique: false });
                }

                // sync_queue 테이블
                if (!db.objectStoreNames.contains('sync_queue')) {
                    const queueStore = db.createObjectStore('sync_queue', {
                        keyPath: 'id'
                    });
                    queueStore.createIndex('status', 'status', { unique: false });
                    queueStore.createIndex('localMemoId', 'localMemoId', { unique: false });
                }
            };
        });
    }

    // 메모 저장
    async saveMemo(memo) {
        const transaction = this.db.transaction(['offline_memos'], 'readwrite');
        const store = transaction.objectStore('offline_memos');
        return store.put(memo);
    }

    // 동기화 대기 중인 메모 조회
    async getPendingMemos() {
        const transaction = this.db.transaction(['offline_memos'], 'readonly');
        const store = transaction.objectStore('offline_memos');
        const index = store.index('syncStatus');
        return index.getAll('pending');
    }

    // 메모 업데이트 (서버 ID 설정)
    async updateMemoWithServerId(localId, serverId) {
        const transaction = this.db.transaction(['offline_memos'], 'readwrite');
        const store = transaction.objectStore('offline_memos');
        const memo = await store.get(localId);
        if (memo) {
            memo.serverId = serverId;
            memo.syncStatus = 'synced';
            return store.put(memo);
        }
    }
}

export const dbManager = new IndexedDBManager();
```

### 2. 오프라인 메모 작성 서비스

```javascript
// services/offline-memo-service.js
import { dbManager } from '../storage/indexeddb-manager.js';
import { syncQueueManager } from './sync-queue-manager.js';
import { networkMonitor } from '../utils/network-monitor.js';

class OfflineMemoService {
    constructor() {
        this.isInitialized = false;
    }

    async init() {
        if (!this.isInitialized) {
            await dbManager.init();
            this.isInitialized = true;
        }
    }

    /**
     * 메모 작성 (오프라인 지원)
     * 1. 로컬 저장소에 저장
     * 2. 동기화 큐에 추가
     * 3. 네트워크가 연결되어 있으면 즉시 동기화 시도
     */
    async createMemo(memoData) {
        await this.init();

        // 로컬 ID 생성 (UUID)
        const localId = this.generateLocalId();

        // 로컬 메모 객체 생성
        const localMemo = {
            localId,
            serverId: null,
            userBookId: memoData.userBookId,
            pageNumber: memoData.pageNumber,
            content: memoData.content,
            tags: memoData.tags || [],
            memoStartTime: memoData.memoStartTime,
            syncStatus: 'pending',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            syncQueueId: null
        };

        // 로컬 저장소에 저장
        await dbManager.saveMemo(localMemo);

        // 동기화 큐에 추가
        const queueItem = await syncQueueManager.enqueue({
            type: 'CREATE',
            localMemoId: localId,
            data: memoData
        });

        // syncQueueId 업데이트
        localMemo.syncQueueId = queueItem.id;
        await dbManager.saveMemo(localMemo);

        // 네트워크가 연결되어 있으면 즉시 동기화 시도
        if (networkMonitor.isOnline) {
            this.syncPendingMemos();
        }

        return localMemo;
    }

    /**
     * 모든 오프라인 메모 조회 (UI 표시용)
     */
    async getAllMemos() {
        await this.init();
        const transaction = dbManager.db.transaction(['offline_memos'], 'readonly');
        const store = transaction.objectStore('offline_memos');
        return store.getAll();
    }

    /**
     * 특정 책의 메모 조회
     */
    async getMemosByBook(userBookId) {
        await this.init();
        const transaction = dbManager.db.transaction(['offline_memos'], 'readonly');
        const store = transaction.objectStore('offline_memos');
        const index = store.index('userBookId');
        return index.getAll(userBookId);
    }

    /**
     * 로컬 ID 생성 (UUID v4)
     */
    generateLocalId() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
            const r = Math.random() * 16 | 0;
            const v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }

    /**
     * 대기 중인 메모 동기화
     */
    async syncPendingMemos() {
        if (!networkMonitor.isOnline) {
            console.log('네트워크가 오프라인 상태입니다.');
            return;
        }

        const pendingMemos = await dbManager.getPendingMemos();
        console.log(`동기화할 메모 수: ${pendingMemos.length}`);

        for (const memo of pendingMemos) {
            try {
                await this.syncSingleMemo(memo);
            } catch (error) {
                console.error(`메모 동기화 실패 (${memo.localId}):`, error);
                // 재시도 로직은 syncQueueManager에서 처리
            }
        }
    }

    /**
     * 단일 메모 동기화
     */
    async syncSingleMemo(localMemo) {
        // 동기화 상태 업데이트
        localMemo.syncStatus = 'syncing';
        await dbManager.saveMemo(localMemo);

        try {
            // 서버 API 호출
            const response = await apiClient.post('/memos', {
                userBookId: localMemo.userBookId,
                pageNumber: localMemo.pageNumber,
                content: localMemo.content,
                tags: localMemo.tags,
                memoStartTime: localMemo.memoStartTime
            });

            // 서버 ID로 업데이트
            await dbManager.updateMemoWithServerId(localMemo.localId, response.data.id);

            // 동기화 큐에서 제거
            if (localMemo.syncQueueId) {
                await syncQueueManager.markAsSuccess(localMemo.syncQueueId);
            }

            console.log(`메모 동기화 성공: ${localMemo.localId} → ${response.data.id}`);
        } catch (error) {
            // 동기화 실패 처리
            localMemo.syncStatus = 'failed';
            await dbManager.saveMemo(localMemo);

            // 동기화 큐에 에러 기록 및 재시도 예약
            if (localMemo.syncQueueId) {
                await syncQueueManager.markAsFailed(localMemo.syncQueueId, error.message);
            }

            throw error;
        }
    }
}

export const offlineMemoService = new OfflineMemoService();
```

### 3. 동기화 큐 관리자

```javascript
// services/sync-queue-manager.js
import { dbManager } from '../storage/indexeddb-manager.js';

class SyncQueueManager {
    constructor() {
        this.maxRetries = 3;
        this.retryDelay = 5000; // 5초
    }

    /**
     * 동기화 큐에 항목 추가
     */
    async enqueue(item) {
        const queueItem = {
            id: this.generateId(),
            type: item.type,
            localMemoId: item.localMemoId,
            data: item.data,
            status: 'PENDING',
            retryCount: 0,
            error: null,
            createdAt: new Date().toISOString(),
            lastRetryAt: null
        };

        const transaction = dbManager.db.transaction(['sync_queue'], 'readwrite');
        const store = transaction.objectStore('sync_queue');
        await store.put(queueItem);

        return queueItem;
    }

    /**
     * 동기화 성공 처리
     */
    async markAsSuccess(queueId) {
        const transaction = dbManager.db.transaction(['sync_queue'], 'readwrite');
        const store = transaction.objectStore('sync_queue');
        const item = await store.get(queueId);
        if (item) {
            item.status = 'SUCCESS';
            await store.put(item);
        }
    }

    /**
     * 동기화 실패 처리 및 재시도 예약
     */
    async markAsFailed(queueId, errorMessage) {
        const transaction = dbManager.db.transaction(['sync_queue'], 'readwrite');
        const store = transaction.objectStore('sync_queue');
        const item = await store.get(queueId);

        if (item) {
            item.status = 'FAILED';
            item.error = errorMessage;
            item.retryCount += 1;
            item.lastRetryAt = new Date().toISOString();

            // 최대 재시도 횟수 확인
            if (item.retryCount < this.maxRetries) {
                // 재시도 예약 (Exponential Backoff)
                const delay = this.retryDelay * Math.pow(2, item.retryCount - 1);
                setTimeout(() => {
                    this.retrySync(item);
                }, delay);
            }

            await store.put(item);
        }
    }

    /**
     * 재시도 실행
     */
    async retrySync(queueItem) {
        // 메모 서비스를 통해 재동기화 시도
        const localMemo = await dbManager.getMemoByLocalId(queueItem.localMemoId);
        if (localMemo && localMemo.syncStatus !== 'synced') {
            queueItem.status = 'PENDING';
            await this.enqueue(queueItem);
            // offlineMemoService.syncSingleMemo 호출
        }
    }

    /**
     * 모든 대기 중인 큐 항목 조회
     */
    async getPendingItems() {
        const transaction = dbManager.db.transaction(['sync_queue'], 'readonly');
        const store = transaction.objectStore('sync_queue');
        const index = store.index('status');
        return index.getAll('PENDING');
    }

    generateId() {
        return 'sync-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
    }
}

export const syncQueueManager = new SyncQueueManager();
```

### 4. 네트워크 상태 모니터

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
            console.log('네트워크 연결 복구');
            this.isOnline = true;
            this.notifyListeners(true);
            this.onNetworkOnline();
        });

        window.addEventListener('offline', () => {
            console.log('네트워크 연결 끊김');
            this.isOnline = false;
            this.notifyListeners(false);
        });
    }

    /**
     * 네트워크 복구 시 대기 중인 메모 동기화
     */
    async onNetworkOnline() {
        // 약간의 지연 후 동기화 (네트워크 안정화 대기)
        setTimeout(async () => {
            try {
                await offlineMemoService.syncPendingMemos();
            } catch (error) {
                console.error('자동 동기화 실패:', error);
            }
        }, 1000);
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

### 5. 통합 API 클라이언트 (오프라인 지원)

```javascript
// services/memo-service.js (개선된 버전)
import { offlineMemoService } from './offline-memo-service.js';
import { networkMonitor } from '../utils/network-monitor.js';
import { apiClient } from './api-client.js';

class MemoService {
    /**
     * 메모 작성 (온라인/오프라인 자동 처리)
     */
    async createMemo(memoData) {
        // 항상 로컬 저장소에 먼저 저장
        const localMemo = await offlineMemoService.createMemo(memoData);

        // 온라인 상태면 즉시 동기화 시도, 오프라인이면 대기
        if (networkMonitor.isOnline) {
            // 백그라운드에서 동기화 (await 하지 않음)
            offlineMemoService.syncPendingMemos().catch(error => {
                console.error('백그라운드 동기화 실패:', error);
            });
        }

        // 로컬 메모를 즉시 반환 (낙관적 업데이트)
        return this.mapLocalMemoToResponse(localMemo);
    }

    /**
     * 메모 목록 조회 (로컬 + 서버 통합)
     */
    async getMemos(userBookId, date) {
        // 로컬 메모 조회
        const localMemos = await offlineMemoService.getMemosByBook(userBookId);

        // 온라인 상태면 서버에서도 조회하여 통합
        if (networkMonitor.isOnline) {
            try {
                const serverResponse = await apiClient.get(`/memos/books/${userBookId}`, {
                    params: { date }
                });
                const serverMemos = serverResponse.data;

                // 로컬 메모와 서버 메모 통합
                return this.mergeMemos(localMemos, serverMemos);
            } catch (error) {
                console.error('서버 메모 조회 실패, 로컬 메모만 반환:', error);
                return this.mapLocalMemosToResponse(localMemos);
            }
        } else {
            // 오프라인 상태면 로컬 메모만 반환
            return this.mapLocalMemosToResponse(localMemos);
        }
    }

    /**
     * 로컬 메모와 서버 메모 통합
     */
    mergeMemos(localMemos, serverMemos) {
        // 서버 메모를 맵으로 변환 (중복 제거용)
        const serverMemoMap = new Map();
        serverMemos.forEach(memo => {
            serverMemoMap.set(memo.id, memo);
        });

        // 로컬 메모 중 동기화 완료된 것은 서버 메모로 대체
        const result = [];
        localMemos.forEach(localMemo => {
            if (localMemo.syncStatus === 'synced' && localMemo.serverId) {
                // 서버 메모가 있으면 사용, 없으면 로컬 메모 사용
                const serverMemo = serverMemoMap.get(localMemo.serverId);
                if (serverMemo) {
                    result.push(serverMemo);
                    serverMemoMap.delete(localMemo.serverId); // 이미 처리됨
                } else {
                    result.push(this.mapLocalMemoToResponse(localMemo));
                }
            } else {
                // 동기화 대기 중인 로컬 메모
                result.push(this.mapLocalMemoToResponse(localMemo));
            }
        });

        // 서버에만 있는 메모 추가
        serverMemoMap.forEach(memo => {
            result.push(memo);
        });

        // 시간순 정렬
        return result.sort((a, b) => {
            const timeA = new Date(a.memoStartTime || a.createdAt);
            const timeB = new Date(b.memoStartTime || b.createdAt);
            return timeA - timeB;
        });
    }

    mapLocalMemoToResponse(localMemo) {
        return {
            id: localMemo.serverId || localMemo.localId, // 서버 ID가 없으면 로컬 ID 사용
            localId: localMemo.localId,
            userBookId: localMemo.userBookId,
            content: localMemo.content,
            tags: localMemo.tags,
            pageNumber: localMemo.pageNumber,
            memoStartTime: localMemo.memoStartTime,
            syncStatus: localMemo.syncStatus,
            createdAt: localMemo.createdAt,
            updatedAt: localMemo.updatedAt
        };
    }

    mapLocalMemosToResponse(localMemos) {
        return localMemos.map(memo => this.mapLocalMemoToResponse(memo));
    }
}

export const memoService = new MemoService();
```

---

## 동기화 전략

### 1. 즉시 동기화 (낙관적 업데이트)

- 메모 작성 즉시 로컬 저장
- UI 즉시 업데이트
- 온라인 상태면 백그라운드에서 동기화
- 사용자 경험 최우선

### 2. 자동 재시도

- Exponential Backoff 전략
- 최대 3회 재시도
- 재시도 간격: 5초, 10초, 20초

### 3. 배치 동기화

- 네트워크 복구 시 모든 대기 중인 메모 순차 동기화
- 순서 보장: `memoStartTime` 기준 정렬 후 동기화

### 4. 부분 실패 처리

- 일부 메모만 동기화 실패해도 나머지는 계속 진행
- 실패한 메모는 재시도 큐에 추가

---

## 충돌 해결

### 시나리오

**충돌이 발생하지 않는 경우**:
- 오프라인 메모는 모두 "생성(CREATE)" 작업
- 서버에서 ID를 생성하므로 중복 생성 문제 없음
- 동일한 메모를 두 번 작성한 경우는 사용자 의도로 간주

### 중복 방지

1. **로컬 ID 기준**: 로컬 메모는 고유한 `localId`로 식별
2. **서버 ID 매핑**: 동기화 성공 시 `serverId` 설정하여 매핑
3. **동기화 상태 관리**: `syncStatus`로 중복 동기화 방지

---

## 에러 처리

### 동기화 실패 시나리오

1. **네트워크 오류**: 재시도 큐에 추가
2. **서버 에러 (4xx)**: 
   - 검증 오류: 로컬 메모에 에러 표시, 수정 유도
   - 인증 오류: 토큰 갱신 후 재시도
3. **서버 에러 (5xx)**: 재시도 큐에 추가

### 사용자 피드백

```javascript
// UI에서 동기화 상태 표시
function renderMemo(memo) {
    const statusIcon = {
        'pending': '⏳ 대기 중',
        'syncing': '🔄 동기화 중',
        'synced': '✓ 동기화 완료',
        'failed': '❌ 동기화 실패'
    };

    return `
        <div class="memo-item" data-local-id="${memo.localId}">
            <div class="memo-content">${memo.content}</div>
            <div class="sync-status">${statusIcon[memo.syncStatus]}</div>
        </div>
    `;
}
```

---

## 구현 단계별 가이드

### Phase 1: 기본 인프라 구축

1. **IndexedDB 초기화**
   - 스키마 정의
   - 테이블 생성
   - CRUD 메서드 구현

2. **네트워크 모니터링**
   - `navigator.onLine` 감지
   - 이벤트 리스너 설정

### Phase 2: 오프라인 메모 작성

1. **로컬 저장 기능**
   - 메모 작성 시 IndexedDB에 저장
   - 로컬 ID 생성

2. **UI 통합**
   - 기존 메모 작성 UI와 통합
   - 로컬 메모 표시

### Phase 3: 동기화 기능

1. **동기화 큐 구현**
   - 큐 항목 생성/관리
   - 상태 관리

2. **서버 동기화**
   - API 호출
   - 서버 ID 매핑
   - 상태 업데이트

### Phase 4: 자동화

1. **네트워크 복구 감지**
   - 자동 동기화 트리거

2. **재시도 로직**
   - Exponential Backoff
   - 실패 처리

### Phase 5: UI 개선

1. **동기화 상태 표시**
   - 대기/동기화 중/완료/실패 표시

2. **에러 피드백**
   - 동기화 실패 시 사용자 알림
   - 수동 재시도 버튼

---

## 테스트 방법

### 1. 오프라인 메모 작성 테스트

#### 방법 1: 브라우저 DevTools 이용 (가장 간단)

**단계별 테스트:**

1. **브라우저 DevTools 열기**
   - F12 키 누르기
   - 또는 우클릭 → 검사

2. **네트워크 차단 설정**
   ```
   DevTools → Network 탭
   → Throttling 드롭다운 선택
   → "Offline" 선택
   ```

3. **메모 작성**
   - 웹사이트에서 메모 작성 UI 접근
   - 메모 내용 입력 (예: "오프라인 테스트 메모")
   - `userBookId`, `pageNumber`, `content` 등 필수 정보 입력
   - 저장 버튼 클릭

4. **확인 사항** ✅
   - ✅ 메모가 UI에 즉시 표시되는가?
   - ✅ 동기화 상태가 "⏳ 대기 중"으로 표시되는가?
   - ✅ 에러 메시지가 표시되지 않는가?
   - ✅ 페이지를 새로고침해도 메모가 유지되는가?

5. **IndexedDB 확인**
   ```
   DevTools → Application 탭
   → Storage → IndexedDB → reading-tracker
   → offline_memos 테이블 클릭
   → 작성한 메모 데이터 확인
   ```
   - `syncStatus`: "pending" 확인
   - `localId`: UUID 형식의 로컬 ID 확인
   - `serverId`: null 확인

6. **동기화 큐 확인**
   ```
   Application 탭 → IndexedDB → reading-tracker
   → sync_queue 테이블 클릭
   → 큐 항목 확인
   ```
   - `status`: "PENDING" 확인
   - `type`: "CREATE" 확인
   - `localMemoId`: 메모의 localId와 일치하는지 확인

#### 방법 2: Clumsy를 이용한 네트워크 차단

**준비:**
1. Clumsy 다운로드 및 설치: https://jagt.github.io/clumsy/
2. 관리자 권한으로 실행

**테스트 단계:**

1. **Clumsy 설정**
   ```
   Filter: outbound and tcp.DstPort == 8080
   Drop: 100%
   Enable 체크
   ```

2. **메모 작성**
   - 웹사이트에서 메모 작성
   - API 호출이 차단되어 오프라인으로 처리됨

3. **확인 사항**
   - 메모가 로컬에 저장되는지 확인
   - UI에 즉시 표시되는지 확인

4. **네트워크 복구 시뮬레이션**
   ```
   Clumsy에서 Drop: 0%로 변경
   또는 Enable 체크 해제
   ```

#### 방법 3: Windows Firewall을 이용한 포트 차단

```powershell
# PowerShell (관리자 권한 필요)

# 1. 포트 차단
New-NetFirewallRule -DisplayName "Block 8080" `
    -Direction Outbound `
    -LocalPort 8080 `
    -Protocol TCP `
    -Action Block

# 2. 메모 작성 테스트
# 웹사이트에서 메모 작성 → 오프라인 처리 확인

# 3. 포트 차단 해제
Remove-NetFirewallRule -DisplayName "Block 8080"

# 4. 자동 동기화 확인
```

---

### 2. 자동 동기화 테스트

#### 시나리오: 네트워크 복구 시 자동 동기화

**테스트 준비:**
1. 오프라인 상태에서 메모 2-3개 작성
2. 각 메모의 동기화 상태가 "⏳ 대기 중"인지 확인

**테스트 단계:**

1. **네트워크 복구 시뮬레이션**

   **방법 A: 브라우저 DevTools**
   ```
   Network 탭 → Throttling → "Online" 선택
   ```

   **방법 B: Clumsy**
   ```
   Drop: 0%로 변경
   또는 Enable 체크 해제
   ```

   **방법 C: 서버 중지 후 재시작**
   ```powershell
   # 서버 중지 (Ctrl+C)
   # 서버 재시작
   mvn spring-boot:run
   ```

2. **자동 동기화 확인**

   **확인 사항** ✅:
   - ✅ 1초 이내에 자동 동기화가 시작되는가?
   - ✅ 동기화 상태가 "⏳ 대기 중" → "🔄 동기화 중" → "✓ 동기화 완료"로 변경되는가?
   - ✅ 콘솔에 "네트워크 연결 복구" 메시지가 출력되는가?
   - ✅ 콘솔에 "동기화할 메모 수: N" 메시지가 출력되는가?

3. **IndexedDB 상태 확인**
   ```
   DevTools → Application → IndexedDB → offline_memos
   ```
   - 동기화 완료된 메모의 `syncStatus`: "synced" 확인
   - `serverId`: 서버에서 생성된 ID 확인 (null이 아님)

4. **서버에서 메모 확인**

   **방법 A: API 직접 호출**
   ```javascript
   // 브라우저 콘솔에서 실행
   fetch('http://localhost:8080/api/v1/memos/books/{userBookId}', {
       headers: {
           'Authorization': 'Bearer YOUR_TOKEN'
       }
   })
   .then(res => res.json())
   .then(data => console.log('서버 메모:', data));
   ```

   **방법 B: Swagger UI**
   ```
   http://localhost:8080/swagger-ui.html
   → GET /api/v1/memos/books/{userBookId} 실행
   ```

   **방법 C: 데이터베이스 직접 확인**
   ```sql
   SELECT * FROM memos 
   WHERE user_id = (SELECT id FROM users WHERE login_id = 'test_user')
   ORDER BY created_at DESC 
   LIMIT 10;
   ```

5. **동기화 큐 확인**
   ```
   Application 탭 → IndexedDB → sync_queue
   ```
   - 동기화 완료된 항목의 `status`: "SUCCESS" 확인
   - 또는 큐에서 제거되었는지 확인

---

### 3. 재시도 로직 테스트

#### 시나리오 1: 서버 오류 시 재시도

**테스트 준비:**
1. Spring Boot 서버 실행 중
2. Clumsy 설정: `Drop: 0%` (정상 통신)

**테스트 단계:**

1. **서버 중지 또는 에러 응답 시뮬레이션**

   **방법 A: 서버 중지**
   ```powershell
   # 서버 프로세스 찾기
   netstat -ano | findstr :8080
   
   # 서버 강제 종료
   taskkill /PID <PID> /F
   ```

   **방법 B: Clumsy로 네트워크 차단**
   ```
   Filter: outbound and tcp.DstPort == 8080
   Drop: 100%
   Enable 체크
   ```

2. **메모 작성**
   - 오프라인 상태에서 메모 작성
   - 로컬 저장 확인

3. **네트워크 복구 (일시적)**
   ```
   Clumsy: Drop: 0%
   서버 재시작 (방법 A 사용 시)
   ```

4. **동기화 실패 확인**
   - 동기화 상태: "🔄 동기화 중" → "❌ 동기화 실패"
   - 콘솔에 에러 메시지 확인

5. **재시도 확인**

   **확인 사항** ✅:
   - ✅ 5초 후 첫 번째 재시도가 실행되는가?
   - ✅ 10초 후 두 번째 재시도가 실행되는가?
   - ✅ 20초 후 세 번째 재시도가 실행되는가?
   - ✅ 최대 3회 재시도 후 중단되는가?

6. **IndexedDB 상태 확인**
   ```
   sync_queue 테이블 확인
   ```
   - `retryCount`: 재시도 횟수 확인 (최대 3)
   - `status`: "FAILED" 확인
   - `error`: 에러 메시지 확인

7. **최종 성공 시나리오**
   - 서버 정상화
   - 네트워크 정상화
   - 수동 재시도 버튼 클릭 (구현 시)
   - 또는 자동 재시도 확인

#### 시나리오 2: 간헐적 네트워크 장애

**테스트 설정:**

1. **Clumsy 설정**
   ```
   Filter: outbound and tcp.DstPort == 8080
   Drop: 50%  # 50% 패킷 손실
   Enable 체크
   ```

2. **메모 작성 및 동기화 시도**
   - 온라인 상태에서 메모 작성
   - 동기화 시도

3. **확인 사항** ✅:
   - ✅ 일부 메모만 성공하고 일부는 실패하는가?
   - ✅ 실패한 메모는 재시도 큐에 추가되는가?
   - ✅ 성공한 메모는 정상적으로 동기화되는가?

---

### 4. 다중 메모 배치 동기화 테스트

#### 시나리오: 여러 메모를 순차적으로 동기화

**테스트 준비:**
1. 오프라인 상태로 전환
2. 여러 메모 작성 (3-5개)
   - 다른 `userBookId` 사용
   - 다른 `memoStartTime` 설정

**테스트 단계:**

1. **메모 작성 시간 순서 확인**
   ```
   IndexedDB → offline_memos
   → memoStartTime 인덱스로 정렬 확인
   ```

2. **네트워크 복구**
   ```
   Network 탭 → Online
   ```

3. **순차 동기화 확인**

   **확인 사항** ✅:
   - ✅ 메모들이 `memoStartTime` 순서로 동기화되는가?
   - ✅ 한 번에 하나씩 순차적으로 동기화되는가?
   - ✅ 일부 실패해도 나머지는 계속 동기화되는가?

4. **콘솔 로그 확인**
   ```javascript
   // 예상 로그:
   "동기화할 메모 수: 5"
   "메모 동기화 성공: local-id-1 → server-id-123"
   "메모 동기화 성공: local-id-2 → server-id-124"
   ...
   ```

---

### 5. IndexedDB 데이터 무결성 테스트

#### 시나리오: 브라우저 재시작 후 데이터 유지

**테스트 단계:**

1. **오프라인 상태에서 메모 작성**
   - 여러 메모 작성 (2-3개)

2. **브라우저 완전 종료**
   - 모든 브라우저 탭 닫기
   - 브라우저 프로세스 종료 확인

3. **브라우저 재시작**

4. **데이터 확인**

   **확인 사항** ✅:
   - ✅ IndexedDB에 메모 데이터가 여전히 존재하는가?
   - ✅ 페이지 로드 시 메모가 UI에 표시되는가?
   - ✅ 동기화 상태가 올바르게 표시되는가?

5. **네트워크 복구 후 동기화**
   - 자동 동기화가 정상 작동하는가?

---

### 6. 동시성 테스트

#### 시나리오: 빠르게 연속으로 메모 작성

**테스트 단계:**

1. **오프라인 상태로 전환**

2. **연속 메모 작성**
   - 1초 간격으로 5개 메모 작성
   - 또는 빠르게 여러 번 클릭

3. **확인 사항** ✅:
   - ✅ 모든 메모가 로컬에 저장되는가?
   - ✅ 각 메모가 고유한 `localId`를 가지는가?
   - ✅ 동기화 큐에 중복 항목이 없는가?
   - ✅ UI에 모든 메모가 표시되는가?

4. **네트워크 복구 후 동기화**
   - 모든 메모가 정상적으로 동기화되는가?

---

### 7. 에러 케이스 테스트

#### 시나리오 1: 서버 검증 오류 (4xx)

**테스트 방법:**

1. **오프라인 상태에서 잘못된 데이터로 메모 작성**
   - `userBookId`: 존재하지 않는 ID
   - `content`: 빈 문자열 또는 null
   - `pageNumber`: 0 또는 음수

2. **로컬 저장 확인**
   - 잘못된 데이터도 로컬에는 저장됨 (서버 검증 전)

3. **네트워크 복구 후 동기화 시도**

4. **서버 에러 응답 확인**

   **확인 사항** ✅:
   - ✅ 서버에서 400 Bad Request 응답
   - ✅ 동기화 상태: "❌ 동기화 실패"
   - ✅ 에러 메시지가 사용자에게 표시되는가?
   - ✅ 수정 가능한 UI가 제공되는가?

#### 시나리오 2: 인증 오류 (401)

**테스트 방법:**

1. **토큰 만료 또는 무효화**
   - `localStorage`에서 `accessToken` 삭제
   - 또는 만료된 토큰 설정

2. **메모 작성 및 동기화 시도**

   **확인 사항** ✅:
   - ✅ 401 에러 발생
   - ✅ 토큰 갱신 시도
   - ✅ 토큰 갱신 성공 시 재시도
   - ✅ 토큰 갱신 실패 시 로그아웃 처리

---

### 8. 성능 테스트

#### 시나리오: 대량 메모 작성 및 동기화

**테스트 단계:**

1. **대량 메모 작성**
   - 오프라인 상태에서 50-100개 메모 작성
   - 스크립트로 자동 생성 가능

2. **로컬 저장 성능 확인**

   **확인 사항** ✅:
   - ✅ 모든 메모가 빠르게 로컬에 저장되는가?
   - ✅ UI가 멈추지 않는가?
   - ✅ IndexedDB 용량 확인

3. **네트워크 복구 후 동기화**

   **확인 사항** ✅:
   - ✅ 모든 메모가 순차적으로 동기화되는가?
   - ✅ 동기화 시간이 적절한가?
   - ✅ 브라우저가 응답하는가?

---

### 9. 브라우저 DevTools를 이용한 상세 모니터링

#### Network 탭 모니터링

1. **오프라인 메모 작성 시**
   ```
   Network 탭 → 요청이 없는지 확인
   (오프라인이므로 API 호출 없음)
   ```

2. **네트워크 복구 시**
   ```
   Network 탭 → POST /api/v1/memos 요청 확인
   → 요청 탭에서 Request/Response 확인
   → Timing 탭에서 응답 시간 확인
   ```

#### Application 탭 모니터링

1. **IndexedDB 실시간 확인**
   ```
   Application → IndexedDB → reading-tracker
   → offline_memos 테이블
   → 데이터 변경 시 실시간 업데이트 확인
   ```

2. **LocalStorage 확인**
   ```
   Application → Local Storage
   → 토큰 정보 확인
   → 네트워크 상태 캐시 확인 (있는 경우)
   ```

#### Console 탭 로깅

**확인할 로그:**

```javascript
// 정상 동기화
"네트워크 연결 복구"
"동기화할 메모 수: 3"
"메모 동기화 성공: local-id → server-id"

// 재시도
"메모 동기화 실패 (local-id): Network error"
"재시도 예약: 5초 후"

// 에러
"자동 동기화 실패: [에러 메시지]"
```

---

### 10. 자동화된 테스트 스크립트

#### JavaScript 테스트 함수

```javascript
// 테스트 헬퍼 함수 (브라우저 콘솔에서 실행)

// 1. IndexedDB 메모 확인
async function checkOfflineMemos() {
    const db = await new Promise((resolve, reject) => {
        const request = indexedDB.open('reading-tracker', 1);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
    
    const transaction = db.transaction(['offline_memos'], 'readonly');
    const store = transaction.objectStore('offline_memos');
    const memos = await store.getAll();
    
    console.log('로컬 메모 목록:', memos);
    return memos;
}

// 2. 동기화 큐 확인
async function checkSyncQueue() {
    const db = await new Promise((resolve, reject) => {
        const request = indexedDB.open('reading-tracker', 1);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
    
    const transaction = db.transaction(['sync_queue'], 'readonly');
    const store = transaction.objectStore('sync_queue');
    const items = await store.getAll();
    
    console.log('동기화 큐:', items);
    return items;
}

// 3. 통합 상태 확인
async function checkSyncStatus() {
    const memos = await checkOfflineMemos();
    const queue = await checkSyncQueue();
    
    const status = {
        totalMemos: memos.length,
        pending: memos.filter(m => m.syncStatus === 'pending').length,
        syncing: memos.filter(m => m.syncStatus === 'syncing').length,
        synced: memos.filter(m => m.syncStatus === 'synced').length,
        failed: memos.filter(m => m.syncStatus === 'failed').length,
        queueItems: queue.length
    };
    
    console.table(status);
    return status;
}

// 사용 예시
checkSyncStatus();
```

---

### 11. 테스트 체크리스트

#### 기본 기능 테스트

- [ ] 오프라인 상태에서 메모 작성 가능
- [ ] 메모가 UI에 즉시 표시됨
- [ ] IndexedDB에 메모가 저장됨
- [ ] 브라우저 재시작 후 메모 유지
- [ ] 네트워크 복구 시 자동 동기화
- [ ] 동기화 상태 UI 표시 (대기/동기화 중/완료/실패)

#### 동기화 테스트

- [ ] 단일 메모 동기화 성공
- [ ] 다중 메모 순차 동기화
- [ ] 동기화 순서가 `memoStartTime` 기준
- [ ] 서버 ID 매핑 정확
- [ ] 동기화 완료 후 상태 업데이트

#### 재시도 테스트

- [ ] 네트워크 오류 시 재시도
- [ ] Exponential Backoff 적용
- [ ] 최대 재시도 횟수 제한
- [ ] 재시도 간격 정확

#### 에러 처리 테스트

- [ ] 서버 검증 오류 처리
- [ ] 인증 오류 처리
- [ ] 네트워크 오류 처리
- [ ] 사용자에게 명확한 에러 메시지

#### 성능 테스트

- [ ] 대량 메모 작성 성능
- [ ] 동기화 성능
- [ ] UI 응답성

---

### 12. Clumsy를 이용한 고급 테스트

#### 네트워크 지연 테스트

```
Filter: outbound and tcp.DstPort == 8080
Lag: 2000ms  # 2초 지연
Drop: 0%
Enable 체크
```

**확인 사항:**
- ✅ 동기화가 느리게 진행되는가?
- ✅ 타임아웃 설정이 적절한가?

#### 패킷 손실 테스트

```
Filter: outbound and tcp.DstPort == 8080
Lag: 0ms
Drop: 30%  # 30% 패킷 손실
Enable 체크
```

**확인 사항:**
- ✅ 일부 요청이 실패하는가?
- ✅ 재시도 로직이 작동하는가?

#### 완전 차단 → 복구 시뮬레이션

```
1. Drop: 100% → 메모 작성 (오프라인)
2. Drop: 0% → 네트워크 복구 → 자동 동기화 확인
3. Drop: 100% → 동기화 중 네트워크 끊김 → 재시도 확인
4. Drop: 0% → 최종 동기화 확인
```

---

### 13. 실시간 모니터링 시스템

> **목적**: 네트워크 끊김, 동기화 실패, 에러 등 문제를 실시간으로 감지하고 추적

#### 모니터링이 필요한 항목

1. **네트워크 상태**
   - 온라인/오프라인 상태 변화
   - 네트워크 연결/끊김 이벤트
   - 네트워크 상태 지속 시간

2. **동기화 상태**
   - 대기 중인 메모 수
   - 동기화 중인 메모 수
   - 동기화 완료/실패 통계
   - 동기화 시간

3. **에러 추적**
   - 네트워크 에러
   - 서버 에러 (4xx, 5xx)
   - 동기화 실패 원인
   - 재시도 횟수

4. **성능 메트릭**
   - 동기화 소요 시간
   - API 응답 시간
   - IndexedDB 작업 시간

---

#### 모니터링 방법 1: 브라우저 DevTools (추천, 추가 설치 불필요) ⭐

**장점:**
- ✅ 추가 소프트웨어 설치 불필요
- ✅ 브라우저에 기본 내장
- ✅ 실시간 모니터링 가능
- ✅ 무료

**사용 방법:**

##### 1. Network 상태 모니터링

```
1. F12 → Network 탭 열기
2. 상태 표시 확인:
   - Online/Offline 버튼
   - Throttling 설정
3. 요청/응답 실시간 확인:
   - 성공한 요청: 초록색 (200)
   - 실패한 요청: 빨간색 (4xx, 5xx)
   - 취소된 요청: 회색
```

**모니터링 항목:**
- API 호출 횟수
- 성공/실패 비율
- 응답 시간
- 요청 크기

##### 2. Application 탭 - IndexedDB 모니터링

```
1. F12 → Application 탭
2. Storage → IndexedDB → reading-tracker
3. 테이블 선택:
   - offline_memos: 동기화 상태 확인
   - sync_queue: 동기화 큐 상태 확인
4. 실시간 데이터 확인:
   - 데이터 추가/수정 시 자동 새로고침
   - 필터링 및 검색 가능
```

**모니터링 항목:**
- `syncStatus`별 메모 개수
- `serverId` 매핑 상태
- 큐 항목 상태 분포

##### 3. Console 탭 - 로그 모니터링

```javascript
// 필터링 옵션 사용:
// - All levels: 모든 로그
// - Errors: 에러만
// - Warnings: 경고만
// - Info: 정보만

// 로그 레벨별 확인:
console.error('❌ [Error]', ...);     // 빨간색
console.warn('⚠️ [Warning]', ...);   // 노란색
console.log('ℹ️ [Info]', ...);        // 기본색
```

**모니터링 항목:**
- 네트워크 상태 변경 로그
- 동기화 진행 상황
- 에러 메시지
- 재시도 알림

##### 4. Performance 탭 - 성능 모니터링

```
1. F12 → Performance 탭
2. Record 버튼 클릭
3. 동기화 작업 수행
4. Stop 버튼 클릭
5. 성능 분석:
   - 함수 실행 시간
   - 메모리 사용량
   - 네트워크 요청 시간
```

---

#### 모니터링 방법 2: 자체 모니터링 대시보드 구현 (무료)

**장점:**
- ✅ 사용자 정의 가능
- ✅ 실시간 상태 표시
- ✅ 추가 소프트웨어 불필요

**구현 예제:**

##### 모니터링 서비스 구현

```javascript
// services/monitoring-service.js
class MonitoringService {
    constructor() {
        this.events = [];
        this.maxEvents = 1000; // 최대 이벤트 수
        this.metrics = {
            networkStatus: navigator.onLine,
            syncStats: {
                pending: 0,
                syncing: 0,
                synced: 0,
                failed: 0
            },
            errorCount: 0,
            lastSyncTime: null
        };
    }

    /**
     * 이벤트 기록
     */
    logEvent(type, data) {
        const event = {
            id: Date.now(),
            timestamp: new Date().toISOString(),
            type, // 'network', 'sync', 'error'
            data
        };

        this.events.push(event);

        // 최대 이벤트 수 제한
        if (this.events.length > this.maxEvents) {
            this.events.shift();
        }

        // 메트릭 업데이트
        this.updateMetrics(type, data);
    }

    /**
     * 네트워크 상태 변경 기록
     */
    logNetworkChange(isOnline) {
        this.logEvent('network', {
            status: isOnline ? 'online' : 'offline',
            timestamp: Date.now()
        });
        this.metrics.networkStatus = isOnline;
    }

    /**
     * 동기화 이벤트 기록
     */
    logSyncEvent(localId, status, error = null) {
        this.logEvent('sync', {
            localId,
            status, // 'start', 'success', 'failed'
            error,
            timestamp: Date.now()
        });

        if (status === 'success') {
            this.metrics.lastSyncTime = Date.now();
        } else if (status === 'failed') {
            this.metrics.errorCount++;
        }
    }

    /**
     * 에러 기록
     */
    logError(error, context) {
        this.logEvent('error', {
            message: error.message,
            stack: error.stack,
            context,
            timestamp: Date.now()
        });
        this.metrics.errorCount++;
    }

    /**
     * 메트릭 업데이트
     */
    updateMetrics(type, data) {
        if (type === 'sync' && data.status) {
            // syncStats 업데이트는 별도 메서드에서 처리
        }
    }

    /**
     * 동기화 통계 업데이트
     */
    async updateSyncStats() {
        const memos = await dbManager.getAllMemos();
        this.metrics.syncStats = {
            pending: memos.filter(m => m.syncStatus === 'pending').length,
            syncing: memos.filter(m => m.syncStatus === 'syncing').length,
            synced: memos.filter(m => m.syncStatus === 'synced').length,
            failed: memos.filter(m => m.syncStatus === 'failed').length
        };
    }

    /**
     * 모니터링 데이터 조회
     */
    getMetrics() {
        return {
            ...this.metrics,
            eventCount: this.events.length,
            recentEvents: this.events.slice(-10) // 최근 10개 이벤트
        };
    }

    /**
     * 이벤트 로그 조회
     */
    getEvents(filter = {}) {
        let filtered = [...this.events];

        if (filter.type) {
            filtered = filtered.filter(e => e.type === filter.type);
        }

        if (filter.startTime) {
            filtered = filtered.filter(e => new Date(e.timestamp) >= filter.startTime);
        }

        if (filter.endTime) {
            filtered = filtered.filter(e => new Date(e.timestamp) <= filter.endTime);
        }

        return filtered;
    }

    /**
     * 에러 통계
     */
    getErrorStats() {
        const errors = this.events.filter(e => e.type === 'error');
        const errorTypes = {};

        errors.forEach(error => {
            const message = error.data.message;
            errorTypes[message] = (errorTypes[message] || 0) + 1;
        });

        return {
            totalErrors: errors.length,
            errorTypes,
            recentErrors: errors.slice(-5)
        };
    }
}

export const monitoringService = new MonitoringService();
```

##### 모니터링 UI 컴포넌트

```javascript
// ui/monitoring-dashboard.js
class MonitoringDashboard {
    constructor() {
        this.container = null;
        this.updateInterval = null;
    }

    /**
     * 대시보드 초기화
     */
    init() {
        // 개발 모드에서만 표시
        if (process.env.NODE_ENV === 'production') {
            return;
        }

        this.createDashboard();
        this.startAutoUpdate();
    }

    /**
     * 대시보드 HTML 생성
     */
    createDashboard() {
        const dashboard = document.createElement('div');
        dashboard.id = 'monitoring-dashboard';
        dashboard.innerHTML = `
            <div class="monitoring-panel">
                <h3>📊 모니터링 대시보드</h3>
                
                <div class="metrics-grid">
                    <div class="metric-card">
                        <div class="metric-label">네트워크 상태</div>
                        <div id="network-status" class="metric-value">-</div>
                    </div>
                    
                    <div class="metric-card">
                        <div class="metric-label">대기 중</div>
                        <div id="sync-pending" class="metric-value">0</div>
                    </div>
                    
                    <div class="metric-card">
                        <div class="metric-label">동기화 중</div>
                        <div id="sync-syncing" class="metric-value">0</div>
                    </div>
                    
                    <div class="metric-card">
                        <div class="metric-label">동기화 완료</div>
                        <div id="sync-synced" class="metric-value">0</div>
                    </div>
                    
                    <div class="metric-card">
                        <div class="metric-label">동기화 실패</div>
                        <div id="sync-failed" class="metric-value">0</div>
                    </div>
                    
                    <div class="metric-card">
                        <div class="metric-label">에러 횟수</div>
                        <div id="error-count" class="metric-value">0</div>
                    </div>
                </div>
                
                <div class="event-log">
                    <h4>최근 이벤트</h4>
                    <div id="event-list"></div>
                </div>
            </div>
        `;

        // 스타일 추가
        dashboard.style.cssText = `
            position: fixed;
            bottom: 20px;
            right: 20px;
            width: 400px;
            max-height: 600px;
            background: white;
            border: 2px solid #007bff;
            border-radius: 8px;
            padding: 16px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.2);
            z-index: 10000;
            font-size: 12px;
            overflow-y: auto;
        `;

        document.body.appendChild(dashboard);
        this.container = dashboard;
    }

    /**
     * 자동 업데이트 시작
     */
    startAutoUpdate() {
        this.update();
        this.updateInterval = setInterval(() => {
            this.update();
        }, 2000); // 2초마다 업데이트
    }

    /**
     * 대시보드 업데이트
     */
    async update() {
        const metrics = monitoringService.getMetrics();
        await monitoringService.updateSyncStats();

        // 네트워크 상태
        const networkStatusEl = document.getElementById('network-status');
        if (networkStatusEl) {
            networkStatusEl.textContent = metrics.networkStatus ? '🟢 온라인' : '🔴 오프라인';
            networkStatusEl.style.color = metrics.networkStatus ? 'green' : 'red';
        }

        // 동기화 통계
        const stats = monitoringService.metrics.syncStats;
        this.updateElement('sync-pending', stats.pending);
        this.updateElement('sync-syncing', stats.syncing);
        this.updateElement('sync-synced', stats.synced);
        this.updateElement('sync-failed', stats.failed);
        this.updateElement('error-count', metrics.errorCount);

        // 최근 이벤트
        this.updateEventList(metrics.recentEvents);
    }

    updateElement(id, value) {
        const el = document.getElementById(id);
        if (el) {
            el.textContent = value;
        }
    }

    updateEventList(events) {
        const eventList = document.getElementById('event-list');
        if (!eventList) return;

        eventList.innerHTML = events
            .slice()
            .reverse()
            .slice(0, 10)
            .map(event => {
                const time = new Date(event.timestamp).toLocaleTimeString();
                const icon = {
                    network: '🌐',
                    sync: '🔄',
                    error: '❌'
                }[event.type] || 'ℹ️';

                return `
                    <div class="event-item" style="padding: 4px; border-bottom: 1px solid #eee;">
                        ${icon} [${time}] ${event.type}: ${JSON.stringify(event.data)}
                    </div>
                `;
            })
            .join('');
    }

    /**
     * 대시보드 닫기
     */
    destroy() {
        if (this.updateInterval) {
            clearInterval(this.updateInterval);
        }
        if (this.container) {
            this.container.remove();
        }
    }
}

export const monitoringDashboard = new MonitoringDashboard();
```

##### 통합 사용법

```javascript
// app.js (앱 초기화 시)
import { monitoringService } from './services/monitoring-service.js';
import { monitoringDashboard } from './ui/monitoring-dashboard.js';
import { networkMonitor } from './utils/network-monitor.js';

// 개발 모드에서만 모니터링 대시보드 활성화
if (process.env.NODE_ENV === 'development') {
    monitoringDashboard.init();
}

// 네트워크 상태 변경 감지 및 기록
networkMonitor.subscribe((isOnline) => {
    monitoringService.logNetworkChange(isOnline);
    monitoringDashboard.update();
});

// 동기화 이벤트 기록
// offlineMemoService에서 동기화 시작/성공/실패 시 호출
monitoringService.logSyncEvent(localId, 'start');
monitoringService.logSyncEvent(localId, 'success');
monitoringService.logSyncEvent(localId, 'failed', error);
```

---

#### 모니터링 방법 3: 브라우저 확장 프로그램 (선택사항)

**추천 확장 프로그램 (무료):**

##### 1. Vue.js DevTools (Vue 사용 시)
- 브라우저 확장 프로그램 설치
- 상태 추적 가능

##### 2. React Developer Tools (React 사용 시)
- 브라우저 확장 프로그램 설치
- 컴포넌트 상태 모니터링

**단점:**
- ⚠️ 특정 프레임워크에 종속
- ⚠️ 순수 JavaScript 프로젝트에는 부적합

**결론**: 순수 JavaScript 프로젝트이므로 브라우저 확장 프로그램보다는 **DevTools나 자체 대시보드**가 더 적합합니다.

---

#### 모니터링 데이터 저장 (선택사항)

**로컬 저장소에 모니터링 데이터 저장:**

```javascript
// IndexedDB에 모니터링 이벤트 저장
async function saveMonitoringEvent(event) {
    const db = await dbManager.init();
    const transaction = db.transaction(['monitoring_events'], 'readwrite');
    const store = transaction.objectStore('monitoring_events');
    await store.add(event);
}

// 모니터링 이벤트 조회
async function getMonitoringEvents(startDate, endDate) {
    const db = await dbManager.init();
    const transaction = db.transaction(['monitoring_events'], 'readonly');
    const store = transaction.objectStore('monitoring_events');
    const index = store.index('timestamp');
    
    const range = IDBKeyRange.bound(startDate, endDate);
    return index.getAll(range);
}
```

---

#### 실시간 알림 시스템 (선택사항)

**중요한 이벤트 발생 시 알림:**

```javascript
// utils/notification-service.js
class NotificationService {
    /**
     * 브라우저 알림 표시 (사용자 허용 필요)
     */
    async showNotification(title, message, type = 'info') {
        // 브라우저 알림 권한 확인
        if (Notification.permission === 'granted') {
            new Notification(title, {
                body: message,
                icon: '/icon.png',
                tag: 'sync-status'
            });
        } else if (Notification.permission !== 'denied') {
            // 권한 요청
            const permission = await Notification.requestPermission();
            if (permission === 'granted') {
                this.showNotification(title, message, type);
            }
        }
    }

    /**
     * 동기화 완료 알림
     */
    notifySyncComplete(count) {
        this.showNotification(
            '동기화 완료',
            `${count}개의 메모가 성공적으로 동기화되었습니다.`,
            'success'
        );
    }

    /**
     * 동기화 실패 알림
     */
    notifySyncFailed(error) {
        this.showNotification(
            '동기화 실패',
            `메모 동기화 중 오류가 발생했습니다: ${error.message}`,
            'error'
        );
    }

    /**
     * 네트워크 복구 알림
     */
    notifyNetworkRecovered() {
        this.showNotification(
            '네트워크 연결 복구',
            '네트워크가 복구되었습니다. 대기 중인 메모를 동기화합니다.',
            'info'
        );
    }
}

export const notificationService = new NotificationService();
```

---

#### 모니터링 체크리스트

**실시간 모니터링 확인 항목:**

- [ ] 네트워크 상태 실시간 표시
- [ ] 동기화 통계 (대기/진행/완료/실패)
- [ ] 에러 발생 시 즉시 알림
- [ ] 네트워크 복구 시 알림
- [ ] 동기화 진행 상황 표시
- [ ] 에러 로그 조회 가능
- [ ] 성능 메트릭 확인 가능

---

### 14. 디버깅 팁

#### 실시간 모니터링

1. **콘솔 로그 활성화**
   ```javascript
   // 개발 모드에서 상세 로그
   console.log('🔍 [OfflineMemo]', ...args);
   console.log('🔄 [SyncQueue]', ...args);
   console.log('🌐 [Network]', ...args);
   ```

2. **IndexedDB 변경 감지**
   ```
   Application 탭에서 IndexedDB 테이블 선택
   → 데이터 변경 시 자동 새로고침 확인
   ```

3. **Network 탭 필터링**
   ```
   Network 탭 → Filter: "memos"
   → 메모 관련 요청만 확인
   ```

#### 디버깅

1. **동기화 실패 시**
   - 콘솔 에러 메시지 확인
   - Network 탭에서 요청/응답 확인
   - 서버 로그 확인

2. **데이터 불일치 시**
   - IndexedDB 데이터 확인
   - 서버 데이터 확인
   - `localId`와 `serverId` 매핑 확인

3. **성능 이슈 시**
   - Performance 탭에서 프로파일링
   - 메모리 사용량 확인
   - IndexedDB 쿼리 성능 확인

---

## 관련 시나리오

### 멀티 디바이스 오프라인 동기화

> **참고**: 본 문서는 단일 디바이스(웹 또는 앱)에서의 오프라인 메모 작성 및 동기화에 대해 다룹니다.  
> 여러 디바이스에서 오프라인 메모를 작성한 후 네트워크 복구 시 양방향 동기화가 필요한 시나리오에 대해서는 **[멀티 디바이스 오프라인 동기화 설계](./MULTI_DEVICE_SYNC.md)** 문서를 참조하세요.

#### 시나리오 요약

```
1. 노트북(웹)에서 오프라인 상태로 메모 A, B 작성
2. 모바일(앱)에서 오프라인 상태로 메모 C, D 작성
3. 네트워크 복구 시:
   - 양방향 동기화 (각 디바이스 → 서버, 서버 → 각 디바이스)
   - 노트북: 메모 A, B, C, D 모두 표시
   - 모바일: 메모 A, B, C, D 모두 표시
   - 메모 내용 손실 없음
   - 정렬 방법(memoStartTime)에 따라 올바르게 표시
```

#### 주요 차이점

| 구분 | 단일 디바이스 (본 문서) | 멀티 디바이스 ([참조](./MULTI_DEVICE_SYNC.md)) |
|------|------------------------|----------------------------------------------|
| **동기화 방향** | 단방향 (로컬 → 서버) | 양방향 (로컬 ↔ 서버) |
| **서버 데이터 다운로드** | 선택사항 | 필수 (다른 디바이스의 메모 받아오기) |
| **데이터 병합** | 간단 (로컬 + 서버) | 복잡 (여러 디바이스의 메모 병합) |
| **충돌 해결** | 거의 없음 | 필요 (동일 시간에 여러 디바이스에서 작성) |
| **중복 방지** | 단순 | 중요 (여러 디바이스 간 중복 방지) |

#### 구현 포인트

1. **양방향 동기화**
   - 업로드: 로컬 메모 → 서버
   - 다운로드: 서버 메모 → 로컬 (다른 디바이스의 메모 포함)

2. **데이터 병합**
   - 로컬 메모와 서버 메모 비교
   - 중복 제거
   - 새 메모 추가

3. **정렬 보장**
   - `memoStartTime` 기준 정렬
   - 시간이 같을 때 대비

---

## 추가 고려사항

### 1. 데이터 정리

- 동기화 완료된 메모는 일정 시간 후 로컬에서 삭제 가능 (선택사항)
- 또는 모든 메모를 로컬에 보관 (오프라인 조회 지원)

### 2. 용량 관리

- IndexedDB 용량 모니터링
- 오래된 메모 삭제 정책 (선택사항)

### 3. 보안

- 로컬 저장소 데이터 암호화 (선택사항)
- 민감한 정보 저장 시 고려

### 4. 확장성

- 메모 수정/삭제도 오프라인 지원 (추후 구현)
- 다른 기능에도 오프라인 지원 확장 가능

---

## 참고 자료

### 관련 문서

- [멀티 디바이스 오프라인 동기화 설계](./MULTI_DEVICE_SYNC.md): 여러 디바이스에서 오프라인 메모 작성 후 양방향 동기화하는 시나리오

### 외부 자료

- [IndexedDB API](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API)
- [Offline-First Architecture](https://offlinefirst.org/)
- [Progressive Web Apps](https://web.dev/progressive-web-apps/)
- [Network Information API](https://developer.mozilla.org/en-US/docs/Web/API/Network_Information_API)

---

## 다음 단계

1. IndexedDB 스키마 구현
2. 오프라인 메모 작성 기능 구현
3. 동기화 큐 구현
4. 네트워크 복구 감지 및 자동 동기화
5. UI 통합 및 테스트

