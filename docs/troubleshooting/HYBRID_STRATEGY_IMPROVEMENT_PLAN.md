# 하이브리드 전략 단점 개선 방안

> **작성일**: 2025-12-09  
> **목적**: 하이브리드 전략의 단점(코드 중복, 상태 전환 처리) 개선 방안 제시  
> **상태**: 🔄 분석 완료

---

## 1. 단점 분석

### 1-1. 코드 중복 가능성

**문제점**:
- 온라인/오프라인 상태에 따라 유사한 로직이 중복될 수 있음
- 예: 메모 삭제 시 온라인/오프라인 모두 IndexedDB 갱신 필요
- 각 메서드(createMemo, updateMemo, deleteMemo)마다 네트워크 상태 분기 필요

**현재 구조**:
```javascript
// memo-service.js
async deleteMemo(memoId) {
  if (networkMonitor.isOnline) {
    // 온라인 로직
  } else {
    // 오프라인 로직
  }
}

// offline-memo-service.js
async deleteMemo(memoId) {
  // 오프라인 로직 (중복 가능)
}
```

### 1-2. 상태 전환 시 처리 필요

**문제점**:
- 오프라인 → 온라인 전환: 동기화 큐 처리 필요
- 온라인 → 오프라인 전환: 진행 중인 서버 요청 처리 필요
- 상태 전환 중 발생하는 요청 처리 (Race Condition)

**현재 구조**:
- `network-monitor.js`에서 `online`/`offline` 이벤트 처리
- 각 서비스에서 개별적으로 상태 전환 처리

## 2. 코드 중복 해결 방안

### 2-1. 전략 패턴 (Strategy Pattern) 적용

**개념**: 네트워크 상태에 따라 다른 전략 객체를 사용

**구조**:
```javascript
// 전략 인터페이스
class MemoOperationStrategy {
  async deleteMemo(memoId) { throw new Error('Not implemented'); }
  async createMemo(memoData) { throw new Error('Not implemented'); }
  async updateMemo(memoId, memoData) { throw new Error('Not implemented'); }
}

// 온라인 전략
class OnlineMemoStrategy extends MemoOperationStrategy {
  async deleteMemo(memoId) {
    // 서버 우선 로직
    await apiClient.delete(API_ENDPOINTS.MEMOS.DELETE(memoId));
    // IndexedDB 갱신
    await this.updateLocalStorage(memoId, 'delete');
  }
}

// 오프라인 전략
class OfflineMemoStrategy extends MemoOperationStrategy {
  async deleteMemo(memoId) {
    // 로컬 우선 로직
    const localMemo = await dbManager.getMemoByServerId(memoId);
    // 동기화 큐 추가
    await syncQueueManager.enqueue({ type: 'DELETE', ... });
  }
}

// 컨텍스트
class MemoService {
  constructor() {
    this.strategy = null;
    this.updateStrategy();
    networkMonitor.onStatusChange(() => this.updateStrategy());
  }
  
  updateStrategy() {
    this.strategy = networkMonitor.isOnline 
      ? new OnlineMemoStrategy() 
      : new OfflineMemoStrategy();
  }
  
  async deleteMemo(memoId) {
    return await this.strategy.deleteMemo(memoId);
  }
}
```

**장점**:
- 코드 중복 제거: 각 전략이 독립적으로 구현
- 확장성: 새로운 전략 추가 용이
- 테스트 용이성: 각 전략을 독립적으로 테스트

**단점**:
- 초기 구현 복잡도 증가
- 클래스 수 증가

### 2-2. 템플릿 메서드 패턴 (Template Method Pattern) 적용

**개념**: 공통 로직은 부모 클래스에, 상태별 로직은 자식 클래스에

**구조**:
```javascript
class MemoOperationTemplate {
  async deleteMemo(memoId) {
    // 공통 전처리
    await this.beforeDelete(memoId);
    
    // 상태별 로직 (템플릿 메서드)
    const result = await this.executeDelete(memoId);
    
    // 공통 후처리
    await this.afterDelete(memoId, result);
    
    return result;
  }
  
  // 템플릿 메서드 (상태별 구현)
  async executeDelete(memoId) {
    throw new Error('Not implemented');
  }
  
  // 공통 메서드
  async beforeDelete(memoId) {
    // 공통 검증 로직
  }
  
  async afterDelete(memoId, result) {
    // 공통 정리 로직 (예: IndexedDB 갱신)
  }
}

class OnlineMemoOperation extends MemoOperationTemplate {
  async executeDelete(memoId) {
    // 온라인 전용 로직
    return await apiClient.delete(API_ENDPOINTS.MEMOS.DELETE(memoId));
  }
}

class OfflineMemoOperation extends MemoOperationTemplate {
  async executeDelete(memoId) {
    // 오프라인 전용 로직
    return await offlineMemoService.deleteMemo(memoId);
  }
}
```

**장점**:
- 공통 로직 재사용: 중복 코드 제거
- 일관된 처리 흐름: 모든 작업이 동일한 패턴 따름
- 유지보수 용이: 공통 로직 수정 시 한 곳만 수정

**단점**:
- 상속 구조 복잡도
- 유연성 제한

### 2-3. 공통 로직 추출 (Helper 함수)

**개념**: 공통 로직을 별도 함수로 추출하여 재사용

**구조**:
```javascript
// 공통 헬퍼 함수
class MemoOperationHelper {
  static async updateLocalStorage(memoId, operation, data = null) {
    // IndexedDB 갱신 로직 (온라인/오프라인 공통)
    const localMemo = await dbManager.getMemoByServerId(memoId);
    if (localMemo) {
      switch (operation) {
        case 'delete':
          await dbManager.deleteMemo(localMemo.localId);
          break;
        case 'update':
          // 업데이트 로직
          break;
      }
    }
  }
  
  static async handleServerError(error, memoId, fallbackOperation) {
    // 서버 오류 시 오프라인 모드로 전환
    if (error.isNetworkError) {
      return await fallbackOperation(memoId);
    }
    throw error;
  }
}

// 사용
async deleteMemo(memoId) {
  if (networkMonitor.isOnline) {
    try {
      await apiClient.delete(API_ENDPOINTS.MEMOS.DELETE(memoId));
      await MemoOperationHelper.updateLocalStorage(memoId, 'delete');
    } catch (error) {
      return await MemoOperationHelper.handleServerError(
        error, 
        memoId, 
        () => this.deleteMemoOffline(memoId)
      );
    }
  } else {
    return await this.deleteMemoOffline(memoId);
  }
}
```

**장점**:
- 구현 간단: 기존 구조 유지하면서 개선
- 점진적 적용: 필요한 부분만 추출
- 유연성: 다양한 상황에 대응

**단점**:
- 완전한 중복 제거 어려움
- 헬퍼 함수 관리 필요

### 2-4. 권장 방안: 하이브리드 접근

**전략 패턴 + 공통 로직 추출**:

1. **상태별 전략 객체 생성** (전략 패턴)
2. **공통 로직은 헬퍼 함수로 추출** (코드 재사용)
3. **상태 전환은 이벤트 기반 처리** (느슨한 결합)

## 3. 상태 전환 처리 개선 방안

### 3-1. 상태 머신 패턴 (State Machine Pattern)

**개념**: 명확한 상태 정의 및 전환 규칙

**구조**:
```javascript
class NetworkStateMachine {
  constructor() {
    this.state = 'offline'; // offline, online, transitioning
    this.pendingOperations = [];
    this.listeners = [];
  }
  
  async transitionToOnline() {
    if (this.state === 'transitioning') {
      // 이미 전환 중이면 대기
      return this.waitForTransition();
    }
    
    this.state = 'transitioning';
    
    try {
      // 1. 진행 중인 서버 요청 완료 대기
      await this.waitForPendingRequests();
      
      // 2. 동기화 큐 처리
      await this.processSyncQueue();
      
      // 3. 상태 변경
      this.state = 'online';
      this.notifyListeners('online');
    } catch (error) {
      this.state = 'offline';
      this.notifyListeners('offline');
    }
  }
  
  async transitionToOffline() {
    if (this.state === 'transitioning') {
      return this.waitForTransition();
    }
    
    this.state = 'transitioning';
    
    try {
      // 1. 진행 중인 서버 요청 취소 또는 큐에 추가
      await this.cancelPendingRequests();
      
      // 2. 상태 변경
      this.state = 'offline';
      this.notifyListeners('offline');
    } catch (error) {
      // 오류 처리
    }
  }
  
  async executeOperation(operation) {
    // 상태에 따라 다른 처리
    if (this.state === 'transitioning') {
      // 전환 중이면 큐에 추가
      this.pendingOperations.push(operation);
      return;
    }
    
    if (this.state === 'online') {
      return await this.executeOnline(operation);
    } else {
      return await this.executeOffline(operation);
    }
  }
}
```

**장점**:
- 명확한 상태 관리: 상태 전환 규칙 명확
- Race Condition 방지: 전환 중 요청 큐잉
- 예측 가능한 동작: 상태별 명확한 처리

**단점**:
- 초기 구현 복잡도
- 상태 관리 오버헤드

### 3-2. 이벤트 기반 처리 (Event-Driven)

**개념**: 상태 전환을 이벤트로 처리하여 느슨한 결합

**구조**:
```javascript
class NetworkStateManager {
  constructor() {
    this.isOnline = false;
    this.eventBus = new EventBus();
    this.setupEventHandlers();
  }
  
  setupEventHandlers() {
    networkMonitor.onStatusChange((isOnline) => {
      if (isOnline && !this.isOnline) {
        // 오프라인 → 온라인 전환
        this.eventBus.emit('network:online', {
          triggerSync: true,
          processQueue: true
        });
      } else if (!isOnline && this.isOnline) {
        // 온라인 → 오프라인 전환
        this.eventBus.emit('network:offline', {
          cancelPending: true,
          queueOperations: true
        });
      }
      this.isOnline = isOnline;
    });
  }
}

// 각 서비스에서 이벤트 구독
class MemoService {
  constructor() {
    eventBus.on('network:online', async (data) => {
      if (data.processQueue) {
        await this.processSyncQueue();
      }
    });
    
    eventBus.on('network:offline', async (data) => {
      if (data.queueOperations) {
        await this.queuePendingOperations();
      }
    });
  }
}
```

**장점**:
- 느슨한 결합: 서비스 간 직접 의존 없음
- 확장성: 새로운 리스너 추가 용이
- 유연성: 이벤트 기반 처리

**단점**:
- 이벤트 순서 보장 어려움
- 디버깅 복잡도

### 3-3. 큐 기반 처리 (Queue-Based)

**개념**: 상태 전환 중 발생하는 요청을 큐에 저장하여 순차 처리

**구조**:
```javascript
class OperationQueue {
  constructor() {
    this.queue = [];
    this.processing = false;
    this.currentState = 'offline';
  }
  
  async enqueue(operation) {
    this.queue.push({
      operation,
      timestamp: Date.now(),
      state: this.currentState
    });
    
    if (!this.processing) {
      this.processQueue();
    }
  }
  
  async processQueue() {
    this.processing = true;
    
    while (this.queue.length > 0) {
      const item = this.queue.shift();
      
      // 상태가 변경되었는지 확인
      if (item.state !== this.currentState) {
        // 상태가 변경되었으면 다시 큐에 추가
        this.queue.unshift(item);
        await this.waitForStateStable();
        continue;
      }
      
      // 작업 실행
      try {
        await item.operation();
      } catch (error) {
        // 오류 처리
      }
    }
    
    this.processing = false;
  }
  
  async waitForStateStable() {
    // 상태가 안정화될 때까지 대기
    return new Promise((resolve) => {
      const checkInterval = setInterval(() => {
        if (!this.isStateTransitioning()) {
          clearInterval(checkInterval);
          resolve();
        }
      }, 100);
    });
  }
}
```

**장점**:
- 순서 보장: 요청 순서대로 처리
- 상태 전환 중 요청 처리: 큐에 저장하여 나중에 처리
- 안정성: 상태 안정화 후 처리

**단점**:
- 지연 가능성: 큐 처리 시간
- 메모리 사용: 큐 크기 관리 필요

### 3-4. 권장 방안: 하이브리드 접근

**상태 머신 + 이벤트 기반 + 큐 처리**:

1. **상태 머신으로 상태 관리** (명확한 상태 정의)
2. **이벤트 기반으로 상태 전환 알림** (느슨한 결합)
3. **큐로 전환 중 요청 처리** (순서 보장)

## 4. 종합 개선 방안

### 4-1. 아키텍처 제안

```
┌─────────────────────────────────────────┐
│      NetworkStateManager                │
│  (상태 머신 + 이벤트 발행)                │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│      MemoOperationStrategy              │
│  (전략 패턴: Online/Offline)            │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│      MemoOperationHelper                │
│  (공통 로직: IndexedDB 갱신 등)          │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│      OperationQueue                     │
│  (상태 전환 중 요청 큐잉)                 │
└─────────────────────────────────────────┘
```

### 4-2. 구현 단계

#### Phase 1: 공통 로직 추출
1. IndexedDB 갱신 로직을 헬퍼 함수로 추출
2. 오류 처리 로직을 헬퍼 함수로 추출
3. 공통 검증 로직을 헬퍼 함수로 추출

#### Phase 2: 전략 패턴 적용
1. `OnlineMemoStrategy` 클래스 생성
2. `OfflineMemoStrategy` 클래스 생성
3. `MemoService`에서 전략 선택 로직 구현

#### Phase 3: 상태 전환 처리
1. `NetworkStateManager` 구현 (상태 머신)
2. 이벤트 기반 상태 전환 알림 구현
3. 전환 중 요청 큐잉 로직 구현

### 4-3. 코드 중복 해결 우선순위

1. **높은 우선순위**: IndexedDB 갱신 로직 (모든 작업에서 공통)
2. **중간 우선순위**: 오류 처리 로직 (서버 오류 → 오프라인 전환)
3. **낮은 우선순위**: 검증 로직 (작업별로 다를 수 있음)

### 4-4. 상태 전환 처리 우선순위

1. **높은 우선순위**: 오프라인 → 온라인 전환 (동기화 큐 처리)
2. **중간 우선순위**: 온라인 → 오프라인 전환 (진행 중 요청 처리)
3. **낮은 우선순위**: 전환 중 요청 큐잉 (Race Condition 방지)

## 5. 구체적 개선 예시

### 5-1. 코드 중복 해결 예시

**Before (중복 코드)**:
```javascript
// 온라인 로직
async deleteMemoOnline(memoId) {
  await apiClient.delete(API_ENDPOINTS.MEMOS.DELETE(memoId));
  const localMemo = await dbManager.getMemoByServerId(memoId);
  if (localMemo) {
    await dbManager.deleteMemo(localMemo.localId);
  }
}

// 오프라인 로직
async deleteMemoOffline(memoId) {
  const localMemo = await dbManager.getMemoByServerId(memoId);
  await syncQueueManager.enqueue({ type: 'DELETE', ... });
  localMemo.syncStatus = 'pending';
  await dbManager.saveMemo(localMemo);
}
```

**After (공통 로직 추출)**:
```javascript
// 공통 헬퍼
class MemoOperationHelper {
  static async updateLocalAfterDelete(memoId) {
    const localMemo = await dbManager.getMemoByServerId(memoId);
    if (localMemo) {
      await dbManager.deleteMemo(localMemo.localId);
    }
  }
}

// 온라인 로직
async deleteMemoOnline(memoId) {
  await apiClient.delete(API_ENDPOINTS.MEMOS.DELETE(memoId));
  await MemoOperationHelper.updateLocalAfterDelete(memoId);
}

// 오프라인 로직
async deleteMemoOffline(memoId) {
  const localMemo = await dbManager.getMemoByServerId(memoId);
  await syncQueueManager.enqueue({ type: 'DELETE', ... });
  localMemo.syncStatus = 'pending';
  await dbManager.saveMemo(localMemo);
}
```

### 5-2. 상태 전환 처리 예시

**Before (개별 처리)**:
```javascript
// 각 서비스에서 개별 처리
networkMonitor.onStatusChange((isOnline) => {
  if (isOnline) {
    offlineMemoService.syncPendingMemos();
  }
});
```

**After (중앙화된 처리)**:
```javascript
// NetworkStateManager에서 중앙화
class NetworkStateManager {
  async transitionToOnline() {
    this.state = 'transitioning';
    
    // 1. 진행 중인 요청 완료 대기
    await this.waitForPendingRequests();
    
    // 2. 모든 서비스의 동기화 큐 처리
    await Promise.all([
      offlineMemoService.syncPendingMemos(),
      // 다른 서비스들...
    ]);
    
    this.state = 'online';
    this.eventBus.emit('network:online');
  }
}

// 각 서비스는 이벤트만 구독
eventBus.on('network:online', () => {
  // 추가 처리 필요 시
});
```

## 6. 결론

### 6-1. 권장 개선 방안

1. **코드 중복 해결**: 전략 패턴 + 공통 로직 추출 (하이브리드)
2. **상태 전환 처리**: 상태 머신 + 이벤트 기반 + 큐 처리 (하이브리드)

### 6-2. 구현 우선순위

1. **1단계**: 공통 로직 추출 (즉시 적용 가능, 낮은 리스크)
2. **2단계**: 전략 패턴 적용 (코드 구조 개선)
3. **3단계**: 상태 전환 처리 개선 (안정성 향상)

### 6-3. 예상 효과

- **코드 중복 감소**: 30-40% 코드 중복 제거 예상
- **유지보수성 향상**: 공통 로직 수정 시 한 곳만 수정
- **안정성 향상**: 상태 전환 중 Race Condition 방지
- **확장성 향상**: 새로운 네트워크 상태 추가 용이

## 7. 참고 문서

- `분산2_프로젝트/docs/troubleshooting/OFFLINE_ONLINE_HYBRID_STRATEGY_ANALYSIS.md`: 하이브리드 전략 분석
- `분산2_프로젝트/docs/fault-tolerance/OFFLINE_MEMO_SYNC.md`: 오프라인 메모 동기화 전략

