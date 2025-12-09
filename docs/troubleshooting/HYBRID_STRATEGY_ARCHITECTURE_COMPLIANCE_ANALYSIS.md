# 하이브리드 전략 개선 방안 아키텍처 준수 여부 분석

> **작성일**: 2025-12-09  
> **목적**: 하이브리드 전략 개선 방안이 기존 아키텍처 문서와 일치하는지 확인  
> **상태**: ✅ 분석 완료

---

## 1. 분석 개요

### 1-1. 제시된 개선 방안

**HYBRID_STRATEGY_IMPROVEMENT_PLAN.md**에서 제시된 권장 방안:

1. **코드 중복 해결**: 전략 패턴 + 공통 로직 추출 (하이브리드)
2. **상태 전환 처리**: 상태 머신 + 이벤트 기반 + 큐 처리 (하이브리드)

### 1-2. 확인 대상 문서

1. `분산2_프로젝트/docs/architecture/ARCHITECTURE.md`
2. `분산2_프로젝트/docs/fault-tolerance/FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md`
3. `분산2_프로젝트/docs/fault-tolerance/OFFLINE_FIRST.md`
4. `분산2_프로젝트/docs/fault-tolerance/DUAL_WRITE_IMPLEMENTATION_ISSUES.md`
5. `분산2_프로젝트_프론트/docs/architecture/IMPLEMENTATION_PLAN.md`

---

## 2. 이벤트 기반 상태 전환 처리 분석

### 2-1. 기존 아키텍처 요구사항

#### IMPLEMENTATION_PLAN.md (프론트엔드)

**4번 원칙: Event-Driven 패턴**:
> 상태 관리는 Event Bus를 통한 이벤트 기반 처리

**구현 요구사항**:
- `js/utils/event-bus.js` - Event Bus 구현
- `js/constants/events.js` - 이벤트 이름 상수 정의
- 상태 변경은 반드시 Event Bus를 통해 이벤트로 발행
- 필요한 컴포넌트에서 구독하도록 구현

**현재 구현**:
- `network-monitor.js`에서 `networkStatusChanged` 커스텀 이벤트 발행
- `window.dispatchEvent()` 사용

### 2-2. 제시된 개선 방안

**이벤트 기반 처리 (Event-Driven)**:
- `NetworkStateManager`에서 이벤트 발행
- 각 서비스에서 이벤트 구독
- 느슨한 결합 유지

**구조 예시**:
```javascript
class NetworkStateManager {
  constructor() {
    this.eventBus = new EventBus();
  }
  
  setupEventHandlers() {
    networkMonitor.onStatusChange((isOnline) => {
      if (isOnline && !this.isOnline) {
        this.eventBus.emit('network:online', {...});
      }
    });
  }
}

// 각 서비스에서 구독
eventBus.on('network:online', async (data) => {
  await this.processSyncQueue();
});
```

### 2-3. 준수 여부 분석

**✅ 완전히 준수함**

**이유**:
1. **Event-Driven 패턴 준수**: IMPLEMENTATION_PLAN.md의 4번 원칙과 완전히 일치
2. **기존 구조 활용**: 현재 `network-monitor.js`에서 이미 커스텀 이벤트 사용 중
3. **확장성**: Event Bus 패턴으로 확장 가능
4. **느슨한 결합**: 서비스 간 직접 의존 없음

**개선점**:
- 현재 `window.dispatchEvent()` 사용 → `EventBus` 클래스로 통합 권장
- 이벤트 이름 상수화 (`js/constants/events.js`)

---

## 3. 전략 패턴 + 공통 로직 추출 분석

### 3-1. 기존 아키텍처 요구사항

#### ARCHITECTURE.md (백엔드)

**함수 단일 책임 원칙**:
> 함수는 반드시 하나의 동작만 담당해야 합니다.

**비기능 요구사항 관련 예외 사항**:
> 비기능 요구사항 관련 코드는 여러 단계를 하나의 책임으로 묶는 것이 적절합니다.

**이유**:
- 높은 응집도 (High Cohesion)
- 가독성 및 흐름 유지
- 변경의 용이성 (Maintainability)

#### IMPLEMENTATION_PLAN.md (프론트엔드)

**구현 원칙**:
- 아키텍처 문서 준수
- 점진적 구현
- 단계별로 구현하고 테스트하며 진행

**서비스 계층 구조**:
```
js/services/
├── api-client.js
├── auth-service.js
├── book-service.js
├── memo-service.js
├── offline-memo-service.js
└── user-service.js
```

### 3-2. 제시된 개선 방안

**전략 패턴 + 공통 로직 추출**:

1. **전략 패턴 적용**:
   - `OnlineMemoStrategy` 클래스 생성
   - `OfflineMemoStrategy` 클래스 생성
   - `MemoService`에서 전략 선택

2. **공통 로직 추출**:
   - `MemoOperationHelper` 클래스 생성
   - IndexedDB 갱신 로직 추출
   - 오류 처리 로직 추출

**구조 예시**:
```javascript
// 전략 인터페이스
class MemoOperationStrategy {
  async deleteMemo(memoId) { throw new Error('Not implemented'); }
}

// 온라인 전략
class OnlineMemoStrategy extends MemoOperationStrategy {
  async deleteMemo(memoId) {
    await apiClient.delete(API_ENDPOINTS.MEMOS.DELETE(memoId));
    await MemoOperationHelper.updateLocalAfterDelete(memoId);
  }
}

// 오프라인 전략
class OfflineMemoStrategy extends MemoOperationStrategy {
  async deleteMemo(memoId) {
    const localMemo = await dbManager.getMemoByServerId(memoId);
    await syncQueueManager.enqueue({ type: 'DELETE', ... });
  }
}

// 컨텍스트
class MemoService {
  constructor() {
    this.strategy = null;
    this.updateStrategy();
  }
  
  async deleteMemo(memoId) {
    return await this.strategy.deleteMemo(memoId);
  }
}
```

### 3-3. 준수 여부 분석

**⚠️ 부분적으로 준수하나, 구조적 고려 필요**

**준수하는 부분**:
1. **공통 로직 추출**: ✅ 함수 단일 책임 원칙 준수
   - IndexedDB 갱신 로직을 헬퍼 함수로 추출
   - 중복 코드 제거

2. **점진적 구현**: ✅ IMPLEMENTATION_PLAN.md 원칙 준수
   - Phase 1: 공통 로직 추출 (즉시 적용 가능)
   - Phase 2: 전략 패턴 적용 (코드 구조 개선)

**고려가 필요한 부분**:
1. **서비스 계층 구조 변경**:
   - 현재: `memo-service.js` → `offline-memo-service.js` 호출
   - 제안: `MemoService` → `OnlineMemoStrategy` / `OfflineMemoStrategy`
   - **영향**: 기존 서비스 계층 구조와 다소 다름

2. **클래스 기반 구조**:
   - 현재: 함수 기반 모듈 (`export const memoService = {...}`)
   - 제안: 클래스 기반 구조 (`class MemoService`)
   - **영향**: 기존 코드 스타일과 다름

3. **비기능 요구사항 예외 원칙**:
   - ARCHITECTURE.md: "비기능 요구사항 관련 코드는 여러 단계를 하나의 책임으로 묶는 것이 적절"
   - 전략 패턴: 각 전략이 독립적으로 구현
   - **충돌 없음**: 전략 패턴은 비즈니스 로직 분리이므로 해당 원칙과 무관

---

## 4. 종합 분석 및 권장 방안

### 4-1. 준수 여부 요약

| 개선 방안 | 준수 여부 | 비고 |
|----------|----------|------|
| **이벤트 기반 상태 전환 처리** | ✅ **완전히 준수** | IMPLEMENTATION_PLAN.md의 Event-Driven 패턴과 일치 |
| **전략 패턴 적용** | ⚠️ **부분 준수** | 구조적 고려 필요 (서비스 계층 구조) |
| **공통 로직 추출** | ✅ **완전히 준수** | 함수 단일 책임 원칙 준수 |

### 4-2. 권장 방안 (기존 아키텍처 준수)

#### 방안 A: 기존 구조 유지 + 공통 로직 추출 (권장) ⭐

**핵심 원칙**: 기존 서비스 계층 구조를 유지하면서 공통 로직만 추출

**구조**:
```javascript
// js/utils/memo-operation-helper.js (새로 생성)
export class MemoOperationHelper {
  static async updateLocalAfterDelete(memoId) {
    const localMemo = await dbManager.getMemoByServerId(memoId);
    if (localMemo) {
      await dbManager.deleteMemo(localMemo.localId);
    }
  }
  
  static async handleServerError(error, memoId, fallbackOperation) {
    if (error.isNetworkError) {
      return await fallbackOperation(memoId);
    }
    throw error;
  }
}

// js/services/memo-service.js (기존 구조 유지)
export const memoService = {
  async deleteMemo(memoId) {
    if (networkMonitor.isOnline) {
      try {
        await apiClient.delete(API_ENDPOINTS.MEMOS.DELETE(memoId));
        await MemoOperationHelper.updateLocalAfterDelete(memoId);
      } catch (error) {
        return await MemoOperationHelper.handleServerError(
          error, 
          memoId, 
          () => this.deleteMemoOffline(memoId)
        );
      }
    } else {
      return await offlineMemoService.deleteMemo(memoId);
    }
  }
};
```

**장점**:
- ✅ 기존 서비스 계층 구조 유지
- ✅ 함수 기반 모듈 구조 유지
- ✅ 공통 로직 추출로 중복 제거
- ✅ 점진적 적용 가능
- ✅ 기존 코드와의 일관성 유지

**단점**:
- ⚠️ 완전한 전략 패턴 적용은 아님
- ⚠️ 네트워크 상태 분기 로직이 각 메서드에 남음

#### 방안 B: 하이브리드 접근 (기존 구조 + 전략 패턴 부분 적용)

**핵심 원칙**: 기존 구조를 유지하되, 전략 선택 로직만 추가

**구조**:
```javascript
// js/services/strategies/memo-operation-strategy.js (새로 생성)
export class MemoOperationStrategy {
  static getStrategy(isOnline) {
    return isOnline 
      ? new OnlineMemoStrategy() 
      : new OfflineMemoStrategy();
  }
}

// js/services/memo-service.js (기존 구조 유지)
export const memoService = {
  async deleteMemo(memoId) {
    const strategy = MemoOperationStrategy.getStrategy(networkMonitor.isOnline);
    return await strategy.deleteMemo(memoId);
  }
};
```

**장점**:
- ✅ 전략 패턴의 이점 활용
- ✅ 기존 서비스 구조 최대한 유지
- ✅ 네트워크 상태 분기 로직 최소화

**단점**:
- ⚠️ 클래스 기반 전략과 함수 기반 서비스 혼재
- ⚠️ 구조적 일관성 약간 저하

#### 방안 C: 완전한 전략 패턴 적용 (구조 변경 필요)

**핵심 원칙**: 제시된 개선 방안 그대로 적용

**구조**:
```javascript
// js/services/memo-service.js (클래스 기반으로 변경)
export class MemoService {
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
- ✅ 완전한 전략 패턴 적용
- ✅ 코드 중복 최소화
- ✅ 확장성 우수

**단점**:
- ❌ 기존 서비스 계층 구조 대폭 변경 필요
- ❌ 함수 기반 → 클래스 기반 전환
- ❌ 기존 코드와의 일관성 저하
- ❌ 점진적 적용 어려움

### 4-3. 최종 권장 방안

**권장: 방안 A (기존 구조 유지 + 공통 로직 추출)**

**이유**:
1. **아키텍처 문서 준수**: IMPLEMENTATION_PLAN.md의 "점진적 구현" 원칙 준수
2. **기존 구조 유지**: 현재 서비스 계층 구조와 일관성 유지
3. **낮은 리스크**: 기존 코드 최소한 수정
4. **즉시 적용 가능**: Phase 1부터 바로 적용 가능
5. **확장성**: 필요 시 방안 B 또는 C로 점진적 전환 가능

**구현 단계**:
1. **Phase 1**: 공통 로직 추출 (방안 A)
   - `MemoOperationHelper` 클래스 생성
   - IndexedDB 갱신 로직 추출
   - 오류 처리 로직 추출

2. **Phase 2**: 이벤트 기반 상태 전환 처리
   - `EventBus` 클래스 생성 (또는 기존 `event-bus.js` 활용)
   - `NetworkStateManager` 구현
   - 이벤트 기반 상태 전환 알림

3. **Phase 3 (선택)**: 전략 패턴 부분 적용 (방안 B)
   - 필요 시 전략 선택 로직만 추가
   - 기존 구조 유지하면서 전략 패턴 이점 활용

---

## 5. 상태 전환 처리 개선 방안 분석

### 5-1. 제시된 개선 방안

**상태 머신 + 이벤트 기반 + 큐 처리**:
- 상태 머신으로 상태 관리 (명확한 상태 정의)
- 이벤트 기반으로 상태 전환 알림 (느슨한 결합)
- 큐로 전환 중 요청 처리 (순서 보장)

### 5-2. 기존 아키텍처와의 일치 여부

**✅ 완전히 준수함**

**이유**:
1. **이벤트 기반**: IMPLEMENTATION_PLAN.md의 Event-Driven 패턴 준수
2. **상태 관리**: 기존 `network-monitor.js`의 상태 관리와 일치
3. **큐 처리**: 기존 `sync-queue-manager.js`와 일치
4. **점진적 적용**: 기존 구조를 확장하는 형태

**구현 시 고려사항**:
- 기존 `network-monitor.js`와의 통합
- 기존 `sync-queue-manager.js`와의 통합
- 이벤트 이름 상수화 (`js/constants/events.js`)

---

## 6. 결론

### 6-1. 준수 여부 요약

| 개선 방안 | 준수 여부 | 권장 방안 |
|----------|----------|----------|
| **이벤트 기반 상태 전환 처리** | ✅ **완전히 준수** | 그대로 적용 가능 |
| **상태 머신 + 큐 처리** | ✅ **완전히 준수** | 기존 구조 확장 |
| **전략 패턴 적용** | ⚠️ **부분 준수** | 방안 A 권장 (공통 로직 추출) |
| **공통 로직 추출** | ✅ **완전히 준수** | 즉시 적용 가능 |

### 6-2. 최종 권장 방안

**1단계: 공통 로직 추출 (즉시 적용 가능)**
- `MemoOperationHelper` 클래스 생성
- IndexedDB 갱신 로직 추출
- 오류 처리 로직 추출
- **기존 구조 유지**

**2단계: 이벤트 기반 상태 전환 처리**
- `EventBus` 클래스 생성 (또는 기존 활용)
- `NetworkStateManager` 구현
- 이벤트 기반 상태 전환 알림
- **기존 `network-monitor.js` 확장**

**3단계 (선택): 전략 패턴 부분 적용**
- 필요 시 전략 선택 로직만 추가
- 기존 구조 유지하면서 전략 패턴 이점 활용

### 6-3. 아키텍처 문서 준수 여부

**✅ 준수함**

**이유**:
1. **Event-Driven 패턴**: IMPLEMENTATION_PLAN.md 준수
2. **점진적 구현**: IMPLEMENTATION_PLAN.md 원칙 준수
3. **함수 단일 책임 원칙**: ARCHITECTURE.md 준수
4. **기존 구조 유지**: 최소한의 변경으로 개선

**주의사항**:
- 전략 패턴 완전 적용 시 기존 구조와 다소 다를 수 있음
- 방안 A (공통 로직 추출)를 권장하여 기존 구조 유지

---

## 7. 참고 문서

- `분산2_프로젝트/docs/troubleshooting/HYBRID_STRATEGY_IMPROVEMENT_PLAN.md`: 개선 방안 상세
- `분산2_프로젝트/docs/troubleshooting/OFFLINE_ONLINE_HYBRID_STRATEGY_ANALYSIS.md`: 하이브리드 전략 분석
- `분산2_프로젝트_프론트/docs/architecture/IMPLEMENTATION_PLAN.md`: 프론트엔드 구현 계획
- `분산2_프로젝트/docs/architecture/ARCHITECTURE.md`: 백엔드 아키텍처

