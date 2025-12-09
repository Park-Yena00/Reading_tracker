# 메모 중복 생성 문제 진단 및 해결 계획

> **작성일**: 2025-12-09  
> **문제**: 메모 작성 시 Primary DB와 Secondary DB에 각각 동일한 메모가 2개씩 생성됨  
> **원인**: 동기화 큐 항목의 동시 처리 및 멱등성 키 재생성 문제  
> **상태**: ✅ 수정 완료

---

## 1. 문제 발생 시나리오

- 사용자가 메모 작성 후 저장 버튼을 한 번 클릭
- Primary DB와 Secondary DB 모두에 동일한 내용의 메모가 각각 2개씩 생성됨
- 예: '1209 test1' 메모가 Primary DB에 2개, Secondary DB에 2개 생성
- 내 서재에 새로운 책 추가는 정상적으로 각 DB에 1개씩 생성됨

## 2. 예상되는 동작 (아키텍처 문서 기반)

- `OFFLINE_MEMO_SYNC.md`에 명시된 오프라인 메모 동기화 전략:
  - 메모 작성 시 로컬 저장소에 저장하고 동기화 큐에 추가
  - 온라인 상태면 즉시 동기화 시도
  - **멱등성 보장**: 동일한 요청을 여러 번 실행해도 결과가 동일해야 함
  - **중복 방지**: 동일한 메모가 중복 저장되지 않도록 보장

## 3. 현재 동작 진단

### 3-1. 메모 작성 흐름

1. **프론트엔드 (`flow-view.js`)**: 사용자가 저장 버튼 클릭
   ```javascript
   await memoService.createMemo(createData);
   ```

2. **메모 서비스 (`memo-service.js`)**: 
   ```javascript
   async createMemo(memoData) {
     // 항상 로컬 저장소에 먼저 저장 (Offline-First)
     const localMemo = await offlineMemoService.createMemo(memoData);
     
     // 온라인 상태면 즉시 동기화 시도 (백그라운드, await 하지 않음)
     if (networkMonitor.isOnline) {
       offlineMemoService.syncPendingMemos().catch(error => {
         console.error('백그라운드 동기화 실패:', error);
       });
     }
   }
   ```

3. **오프라인 메모 서비스 (`offline-memo-service.js`)**:
   - 로컬 저장소에 메모 저장
   - 동기화 큐에 'PENDING' 상태로 추가
   - 온라인 상태면 `syncPendingMemos()` 호출 (백그라운드)

### 3-2. 동기화 프로세스

**클라이언트 (`offline-memo-service.js`)**:
```javascript
async syncPendingMemos() {
  // 동기화 큐에서 PENDING 항목 조회
  const pendingQueueItems = await syncQueueManager.getPendingItems();
  
  for (const queueItem of pendingQueueItems) {
    await this.syncQueueItem(queueItem);
  }
}

async syncQueueItem(queueItem) {
  // 상태를 'SYNCING'으로 변경
  await syncQueueManager.updateStatus(queueItem.id, 'SYNCING');
  
  // 멱등성 키 생성 (매번 새로운 키 생성)
  const idempotencyKey = this.generateLocalId();
  
  // API 호출
  response = await apiClient.post(API_ENDPOINTS.MEMOS.CREATE, queueItem.data, {
    headers: { 'Idempotency-Key': idempotencyKey }
  });
}
```

**Service Worker (`service-worker.js`)**:
```javascript
async function syncPendingMemos() {
  // 동기화 큐에서 PENDING 항목 조회
  const pendingQueueItems = await getPendingQueueItemsFromIndexedDB();
  
  for (const queueItem of pendingQueueItems) {
    // 상태를 'SYNCING'으로 변경
    await updateQueueItemStatus(queueItem.id, 'SYNCING');
    
    // API 호출 (멱등성 키 없음)
    const response = await replayRequest(queueItem);
  }
}
```

## 4. 문제점 분석

### 4-1. 동시성 문제 (Race Condition)

**문제**:
- 클라이언트(`offline-memo-service.js`)와 Service Worker(`service-worker.js`)가 동시에 `syncPendingMemos()`를 호출할 수 있음
- 둘 다 'PENDING' 상태인 항목을 조회하고 'SYNCING'으로 변경하려고 시도
- 상태 변경과 실제 API 호출 사이에 시간 차이가 있어서, 같은 항목이 여러 번 처리될 수 있음

**시나리오**:
1. 클라이언트가 'PENDING' 항목을 조회 (항목 A)
2. Service Worker가 동시에 'PENDING' 항목을 조회 (항목 A)
3. 클라이언트가 항목 A의 상태를 'SYNCING'으로 변경
4. Service Worker가 항목 A의 상태를 'SYNCING'으로 변경 (이미 변경됨)
5. 클라이언트가 API 호출 (메모 1 생성)
6. Service Worker가 API 호출 (메모 2 생성) ← **중복 생성**

### 4-2. 멱등성 키 재생성 문제

**문제**:
- `syncQueueItem()`에서 매번 새로운 `idempotencyKey`를 생성함
- 같은 큐 항목이 여러 번 처리되면 각각 다른 `idempotencyKey`를 사용하여 중복 생성됨
- 멱등성 키는 큐 항목별로 고정되어야 함

**현재 코드**:
```javascript
// offline-memo-service.js:427
const idempotencyKey = this.generateLocalId(); // 매번 새로운 키 생성
```

### 4-3. Service Worker의 멱등성 키 부재

**문제**:
- Service Worker의 `replayRequest()` 함수에서 멱등성 키를 전송하지 않음
- 클라이언트와 Service Worker가 동시에 동기화를 시도하면 중복 생성됨

**현재 코드**:
```javascript
// service-worker.js:544
return fetch(fullUrl, {
  method: method,
  headers: {
    'Content-Type': 'application/json',
    // Idempotency-Key 헤더 없음
  },
  body: body
});
```

### 4-4. 중복 클릭 방지 부재

**문제**:
- 프론트엔드에서 저장 버튼을 여러 번 클릭해도 중복 방지 로직이 없음
- 빠른 연속 클릭 시 동일한 메모가 여러 번 큐에 추가될 수 있음

## 5. 수정 계획

### 5-1. 동기화 큐 항목에 멱등성 키 저장 및 재사용

**목적**: 동일한 큐 항목이 여러 번 처리되어도 같은 멱등성 키를 사용하여 중복 생성 방지

**수정 파일**: `분산2_프로젝트_프론트/js/services/offline-memo-service.js`

**변경 내용**:
1. 동기화 큐 항목 생성 시 `idempotencyKey` 필드 추가
2. `syncQueueItem()`에서 큐 항목의 `idempotencyKey`를 재사용 (없으면 생성 후 저장)

```javascript
// createMemo() 메서드 수정
async createMemo(memoData) {
  // ...
  const localId = this.generateLocalId();
  const idempotencyKey = this.generateLocalId(); // 멱등성 키 생성
  
  // 동기화 큐에 추가 (idempotencyKey 포함)
  const queueItem = await syncQueueManager.enqueue({
    type: 'CREATE',
    localMemoId: localId,
    data: memoData,
    idempotencyKey: idempotencyKey // 멱등성 키 저장
  });
  // ...
}

// syncQueueItem() 메서드 수정
async syncQueueItem(queueItem) {
  await syncQueueManager.updateStatus(queueItem.id, 'SYNCING');
  
  // 멱등성 키 재사용 (없으면 생성 후 저장)
  let idempotencyKey = queueItem.idempotencyKey;
  if (!idempotencyKey) {
    idempotencyKey = this.generateLocalId();
    queueItem.idempotencyKey = idempotencyKey;
    await syncQueueManager.updateQueueItem(queueItem);
  }
  
  // API 호출 (고정된 멱등성 키 사용)
  response = await apiClient.post(API_ENDPOINTS.MEMOS.CREATE, queueItem.data, {
    headers: { 'Idempotency-Key': idempotencyKey }
  });
}
```

### 5-2. Service Worker에 멱등성 키 지원 추가

**목적**: Service Worker에서도 멱등성 키를 사용하여 중복 생성 방지

**수정 파일**: `분산2_프로젝트_프론트/service-worker.js`

**변경 내용**:
1. `replayRequest()` 함수에서 큐 항목의 `idempotencyKey`를 헤더에 포함

```javascript
// replayRequest() 함수 수정
async function replayRequest(queueItem) {
  // ...
  const headers = {
    'Content-Type': 'application/json',
  };
  
  // CREATE 요청 시 멱등성 키 포함
  if (queueItem.type === 'CREATE' && queueItem.idempotencyKey) {
    headers['Idempotency-Key'] = queueItem.idempotencyKey;
  }
  
  return fetch(fullUrl, {
    method: method,
    headers: headers,
    body: body
  });
}
```

### 5-3. 동기화 프로세스의 단일 실행 보장

**목적**: 동일한 큐 항목이 동시에 여러 번 처리되지 않도록 보장

**수정 파일**: `분산2_프로젝트_프론트/js/services/offline-memo-service.js`

**변경 내용**:
1. `syncPendingMemos()`에서 'SYNCING' 상태인 항목은 건너뛰기
2. 상태 변경을 원자적으로 수행 (IndexedDB 트랜잭션 활용)

```javascript
async syncPendingMemos() {
  // ...
  const pendingQueueItems = await syncQueueManager.getPendingItems();
  
  for (const queueItem of pendingQueueItems) {
    // 이미 'SYNCING' 상태인 항목은 건너뛰기
    if (queueItem.status === 'SYNCING') {
      console.log(`동기화 중인 항목 건너뛰기: ${queueItem.id}`);
      continue;
    }
    
    // 원자적 상태 변경 시도
    const updated = await syncQueueManager.tryUpdateStatus(queueItem.id, 'PENDING', 'SYNCING');
    if (!updated) {
      // 다른 프로세스가 이미 처리 중이면 건너뛰기
      console.log(`동기화 중인 항목 건너뛰기: ${queueItem.id}`);
      continue;
    }
    
    try {
      await this.syncQueueItem(queueItem);
      // ...
    } catch (error) {
      // ...
    }
  }
}
```

**추가 필요**: `sync-queue-manager.js`에 `tryUpdateStatus()` 메서드 추가

```javascript
// sync-queue-manager.js에 추가
async tryUpdateStatus(queueId, expectedStatus, newStatus) {
  return new Promise((resolve, reject) => {
    const transaction = dbManager.db.transaction(['sync_queue'], 'readwrite');
    const store = transaction.objectStore('sync_queue');
    const getRequest = store.get(queueId);
    
    getRequest.onsuccess = () => {
      const item = getRequest.result;
      if (item && item.status === expectedStatus) {
        // 예상 상태와 일치하면 업데이트
        item.status = newStatus;
        item.updatedAt = new Date().toISOString();
        const putRequest = store.put(item);
        putRequest.onsuccess = () => resolve(true);
        putRequest.onerror = () => reject(putRequest.error);
      } else {
        // 상태가 예상과 다르면 실패 (다른 프로세스가 이미 처리 중)
        resolve(false);
      }
    };
    getRequest.onerror = () => reject(getRequest.error);
  });
}
```

### 5-4. 프론트엔드 중복 클릭 방지

**목적**: 저장 버튼을 여러 번 클릭해도 중복 요청 방지

**수정 파일**: `분산2_프로젝트_프론트/js/views/pages/flow-view.js`

**변경 내용**:
1. 저장 버튼 클릭 시 비활성화
2. 메모 저장 완료 후 다시 활성화

```javascript
async handleSaveMemo() {
  // 저장 버튼 비활성화
  if (this.memoEditor && this.memoEditor.btnSaveMemo) {
    this.memoEditor.btnSaveMemo.disabled = true;
    this.memoEditor.btnSaveMemo.textContent = '저장 중...';
  }
  
  try {
    // 메모 저장 로직
    await memoService.createMemo(createData);
    // ...
  } catch (error) {
    console.error('메모 저장 실패:', error);
    alert('메모 저장에 실패했습니다. 다시 시도해주세요.');
  } finally {
    // 저장 버튼 다시 활성화
    if (this.memoEditor && this.memoEditor.btnSaveMemo) {
      this.memoEditor.btnSaveMemo.disabled = false;
      this.memoEditor.btnSaveMemo.textContent = '저장';
    }
  }
}
```

## 6. 예상되는 개선 효과

- 동일한 메모가 중복 생성되지 않도록 보장
- 클라이언트와 Service Worker가 동시에 동기화를 시도해도 중복 방지
- 저장 버튼을 여러 번 클릭해도 중복 요청 방지
- 멱등성 보장을 통한 안정적인 오프라인 동기화

## 7. 수정 파일 목록

1. `분산2_프로젝트_프론트/js/services/offline-memo-service.js`
   - `createMemo()`: 멱등성 키 생성 및 큐 항목에 저장
   - `syncQueueItem()`: 큐 항목의 멱등성 키 재사용
   - `syncPendingMemos()`: 'SYNCING' 상태 항목 건너뛰기 및 원자적 상태 변경

2. `분산2_프로젝트_프론트/js/services/sync-queue-manager.js`
   - `enqueue()`: `idempotencyKey` 필드 지원
   - `tryUpdateStatus()`: 원자적 상태 변경 메서드 추가

3. `분산2_프로젝트_프론트/service-worker.js`
   - `replayRequest()`: CREATE 요청 시 멱등성 키 헤더 포함

4. `분산2_프로젝트_프론트/js/views/pages/flow-view.js`
   - `handleMemoSave()`: 중복 클릭 방지 로직 추가

## 8. 수정 완료 체크리스트

- [x] `분산2_프로젝트_프론트/js/services/sync-queue-manager.js` 수정 완료
  - `enqueue()`: `idempotencyKey` 필드 지원 추가
  - `tryUpdateStatus()`: 원자적 상태 변경 메서드 추가
- [x] `분산2_프로젝트_프론트/js/services/offline-memo-service.js` 수정 완료
  - `createMemo()`: 멱등성 키 생성 및 큐 항목에 저장
  - `syncQueueItem()`: 큐 항목의 멱등성 키 재사용
  - `syncPendingMemos()`: 'SYNCING' 상태 항목 건너뛰기 및 원자적 상태 변경
- [x] `분산2_프로젝트_프론트/service-worker.js` 수정 완료
  - `replayRequest()`: CREATE 요청 시 멱등성 키 헤더 포함
- [x] `분산2_프로젝트_프론트/js/views/pages/flow-view.js` 수정 완료
  - `handleMemoSave()`: 중복 클릭 방지 로직 추가 (저장 버튼 비활성화)

## 9. 참고 문서

- `분산2_프로젝트/docs/fault-tolerance/OFFLINE_MEMO_SYNC.md`: 오프라인 메모 동기화 전략
- `분산2_프로젝트/docs/architecture/ARCHITECTURE.md`: 아키텍처 원칙

