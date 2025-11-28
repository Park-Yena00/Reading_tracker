# 멀티 디바이스 오프라인 동기화 설계

> **목적**: 여러 디바이스(웹, 모바일 앱)에서 오프라인 상태로 작성한 메모가 네트워크 복구 시 모든 디바이스에서 동기화되고 무결성을 유지하는 기능 구현  
> **비기능 품질**: Fault Tolerance (장애 허용), Data Integrity (데이터 무결성)  
> **관련 문서**: [오프라인 메모 작성 및 동기화 설계](./OFFLINE_MEMO_SYNC.md)

---

## 📋 목차

1. [시나리오 분석](#시나리오-분석)
2. [문제점 및 도전 과제](#문제점-및-도전-과제)
3. [해결 방법 개요](#해결-방법-개요)
4. [아키텍처 설계](#아키텍처-설계)
5. [구현 방법](#구현-방법)
6. [동기화 전략](#동기화-전략)
7. [동시성 제어](#동시성-제어-concurrency-control)
8. [충돌 해결](#충돌-해결)
9. [데이터 무결성 보장](#데이터-무결성-보장)
10. [구현 단계별 가이드](#구현-단계별-가이드)
11. [테스트 방법](#테스트-방법)

---

## 시나리오 분석

### 시나리오 설명

```
[시나리오] 다중 디바이스 오프라인 메모 작성 및 동기화

1. 사용자가 노트북(웹)에서 오프라인 상태로 메모 작성
   - 네트워크 연결 없음
   - 메모 A, B 작성 (로컬 저장소에 저장)

2. 사용자가 모바일 디바이스(앱)에서도 오프라인 상태로 메모 작성
   - 네트워크 연결 없음
   - 메모 C, D 작성 (로컬 저장소에 저장)

3. 네트워크 복구
   - 노트북에서 네트워크 연결
   - 모바일 디바이스에서 네트워크 연결

4. 동기화 요구사항
   - 노트북의 메모 A, B → 서버로 동기화
   - 모바일의 메모 C, D → 서버로 동기화
   - 서버에서 모든 메모를 받아와 각 디바이스에 동기화
   - 노트북: 메모 A, B, C, D 모두 표시
   - 모바일: 메모 A, B, C, D 모두 표시
   - 메모 내용 손실 없음
   - 정렬 방법(memoStartTime)에 따라 올바르게 표시
```

### 요구사항

1. **다중 디바이스 지원**
   - 웹 (노트북/데스크톱)
   - 모바일 앱 (Android/iOS)
   - 동일한 사용자 계정 사용

2. **오프라인 메모 작성**
   - 각 디바이스에서 독립적으로 오프라인 메모 작성 가능
   - 로컬 저장소에 임시 저장

3. **양방향 동기화**
   - 각 디바이스 → 서버: 로컬 메모 업로드
   - 서버 → 각 디바이스: 다른 디바이스의 메모 다운로드

4. **데이터 무결성**
   - 메모 내용 손실 없음
   - 중복 저장 방지
   - 시간 순서 정렬 유지

5. **충돌 해결**
   - 동일 시간에 여러 디바이스에서 메모 작성 시 처리
   - 중복 메모 방지

---

## 문제점 및 도전 과제

### 1. 중복 동기화 문제

**문제:**
- 디바이스 A에서 메모 작성 후 동기화
- 디바이스 B가 같은 메모를 다시 동기화하려고 시도
- 중복 저장 발생

**해결 필요:**
- 서버에서 중복 검사
- 로컬에서 중복 검사

### 2. 시간 정렬 문제

**문제:**
- 각 디바이스의 시스템 시간이 다를 수 있음
- 네트워크 시간과 디바이스 시간 불일치
- 정렬 순서가 잘못될 수 있음

**해결 필요:**
- 서버에서 실제 저장 시간 기준 정렬
- 또는 클라이언트 시간과 서버 시간 동기화

### 3. 동기화 타이밍 문제

**문제:**
- 디바이스 A가 동기화하는 동안 디바이스 B도 동기화 시도
- 일부 메모만 동기화되고 일부는 누락될 수 있음

**해결 필요:**
- 서버에서 최신 메모 목록 제공
- 클라이언트에서 누락된 메모 확인

### 4. 디바이스 식별

**문제:**
- 어떤 디바이스에서 작성된 메모인지 추적
- 동일 사용자의 여러 디바이스 구분

**해결 필요:**
- 디바이스 ID 관리
- 사용자-디바이스 매핑

### 5. 동시성 문제 (Race Condition) ⚠️ **중요**

#### 시나리오 1: 동시에 같은 책 추가 (중복 저장)

**문제:**
- 디바이스 A와 디바이스 B가 거의 동시에 같은 책을 서재에 추가
- 두 요청이 교차 진행되면서 중복 저장 발생 가능

**상세 플로우 (문제 발생):**
```
시간  | 디바이스 A (노트북)              | 디바이스 B (모바일)
------|--------------------------------|--------------------------------
T1    | POST /user/books               |
      | ISBN 확인 요청                  |
T2    |                                | POST /user/books
      |                                | ISBN 확인 요청
T3    | ISBN 없음 (결과)               |
T4    |                                | ISBN 없음 (결과) ← A가 아직 저장 안 함
T5    | Book 저장 시작                 |
T6    |                                | Book 저장 시작
T7    | Book 저장 완료                 |
T8    | user_books 저장 시도           |
T9    |                                | Book 저장 완료
T10   |                                | user_books 저장 시도
      | → 중복 저장 발생 가능!
```

#### 시나리오 2: 동시에 같은 책 수정 (Lost Update)

**문제:**
- 디바이스 A: 책 상세정보 수정 (예: 카테고리 변경)
- 디바이스 B: 같은 책의 상세정보 수정 (예: 진행률 변경)
- 나중에 수정한 것이 먼저 수정한 것을 덮어쓸 수 있음

**상세 플로우 (문제 발생):**
```
시간  | 디바이스 A (노트북)              | 디바이스 B (모바일)
------|--------------------------------|--------------------------------
T1    | GET /user/books/{id}           |
      | 카테고리: Reading               |
T2    |                                | GET /user/books/{id}
      |                                | 카테고리: Reading
T3    | PUT /user/books/{id}           |
      | 카테고리: Finished로 변경       |
T4    |                                | PUT /user/books/{id}
      |                                | 진행률: 80으로 변경
T5    | 서버 저장 완료                 |
      | 카테고리: Finished              |
T6    |                                | 서버 저장 완료
      |                                | 진행률: 80
      |                                | 카테고리: Reading (T2 시점 데이터 기반)
      | → A의 변경사항(Finished)이 덮어써짐!
```

---

## 해결 방법 개요

### 핵심 원칙

1. **서버가 Single Source of Truth (SSOT)**
   - 최종 저장소는 서버의 데이터베이스
   - 모든 디바이스는 서버에서 최신 데이터 동기화

2. **양방향 동기화**
   - 업로드: 로컬 메모 → 서버
   - 다운로드: 서버 메모 → 로컬

3. **최신 데이터 우선**
   - 서버에서 최신 메모 목록 조회
   - 로컬과 서버 데이터 병합
   - 중복 제거

4. **시간 기반 정렬**
   - 서버에서 `memoStartTime` 기준 정렬
   - 또는 `created_at` 기준 정렬

---

## 아키텍처 설계

### 전체 동기화 플로우

```
┌─────────────────────────────────────────────────────────┐
│  [디바이스 1: 노트북 (웹)]                               │
│  오프라인 상태에서 메모 A, B 작성                        │
│  → 로컬 저장소(IndexedDB)에 저장                         │
└─────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────┐
│  [디바이스 2: 모바일 (앱)]                               │
│  오프라인 상태에서 메모 C, D 작성                        │
│  → 로컬 저장소(SQLite/SharedPreferences)에 저장          │
└─────────────────────────────────────────────────────────┘
                    ↓
        [네트워크 복구 - 양쪽 디바이스]
                    ↓
┌─────────────────────────────────────────────────────────┐
│  [동기화 프로세스]                                       │
│                                                          │
│  1. 디바이스 1 동기화:                                   │
│     - 로컬 메모 A, B → 서버 업로드                       │
│     - 서버에서 최신 메모 목록 다운로드                   │
│     - 로컬 데이터와 병합 (메모 C, D 추가)                │
│                                                          │
│  2. 디바이스 2 동기화:                                   │
│     - 로컬 메모 C, D → 서버 업로드                       │
│     - 서버에서 최신 메모 목록 다운로드                   │
│     - 로컬 데이터와 병합 (메모 A, B 추가)                │
│                                                          │
│  3. 결과:                                                │
│     - 디바이스 1: 메모 A, B, C, D 모두 표시             │
│     - 디바이스 2: 메모 A, B, C, D 모두 표시             │
│     - 서버: 메모 A, B, C, D 모두 저장                   │
└─────────────────────────────────────────────────────────┘
```

### 동기화 순서

```
[Step 1] 네트워크 복구 감지
    ↓
[Step 2] 로컬 메모 업로드 (Pending 상태 메모)
    ↓
    for each (로컬 pending 메모) {
        POST /api/v1/memos
        → 서버에 저장
        → 로컬에 serverId 업데이트
    }
    ↓
[Step 3] 서버에서 최신 메모 목록 다운로드
    ↓
    GET /api/v1/memos/books/{userBookId}
    → 서버의 모든 메모 조회
    ↓
[Step 4] 로컬 데이터와 병합
    ↓
    - 서버 메모 중 로컬에 없는 것 추가
    - 로컬 메모 중 동기화 완료된 것은 서버 메모로 대체
    - 중복 제거
    ↓
[Step 5] 시간 순서 정렬
    ↓
    - memoStartTime 또는 created_at 기준 정렬
    ↓
[Step 6] UI 업데이트
    ↓
    - 정렬된 메모 목록 표시
```

---

## 구현 방법

### 1. 서버 측 구현 (변경 불필요, 기존 API 활용)

현재 서버 API는 이미 멀티 디바이스를 지원합니다:

```
POST /api/v1/memos
→ 메모 생성 (어떤 디바이스에서든 가능)

GET /api/v1/memos/books/{userBookId}
→ 특정 책의 모든 메모 조회 (모든 디바이스의 메모 포함)

GET /api/v1/today-flow
→ 오늘의 흐름 조회 (모든 디바이스의 메모 포함)
```

**서버 변경사항**: 없음 (기존 API 그대로 사용)

---

### 2. 클라이언트 측 구현 (웹)

#### 양방향 동기화 서비스

```javascript
// services/bidirectional-sync-service.js
import { offlineMemoService } from './offline-memo-service.js';
import { apiClient } from './api-client.js';
import { networkMonitor } from '../utils/network-monitor.js';

class BidirectionalSyncService {
    constructor() {
        this.isSyncing = false;
    }

    /**
     * 전체 동기화 프로세스 (양방향)
     * 1. 로컬 메모 업로드
     * 2. 서버에서 최신 메모 다운로드
     * 3. 로컬과 서버 데이터 병합
     */
    async syncAll(userBookId) {
        if (this.isSyncing) {
            console.log('동기화가 이미 진행 중입니다.');
            return;
        }

        if (!networkMonitor.isOnline) {
            console.log('네트워크가 오프라인 상태입니다.');
            return;
        }

        this.isSyncing = true;

        try {
            // Step 1: 로컬 메모 업로드
            await this.uploadLocalMemos();

            // Step 2: 서버에서 최신 메모 다운로드
            const serverMemos = await this.downloadServerMemos(userBookId);

            // Step 3: 로컬 데이터와 병합
            await this.mergeMemos(serverMemos);

            console.log('양방향 동기화 완료');
        } catch (error) {
            console.error('동기화 실패:', error);
            throw error;
        } finally {
            this.isSyncing = false;
        }
    }

    /**
     * Step 1: 로컬 메모 업로드
     */
    async uploadLocalMemos() {
        const pendingMemos = await offlineMemoService.getPendingMemos();
        console.log(`업로드할 로컬 메모 수: ${pendingMemos.length}`);

        for (const memo of pendingMemos) {
            try {
                // 서버로 업로드
                const response = await apiClient.post('/memos', {
                    userBookId: memo.userBookId,
                    pageNumber: memo.pageNumber,
                    content: memo.content,
                    tags: memo.tags,
                    memoStartTime: memo.memoStartTime
                });

                // 로컬 메모 업데이트 (serverId 설정)
                await dbManager.updateMemoWithServerId(
                    memo.localId,
                    response.data.id
                );

                console.log(`메모 업로드 성공: ${memo.localId} → ${response.data.id}`);
            } catch (error) {
                console.error(`메모 업로드 실패 (${memo.localId}):`, error);
                // 실패한 메모는 재시도 큐에 추가 (기존 로직 사용)
                throw error;
            }
        }
    }

    /**
     * Step 2: 서버에서 최신 메모 다운로드
     */
    async downloadServerMemos(userBookId, date = null) {
        try {
            let endpoint = `/memos/books/${userBookId}`;
            const params = date ? { date } : {};

            const response = await apiClient.get(endpoint, { params });
            return response.data; // MemoResponse[] 배열
        } catch (error) {
            console.error('서버 메모 다운로드 실패:', error);
            throw error;
        }
    }

    /**
     * Step 3: 로컬 데이터와 서버 데이터 병합
     */
    async mergeMemos(serverMemos) {
        // 로컬 메모 조회
        const localMemos = await offlineMemoService.getAllMemos();

        // 서버 메모를 맵으로 변환 (serverId 기준)
        const serverMemoMap = new Map();
        serverMemos.forEach(memo => {
            serverMemoMap.set(memo.id, memo);
        });

        // 로컬 메모를 맵으로 변환 (localId 기준)
        const localMemoMap = new Map();
        localMemos.forEach(memo => {
            localMemoMap.set(memo.localId, memo);
        });

        // 병합 전략:
        // 1. 동기화 완료된 로컬 메모는 서버 메모로 대체
        // 2. 동기화 대기 중인 로컬 메모는 유지 (아직 업로드 중)
        // 3. 서버에만 있는 메모는 로컬에 추가

        const mergedMemos = [];

        // 1. 동기화 완료된 로컬 메모 처리
        for (const [localId, localMemo] of localMemoMap) {
            if (localMemo.syncStatus === 'synced' && localMemo.serverId) {
                // 서버 메모가 있으면 서버 메모 사용
                const serverMemo = serverMemoMap.get(localMemo.serverId);
                if (serverMemo) {
                    mergedMemos.push(serverMemo);
                    serverMemoMap.delete(localMemo.serverId); // 이미 처리됨
                } else {
                    // 서버 메모가 없으면 로컬 메모 사용 (최신 상태)
                    mergedMemos.push(this.mapLocalToServer(localMemo));
                }
            } else {
                // 동기화 대기 중인 로컬 메모는 유지
                mergedMemos.push(this.mapLocalToServer(localMemo));
            }
        }

        // 2. 서버에만 있는 메모 추가 (다른 디바이스에서 작성한 메모)
        for (const [serverId, serverMemo] of serverMemoMap) {
            // 로컬에 없는 서버 메모인지 확인
            const existingLocal = Array.from(localMemoMap.values())
                .find(m => m.serverId === serverId);

            if (!existingLocal) {
                // 새로운 메모 → 로컬에 저장 (동기화 완료 상태로)
                await this.saveServerMemoToLocal(serverMemo);
                mergedMemos.push(serverMemo);
            }
        }

        // 3. 시간 순서 정렬
        mergedMemos.sort((a, b) => {
            const timeA = new Date(a.memoStartTime || a.createdAt);
            const timeB = new Date(b.memoStartTime || b.createdAt);
            return timeA - timeB;
        });

        return mergedMemos;
    }

    /**
     * 서버 메모를 로컬에 저장 (다른 디바이스에서 작성한 메모)
     */
    async saveServerMemoToLocal(serverMemo) {
        const localMemo = {
            localId: `synced-${serverMemo.id}`, // 서버 ID 기반 로컬 ID
            serverId: serverMemo.id,
            userBookId: serverMemo.userBookId,
            pageNumber: serverMemo.pageNumber,
            content: serverMemo.content,
            tags: serverMemo.tags || [],
            memoStartTime: serverMemo.memoStartTime,
            syncStatus: 'synced', // 이미 서버에 있으므로 동기화 완료
            createdAt: serverMemo.createdAt,
            updatedAt: serverMemo.updatedAt,
            syncQueueId: null
        };

        await dbManager.saveMemo(localMemo);
        console.log(`서버 메모를 로컬에 저장: ${serverMemo.id}`);
    }

    /**
     * 로컬 메모를 서버 형식으로 매핑
     */
    mapLocalToServer(localMemo) {
        return {
            id: localMemo.serverId || localMemo.localId,
            localId: localMemo.localId,
            userBookId: localMemo.userBookId,
            pageNumber: localMemo.pageNumber,
            content: localMemo.content,
            tags: localMemo.tags,
            memoStartTime: localMemo.memoStartTime,
            createdAt: localMemo.createdAt,
            updatedAt: localMemo.updatedAt,
            syncStatus: localMemo.syncStatus
        };
    }
}

export const bidirectionalSyncService = new BidirectionalSyncService();
```

#### 네트워크 복구 시 자동 양방향 동기화

```javascript
// utils/network-monitor.js (개선된 버전)
class NetworkMonitor {
    async onNetworkOnline() {
        // 약간의 지연 후 동기화 (네트워크 안정화 대기)
        setTimeout(async () => {
            try {
                // 양방향 동기화 실행
                // 모든 책에 대해 동기화 (또는 현재 보고 있는 책만)
                const userBooks = await getUserBooks(); // 사용자의 책 목록
                
                for (const book of userBooks) {
                    await bidirectionalSyncService.syncAll(book.id);
                }
            } catch (error) {
                console.error('자동 양방향 동기화 실패:', error);
            }
        }, 1000);
    }
}
```

---

### 3. 클라이언트 측 구현 (모바일 앱 - Kotlin)

#### 양방향 동기화 서비스 (Kotlin)

```kotlin
// services/BidirectionalSyncService.kt
class BidirectionalSyncService(
    private val localMemoRepository: LocalMemoRepository,
    private val apiClient: ApiClient,
    private val networkMonitor: NetworkMonitor
) {
    private var isSyncing = false

    /**
     * 전체 동기화 프로세스 (양방향)
     */
    suspend fun syncAll(userBookId: Long) {
        if (isSyncing) {
            Log.d(TAG, "동기화가 이미 진행 중입니다.")
            return
        }

        if (!networkMonitor.isOnline()) {
            Log.d(TAG, "네트워크가 오프라인 상태입니다.")
            return
        }

        isSyncing = true

        try {
            // Step 1: 로컬 메모 업로드
            uploadLocalMemos()

            // Step 2: 서버에서 최신 메모 다운로드
            val serverMemos = downloadServerMemos(userBookId)

            // Step 3: 로컬 데이터와 병합
            mergeMemos(serverMemos)

            Log.d(TAG, "양방향 동기화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "동기화 실패", e)
            throw e
        } finally {
            isSyncing = false
        }
    }

    /**
     * Step 1: 로컬 메모 업로드
     */
    private suspend fun uploadLocalMemos() {
        val pendingMemos = localMemoRepository.getPendingMemos()
        Log.d(TAG, "업로드할 로컬 메모 수: ${pendingMemos.size}")

        pendingMemos.forEach { memo ->
            try {
                // 서버로 업로드
                val response = apiClient.createMemo(
                    MemoCreateRequest(
                        userBookId = memo.userBookId,
                        pageNumber = memo.pageNumber,
                        content = memo.content,
                        tags = memo.tags,
                        memoStartTime = memo.memoStartTime
                    )
                )

                // 로컬 메모 업데이트 (serverId 설정)
                localMemoRepository.updateMemoWithServerId(
                    memo.localId,
                    response.data.id
                )

                Log.d(TAG, "메모 업로드 성공: ${memo.localId} → ${response.data.id}")
            } catch (e: Exception) {
                Log.e(TAG, "메모 업로드 실패 (${memo.localId})", e)
                // 재시도 로직
            }
        }
    }

    /**
     * Step 2: 서버에서 최신 메모 다운로드
     */
    private suspend fun downloadServerMemos(userBookId: Long): List<MemoResponse> {
        return try {
            apiClient.getBookMemos(userBookId).data
        } catch (e: Exception) {
            Log.e(TAG, "서버 메모 다운로드 실패", e)
            throw e
        }
    }

    /**
     * Step 3: 로컬 데이터와 서버 데이터 병합
     */
    private suspend fun mergeMemos(serverMemos: List<MemoResponse>) {
        val localMemos = localMemoRepository.getAllMemos()

        // 서버 메모 맵
        val serverMemoMap = serverMemos.associateBy { it.id }

        // 병합 및 저장
        serverMemos.forEach { serverMemo ->
            // 로컬에 없는 서버 메모인지 확인
            val existingLocal = localMemos.find { 
                it.serverId == serverMemo.id 
            }

            if (existingLocal == null) {
                // 새로운 메모 → 로컬에 저장
                localMemoRepository.saveServerMemo(serverMemo)
            }
        }

        // 정렬은 UI에서 수행 (memoStartTime 기준)
    }
}
```

---

## 동기화 전략

### 1. 업로드 우선 전략

1. 로컬 메모를 먼저 서버에 업로드
2. 그 다음 서버에서 최신 메모 다운로드
3. 병합 시 로컬 메모가 우선 (아직 업로드 중일 수 있으므로)

### 2. 중복 방지 전략

**서버 측:**
- `memoStartTime` + `userBookId` + `content` 기준 중복 검사 (선택사항)
- 또는 클라이언트에서 관리 (서버는 항상 저장)

**클라이언트 측:**
- `serverId` 기준 중복 제거
- 동일한 `serverId`를 가진 메모는 하나만 표시

### 3. 시간 정렬 전략

**정렬 기준:**
1. `memoStartTime` (사용자가 설정한 시간)
2. 없으면 `createdAt` (실제 생성 시간)

**정렬 순서:**
- 오름차순 (가장 오래된 메모부터)
- 또는 내림차순 (가장 최신 메모부터)

---

## 동시성 제어 (Concurrency Control)

### 1. 동시 책 추가 중복 방지

#### 문제점

두 디바이스에서 거의 동시에 같은 책(동일 ISBN)을 서재에 추가할 때:
1. 디바이스 A: ISBN 확인 → 없음 → Book 저장 → user_books 저장
2. 디바이스 B: ISBN 확인 → 없음 (A가 아직 저장 안 함) → Book 저장 → user_books 저장
3. 결과: 중복 저장 발생

#### 해결 방법

**방법 1: 데이터베이스 Unique Constraint 활용 (현재 구현)** ⭐

현재 `user_books` 테이블에는 이미 Unique 제약조건이 있습니다:
```sql
UNIQUE(user_id, book_id)
```

**하지만 여전히 문제가 발생할 수 있는 이유:**
- Book 저장과 user_books 저장 사이에 시간 차이가 있음
- 두 트랜잭션이 교차 진행될 수 있음

**개선 방법:**
1. **트랜잭션 격리 수준 조정**
   ```java
   @Transactional(isolation = Isolation.SERIALIZABLE)
   public UserShelfBook addBookToShelf(Book book, UserShelfBook userShelfBook) {
       // ...
   }
   ```

2. **Unique Constraint 위반 시 처리**
   ```java
   try {
       return userBookRepository.save(userShelfBook);
   } catch (DataIntegrityViolationException e) {
       // UNIQUE 제약조건 위반 시
       // 이미 존재하는 경우 기존 데이터 반환
       Optional<UserShelfBook> existing = userBookRepository
           .findByUserIdAndBookId(userShelfBook.getUserId(), savedBook.getId());
       return existing.orElseThrow(() -> 
           new IllegalArgumentException("책 추가 중 오류가 발생했습니다."));
   }
   ```

**방법 2: Pessimistic Locking (비관적 잠금)**

데이터베이스 레벨에서 잠금을 걸어 동시 접근 방지:
```java
@Transactional
public UserShelfBook addBookToShelf(Book book, UserShelfBook userShelfBook) {
    // 사용자 ID와 ISBN으로 잠금
    // 다른 트랜잭션은 이 작업이 완료될 때까지 대기
    UserShelfBook lock = userBookRepository
        .findByUserIdAndBookIsbnWithLock(userShelfBook.getUserId(), book.getIsbn());
    
    if (lock != null) {
        throw new IllegalArgumentException("이미 내 서재에 추가된 책입니다.");
    }
    
    // 나머지 로직...
}
```

**Repository 메서드:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT ub FROM UserShelfBook ub WHERE ub.userId = :userId AND ub.book.isbn = :isbn")
Optional<UserShelfBook> findByUserIdAndBookIsbnWithLock(
    @Param("userId") Long userId, 
    @Param("isbn") String isbn
);
```

**방법 3: SELECT FOR UPDATE (권장)** ⭐

트랜잭션 내에서 명시적 잠금:
```java
@Transactional
public UserShelfBook addBookToShelf(Book book, UserShelfBook userShelfBook) {
    // 1. Book 조회 및 잠금 (또는 생성)
    Book savedBook = findOrCreateBook(book);
    
    // 2. user_books 조회 및 잠금 (SELECT FOR UPDATE)
    Optional<UserShelfBook> existing = userBookRepository
        .findByUserIdAndBookIdForUpdate(
            userShelfBook.getUserId(), 
            savedBook.getId()
        );
    
    if (existing.isPresent()) {
        throw new IllegalArgumentException("이미 내 서재에 추가된 책입니다.");
    }
    
    // 3. user_books 저장
    return userBookRepository.save(userShelfBook);
}
```

**Repository 메서드:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT ub FROM UserShelfBook ub WHERE ub.userId = :userId AND ub.book.id = :bookId")
Optional<UserShelfBook> findByUserIdAndBookIdForUpdate(
    @Param("userId") Long userId, 
    @Param("bookId") Long bookId
);
```

---

### 2. 동시 책 수정 Lost Update 방지

#### 문제점

두 디바이스에서 거의 동시에 같은 책의 상세정보를 수정할 때:
- 디바이스 A의 수정사항이 디바이스 B의 수정으로 덮어써질 수 있음

#### 해결 방법

**방법 1: Optimistic Locking (낙관적 잠금) - 권장** ⭐

버전 필드를 사용하여 동시 수정 감지:

**1. 엔티티에 버전 필드 추가:**
```sql
-- Flyway 마이그레이션 파일
ALTER TABLE user_books ADD COLUMN version INT NOT NULL DEFAULT 0;
```

**2. 엔티티 클래스 수정:**
```java
@Entity
@Table(name = "user_books")
public class UserShelfBook {
    // ... 기존 필드들 ...
    
    @Version
    @Column(name = "version")
    private Integer version;  // 자동으로 버전 관리
    
    // ... getter, setter ...
}
```

**3. 서비스 로직:**
```java
@Transactional
public UserShelfBook updateBookDetail(UserShelfBook userBook) {
    // userBook은 이미 조회된 엔티티 (특정 version을 가짐)
    // JPA가 자동으로 version을 확인하고 업데이트
    
    // 카테고리별 입력값 검증
    validateCategorySpecificFields(userBook);
    
    // 진행률 기반 자동 카테고리 변경
    if (userBook.getReadingProgress() != null) {
        autoUpdateCategoryByProgress(userBook);
    }
    
    userBook.setUpdatedAt(LocalDateTime.now());
    
    try {
        return userBookRepository.save(userBook);
    } catch (OptimisticLockingFailureException e) {
        // 버전이 다름 = 다른 디바이스에서 이미 수정됨
        throw new IllegalStateException(
            "다른 디바이스에서 이미 수정되었습니다. 최신 데이터를 다시 조회해주세요.", e);
    }
}
```

**4. Controller에서 처리:**
```java
@PutMapping("/user/books/{userBookId}")
public ApiResponse<BookDetailResponse> updateBookDetail(
        @PathVariable Long userBookId,
        @Valid @RequestBody BookDetailUpdateRequest request) {
    
    User user = getCurrentUser();
    
    // 최신 엔티티 조회 (version 포함)
    UserShelfBook existingBook = userBookRepository.findById(userBookId)
        .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
    
    // 권한 확인
    if (!existingBook.getUserId().equals(user.getId())) {
        throw new IllegalArgumentException("권한이 없습니다.");
    }
    
    try {
        // DTO에서 엔티티로 부분 업데이트
        bookMapper.updateBookDetailFromRequest(existingBook, request);
        
        // 서비스 호출 (Optimistic Locking 자동 적용)
        UserShelfBook updatedBook = bookService.updateBookDetail(existingBook);
        
        BookDetailResponse response = bookMapper.toBookDetailResponse(updatedBook);
        return ApiResponse.success(response);
        
    } catch (OptimisticLockingFailureException e) {
        // 동시 수정 충돌 발생
        return ApiResponse.error("다른 디바이스에서 이미 수정되었습니다. 페이지를 새로고침해주세요.");
    }
}
```

**방법 2: 최신 데이터 조회 후 병합 (Merge Strategy)**

```java
@Transactional
public UserShelfBook updateBookDetail(Long userBookId, BookDetailUpdateRequest request) {
    // 최신 엔티티 조회 (다른 디바이스의 수정사항 포함)
    UserShelfBook existingBook = userBookRepository.findById(userBookId)
        .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
    
    // 부분 업데이트 (null이 아닌 필드만 업데이트)
    if (request.getCategory() != null) {
        existingBook.setCategory(request.getCategory());
    }
    if (request.getReadingProgress() != null) {
        existingBook.setReadingProgress(request.getReadingProgress());
    }
    // ... 기타 필드들
    
    validateCategorySpecificFields(existingBook);
    autoUpdateCategoryByProgress(existingBook);
    
    existingBook.setUpdatedAt(LocalDateTime.now());
    return userBookRepository.save(existingBook);
}
```

**방법 3: Last-Write-Wins (마지막 작성 우선)**

가장 최근 수정을 우선시하는 방식:
```java
@Transactional
public UserShelfBook updateBookDetail(UserShelfBook userBook) {
    // updated_at을 확인하여 최신 데이터만 허용
    UserShelfBook latest = userBookRepository.findById(userBook.getId())
        .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));
    
    if (latest.getUpdatedAt().isAfter(userBook.getUpdatedAt())) {
        throw new IllegalStateException("다른 디바이스에서 더 최근에 수정되었습니다.");
    }
    
    // 나머지 로직...
}
```

**단점:** Lost Update가 발생할 수 있음 (권장하지 않음)

---

### 3. 트랜잭션 격리 수준 설정

**현재 설정 확인:**
```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

**개선 방안:**

```yaml
spring:
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
      javax:
        persistence:
          lock:
            timeout: 10000  # 잠금 타임아웃 (10초)
    open-in-view: false  # 성능 최적화
```

**트랜잭션 격리 수준 설정:**
```java
// 특정 메서드에만 적용
@Transactional(isolation = Isolation.REPEATABLE_READ)
public UserShelfBook addBookToShelf(Book book, UserShelfBook userShelfBook) {
    // ...
}

// 또는 전체 서비스 클래스에 적용
@Service
@Transactional(isolation = Isolation.REPEATABLE_READ)
public class BookService {
    // ...
}
```

**격리 수준 비교:**

| 격리 수준 | 설명 | Lost Update 방지 | 성능 |
|----------|------|-----------------|------|
| **READ UNCOMMITTED** | 커밋되지 않은 데이터 읽기 가능 | ❌ | ⭐⭐⭐ |
| **READ COMMITTED** | 커밋된 데이터만 읽기 (기본값) | ❌ | ⭐⭐ |
| **REPEATABLE READ** | 같은 트랜잭션에서 같은 데이터 일관성 보장 | ✅ | ⭐ |
| **SERIALIZABLE** | 완전한 직렬화, 모든 동시성 문제 방지 | ✅ | ❌ |

**권장 설정:** `REPEATABLE READ` (MySQL 기본값이기도 함)

---

## 충돌 해결

### 시나리오 1: 동일 시간에 여러 디바이스에서 메모 작성

**상황:**
- 디바이스 A: 메모 작성 (memoStartTime: 10:00:00)
- 디바이스 B: 메모 작성 (memoStartTime: 10:00:00)
- 동일한 시간

**해결:**
- 서버에서 `created_at`을 기준으로 정렬
- 또는 `memoStartTime`이 같으면 `serverId` (생성 순서)로 정렬

```javascript
// 정렬 함수
function sortMemos(memos) {
    return memos.sort((a, b) => {
        // 1순위: memoStartTime
        const timeA = new Date(a.memoStartTime || a.createdAt);
        const timeB = new Date(b.memoStartTime || b.createdAt);
        
        if (timeA.getTime() !== timeB.getTime()) {
            return timeA - timeB;
        }
        
        // 2순위: created_at (memoStartTime이 같을 때)
        const createdA = new Date(a.createdAt);
        const createdB = new Date(b.createdAt);
        
        if (createdA.getTime() !== createdB.getTime()) {
            return createdA - createdB;
        }
        
        // 3순위: serverId (최종 순서 보장)
        return (a.id || 0) - (b.id || 0);
    });
}
```

### 시나리오 2: 동일한 내용의 메모가 여러 디바이스에서 작성

**상황:**
- 디바이스 A: "책 내용 정리" 메모 작성
- 디바이스 B: "책 내용 정리" 메모 작성 (우연히 같은 내용)

**해결:**
- 다른 메모로 간주 (중복 제거하지 않음)
- 사용자가 의도적으로 작성한 것으로 간주
- `serverId`가 다르면 다른 메모

---

## 데이터 무결성 보장

### 1. 메모 내용 손실 방지

**전략:**
1. 업로드 전 모든 로컬 메모 백업
2. 업로드 실패 시 재시도
3. 서버 응답 확인 후 로컬 업데이트

### 2. 중복 저장 방지

**전략:**
1. `serverId` 기준 중복 검사
2. 업로드 시 이미 존재하는 메모는 스킵
3. 다운로드 시 로컬에 있는 메모는 업데이트만

### 3. 정렬 무결성 보장

**전략:**
1. 서버에서 정렬된 목록 제공
2. 클라이언트에서도 정렬 수행 (이중 체크)
3. `memoStartTime` 기준 정렬

---

## 구현 단계별 가이드

### Phase 1: 기본 양방향 동기화

1. **로컬 메모 업로드 기능**
   - 기존 오프라인 동기화 활용
   - 모든 pending 메모 업로드

2. **서버 메모 다운로드 기능**
   - GET /api/v1/memos/books/{userBookId} 호출
   - 서버의 모든 메모 조회

### Phase 2: 데이터 병합

1. **병합 로직 구현**
   - 로컬 메모와 서버 메모 비교
   - 중복 제거
   - 새 메모 추가

2. **로컬 저장소 업데이트**
   - 서버 메모를 로컬에 저장
   - 동기화 상태 업데이트

### Phase 3: 정렬 및 UI

1. **정렬 로직 구현**
   - `memoStartTime` 기준 정렬
   - 시간이 같을 때 대비

2. **UI 업데이트**
   - 정렬된 메모 목록 표시
   - 동기화 상태 표시

### Phase 4: 자동화

1. **네트워크 복구 시 자동 동기화**
   - 양방향 동기화 자동 실행

2. **주기적 동기화** (선택사항)
   - 주기적으로 서버에서 최신 메모 확인

### Phase 5: 동시성 제어 (서버 측)

1. **Optimistic Locking 구현**
   - `user_books` 테이블에 `version` 필드 추가
   - 엔티티에 `@Version` 어노테이션 추가
   - 수정 시 버전 체크 및 예외 처리

2. **Pessimistic Locking 구현**
   - 책 추가 시 `SELECT FOR UPDATE` 사용
   - 중복 저장 방지

3. **트랜잭션 격리 수준 설정**
   - 적절한 격리 수준 선택 (REPEATABLE READ 권장)
   - 성능과 데이터 무결성 균형

---

## 테스트 방법

### 1. 기본 멀티 디바이스 동기화 테스트

**테스트 시나리오:**
1. 노트북(웹)에서 오프라인 상태로 메모 A, B 작성
2. 모바일(앱)에서 오프라인 상태로 메모 C, D 작성
3. 노트북 네트워크 연결 → 자동 동기화
4. 모바일 네트워크 연결 → 자동 동기화
5. 확인:
   - 노트북에 메모 A, B, C, D 모두 표시
   - 모바일에 메모 A, B, C, D 모두 표시
   - 서버에 메모 A, B, C, D 모두 저장

### 2. 시간 정렬 테스트

**테스트 시나리오:**
1. 노트북: 메모 A (10:00), 메모 B (10:30) 작성
2. 모바일: 메모 C (10:15), 메모 D (10:45) 작성
3. 동기화
4. 확인:
   - 정렬 순서: A(10:00) → C(10:15) → B(10:30) → D(10:45)
   - 시간 순서가 올바른가?

### 3. 중복 방지 테스트

**테스트 시나리오:**
1. 노트북에서 메모 작성 및 동기화
2. 모바일에서 같은 메모를 다시 업로드 시도
3. 확인:
   - 중복 저장되지 않는가?
   - 하나의 메모만 표시되는가?

### 4. 네트워크 복구 순서 테스트

**테스트 시나리오:**
1. 두 디바이스 모두 오프라인에서 메모 작성
2. 노트북 먼저 네트워크 연결
3. 모바일 나중에 네트워크 연결
4. 확인:
   - 각 디바이스가 다른 디바이스의 메모를 받는가?
   - 모든 메모가 양쪽 디바이스에 표시되는가?

### 5. 동시성 문제 테스트 (Race Condition)

#### 테스트 1: 동시에 같은 책 추가 (중복 저장 방지)

**테스트 시나리오:**
1. 노트북(웹)과 모바일(앱)에서 동일한 사용자로 로그인
2. 두 디바이스에서 거의 동시에 같은 책(동일 ISBN)을 서재에 추가
   - 노트북: POST /api/v1/user/books (ISBN: 978-123-456-7890)
   - 모바일: POST /api/v1/user/books (ISBN: 978-123-456-7890)
3. 확인:
   - 중복 저장이 발생하지 않는가?
   - 한 디바이스만 성공하고 다른 디바이스는 에러를 반환하는가?
   - 에러 메시지가 명확한가? ("이미 내 서재에 추가된 책입니다.")

**테스트 방법:**
```javascript
// 브라우저 콘솔에서 두 요청을 거의 동시에 실행
Promise.all([
    fetch('http://localhost:8080/api/v1/user/books', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer TOKEN'
        },
        body: JSON.stringify({
            isbn: '978-123-456-7890',
            // ... 기타 정보
        })
    }),
    fetch('http://localhost:8080/api/v1/user/books', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer TOKEN'
        },
        body: JSON.stringify({
            isbn: '978-123-456-7890',
            // ... 기타 정보
        })
    })
]).then(responses => {
    responses.forEach((res, idx) => {
        console.log(`Request ${idx + 1}:`, res.status, res.statusText);
        res.json().then(data => console.log(`Response ${idx + 1}:`, data));
    });
});
```

**확인 사항:**
- ✅ 하나의 요청만 성공 (200 OK)
- ✅ 다른 요청은 에러 반환 (400 Bad Request 또는 409 Conflict)
- ✅ 데이터베이스에 하나의 user_books 레코드만 존재
- ✅ Unique Constraint 위반이 발생하지 않음

#### 테스트 2: 동시에 같은 책 수정 (Lost Update 방지)

**테스트 시나리오:**
1. 노트북과 모바일에서 동일한 사용자로 로그인
2. 같은 책을 두 디바이스에서 조회
3. 거의 동시에 서로 다른 필드를 수정:
   - 노트북: 카테고리를 "Reading" → "Finished"로 변경
   - 모바일: 진행률을 50 → 80으로 변경
4. 확인:
   - Optimistic Locking이 작동하는가?
   - 한 디바이스의 수정이 다른 디바이스의 수정을 덮어쓰지 않는가?

**테스트 방법:**
```javascript
// Step 1: 책 조회
const bookId = 123; // 실제 userBookId

Promise.all([
    fetch(`http://localhost:8080/api/v1/user/books/${bookId}`, {
        headers: { 'Authorization': 'Bearer TOKEN' }
    }),
    fetch(`http://localhost:8080/api/v1/user/books/${bookId}`, {
        headers: { 'Authorization': 'Bearer TOKEN' }
    })
]).then(async ([res1, res2]) => {
    const book1 = await res1.json();
    const book2 = await res2.json();
    
    // Step 2: 거의 동시에 수정
    Promise.all([
        fetch(`http://localhost:8080/api/v1/user/books/${bookId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer TOKEN'
            },
            body: JSON.stringify({
                category: 'Finished',
                version: book1.data.version  // 버전 포함
            })
        }),
        fetch(`http://localhost:8080/api/v1/user/books/${bookId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer TOKEN'
            },
            body: JSON.stringify({
                readingProgress: 80,
                version: book2.data.version  // 같은 버전
            })
        })
    ]).then(responses => {
        responses.forEach((res, idx) => {
            console.log(`Update ${idx + 1}:`, res.status);
            res.json().then(data => {
                if (res.status === 409) {
                    console.log(`⚠️ 충돌 발생: ${data.message}`);
                } else {
                    console.log(`✅ 성공:`, data);
                }
            });
        });
    });
});
```

**확인 사항:**
- ✅ Optimistic Locking 적용 시: 하나는 성공, 하나는 409 Conflict
- ✅ 에러 메시지: "다른 디바이스에서 이미 수정되었습니다. 최신 데이터를 다시 조회해주세요."
- ✅ 최신 데이터 조회 후 재시도 가능
- ✅ Lost Update가 발생하지 않음

#### 테스트 3: 대량 동시 요청 테스트

**테스트 시나리오:**
1. 여러 디바이스에서 동시에 10개 이상의 요청 전송
2. 같은 책을 추가하려고 시도
3. 확인:
   - 데이터 무결성이 유지되는가?
   - 성능 저하가 없는가?

**테스트 도구:**
- **JMeter**: 부하 테스트
- **Apache Bench (ab)**: 간단한 부하 테스트
- **Postman Collection Runner**: API 테스트

```bash
# Apache Bench 예제
ab -n 100 -c 10 -p request.json -T application/json \
   -H "Authorization: Bearer TOKEN" \
   http://localhost:8080/api/v1/user/books
```

**확인 사항:**
- ✅ 모든 요청 중 하나만 성공
- ✅ 나머지는 적절한 에러 응답
- ✅ 데이터베이스에 중복 레코드 없음
- ✅ 응답 시간이 적절함

---

## 참고 자료

- [오프라인 메모 작성 및 동기화 설계](./OFFLINE_MEMO_SYNC.md)
- [IndexedDB API](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API)
- [Android Room Database](https://developer.android.com/training/data-storage/room)
- [Offline-First Architecture](https://offlinefirst.org/)

---

## 다음 단계

1. 양방향 동기화 서비스 구현
2. 데이터 병합 로직 구현
3. 정렬 로직 구현
4. 자동 동기화 트리거 구현
5. 멀티 디바이스 테스트

