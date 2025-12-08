# 비기능 품질 개선 구현 로드맵

> **목적**: Fault Tolerance(장애 허용) 비기능 품질 개선을 위한 전체 구현 계획 및 로드맵  
> **범위**: 오프라인 메모 동기화, MySQL 이중화, 클라이언트 기능 완성  
> **최종 업데이트**: 2024년

---

## 📋 목차

1. [개요](#개요)
2. [비기능 품질 시나리오](#비기능-품질-시나리오)
3. [구현 순서 및 단계](#구현-순서-및-단계)
4. [시나리오 1: 오프라인 메모 동기화](#시나리오-1-오프라인-메모-동기화)
5. [시나리오 2: MySQL 이중화 및 양방향 동기화](#시나리오-2-mysql-이중화-및-양방향-동기화)
6. [클라이언트 기능 완성](#클라이언트-기능-완성)
7. [권장 해결 로직](#권장-해결-로직)
8. [리스크 관리](#리스크-관리)
9. [참고 자료](#참고-자료)

---

## 개요

본 문서는 Reading Tracker 프로젝트의 비기능 품질 개선을 위한 전체 구현 계획을 정리합니다. 두 가지 주요 Fault Tolerance 시나리오를 단계적으로 구현하여 시스템의 안정성과 가용성을 향상시킵니다.

### 핵심 원칙

1. **단계적 구현**: 복잡한 인프라 변경 전에 클라이언트 기능을 먼저 안정화
2. **리스크 격리**: 각 단계를 독립적으로 검증하여 전체 시스템 안정성 보장
3. **검증 우선**: 각 단계 완료 후 충분한 테스트를 통해 안정성 확보

---

## 비기능 품질 시나리오

### 시나리오 1: 오프라인 메모 동기화

**목적**: 네트워크가 없는 환경에서도 메모를 작성하고, 네트워크 복구 시 자동으로 서버에 동기화

**특징**:
- 클라이언트 측 구현 (웹)
- 백엔드 API 변경 불필요
- Offline-First 아키텍처

**상세 문서**: [OFFLINE_MEMO_SYNC.md](./OFFLINE_MEMO_SYNC.md)

### 시나리오 2: MySQL 이중화 및 양방향 동기화

**목적**: 데이터베이스 장애 시에도 서비스 지속성을 보장하기 위한 Master-Master 구성

**요구사항**:
- 두 개의 Master DBMS 구성
- 각 DBMS에서 Read, Insert, Update, Delete 모두 가능
- 두 DBMS 간 완전한 데이터 동기화 (데이터 무결성 보장)
- Read 작업: 하나의 DB 장애 시 다른 DB에서 읽기
- Write 작업: 하나의 DB에 먼저 실행 → 성공 시 다른 DB에도 실행 → 실패 시 양쪽 모두 롤백

**특징**:
- 서버/인프라 측 구현
- 분산 트랜잭션 관리 필요
- 백엔드 코드 대폭 수정 필요

---

## 구현 순서 및 단계

### 권장 순서: 단계별 순차 진행 (옵션 A)

```
Phase 1: 클라이언트 기능 완성 (2-3주)
    ↓
Phase 2: 인프라 개선 (3-4주)
```

### 단계별 상세 계획

#### Phase 1: 클라이언트 기능 완성

**기간**: 2-3주  
**목표**: 웹 UI 오프라인 동기화 구현

**작업 내용**:
1. 웹 UI 오프라인 동기화 구현
   - IndexedDB 스키마 설계 및 구현
   - 오프라인 메모 작성 기능
   - 동기화 큐 관리
   - 네트워크 복구 감지 및 자동 동기화
   - UI 통합 및 동기화 상태 표시

**완료 기준**: ✅ **모두 완료**
- [x] 오프라인 상태에서 메모 작성 가능
- [x] 오프라인 상태에서 메모 수정 가능 (시나리오 1: 동기화 중 수정 허용)
- [x] 오프라인 상태에서 메모 삭제 가능 (시나리오 2, 5: WAITING 상태 처리)
- [x] 네트워크 복구 시 자동 동기화 작동
- [x] 2-Phase Health Check (로컬 서버 + 외부 서비스)
- [x] 웹 UI에서 동기화 상태 표시 (메모 카드에 아이콘 표시)
- [x] Toast 메시지로 동기화 결과 피드백
- [x] Service Worker 백그라운드 동기화
- [x] 멱등성 보장 (서버 측 Redis)
- [x] 데이터 무결성 보장 (시나리오 1, 2, 5, 6 개선 사항 반영)

#### Phase 2: 인프라 개선

**기간**: 3-4주  
**목표**: MySQL Master-Master 구성 및 분산 트랜잭션 관리

**작업 내용**:
1. MySQL Master-Master 구성
   - 두 개의 MySQL 인스턴스 설정
   - 양방향 복제 구성
   - 데이터 동기화 검증

2. 백엔드 코드 수정
   - Connection Pool 다중화 (Primary/Secondary)
   - 커스텀 트랜잭션 매니저 구현
   - 분산 트랜잭션 관리 로직
   - Read Failover 로직
   - Write 동기화 및 롤백 메커니즘

3. 모든 Service 메서드 수정
   - `@Transactional` 메서드 수정 (17개)
   - Read 작업: Failover 로직 추가
   - Write 작업: 2PC 패턴 적용

4. 테스트 및 검증
   - 단위 테스트
   - 통합 테스트
   - 장애 시나리오 테스트

**완료 기준**:
- [x] 두 MySQL 인스턴스 정상 동작 (구성 완료)
- [x] 양방향 복제 정상 작동 (Custom Dual Write 구현 완료)
- [x] Read Failover 정상 작동 ✅
- [x] Write 동기화 및 롤백 정상 작동 ✅
- [x] 모든 Service 메서드 수정 완료 ✅
- [ ] 장애 시나리오 테스트 통과 (테스트 필요)

---

## 시나리오 1: 오프라인 메모 동기화

### 개요

네트워크가 없는 환경에서 메모를 작성하고, 네트워크 복구 시 자동으로 서버에 동기화하는 기능입니다. **✅ 구현 완료**

### 아키텍처

```
[사용자 메모 작성/수정/삭제]
        ↓
[로컬 저장소에 저장] ← IndexedDB (웹)
        ↓
[UI 즉시 업데이트] (Optimistic UI)
        ↓
[동기화 큐에 추가] (CREATE/UPDATE/DELETE)
        ↓
[네트워크 상태 확인] (2-Phase Health Check)
        ├─ 온라인 + 서버 연결 가능 → [Service Worker 동기화]
        │                                   ↓
        │                           [서버로 전송]
        │                                   ↓
        │                           [서버 응답 처리]
        │                                   ├─ 성공 → [로컬 메모 업데이트 (서버 ID)]
        │                                   │         [Toast 메시지 표시]
        │                                   └─ 실패 → [재시도 큐에 추가]
        │                                             [Exponential Backoff]
        └─ 오프라인 → [대기 상태 표시]
                            ↓
                    [네트워크 재연결 감지]
                            ↓
                    [2-Phase Health Check]
                            ├─ 로컬 서버 연결 확인
                            └─ 외부 서비스(Aladin API) 연결 확인
                            ↓
                    [대기 중인 메모 동기화]
                            ↓
                    [WAITING 상태 처리] (시나리오 2, 5)
                            ↓
                    [순차 동기화] (작성 시간 순서)
```

### 구현 방법

#### 웹 (JavaScript) ✅ 구현 완료

**기술 스택**:
- **IndexedDB**: 로컬 저장소 (`offline_memos`, `sync_queue` 테이블)
- **Service Worker**: 네트워크 요청 가로채기 및 백그라운드 동기화
- **`navigator.onLine` API**: 네트워크 어댑터 상태 감지
- **2-Phase Health Check**: 로컬 서버 + 외부 서비스(Aladin API) 연결 확인
- **Toast 메시지**: 동기화 상태 UI 피드백

**주요 컴포넌트**:
1. **`IndexedDBManager`** (`js/storage/indexeddb-manager.js`): 로컬 저장소 관리
   - `offline_memos`: 오프라인 메모 저장
   - `sync_queue`: 동기화 큐 관리
   - 인덱스: `syncStatus`, `userBookId`, `memoStartTime`, `serverId`, `status`, `localMemoId`

2. **`OfflineMemoService`** (`js/services/offline-memo-service.js`): 오프라인 메모 작성 및 관리
   - `createMemo()`: 메모 생성 (Optimistic UI)
   - `updateMemo()`: 메모 수정 (시나리오 1: 동기화 중 수정 허용)
   - `deleteMemo()`: 메모 삭제 (시나리오 2, 5: WAITING 상태 처리)
   - `syncPendingMemos()`: 대기 중인 메모 동기화 (WAITING 상태 처리 포함)
   - `syncQueueItem()`: 개별 큐 항목 동기화

3. **`SyncQueueManager`** (`js/services/sync-queue-manager.js`): 동기화 큐 관리
   - `enqueue()`: 큐 항목 추가 (WAITING 상태 지원, `originalQueueId` 지원)
   - `getWaitingItems()`: WAITING 상태 항목 조회
   - `getQueueItem()`: 특정 큐 항목 조회
   - `updateQueueItem()`: 큐 항목 업데이트
   - 상태: `PENDING`, `WAITING`, `SYNCING`, `SUCCESS`, `FAILED`

4. **`NetworkMonitor`** (`js/utils/network-monitor.js`): 네트워크 상태 모니터링
   - `checkServerHealth()`: 로컬 서버 헬스체크
   - `checkExternalServiceHealth()`: 외부 서비스(Aladin API) 헬스체크
   - `notifyNetworkStatus()`: 네트워크 상태 이벤트 디스패치
   - `handleSyncSuccess()`: 동기화 완료 후 Toast 메시지 표시

5. **`MemoService`** (`js/services/memo-service.js`): 메모 서비스 통합
   - `mergeMemos()`: 로컬 메모와 서버 메모 통합 (시나리오 6: 중복 방지)
   - 동기화 대기 중인 메모 우선 표시

6. **`service-worker.js`**: Service Worker 백그라운드 동기화
   - 네트워크 요청 가로채기
   - 실패한 요청을 동기화 큐에 저장
   - 백그라운드 동기화 실행
   - WAITING 상태 처리 로직

#### 네트워크 연결 감지 및 자동 동기화 메커니즘 ✅ 구현 완료

**핵심 원리**:
- `navigator.onLine` API로 네트워크 어댑터 상태 확인
- `online` / `offline` 이벤트로 네트워크 상태 변경 감지
- **2-Phase Health Check**: 로컬 서버 + 외부 서비스(Aladin API) 연결 확인
- **Toast 메시지**: 동기화 상태 UI 피드백

**구현 방식**:

```javascript
// utils/network-monitor.js
class NetworkMonitor {
    async onNetworkOnline() {
        // 1초 대기 (네트워크 안정화)
        await this.delay(1000);
        
        // 2-Phase Health Check
        const isLocalServerReachable = await this.checkServerHealth();
        const isExternalServiceReachable = await this.checkExternalServiceHealth();
        
        if (isLocalServerReachable) {
            // 로컬 서버 연결 가능 → 동기화 시작
            const result = await offlineMemoService.syncPendingMemos();
            
            // 동기화 완료 후 Toast 메시지 표시
            this.handleSyncSuccess(result, isExternalServiceReachable);
        } else {
            // 네트워크는 연결되었지만 서버 접근 불가
            console.warn('네트워크는 연결되었지만 서버에 접근할 수 없습니다.');
            // 재시도 예약
            setTimeout(() => this.onNetworkOnline(), 5000);
        }
    }
    
    /**
     * 로컬 서버 헬스체크 (Phase 1)
     */
    async checkServerHealth() {
        try {
            const response = await fetch('/api/v1/health', {
                method: 'HEAD',
                signal: AbortSignal.timeout(3000)  // 3초 타임아웃
            });
            return response.ok;
        } catch (error) {
            console.error('로컬 서버 헬스체크 실패:', error);
            return false;
        }
    }
    
    /**
     * 외부 서비스 헬스체크 (Phase 2: Aladin API)
     */
    async checkExternalServiceHealth() {
        try {
            const response = await fetch('/api/v1/health/aladin', {
                method: 'GET',
                signal: AbortSignal.timeout(3000)
            });
            return response.ok;
        } catch (error) {
            console.warn('외부 서비스(Aladin API) 연결 불가:', error);
            return false;
        }
    }
    
    /**
     * 동기화 완료 후 Toast 메시지 표시
     */
    handleSyncSuccess(result, isExternalServiceReachable) {
        if (result.successCount > 0) {
            showToast(`✅ ${result.successCount}개의 메모 동기화 완료.`, 'success');
        }
        if (result.failedCount > 0) {
            showToast(`⚠️ ${result.failedCount}개의 메모 동기화 실패.`, 'warning');
        }
        if (!isExternalServiceReachable) {
            showToast('⚠️ 외부 서비스 연결 불가. 검색 제한됨.', 'warning');
        }
    }
}
```

**이점**:
- `navigator.onLine`만으로는 Wi-Fi 연결되어 있지만 인터넷 접속 불가 상황을 감지하지 못함
- 2-Phase Health Check로 로컬 서버와 외부 서비스 연결 상태를 각각 확인
- 외부 서비스(Aladin API) 연결 불가 시에도 로컬 서버 기능은 정상 사용 가능
- Toast 메시지로 사용자에게 명확한 피드백 제공
- 서버 접근 불가 시 자동 재시도로 안정성 향상

**상세 구현**: [OFFLINE_MEMO_SYNC.md](./OFFLINE_MEMO_SYNC.md) 참조

### 동기화 전략 ✅ 구현 완료

1. **낙관적 업데이트 (Optimistic UI)**: 메모 작성/수정/삭제 즉시 로컬 저장 및 UI 업데이트
   - 시나리오 1: 동기화 중인 메모도 수정 가능 (`syncing_create` 상태 허용)
   - 시나리오 2, 5: DELETE 시도 즉시 UI에서 숨김 (Optimistic Deletion)
   - 시나리오 6: 동기화 대기 중인 메모 우선 표시

2. **자동 재시도**: Exponential Backoff 전략 (5초, 10초, 20초)
   - 최대 3회 재시도
   - 실패 시 `failed` 상태로 표시

3. **순차 동기화**: `memoStartTime` 또는 `createdAt` 기준 정렬 후 순차 동기화
   - 시나리오 2, 5: WAITING 상태 항목은 원본 항목 완료 후 실행

4. **부분 실패 처리**: 일부 메모만 실패해도 나머지는 계속 진행
   - 성공/실패 개수 반환하여 Toast 메시지 표시

5. **상태 관리 강화**:
   - `syncStatus`: `pending`, `syncing_create`, `syncing_update`, `syncing_delete`, `waiting`, `synced`, `failed`
   - 큐 항목 상태: `PENDING`, `WAITING`, `SYNCING`, `SUCCESS`, `FAILED`

6. **WAITING 상태 처리** (시나리오 2, 5):
   - UPDATE 동기화 중 DELETE 시도 시 `WAITING` 상태로 설정
   - 원본 항목(`originalQueueId`)이 `SUCCESS` 상태가 되면 `PENDING`으로 변경하여 실행
   - Service Worker와 클라이언트 양쪽에서 처리

### 백엔드 변경사항 ✅ 구현 완료

**멱등성 보장**:
- **POST /api/v1/memos**: `Idempotency-Key` 헤더 지원 (Redis 기반)
  - 동일한 키로 재요청 시 캐시된 응답 반환
  - 네트워크 재차단 시 중복 생성 방지
- **DELETE /api/v1/memos/{memoId}**: 이미 삭제된 메모에 대해서도 성공 응답 반환
  - `findById().orElse(null)` 사용하여 멱등성 보장

**헬스체크 엔드포인트**:
- `GET /api/v1/health`: 로컬 서버 상태 확인
- `GET /api/v1/health/aladin`: 외부 서비스(Aladin API) 연결 확인

**Redis 통합**:
- 멱등성 키 관리 (`IdempotencyKeyService`)
- Refresh Token 저장 (기존 MySQL에서 마이그레이션)
- 태그 데이터 캐싱 (Purger-Driven Invalidation, 7일 TTL)
- 내 서재 정보 캐싱 (Write-Through 패턴, 5-10분 TTL)

**기존 API 유지**:
- `POST /api/v1/memos`: 메모 작성 (멱등성 보장 추가)
- `PUT /api/v1/memos/{memoId}`: 메모 수정
- `DELETE /api/v1/memos/{memoId}`: 메모 삭제 (멱등성 보장 추가)
- `GET /api/v1/memos/books/{userBookId}`: 메모 조회

---

## 시나리오 2: MySQL 이중화 및 양방향 동기화

### 개요

두 개의 Master DBMS를 구성하여 데이터베이스 장애 시에도 서비스 지속성을 보장합니다. **✅ 구현 완료**

### 요구사항 상세

#### 1. Master-Master 구성
- 두 개의 MySQL 인스턴스를 모두 Master로 설정
- 각 DBMS에서 Read, Insert, Update, Delete 모두 가능

#### 2. 데이터 무결성
- 두 DBMS 간 완전한 데이터 동기화 보장
- MySQL Replication 또는 커스텀 동기화 로직 사용

#### 3. Read 작업 (90% 사용)
- 하나의 DB에서 데이터 읽기 시도
- 실패 시 자동으로 다른 DB에서 읽기 (Failover)
- 사용자에게는 투명하게 처리

#### 4. Write 작업 (10% 사용)
- **Phase 1**: Primary DB에 먼저 실행
- **Phase 2**: 성공 시 Secondary DB에도 동일 작업 실행
- **실패 처리**: 하나의 DB에서 실패 시 양쪽 모두 롤백
- 사용자에게는 try-catch exception 처리로 실패 알림

### 아키텍처

```
[사용자 요청]
        ↓
[Service Layer]
        ↓
    ┌───┴───┐
    │       │
[Read]   [Write]
    │       │
    │   ┌───┴───┐
    │   │       │
    │ [Primary] [Secondary]
    │   │       │
    │   └───┬───┘
    │       │
    │   [Custom Dual Write]
    │       │
    │   ├─ Primary 성공 → Secondary 시도
    │   │   ├─ Secondary 성공 → Commit
    │   │   └─ Secondary 실패 → Primary 보상 트랜잭션 (DELETE)
    │   │
    │   └─ Primary 실패 → Exception (Failover 불가)
    │
[Read Failover]
    │
    ├─ Primary 성공 → 반환
    └─ Primary 실패 → Secondary 시도
```

### 핵심 전략: Custom Dual Write 및 Read Failover

#### 전략 개요

MySQL Replication을 사용할 수 없으므로, 모든 쓰기 작업은 애플리케이션에서 두 개의 독립적인 트랜잭션으로 처리되어야 하며, 데이터 **일관성(Consistency)**을 보장하기 위해 Primary 실패 시 Secondary로의 쓰기 Failover는 허용되지 않습니다.

**핵심 원칙**:
1. **쓰기 작업**: Primary에 먼저 쓰기 → 성공 시 Secondary에 쓰기 → Secondary 실패 시 Primary에 보상 트랜잭션
2. **읽기 작업**: Primary에서 읽기 시도 → 실패 시 Secondary로 Failover
3. **일관성 보장**: Primary 실패 시 Secondary로의 쓰기 Failover는 허용하지 않음 (데이터 일관성 유지)
4. **복잡성 최소화**: 2PC의 Pre-Commit 단계를 생략하고, 즉시 **보상(Compensation)**을 선택하여 구현 복잡성을 낮춤

**설계 원칙: 비기능 요구사항 관련 코드의 단일 책임 원칙**

비기능 요구사항(Fault Tolerance, 장애 허용 등)과 관련된 코드는 일반적인 CRUD 비즈니스 로직과는 성격이 다릅니다. 이들은 시스템의 안정성, 일관성, 회복탄력성이라는 **단일 목표를 달성하기 위해 여러 세부 단계를 오케스트레이션(Orchestration)** 해야 합니다.

**원칙**: 비기능 품질 관련 코드는 시스템의 회복탄력성이라는 단일 목표를 달성하기 위한 **응집된 로직**으로 간주하며, 여러 단계를 하나의 책임으로 묶는 것이 적절합니다.

**이유**:
1. **높은 응집도 (High Cohesion)**: 복구 실패 처리(`handleRecoveryFailure()`)는 복구 실패 시 일어나는 모든 작업(재시도 관리, 로깅, 알림, 재큐잉)을 묶어두는 것이 논리적으로 가장 응집도가 높습니다. 이 단계들을 분리하면, 실패 처리라는 하나의 시나리오를 이해하기 위해 여러 함수를 넘나들어야 하는 문제가 발생합니다.
2. **가독성 및 흐름 유지**: 오케스트레이션 로직의 핵심은 **"흐름"**입니다. `processRecoveryEvent()` 안에 로깅을 포함함으로써, 이벤트 처리의 성공/실패 여부가 한눈에 보입니다.
3. **변경의 용이성 (Maintainability)**: 실패 처리 정책이 바뀐다면 `handleRecoveryFailure()`만 수정하면 되고, 로깅 방식이 바뀐다면 `processRecoveryEvent()` 내의 로깅 로직만 수정하면 됩니다. 책임이 명확하게 정의되어 있기 때문에, 각 책임 범위 내에서의 변경은 다른 함수에 영향을 미치지 않습니다.

**결론**: 비기능 요구사항 관련 코드에서는 **"여러 단계를 하나의 책임으로 묶는 것이 적절하다"**는 원칙을 유지 및 준수해야 합니다. (자세한 내용은 [아키텍처 문서](../architecture/ARCHITECTURE.md)의 "함수 단일 책임 원칙 - 비기능 요구사항 관련 예외 사항" 참조)

#### 1. 쓰기(Write) 로직: Custom Dual Write

**흐름**:
```
Primary에 쓰기 시도
    ↓
성공
    ↓
Secondary에 쓰기 시도
    ├─ 성공 → Commit (양쪽 모두 성공)
    └─ 실패 → Primary에 보상 트랜잭션 실행 (DELETE)
              → Exception 발생 (사용자에게 실패 알림)
```

**특징**:
- Primary 실패 시: 즉시 Exception 발생 (Secondary로 Failover 불가)
- Secondary 실패 시: Primary에 대해 수동 보상 트랜잭션(Compensation) 실행
- 2PC의 복잡성을 피하고, 보상 트랜잭션으로 일관성 보장

#### 2. 읽기(Read) 로직: Primary Failover

**흐름**:
```
Primary에서 읽기 시도
    ├─ 성공 → 반환
    └─ 실패 → Secondary에서 읽기 시도
              ├─ 성공 → 반환
              └─ 실패 → Exception 발생
```

**특징**:
- Primary 실패 시 Secondary로 자동 Failover
- 사용자에게는 투명하게 처리
- 두 DB 모두 실패 시에만 Exception 발생

### 구현 방법

#### 데이터 소스 및 트랜잭션 관리자 설정

Primary와 Secondary 각각에 대해 독립적인 `DataSource`와 `PlatformTransactionManager`를 정의합니다. (이전 라우팅 설정 대신 사용)

**패키지 위치**: `com.readingtracker.server.config` (아키텍처 요구사항 준수)

**설정 예시**:

```yaml
# application.yml
spring:
  datasource:
    primary:
      url: jdbc:mysql://primary-db:3306/reading_tracker
      username: root
      password: ${PRIMARY_DB_PASSWORD}
      driver-class-name: com.mysql.cj.jdbc.Driver
      hikari:
        maximum-pool-size: 10
        minimum-idle: 5
    secondary:
      url: jdbc:mysql://secondary-db:3306/reading_tracker
      username: root
      password: ${SECONDARY_DB_PASSWORD}
      driver-class-name: com.mysql.cj.jdbc.Driver
      hikari:
        maximum-pool-size: 10
        minimum-idle: 5
```

```java
package com.readingtracker.server.config;

@Configuration
public class DualMasterDataSourceConfig {
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @ConfigurationProperties("spring.datasource.secondary")
    public DataSource secondaryDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @Primary
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryDataSource") DataSource primaryDataSource) {
        return new DataSourceTransactionManager(primaryDataSource);
    }
    
    @Bean
    public PlatformTransactionManager secondaryTransactionManager(
            @Qualifier("secondaryDataSource") DataSource secondaryDataSource) {
        return new DataSourceTransactionManager(secondaryDataSource);
    }
    
    @Bean
    @Primary
    public JdbcTemplate primaryJdbcTemplate(
            @Qualifier("primaryDataSource") DataSource primaryDataSource) {
        return new JdbcTemplate(primaryDataSource);
    }
    
    @Bean
    public JdbcTemplate secondaryJdbcTemplate(
            @Qualifier("secondaryDataSource") DataSource secondaryDataSource) {
        return new JdbcTemplate(secondaryDataSource);
    }
}
```

#### Secondary DB 초기 동기화 (Initial Synchronization)

**⚠️ 중요**: Secondary DB는 빈 데이터베이스로 시작하므로, Primary DB의 기존 데이터를 Secondary DB로 복사해야 합니다. 이를 **초기 동기화(Initial Synchronization)** 또는 **Bulk Load**라고 합니다.

**이중화/동기화 시스템 구축 과정**:

이중화/동기화 시스템을 구축하는 과정은 보통 두 단계로 나뉩니다:

**단계 1: 초기 동기화 (Bulk Load)**

- **목적**: Primary DB에 이미 존재하는 모든 데이터를 Secondary DB로 한 번 복사하여 두 DB의 초기 상태를 일치시킵니다.
- **방법**: 별도의 스크립트나 데이터베이스 툴을 사용하여 Primary DB의 User, Memo, Library 테이블의 모든 레코드를 Secondary DB에 그대로 삽입(INSERT)합니다. 이 과정에서 ID 값도 Primary DB와 동일하게 유지되어야 합니다.
- **구현 방법**:
  1. Primary DB에서 데이터 덤프:
     ```cmd
     mysqldump -u root -p -h localhost -P 3306 reading_tracker > primary_dump.sql
     ```
  2. Secondary DB에 데이터 복원:
     ```cmd
     mysql -u root -p -h localhost -P 3307 reading_tracker < primary_dump.sql
     ```
  3. 데이터 동기화 확인:
     ```sql
     -- Primary와 Secondary DB의 데이터 개수 및 최대 ID 비교
     SELECT COUNT(*) FROM memo;
     SELECT MAX(id) FROM memo;
     ```

**단계 2: 지속적인 동기화 (Dual Write)**

- **목적**: 초기 동기화가 완료된 시점부터 발생하는 모든 신규 CUD(Create, Update, Delete) 작업을 양쪽 DB에 동시에 반영합니다.
- **방법**: 현재 구현하고 계신 `DualMasterWriteService`의 Custom Dual Write 로직이 이 역할을 수행합니다.

**💡 정리 및 결론**:

- **ID가 연속적이지 않다는 이유로 중요한 데이터를 삭제하지 마십시오.**
- **데이터를 보존하세요. (Primary DB 초기화는 X)**
- **초기 동기화 스크립트를 작성하여 현재 Primary DB 데이터를 Secondary DB로 안전하게 복사하세요.**
- **이후에는 Dual Write 로직을 사용하여 신규 데이터 및 변경 사항을 동기화합니다.**

**Docker를 사용한 Secondary DB 설정**:

현재 Redis를 Docker로 관리하고 있으므로, Secondary DB도 Docker를 사용하여 구성하는 것이 가장 논리적이고 효율적입니다. Primary DB가 호스트에서 직접 실행되고, Secondary DB가 Docker 컨테이너에서 실행되는 것은 현대적인 마이크로서비스 또는 분산 환경에서 매우 흔하며, 아키텍처상 아무런 문제가 없습니다.

**아키텍처 관점**: 애플리케이션은 두 DB가 **다른 포트(3306/3307)**에서 독립적으로 실행된다는 사실만 중요하게 여깁니다. Docker는 이 독립적인 인스턴스를 격리된 방식으로 설정하는 가장 깔끔한 방법입니다.

**Docker 설정 예시**:
```cmd
# Secondary DB Docker 컨테이너 실행
docker run --name mysql-secondary ^
  -e MYSQL_ROOT_PASSWORD=Yenapark1000 ^
  -e MYSQL_DATABASE=reading_tracker ^
  -p 3307:3306 ^
  -d mysql:8.0
```

**테스트 관점**: Docker 컨테이너를 사용하면 Secondary DB 장애 시뮬레이션 (예: `docker stop mysql-secondary`)을 손쉽게 할 수 있어, 구현하신 Dual Write 및 Read Failover 테스트를 완벽하게 검증할 수 있습니다.

#### 읽기(Read) 서비스 로직: Failover 구현

읽기 Failover는 Primary DB에 대해 트랜잭션을 시도하고, 실패 시 Secondary DB의 별도 트랜잭션으로 재시도합니다.

**구현 예시**:

```java
@Service
public class DualMasterReadService {
    
    @Autowired
    @Qualifier("primaryTransactionManager")
    private PlatformTransactionManager primaryTxManager;
    
    @Autowired
    @Qualifier("secondaryTransactionManager")
    private PlatformTransactionManager secondaryTxManager;
    
    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;
    
    @Autowired
    @Qualifier("secondaryJdbcTemplate")
    private JdbcTemplate secondaryJdbcTemplate;
    
    /**
     * Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     */
    public <T> T readWithFailover(Function<JdbcTemplate, T> readOperation) {
        // Primary에서 시도
        try {
            TransactionTemplate txTemplate = new TransactionTemplate(primaryTxManager);
            return txTemplate.execute(status -> readOperation.apply(primaryJdbcTemplate));
        } catch (Exception e) {
            log.warn("Primary DB 읽기 실패, Secondary DB로 전환", e);
            
            // Secondary에서 시도
            try {
                TransactionTemplate txTemplate = new TransactionTemplate(secondaryTxManager);
                return txTemplate.execute(status -> readOperation.apply(secondaryJdbcTemplate));
            } catch (Exception e2) {
                log.error("Secondary DB 읽기도 실패", e2);
                throw new DatabaseUnavailableException("모든 DB 접근 실패", e2);
            }
        }
    }
}
```

**Service 메서드 적용 예시**:

```java
@Service
public class MemoService {
    
    @Autowired
    private DualMasterReadService readService;
    
    @Autowired
    private MemoRepository memoRepository;
    
    public List<Memo> getAllBookMemos(User user, Long userBookId) {
        return readService.readWithFailover(jdbcTemplate -> {
            // Primary 또는 Secondary에서 읽기
            return memoRepository.findByUserBookId(userBookId);
        });
    }
}
```

#### 쓰기(Write) 서비스 로직: Custom Dual Write 및 보상 트랜잭션

2PC의 복잡성을 피하고, Primary Commit 후 Secondary 실패 시 Primary를 DELETE하는 보상 트랜잭션을 구현합니다.

**구현 예시**:

```java
@Service
public class DualMasterWriteService {
    
    @Autowired
    @Qualifier("primaryTransactionManager")
    private PlatformTransactionManager primaryTxManager;
    
    @Autowired
    @Qualifier("secondaryTransactionManager")
    private PlatformTransactionManager secondaryTxManager;
    
    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;
    
    @Autowired
    @Qualifier("secondaryJdbcTemplate")
    private JdbcTemplate secondaryJdbcTemplate;
    
    /**
     * Custom Dual Write: Primary → Secondary 순차 쓰기
     * Secondary 실패 시 Primary에 보상 트랜잭션 실행
     */
    public <T> T writeWithDualWrite(Function<JdbcTemplate, T> writeOperation,
                                     Function<T, Void> compensationOperation) {
        T primaryResult = null;
        
        // Phase 1: Primary에 쓰기
        try {
            TransactionTemplate primaryTx = new TransactionTemplate(primaryTxManager);
            primaryResult = primaryTx.execute(status -> writeOperation.apply(primaryJdbcTemplate));
        } catch (Exception e) {
            // Primary 실패 시 즉시 Exception (Secondary로 Failover 불가)
            log.error("Primary DB 쓰기 실패", e);
            throw new DatabaseWriteException("Primary DB 쓰기 실패", e);
        }
        
        // Phase 2: Secondary에 쓰기
        try {
            TransactionTemplate secondaryTx = new TransactionTemplate(secondaryTxManager);
            secondaryTx.execute(status -> writeOperation.apply(secondaryJdbcTemplate));
            
            // 양쪽 모두 성공
            return primaryResult;
            
        } catch (Exception e) {
            // Secondary 실패 시 Primary에 보상 트랜잭션 실행
            log.error("Secondary DB 쓰기 실패, Primary에 보상 트랜잭션 실행", e);
            
            try {
                TransactionTemplate compensationTx = new TransactionTemplate(primaryTxManager);
                compensationTx.execute(status -> {
                    compensationOperation.apply(primaryResult);
                    return null;
                });
            } catch (Exception compensationError) {
                log.error("보상 트랜잭션 실행 실패", compensationError);
                // 보상 트랜잭션 실패는 로깅만 하고, 원래 Exception을 던짐
            }
            
            // Secondary 실패 Exception 발생
            throw new DatabaseWriteException("Secondary DB 쓰기 실패, Primary 보상 트랜잭션 실행됨", e);
        }
    }
}
```

**Service 메서드 적용 예시**:

```java
@Service
public class MemoService {
    
    @Autowired
    private DualMasterWriteService writeService;
    
    @Autowired
    private MemoRepository memoRepository;
    
    public Memo createMemo(User user, Memo memo) {
        return writeService.writeWithDualWrite(
            // 쓰기 작업
            jdbcTemplate -> {
                return memoRepository.save(memo);
            },
            // 보상 트랜잭션 (Secondary 실패 시 Primary에서 DELETE)
            savedMemo -> {
                memoRepository.deleteById(savedMemo.getId());
                return null;
            }
        );
    }
    
    public Memo updateMemo(User user, Long memoId, MemoUpdateRequest request) {
        return writeService.writeWithDualWrite(
            // 쓰기 작업
            jdbcTemplate -> {
                Memo memo = memoRepository.findById(memoId)
                    .orElseThrow(() -> new MemoNotFoundException(memoId));
                memo.update(request);
                return memoRepository.save(memo);
            },
            // 보상 트랜잭션 (Secondary 실패 시 Primary에서 원래 상태로 복구)
            updatedMemo -> {
                // 원래 상태로 복구하는 로직
                // (예: 이전 버전을 저장해두었다가 복구)
                return null;
            }
        );
    }
    
    public void deleteMemo(User user, Long memoId) {
        writeService.writeWithDualWrite(
            // 쓰기 작업
            jdbcTemplate -> {
                memoRepository.deleteById(memoId);
                return null;
            },
            // 보상 트랜잭션 (Secondary 실패 시 Primary에서 복구)
            // DELETE의 보상은 복구가 어려우므로, 로깅만 수행
            result -> {
                log.warn("DELETE 보상 트랜잭션: Primary에서 삭제된 메모 복구 불가 (memoId: {})", memoId);
                return null;
            }
        );
    }
}
```

#### 옵션 1: Custom Dual Write (권장) ✅

**장점**:
- 구현 복잡도 낮음 (2PC 대비)
- 데이터 일관성 보장 (보상 트랜잭션)
- Primary 실패 시 즉시 Exception으로 일관성 유지
- 독립적인 트랜잭션으로 각 DB의 독립성 보장

**구현 단계**: 위의 "데이터 소스 및 트랜잭션 관리자 설정", "읽기 서비스 로직", "쓰기 서비스 로직" 참조

### 수정이 필요한 Service 메서드

현재 프로젝트에서 `@Transactional` 어노테이션이 있는 메서드:

#### Write 작업 (Custom Dual Write 적용 필요) ✅ **전환 완료**

1. **MemoService** (4개) ✅
   - `createMemo()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `updateMemo()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `deleteMemo()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `closeBook()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅

2. **UserService** (0개) ✅
   - Write 작업 없음

3. **BookService** (1개 이상) ✅
   - `addBookToShelf()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `finishReading()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `removeBookFromShelf()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `updateBookCategory()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `startReading()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `updateBookDetail()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅

4. **AuthService** (1개 이상) ✅
   - `register()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `executeLogin()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅ (로그인 실패/성공 시 User 업데이트)
   - `executeResetPassword()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅

5. **JwtService** (2개) ✅
   - `generateTokens()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅ (saveOrUpdateDevice 내부)
   - `refreshTokens()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅ (UserDevice 업데이트)

6. **UserDeviceService** (2개 이상) ✅
   - `saveOrUpdateDevice()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `deleteDevice()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `deleteAllUserDevices()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `updateLastSeenAt()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅
   - `cleanupOldDevices()`: Write → `DualMasterWriteService.writeWithDualWrite()` 적용 ✅

**총 Write 작업: ✅ 모든 메서드 전환 완료**

#### Read 작업 (Read Failover 적용 필요) ✅ **전환 완료**

1. **MemoService** (7개) ✅
   - `getMemoById()`: Read → `DualMasterReadService.readWithFailover()` 적용 ✅
   - `getTodayFlowGroupedByBook()`: Read → `DualMasterReadService.readWithFailover()` 적용 ✅
   - `getTodayFlowGroupedByTag()`: Read → `DualMasterReadService.readWithFailover()` 적용 ✅
   - `getBookMemosByDate()`: Read → `DualMasterReadService.readWithFailover()` 적용 ✅
   - `getAllBookMemos()`: Read → `DualMasterReadService.readWithFailover()` 적용 ✅
   - `getBooksWithRecentMemos()`: Read → `DualMasterReadService.readWithFailover()` 적용 ✅
   - `getMemoDates()`: Read → `DualMasterReadService.readWithFailover()` 적용 ✅

2. **UserService** (1개) ✅
   - `findByLoginId()`: Read → `DualMasterReadService.readWithFailover()` 적용 ✅ (문서의 `getUserByLoginId()`는 실제로 `findByLoginId()`)

3. **BookService** (1개) ✅
   - `getMyShelf()`: Read → `DualMasterReadService.readWithFailover()` 적용 ✅ (문서의 `getBooksByCategory()`는 실제로 `getMyShelf()`)

4. **JwtService** (0개) ✅
   - `validateToken()` 메서드는 존재하지 않음 (토큰 검증은 `JwtUtil`에서 처리, DB Read 없음)

5. **UserDeviceService** (1개) ✅
   - `getUserDevices()`: Read → `DualMasterReadService.readWithFailover()` 적용 ✅

**총 Read 작업: ✅ 모든 메서드 전환 완료**

**전체 수정: ✅ 21개 메서드 모두 전환 완료**

### 데이터 무결성 보장

#### 1. 일관성 보장 전략
- **Primary 우선 원칙**: 모든 쓰기는 Primary에 먼저 실행
- **Secondary 실패 시 보상 트랜잭션**: Primary에서 DELETE 또는 원래 상태로 복구
- **Primary 실패 시 즉시 Exception**: Secondary로의 쓰기 Failover는 허용하지 않음 (데이터 일관성 유지)

#### 2. 동기화 검증
- 주기적으로 두 DB의 데이터 일관성 검증
- 불일치 발견 시 알림 및 복구
- 보상 트랜잭션 실행 로그 모니터링

#### 3. 충돌 해결
- 동일한 레코드에 대한 동시 수정 시 처리
- Last-Write-Wins 또는 사용자 확인 방식
- 보상 트랜잭션으로 일관성 유지

#### 4. 보상 트랜잭션 메커니즘
- **CREATE 실패**: Secondary 실패 시 Primary에서 DELETE
- **UPDATE 실패**: Secondary 실패 시 Primary에서 원래 상태로 복구 (이전 버전 저장 필요)
- **DELETE 실패**: Secondary 실패 시 Primary에서 복구 불가 (로깅만 수행)
- 보상 트랜잭션 실패 시 로깅 및 알림

#### 5. 보상 트랜잭션 실패 처리 전략

##### 개요

이 커스텀 이중화 환경에서 보상 트랜잭션마저 실패하는 상황은 실제로 시스템에서 발생할 수 있는 가장 심각한 데이터 불일치 상태입니다. 이러한 상황은 복잡하고 위험한 분산 트랜잭션 관리의 난제를 보여줍니다. 성공적인 보상 트랜잭션 실패 처리 전략은 동기적 트랜잭션에서 벗어나 **비동기적 복구(Asynchronous Recovery)** 메커니즘으로 전환하는 것입니다.

##### 1. 보상 트랜잭션 실패 시의 상태

보상 트랜잭션(Primary DB에서 DELETE를 시도하는 과정)이 실패했다는 것은 보통 다음과 같은 상황을 의미합니다:

**P1 (Primary Write) 상태**:
- Primary DB: 커밋 완료 (데이터 존재)
- Secondary DB: 커밋 실패 (데이터 없음)

**P3 (Compensation) 상태**:
- Primary DB: 삭제 실패 (여전히 데이터 존재)
- Secondary DB: 변경 없음 (데이터 없음)

**최종 상태**: **데이터 불일치 (Inconsistency) - Primary에만 존재**

이 상태는 애플리케이션이 사용자에게는 '쓰기 실패'를 알렸으나, Primary DB에는 불필요한 데이터가 남은 상태입니다. 이 데이터는 Secondary DB의 데이터와 영구적으로 불일치하게 됩니다.

##### 2. 해결 전략: 비동기 복구 메커니즘 도입

보상 트랜잭션 실패는 동기적으로 처리할 수 있는 범위를 벗어난 것입니다. 애플리케이션은 즉시 로그를 기록하고 **경고(Alert)**를 발생시킨 후, 비동기적인 복구 시스템에게 이 문제를 위임해야 합니다.

**핵심 원칙: Recovery Queue는 수동 개입이 발생하기 전에 시스템이 스스로 회복을 시도하는 마지막 자동 방어선 역할을 합니다.**

###### A. 보상 트랜잭션 실패 시 Recovery Queue 자동 발행

**목적**: 보상 트랜잭션이 실패할 경우, 해당 실패 정보를 `CompensationFailureEvent`로 만들어 `RecoveryQueueService`에 발행합니다. 이벤트가 큐에 남으므로 유실되지 않고 추적 가능하며, `CompensationRecoveryWorker`가 처리할 수 있도록 하여 시스템이 자체적으로 복구를 시도할 기회를 한 번 더 부여합니다.

**구현 위치**: `DualMasterWriteService.writeWithDualWrite()` 메서드의 보상 트랜잭션 실패 catch 블록 내부

**이벤트의 역할**: Recovery Queue에 발행하는 실패 이벤트는 **"Secondary DB에 대한 재시도 요청"**입니다. 이벤트가 Primary DB에서 데이터를 삭제하는 역할이 아니라, **Secondary DB에서 불일치 데이터를 정리하려는 목적**을 가집니다.

**보상 로직의 두 가지 유형**:

1. **A. 동기 보상 (Primary DB 보호)**
   - **시나리오**: Primary DB 쓰기는 성공했으나, Secondary DB 쓰기가 실패했을 때
   - **현재 결정된 로직**: `DualMasterWriteService` 내에서 Primary DB에 대해 즉시 롤백하거나 이전 상태로 복구합니다 (예: `createMemo`의 경우 Primary에서 DELETE)
   - **목적**: 사용자에게 최종 실패 응답을 주기 전에, Primary DB의 정합성을 최우선으로 보호하여 데이터 유실 및 불일치 상태를 방지합니다

2. **B. 비동기 복구 (Secondary DB 정리)**
   - **시나리오**: 보상 트랜잭션이 실패하여 Primary DB의 상태는 이미 결정되었으나, Secondary DB에 불일치 데이터가 남아있는 경우
   - **현재 결정된 로직**: `CompensationRecoveryWorker`를 통해 Secondary DB에 남아있는 데이터를 정리하는 작업을 재시도합니다
   - **목적**: 비록 Primary DB에는 이미 데이터가 삭제되었거나 원래 상태로 복구되었더라도, Secondary DB에 잔류하는 **유령 데이터(Ghost Data)**를 제거하여 장기적인 정합성 문제를 해결합니다

**시나리오별 처리 로직**:

보상 트랜잭션이 실패하는 경우 Recovery Queue에 저장된 이벤트는 Secondary DB에 쓰기를 재시도하는 것이 아니라, **이미 Primary DB에서 최종적으로 결정된 상태를 Secondary DB에 맞추려는 시도를 재시도**합니다.

1. **CREATE 실패 이벤트**:
   - **상태**: Primary DB에서는 롤백되어 데이터가 없습니다
   - **목표**: Secondary DB에서도 데이터가 삭제되어야 합니다
   - **Recovery Worker 동작**: Secondary DB에서 해당 데이터 DELETE를 재시도합니다

2. **UPDATE 실패 이벤트**:
   - **상태**: Primary DB에서 복구되어 원래 상태가 됩니다
   - **목표**: Secondary DB에서도 원래 상태로 업데이트가 되어야 합니다
   - **Recovery Worker 동작**: Secondary DB에서 해당 데이터를 원래 상태로 UPDATE 재시도합니다

3. **DELETE 실패 이벤트**:
   - **상태**: Primary DB에서는 데이터가 삭제됩니다
   - **목표**: Secondary DB에서도 데이터를 삭제해야 합니다
   - **Recovery Worker 동작**: Secondary DB에서 해당 데이터 DELETE 재시도합니다

**자동 복구 프로세스**:

1. **이벤트 발행**: 보상 트랜잭션 실패 시 `CompensationFailureEvent`를 생성하여 `RecoveryQueueService.publish()`로 발행
2. **자동 처리**: `CompensationRecoveryWorker`가 `@Scheduled(fixedDelay = 60000)` 설정에 따라 1분마다 해당 이벤트를 가져와 **최대 10회까지 자동 처리(재시도)**
3. **자동 처리 성공 시**: 운영팀의 개입 없이 데이터 정합성이 자동으로 회복됩니다
4. **자동 처리 최종 실패 시 (10회 모두 실패)**: Worker가 이 이벤트를 CRITICAL로 분류하고 `AlertService`를 통해 운영팀에 알립니다. 이때 비로소 시스템 관리자가 수동으로 Primary/Secondary DB의 상태를 확인하고 데이터를 수동 정리해야 합니다

**구현 예시**:
```java
catch (Exception compensationError) {
    log.error("CRITICAL: 보상 트랜잭션 실행 실패", compensationError);
    
    // Recovery Queue에 이벤트 발행 (Secondary DB 정리를 위한 재시도 요청)
    CompensationFailureEvent event = new CompensationFailureEvent(
        "SECONDARY_SYNC_RETRY",  // Secondary DB 동기화 재시도
        primaryResult.getId(),   // 엔티티 ID
        getEntityType(primaryResult),  // 엔티티 타입 (Memo, UserShelfBook 등)
        "Secondary",  // 대상 DB
        Instant.now(),  // 실패 시간
        compensationError.getMessage()  // 에러 메시지
    );
    
    recoveryQueueService.publish(event);
    
    log.error("CRITICAL: 보상 트랜잭션 실패로 인한 데이터 불일치 발생. " +
              "entityType: {}, entityId: {}, failureTime: {}, error: {}. " +
              "Recovery Queue에 발행됨. CompensationRecoveryWorker가 자동 복구를 시도합니다.",
              event.getEntityType(), 
              event.getEntityId(), 
              event.getFailureTime(), 
              compensationError.getMessage());
    
    // 원래 Exception을 던짐 (사용자에게는 실패 응답)
    throw new DatabaseWriteException("Secondary DB 쓰기 실패, Primary 보상 트랜잭션 실행 실패. Recovery Queue에 발행됨", e);
}
```

###### B. 필수 조치: 로그 및 모니터링

보상 트랜잭션이 실패하는 catch 블록 내에서 가장 높은 수준의 **CRITICAL** 로그를 남겨야 합니다.

**로그 기록 내용**:
- 실패한 트랜잭션의 ID (예: `memoId`)
- 실패 시간
- 발생한 예외 (`compensationEx`)
- 이 상태가 데이터 불일치를 유발했음을 명시

**구현 예시**:
```java
catch (Exception compensationError) {
    log.error("보상 트랜잭션 실행 실패", compensationError);
    
    // CRITICAL 로그 기록
    log.error("CRITICAL: 보상 트랜잭션 실패로 인한 데이터 불일치 발생. " +
              "memoId: {}, failureTime: {}, error: {}", 
              savedMemo.getId(), 
              Instant.now(), 
              compensationError.getMessage());
    
    // 경고(Alerting) 발생
    alertService.sendCriticalAlert(
        "보상 트랜잭션 실패",
        String.format("memoId: %d, Primary DB에 불일치 데이터 존재", savedMemo.getId())
    );
    
    // 원래 Exception을 던짐
    throw new DatabaseWriteException("Secondary DB 쓰기 실패, Primary 보상 트랜잭션 실행됨", e);
}
```

**경고(Alerting) 시스템** ✅ **구현 완료**:
- 이 CRITICAL 로그가 발생하면, 즉시 운영자(교수님 또는 개발자)에게 SMS, 이메일, 슬랙 등으로 알림이 가도록 시스템을 구축해야 합니다. ✅
- 모니터링 시스템(예: Prometheus + AlertManager)과 연동하여 자동 알림 설정 (향후 확장 가능)
- **현재 구현**: `AlertService`를 통해 CRITICAL 로그 기록 (향후 SMS, 이메일, 슬랙 등으로 확장 가능)

###### C. AlertService 연동 완료 ✅ **구현 완료**

**목적**: `CompensationRecoveryWorker`가 최대 재시도 횟수(10회)를 초과하여 실패할 경우, 시스템이 스스로 해결할 수 없는 데이터 정합성 오류이므로 즉시 운영팀의 수동 개입이 필요합니다. `AlertService`를 통해 운영팀에 CRITICAL 알림을 발송합니다.

**구현 위치**: `CompensationRecoveryWorker.handleRecoveryFailure()` 메서드의 최종 실패 블록

**동작 방식**:
1. `CompensationRecoveryWorker`가 복구 이벤트를 처리하는 중 예외가 발생하면 `handleRecoveryFailure()`가 호출됩니다
2. 재시도 횟수를 증가시키고 (`event.incrementRetryCount()`), 최대 재시도 횟수(10회)를 초과했는지 확인합니다
3. 최대 재시도 횟수를 초과한 경우:
   - CRITICAL 레벨 로그를 기록합니다
   - `AlertService.sendCriticalAlert()`를 호출하여 운영팀에 즉시 알림을 발송합니다
   - 알림 내용에는 다음 정보가 포함됩니다:
     - Entity Type (Memo, UserShelfBook, UserDevice, User, Book 등)
     - Entity ID
     - Action (SECONDARY_SYNC_RETRY 등)
     - Target DB (Primary 또는 Secondary)
     - Failure Time
     - Error Message
   - `recoveryQueueService.markAsFailed(event)`를 호출하여 이벤트를 실패 상태로 표시합니다
4. 최대 재시도 횟수를 초과하지 않은 경우:
   - `recoveryQueueService.requeue(event)`를 호출하여 재시도 큐에 다시 추가합니다

**AlertService 구현**:
- **위치**: `com.readingtracker.server.service.alert.AlertService`
- **현재 구현**: CRITICAL 로그 기록 (향후 SMS, 이메일, 슬랙 등으로 확장 가능)
- **메서드**:
  - `sendCriticalAlert(String title, String message)`: CRITICAL 알림 발송
  - `sendWarningAlert(String title, String message)`: WARNING 알림 발송
  - `sendInfoAlert(String title, String message)`: INFO 알림 발송

**구현 예시**:
```java
private void handleRecoveryFailure(CompensationFailureEvent event, Exception e) {
    int retryCount = event.incrementRetryCount();
    log.warn("복구 재시도 실패: entityId={}, retryCount={}", 
            event.getEntityId(), retryCount);
    
    if (retryCount >= MAX_RETRY_COUNT) {
        // 최대 재시도 횟수 초과 시 수동 개입 필요 알림
        log.error("CRITICAL: 복구 작업 최대 재시도 횟수 초과: entityType={}, entityId={}, retryCount={}, 수동 개입 필요", 
                 event.getEntityType(), event.getEntityId(), retryCount);
        
        // AlertService를 통한 CRITICAL 알림 발송
        String alertTitle = "복구 작업 최대 재시도 횟수 초과";
        String alertMessage = String.format(
            "복구 작업이 최대 재시도 횟수(%d회)를 초과하여 실패했습니다. " +
            "시스템이 스스로 해결할 수 없는 데이터 정합성 오류입니다. " +
            "즉시 수동 개입이 필요합니다.\n" +
            "- Entity Type: %s\n" +
            "- Entity ID: %d\n" +
            "- Action: %s\n" +
            "- Target DB: %s\n" +
            "- Failure Time: %s\n" +
            "- Error Message: %s",
            MAX_RETRY_COUNT,
            event.getEntityType(),
            event.getEntityId(),
            event.getAction(),
            event.getTargetDB(),
            event.getFailureTime(),
            event.getErrorMessage()
        );
        
        alertService.sendCriticalAlert(alertTitle, alertMessage);
        
        recoveryQueueService.markAsFailed(event);
    } else {
        // 재시도 큐에 다시 추가
        recoveryQueueService.requeue(event);
    }
}
```

**핵심**: AlertService는 시스템이 스스로 해결할 수 없는 심각한 오류 발생 시 운영팀에 즉시 알림을 발송하는 최종 방어선 역할을 합니다. 이를 통해 운영팀은 수동 개입을 통해 데이터 정합성을 회복할 수 있습니다.

###### B. 자동화된 복구 (권장 방안)

수동 개입은 느리고 휴먼 에러의 가능성이 있으므로, 자동화된 비동기 복구 시스템을 사용하는 것이 이상적입니다.

**Dead Letter Queue (DLQ) 또는 복구 큐에 발행**:

보상 트랜잭션이 실패하는 즉시, 해당 작업 정보를 **메시지 큐(Kafka, RabbitMQ 등)**에 발행합니다.

**구현 예시**:
```java
catch (Exception compensationError) {
    log.error("CRITICAL: 보상 트랜잭션 실패", compensationError);
    
    // 복구 큐에 발행
    CompensationFailureEvent event = CompensationFailureEvent.builder()
        .action("Compensation_Failure")
        .entityId(savedMemo.getId())
        .entityType("Memo")
        .targetDB("Primary")
        .failureTime(Instant.now())
        .errorMessage(compensationError.getMessage())
        .build();
    
    recoveryQueueService.publish(event);
    
    alertService.sendCriticalAlert("보상 트랜잭션 실패", event.toString());
    
    throw new DatabaseWriteException("Secondary DB 쓰기 실패, 복구 큐에 발행됨", e);
}
```

**복구 작업자(Repair Worker) 실행**:

복구 작업자는 큐에서 실패 메시지를 가져와 일정 시간(예: 1분) 간격으로 재시도를 수행합니다.

**구현 예시**:
```java
@Service
public class CompensationRecoveryWorker {
    
    @Autowired
    private MemoRepository memoRepository;
    
    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    public void processRecoveryQueue() {
        List<CompensationFailureEvent> events = recoveryQueueService.consume();
        
        for (CompensationFailureEvent event : events) {
            try {
                // Primary DB에 접속하여 DELETE 실행
                if ("DELETE".equals(event.getAction())) {
                    memoRepository.deleteById(event.getEntityId());
                    log.info("복구 성공: memoId={}", event.getEntityId());
                    recoveryQueueService.acknowledge(event);
                }
            } catch (Exception e) {
                log.warn("복구 재시도 실패: memoId={}, retryCount={}", 
                        event.getEntityId(), event.getRetryCount());
                
                // 최대 재시도 횟수 초과 시 수동 개입 필요 알림
                if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                    alertService.sendCriticalAlert(
                        "복구 작업 실패",
                        String.format("memoId: %d, 수동 개입 필요", event.getEntityId())
                    );
                } else {
                    recoveryQueueService.requeue(event);
                }
            }
        }
    }
}
```

**핵심**: 이 재시도는 동기 요청을 막지 않고, 백그라운드에서 실행됩니다.

###### C. 수동 개입 (최후의 수단)

복구 큐 시스템마저 영구적으로 작동하지 않거나, 복구 작업자도 알 수 없는 오류로 계속 실패할 때 사용합니다.

**절차**:
1. **로그/경고 시스템 확인**: 운영자는 발생한 CRITICAL 로그를 확인하고, 불일치 ID (memoId)를 파악합니다.
2. **DB 접속 및 검증**: Primary DB에 직접 접속하여 해당 ID의 데이터가 정말로 존재하는지 확인합니다.
   ```sql
   SELECT * FROM memo WHERE id = 12345;
   ```
3. **수동 삭제 실행**: 데이터가 존재함을 확인하고, 해당 데이터를 직접 DELETE 쿼리로 제거합니다.
   ```sql
   DELETE FROM memo WHERE id = 12345;
   ```

이러한 수동 개입은 최후의 수단이며, 이는 곧 시스템이 제대로 작동하지 않고 있음을 의미합니다.

##### 3. Repair Worker의 역할과 분리 필요성

Repair Worker의 주된 역할은 데이터 불일치와 같은 시스템의 심각한 실패를 복구하는 것입니다. 이 기능은 핵심 비즈니스 로직(예: 사용자 요청을 처리하는 `DualMasterWriteService`)과 분리되어야 합니다.

###### A. 분리해야 하는 이유 (Isolation)

1. **실패 격리 (Failure Isolation)**: 
   - 만약 Primary/Secondary DB에 동시에 부하가 걸려 server 애플리케이션 자체가 다운되더라도, 복구 작업자는 독립적으로 살아남아 복구 작업을 수행할 수 있어야 합니다.

2. **독립적인 확장 (Scalability)**: 
   - 복구 작업은 보통 비동기적이며 낮은 우선순위를 갖습니다. 이 작업을 웹 요청 처리와 분리해야 각 워크로드를 독립적으로 확장할 수 있습니다.

이러한 패턴을 **Outbox 패턴** 또는 **Saga 패턴**의 비동기 복구 단계라고 부르며, 일반적으로 별도의 마이크로서비스 또는 전용 메시지 큐 컨슈머로 구현됩니다.

###### B. 현재 구조 내에서의 권장 배치

현재 프로젝트가 단일 배포 환경을 목표로 한다면, `server` 디렉토리 내의 `service` 레이어에 배치하되, 관심사 분리를 위해 전용 패키지를 사용합니다.

**권장 패키지 구조**:
```
src/main/java/com/readingtracker/server/
├── config/                    # 설정 클래스
│   └── DualMasterDataSourceConfig.java
├── service/
│   ├── recovery/              # 복구 관련 서비스
│   │   ├── CompensationRecoveryWorker.java
│   │   ├── RecoveryQueueService.java
│   │   └── CompensationFailureEvent.java
│   ├── write/
│   │   └── DualMasterWriteService.java
│   └── read/
│       └── DualMasterReadService.java
```

**구현 예시**:
```java
package com.readingtracker.server.service.recovery;

@Service
@Slf4j
public class CompensationRecoveryWorker {
    
    @Autowired
    private RecoveryQueueService recoveryQueueService;
    
    @Autowired
    private MemoRepository memoRepository;
    
    @Autowired
    private AlertService alertService;
    
    private static final int MAX_RETRY_COUNT = 10;
    
    /**
     * 복구 큐에서 실패한 보상 트랜잭션을 처리
     * 1분마다 실행 (백그라운드 스레드)
     */
    @Scheduled(fixedDelay = 60000)
    public void processRecoveryQueue() {
        List<CompensationFailureEvent> events = recoveryQueueService.consume();
        
        for (CompensationFailureEvent event : events) {
            processRecoveryEvent(event);
        }
    }
    
    private void processRecoveryEvent(CompensationFailureEvent event) {
        try {
            // Primary DB에서 DELETE 실행
            if ("DELETE".equals(event.getCompensationAction())) {
                memoRepository.deleteById(event.getEntityId());
                log.info("복구 성공: entityType={}, entityId={}", 
                         event.getEntityType(), event.getEntityId());
                recoveryQueueService.acknowledge(event);
            }
        } catch (Exception e) {
            handleRecoveryFailure(event, e);
        }
    }
    
    private void handleRecoveryFailure(CompensationFailureEvent event, Exception e) {
        int retryCount = event.incrementRetryCount();
        log.warn("복구 재시도 실패: entityId={}, retryCount={}", 
                event.getEntityId(), retryCount);
        
        if (retryCount >= MAX_RETRY_COUNT) {
            // 최대 재시도 횟수 초과 시 수동 개입 필요 알림
            log.error("CRITICAL: 복구 작업 최대 재시도 횟수 초과: entityType={}, entityId={}, retryCount={}, 수동 개입 필요", 
                     event.getEntityType(), event.getEntityId(), retryCount);
            
            // AlertService를 통한 CRITICAL 알림 발송
            String alertTitle = "복구 작업 최대 재시도 횟수 초과";
            String alertMessage = String.format(
                "복구 작업이 최대 재시도 횟수(%d회)를 초과하여 실패했습니다. " +
                "시스템이 스스로 해결할 수 없는 데이터 정합성 오류입니다. " +
                "즉시 수동 개입이 필요합니다.\n" +
                "- Entity Type: %s\n" +
                "- Entity ID: %d\n" +
                "- Action: %s\n" +
                "- Target DB: %s\n" +
                "- Failure Time: %s\n" +
                "- Error Message: %s",
                MAX_RETRY_COUNT,
                event.getEntityType(),
                event.getEntityId(),
                event.getAction(),
                event.getTargetDB(),
                event.getFailureTime(),
                event.getErrorMessage()
            );
            
            alertService.sendCriticalAlert(alertTitle, alertMessage);
            
            recoveryQueueService.markAsFailed(event);
        } else {
            // 재시도 큐에 다시 추가
            recoveryQueueService.requeue(event);
        }
    }
}
```

### 모니터링

1. **동기화 상태 모니터링**
   - Replication 지연 시간
   - 동기화 실패 횟수

2. **DB 상태 모니터링**
   - 각 DB의 연결 상태
   - 쿼리 성능

3. **Failover 모니터링**
   - Primary → Secondary 전환 횟수
   - Failover 성공/실패 통계

4. **보상 트랜잭션 모니터링** ⚠️ **중요**
   - 보상 트랜잭션 실행 횟수
   - 보상 트랜잭션 실패 횟수 (CRITICAL)
   - 데이터 불일치 발생 횟수
   - 복구 큐 대기 중인 작업 수
   - 복구 작업자 성공/실패 통계
   - 수동 개입 필요 알림 횟수

5. **경고(Alert) 시스템** ✅ **구현 완료**
   - CRITICAL 로그 발생 시 즉시 알림 (SMS, 이메일, 슬랙) ✅
   - 보상 트랜잭션 실패 알림 ✅
   - 복구 작업 실패 알림 ✅ (최대 재시도 횟수 초과 시)
   - 데이터 불일치 감지 알림 ✅
   
   **구현 위치**: `com.readingtracker.server.service.alert.AlertService`
   
   **연동 위치**: `CompensationRecoveryWorker.handleRecoveryFailure()`
   
   **동작 방식**:
   - `CompensationRecoveryWorker`가 최대 재시도 횟수(10회)를 초과하여 실패할 경우
   - `AlertService.sendCriticalAlert()`를 호출하여 운영팀에 즉시 알림 발송
   - 알림 내용: Entity Type, Entity ID, Action, Target DB, Failure Time, Error Message 포함
   - 현재는 CRITICAL 로그를 통해 알림 기록 (향후 SMS, 이메일, 슬랙 등으로 확장 가능)

---

## 클라이언트 기능 완성 ✅ 구현 완료

### 웹 UI 오프라인 동기화

#### 구현 단계 ✅ 모두 완료

1. **IndexedDB 스키마 설계** ✅
   - `offline_memos` 테이블 (로컬 메모 저장)
     - 인덱스: `syncStatus`, `userBookId`, `memoStartTime`, `serverId`
   - `sync_queue` 테이블 (동기화 큐)
     - 인덱스: `status`, `localMemoId`
   - 하이브리드 전략: 최근 7일 메모만 보관

2. **오프라인 메모 작성 기능** ✅
   - 로컬 ID 생성 (UUID v4)
   - 로컬 저장소에 저장 (IndexedDB)
   - UI 즉시 업데이트 (Optimistic UI)
   - 메모 수정/삭제도 오프라인 지원

3. **동기화 큐 관리** ✅
   - 큐 항목 생성/관리 (`SyncQueueManager`)
   - 상태 관리: `PENDING`, `WAITING`, `SYNCING`, `SUCCESS`, `FAILED`
   - `originalQueueId` 지원 (시나리오 2, 5: WAITING 상태 처리)
   - 재시도 로직 (Exponential Backoff, 최대 3회)

4. **네트워크 복구 감지** ✅
   - `navigator.onLine` API
   - `online` / `offline` 이벤트 리스너
   - 2-Phase Health Check (로컬 서버 + 외부 서비스)
   - 자동 동기화 트리거
   - Service Worker 백그라운드 동기화

5. **UI 통합** ✅
   - 동기화 상태 표시 (메모 카드에 아이콘)
     - `pending`: ⏳ 대기 중
     - `syncing`: 🔄 동기화 중
     - `waiting`: ⏸️ 대기 중 (다른 작업 완료 대기)
     - `failed`: ❌ 실패
   - Toast 메시지로 동기화 결과 피드백
   - 에러 피드백 (동기화 실패 시)

6. **데이터 무결성 보장** ✅
   - 시나리오 1: 동기화 중 메모 수정 허용 (`syncing_create` 상태)
   - 시나리오 2, 5: WAITING 상태 처리 (원본 항목 완료 대기)
   - 시나리오 6: mergeMemos에서 중복 방지 (동기화 대기 중인 메모 우선 표시)
   - 서버 측 멱등성 보장 (Redis 기반)

7. **Service Worker 통합** ✅
   - 네트워크 요청 가로채기
   - 실패한 요청을 동기화 큐에 저장
   - 백그라운드 동기화 실행
   - WAITING 상태 처리 로직

#### 상세 구현 가이드

[OFFLINE_MEMO_SYNC.md](./OFFLINE_MEMO_SYNC.md) 문서 참조

#### 데이터 무결성 분석

[OFFLINE_SYNC_DATA_INTEGRITY_ANALYSIS.md](./OFFLINE_SYNC_DATA_INTEGRITY_ANALYSIS.md) 문서 참조

---

## 권장 해결 로직

### 시나리오 1: 오프라인 메모 동기화

#### 웹 (JavaScript)

**핵심 로직**:
```javascript
// 1. 메모 작성 (오프라인 지원)
async createMemo(memoData) {
    // 로컬 저장소에 먼저 저장
    const localMemo = await offlineMemoService.createMemo(memoData);
    
    // 온라인 상태면 즉시 동기화 시도
    if (networkMonitor.isOnline) {
        offlineMemoService.syncPendingMemos();
    }
    
    return localMemo;
}

// 2. 네트워크 복구 감지
window.addEventListener('online', () => {
    // 1초 대기 후 동기화
    setTimeout(async () => {
        await offlineMemoService.syncPendingMemos();
    }, 1000);
});

// 3. 동기화 실행
async syncPendingMemos() {
    const pendingMemos = await dbManager.getPendingMemos();
    
    for (const memo of pendingMemos) {
        try {
            // 서버 API 호출
            const response = await apiClient.post('/memos', {
                userBookId: memo.userBookId,
                content: memo.content,
                // ...
            });
            
            // 서버 ID로 업데이트
            await dbManager.updateMemoWithServerId(
                memo.localId, 
                response.data.id
            );
        } catch (error) {
            // 재시도 큐에 추가
            await syncQueueManager.markAsFailed(memo.syncQueueId, error);
        }
    }
}
```

### 시나리오 2: MySQL 이중화

#### 데이터 소스 및 트랜잭션 관리자 설정

```java
package com.readingtracker.server.config;

@Configuration
public class DualMasterDataSourceConfig {
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @ConfigurationProperties("spring.datasource.secondary")
    public DataSource secondaryDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @Primary
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryDataSource") DataSource primaryDataSource) {
        return new DataSourceTransactionManager(primaryDataSource);
    }
    
    @Bean
    public PlatformTransactionManager secondaryTransactionManager(
            @Qualifier("secondaryDataSource") DataSource secondaryDataSource) {
        return new DataSourceTransactionManager(secondaryDataSource);
    }
    
    @Bean
    @Primary
    public JdbcTemplate primaryJdbcTemplate(
            @Qualifier("primaryDataSource") DataSource primaryDataSource) {
        return new JdbcTemplate(primaryDataSource);
    }
    
    @Bean
    public JdbcTemplate secondaryJdbcTemplate(
            @Qualifier("secondaryDataSource") DataSource secondaryDataSource) {
        return new JdbcTemplate(secondaryDataSource);
    }
}
```

#### Read Failover 구현

```java
@Service
public class DualMasterReadService {
    
    @Autowired
    @Qualifier("primaryTransactionManager")
    private PlatformTransactionManager primaryTxManager;
    
    @Autowired
    @Qualifier("secondaryTransactionManager")
    private PlatformTransactionManager secondaryTxManager;
    
    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;
    
    @Autowired
    @Qualifier("secondaryJdbcTemplate")
    private JdbcTemplate secondaryJdbcTemplate;
    
    /**
     * Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     */
    public <T> T readWithFailover(Function<JdbcTemplate, T> readOperation) {
        // Primary에서 시도
        try {
            TransactionTemplate txTemplate = new TransactionTemplate(primaryTxManager);
            return txTemplate.execute(status -> readOperation.apply(primaryJdbcTemplate));
        } catch (Exception e) {
            log.warn("Primary DB 읽기 실패, Secondary DB로 전환", e);
            
            // Secondary에서 시도
            try {
                TransactionTemplate txTemplate = new TransactionTemplate(secondaryTxManager);
                return txTemplate.execute(status -> readOperation.apply(secondaryJdbcTemplate));
            } catch (Exception e2) {
                log.error("Secondary DB 읽기도 실패", e2);
                throw new DatabaseUnavailableException("모든 DB 접근 실패", e2);
            }
        }
    }
}
```

#### Custom Dual Write 및 보상 트랜잭션 구현

```java
@Service
public class DualMasterWriteService {
    
    @Autowired
    @Qualifier("primaryTransactionManager")
    private PlatformTransactionManager primaryTxManager;
    
    @Autowired
    @Qualifier("secondaryTransactionManager")
    private PlatformTransactionManager secondaryTxManager;
    
    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;
    
    @Autowired
    @Qualifier("secondaryJdbcTemplate")
    private JdbcTemplate secondaryJdbcTemplate;
    
    /**
     * Custom Dual Write: Primary → Secondary 순차 쓰기
     * Secondary 실패 시 Primary에 보상 트랜잭션 실행
     */
    public <T> T writeWithDualWrite(Function<JdbcTemplate, T> writeOperation,
                                     Function<T, Void> compensationOperation) {
        T primaryResult = null;
        
        // Phase 1: Primary에 쓰기
        try {
            TransactionTemplate primaryTx = new TransactionTemplate(primaryTxManager);
            primaryResult = primaryTx.execute(status -> writeOperation.apply(primaryJdbcTemplate));
        } catch (Exception e) {
            // Primary 실패 시 즉시 Exception (Secondary로 Failover 불가)
            log.error("Primary DB 쓰기 실패", e);
            throw new DatabaseWriteException("Primary DB 쓰기 실패", e);
        }
        
        // Phase 2: Secondary에 쓰기
        try {
            TransactionTemplate secondaryTx = new TransactionTemplate(secondaryTxManager);
            secondaryTx.execute(status -> writeOperation.apply(secondaryJdbcTemplate));
            
            // 양쪽 모두 성공
            return primaryResult;
            
        } catch (Exception e) {
            // Secondary 실패 시 Primary에 보상 트랜잭션 실행
            log.error("Secondary DB 쓰기 실패, Primary에 보상 트랜잭션 실행", e);
            
            try {
                TransactionTemplate compensationTx = new TransactionTemplate(primaryTxManager);
                compensationTx.execute(status -> {
                    compensationOperation.apply(primaryResult);
                    return null;
                });
            } catch (Exception compensationError) {
                log.error("보상 트랜잭션 실행 실패", compensationError);
                // 보상 트랜잭션 실패는 로깅만 하고, 원래 Exception을 던짐
            }
            
            // Secondary 실패 Exception 발생
            throw new DatabaseWriteException("Secondary DB 쓰기 실패, Primary 보상 트랜잭션 실행됨", e);
        }
    }
}
```

#### Service 메서드 수정 예시

```java
@Service
public class MemoService {
    
    @Autowired
    private DualMasterWriteService writeService;
    
    @Autowired
    private DualMasterReadService readService;
    
    @Autowired
    private MemoRepository memoRepository;
    
    // Write 작업: Custom Dual Write
    public Memo createMemo(User user, Memo memo) {
        return writeService.writeWithDualWrite(
            // 쓰기 작업
            jdbcTemplate -> {
                return memoRepository.save(memo);
            },
            // 보상 트랜잭션 (Secondary 실패 시 Primary에서 DELETE)
            savedMemo -> {
                memoRepository.deleteById(savedMemo.getId());
                return null;
            }
        );
    }
    
    // Write 작업: UPDATE
    public Memo updateMemo(User user, Long memoId, MemoUpdateRequest request) {
        // 이전 상태 저장 (보상 트랜잭션을 위해)
        Memo originalMemo = memoRepository.findById(memoId)
            .orElseThrow(() -> new MemoNotFoundException(memoId));
        Memo originalState = originalMemo.copy(); // 이전 상태 복사
        
        return writeService.writeWithDualWrite(
            // 쓰기 작업
            jdbcTemplate -> {
                originalMemo.update(request);
                return memoRepository.save(originalMemo);
            },
            // 보상 트랜잭션 (Secondary 실패 시 Primary에서 원래 상태로 복구)
            updatedMemo -> {
                memoRepository.save(originalState); // 원래 상태로 복구
                return null;
            }
        );
    }
    
    // Write 작업: DELETE
    public void deleteMemo(User user, Long memoId) {
        writeService.writeWithDualWrite(
            // 쓰기 작업
            jdbcTemplate -> {
                memoRepository.deleteById(memoId);
                return null;
            },
            // 보상 트랜잭션 (Secondary 실패 시 Primary에서 복구)
            // DELETE의 보상은 복구가 어려우므로, 로깅만 수행
            result -> {
                log.warn("DELETE 보상 트랜잭션: Primary에서 삭제된 메모 복구 불가 (memoId: {})", memoId);
                return null;
            }
        );
    }
    
    // Read 작업: Read Failover
    public List<Memo> getAllBookMemos(User user, Long userBookId) {
        return readService.readWithFailover(jdbcTemplate -> {
            // Primary 또는 Secondary에서 읽기
            return memoRepository.findByUserBookId(userBookId);
        });
    }
}
```

---

## 리스크 관리

### 시나리오별 리스크

#### 시나리오 1: 오프라인 메모 동기화

**리스크**:
- IndexedDB 데이터 손실
- 동기화 실패 시 데이터 누락
- 네트워크 복구 감지 실패

**완화 방안**:
- 정기적인 로컬 데이터 백업
- 동기화 상태 모니터링
- 수동 동기화 버튼 제공

#### 시나리오 2: MySQL 이중화

**리스크**:
- 두 DB 간 데이터 불일치
- 동기화 지연
- 분산 트랜잭션 실패
- Failover 실패
- **보상 트랜잭션 실패** ⚠️ **최고 위험도**
  - Primary DB에 불일치 데이터 영구 존재
  - Secondary DB와 데이터 불일치 상태 지속
  - 사용자에게는 실패로 알려졌으나 실제로는 Primary에 데이터 존재
- 복구 작업자 실패
- 복구 큐 시스템 장애

**완화 방안**:
- 주기적인 데이터 일관성 검증
- Replication 지연 모니터링
- 자동 복구 메커니즘 (Repair Worker)
- 장애 시나리오 테스트
- **보상 트랜잭션 실패 처리**:
  - CRITICAL 로그 기록 및 즉시 알림
  - 비동기 복구 메커니즘 (DLQ + Repair Worker)
  - 수동 개입 절차 문서화
  - 보상 트랜잭션 실패 모니터링 및 대시보드
- **복구 시스템 격리**:
  - Repair Worker를 핵심 비즈니스 로직과 분리
  - 독립적인 확장 및 장애 격리
  - 복구 큐 시스템 모니터링

### DualWriteVerificationTest 통합 테스트

현재 구현된 Dual Write 및 Failover 로직의 안정성을 증명하는 유일한 방법입니다. 이 테스트는 Phase 2 진입 전 필수 검증 항목으로, 모든 장애 시나리오를 시뮬레이션하여 시스템의 복원력을 검증합니다.

#### 필수 테스트 시나리오

##### 1. Happy Path Test: Primary/Secondary 동시 쓰기 성공 및 데이터 정합성

**목적**: 정상적인 상황에서 Dual Write가 올바르게 작동하고, Primary와 Secondary DB의 데이터가 일치하는지 검증합니다.

**테스트 절차**:
1. 테스트 User와 UserShelfBook 생성
2. `MemoService.createMemo()` 호출
3. `DataConsistencyVerifier.verifyMemoConsistency()`를 사용하여 Primary와 Secondary DB의 데이터 일치 확인
4. 모든 필드(id, user_id, book_id, page_number, content, memo_start_time 등)가 일치하는지 검증

**검증 항목**:
- Primary DB에 데이터가 정상적으로 저장됨
- Secondary DB에 데이터가 정상적으로 저장됨
- 두 DB의 모든 필드가 완전히 일치함

##### 2. Secondary Write Failure Test: 보상 트랜잭션 검증

**목적**: Secondary DB 쓰기 실패 시 Primary DB의 보상 트랜잭션이 올바르게 실행되어 데이터 불일치를 방지하는지 검증합니다.

**테스트 절차**:
1. Secondary DB 연결을 임시로 끊거나 Secondary DB를 다운시킴
2. `MemoService.createMemo()` 호출
3. `DatabaseWriteException`이 발생하는지 확인
4. Primary DB에서 해당 메모 ID로 조회하여 데이터가 없는지 확인 (보상 트랜잭션의 롤백 확인)

**검증 항목**:
- Secondary DB 쓰기 실패 시 예외가 발생함
- Primary DB에 데이터가 저장되지 않음 (보상 트랜잭션으로 DELETE됨)
- 사용자에게는 실패 응답이 반환됨

**구현 방법**:
- 테스트 환경에서 Secondary DB 연결을 모의(Mock)하거나 실제로 끊음
- `@TestPropertySource` 또는 테스트 전용 설정을 사용하여 Secondary DB 연결을 비활성화

##### 3. Secondary Cleanup Failure Test (DELETE 시): Recovery Queue 발행 및 Worker 처리 검증

**목적**: DELETE 작업에서 Secondary DB 삭제 실패 시 Recovery Queue에 이벤트가 발행되고, CompensationRecoveryWorker가 이를 처리하여 Secondary DB의 유령 데이터를 정리하는지 검증합니다.

**테스트 절차**:
1. 정상적으로 메모 생성 (Primary와 Secondary 모두에 저장)
2. Secondary DB 연결을 임시로 끊음
3. `MemoService.deleteMemo()` 호출
4. Primary DB에서 메모가 삭제되었는지 확인
5. `RecoveryQueueService`에서 `DELETE_SECONDARY_CLEANUP` 이벤트가 발행되었는지 확인
6. Secondary DB 연결 복구
7. `CompensationRecoveryWorker.processRecoveryQueue()`를 수동으로 호출하거나 스케줄러가 실행될 때까지 대기
8. Secondary DB에서 해당 메모가 삭제되었는지 확인

**검증 항목**:
- Primary DB에서 메모가 정상적으로 삭제됨
- `DELETE_SECONDARY_CLEANUP` 이벤트가 Recovery Queue에 발행됨
- CompensationRecoveryWorker가 이벤트를 처리하여 Secondary DB에서 메모가 삭제됨
- `memo_tags` 테이블의 관련 데이터도 함께 삭제됨

##### 4. Read Failover Test: Primary DB 장애 시 Secondary DB로의 Failover 검증

**목적**: Primary DB 장애 시 Read Failover가 올바르게 작동하여 Secondary DB에서 데이터를 읽을 수 있는지 검증합니다.

**테스트 절차**:
1. 정상적으로 메모 생성 (Primary와 Secondary 모두에 저장)
2. Primary DB 연결을 임시로 끊거나 Primary DB를 다운시킴
3. `MemoService.getMemoById()` 호출
4. Secondary DB에서 데이터가 정상적으로 읽히는지 확인
5. 반환된 메모 데이터가 올바른지 검증

**검증 항목**:
- Primary DB 연결 실패 시 예외가 발생하지 않음
- Secondary DB에서 데이터가 정상적으로 읽힘
- 반환된 메모 데이터가 예상한 값과 일치함

**구현 방법**:
- 테스트 환경에서 Primary DB 연결을 모의(Mock)하거나 실제로 끊음
- `DualMasterReadService.readWithFailover()`가 Secondary DB로 자동 Failover하는지 확인

#### 테스트 구현 고려사항

**환경 설정**:
- 테스트 전용 프로파일(`@ActiveProfiles("test")`) 사용
- Primary와 Secondary DB를 독립적으로 제어할 수 있는 테스트 환경 구성
- DB 연결을 임시로 끊거나 모의할 수 있는 메커니즘 구현

**데이터 준비**:
- 각 테스트 전에 필요한 테스트 데이터(User, UserShelfBook, Book 등) 생성
- 테스트 후 데이터 정리 (`@AfterEach`에서 명시적으로 삭제)

**데이터 정리 (필수)**:
- **문제**: `DualMasterWriteService`는 별도의 `TransactionTemplate`을 사용하므로, 테스트의 `@Transactional` 롤백 범위를 벗어나 실제 DB에 데이터가 커밋됩니다.
- **해결**: `@AfterEach`에서 생성된 테스트 데이터를 추적하고, Primary와 Secondary DB 양쪽에서 명시적으로 삭제해야 합니다.
- **구현 방법**:
  - `createdMemoIds` 리스트를 사용하여 테스트에서 생성한 메모 ID만 추적
  - 각 테스트 메서드에서 `createdMemoIds.add(memoId)`로 ID 기록
  - `@AfterEach`에서 `DELETE FROM memo WHERE id IN (:ids)` 쿼리로 Primary와 Secondary DB 양쪽에서 삭제
  - **중요**: 테이블 전체를 비우는 것이 아니라, `createdMemoIds`에 기록된 ID만 삭제하여 실제 개발/운영 데이터를 보호
- **이유**:
  - 시연 오류 방지: 라이브 데모에서 테스트 데이터와 ID 충돌 방지
  - 데이터 오염 방지: 시연 페이지에서 테스트 데이터가 노출되지 않도록 보장
  - 개발 데이터 보호: 실제 개발 중인 데이터나 중요한 시연용 데이터가 삭제되지 않도록 보장

**비동기 처리**:
- `CompensationRecoveryWorker`의 스케줄러 실행을 기다리거나 수동으로 호출
- `RecoveryQueueService`의 큐 상태를 직접 확인

**예외 처리**:
- 예상된 예외(`DatabaseWriteException`)가 발생하는지 검증
- 예상치 못한 예외가 발생하지 않는지 확인

#### 테스트 실행 순서

1. **Happy Path Test** 실행 → 정상 동작 확인
2. **Secondary Write Failure Test** 실행 → 보상 트랜잭션 검증
3. **Secondary Cleanup Failure Test** 실행 → Recovery Queue 및 Worker 검증
4. **Read Failover Test** 실행 → Failover 메커니즘 검증

모든 테스트가 통과해야 Phase 2 진입이 가능합니다.

### 구현 순서의 중요성

**옵션 A (권장)**: 클라이언트 기능 먼저 → 인프라 개선
- ✅ 안정적인 백엔드 API 위에서 클라이언트 개발
- ✅ 각 단계 독립적으로 검증 가능
- ✅ 인프라 변경 시 클라이언트 기능은 안정적

---

## 참고 자료

### 관련 문서

- [오프라인 메모 동기화 상세 설계](./OFFLINE_MEMO_SYNC.md)
- [멀티 디바이스 오프라인 동기화](./MULTI_DEVICE_SYNC.md)
- [Fault Tolerance 테스트](./FAULT_TOLERANCE_TESTING.md)

### 외부 자료

#### 오프라인 동기화
- [IndexedDB API](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API)
- [Offline-First Architecture](https://offlinefirst.org/)

#### MySQL 이중화
- [MySQL Replication](https://dev.mysql.com/doc/refman/8.0/en/replication.html)
- [MySQL Master-Master Replication](https://dev.mysql.com/doc/refman/8.0/en/replication-multi-master.html)
- [Spring DataSource Routing](https://www.baeldung.com/spring-abstract-routing-data-source)
- [Two-Phase Commit](https://en.wikipedia.org/wiki/Two-phase_commit_protocol)

---

## 다음 단계

1. **Phase 1 시작**: 웹 UI 오프라인 동기화 구현
2. **문서 검토**: [OFFLINE_MEMO_SYNC.md](./OFFLINE_MEMO_SYNC.md) 상세 검토
3. **프로토타입**: 작은 규모로 프로토타입 구현 및 검증
4. **단계별 완료**: 각 Phase 완료 후 충분한 테스트 수행

---

**문서 버전**: 1.0  
**최종 업데이트**: 2024년  
**작성자**: Development Team

