# Offline-First 아키텍처 설계 및 전략

> **목적**: 오프라인 메모 동기화 기능을 위한 Offline-First 아키텍처 설계 및 기술 선택  
> **관련 문서**: [오프라인 메모 작성 및 동기화 설계](./OFFLINE_MEMO_SYNC.md)

---

## 📋 목차

1. [Service Worker 사용 여부 결정](#service-worker-사용-여부-결정)
2. [캐싱 전략 분석](#캐싱-전략-분석)
3. [오프라인 메모 조회 시나리오 분석](#오프라인-메모-조회-시나리오-분석)
4. [용량 및 성능 영향 분석](#용량-및-성능-영향-분석)
5. [데이터 정리 전략 비교](#데이터-정리-전략-비교)
6. [하이브리드 전략 선택](#하이브리드-전략-선택)

---

## Service Worker 사용 여부 결정

### 질문: Service Worker를 사용하는 것이 좋을까?

### 비교 분석

#### Service Worker 사용하지 않는 경우 (현재 방식)

**구현 방식**:
- `navigator.onLine` API
- `online`/`offline` 이벤트 리스너
- IndexedDB 직접 접근
- 페이지 컨텍스트에서 동기화 실행

**성능 측면**:

| 항목 | 평가 |
|------|------|
| 초기 로딩 | ✅ 빠름 (Service Worker 등록/설치 불필요) |
| 메모리 사용 | ✅ 적음 (별도 워커 프로세스 없음) |
| 구현 복잡도 | ✅ 낮음 (추가 복잡도 없음) |
| 백그라운드 동기화 | ❌ 불가능 (페이지가 열려있어야 함) |

**신뢰성 측면**:

| 항목 | 평가 |
|------|------|
| 브라우저 호환성 | ✅ 우수 (모든 모던 브라우저 지원) |
| HTTPS 요구사항 | ✅ 불필요 (HTTP에서도 동작) |
| 디버깅 | ✅ 용이 (일반 JavaScript 디버깅) |
| 페이지 종료 후 동기화 | ❌ 불가능 (페이지 종료 시 동기화 중단) |

---

#### Service Worker 사용하는 경우

**구현 방식**:
- Service Worker 등록 및 설치
- `fetch` 이벤트로 네트워크 요청 가로채기
- Background Sync API 사용
- Cache API로 리소스 캐싱

**성능 측면**:

| 항목 | 평가 |
|------|------|
| 초기 로딩 | ⚠️ 약간 느림 (Service Worker 등록/설치 시간) |
| 메모리 사용 | ⚠️ 약간 많음 (별도 워커 프로세스) |
| 구현 복잡도 | ❌ 높음 (등록, 업데이트, 버전 관리 필요) |
| 백그라운드 동기화 | ✅ 가능 (페이지가 닫혀도 동기화 가능) |

**신뢰성 측면**:

| 항목 | 평가 |
|------|------|
| 브라우저 호환성 | ⚠️ 제한적 (일부 기능은 일부 브라우저만 지원) |
| HTTPS 요구사항 | ❌ 필수 |
| 디버깅 | ⚠️ 복잡 (워커 컨텍스트 분리) |
| 페이지 종료 후 동기화 | ✅ 가능 (Background Sync) |

---

### 종합 비교표

| 측면 | Service Worker 없음 (현재 방식) | Service Worker 사용 |
|------|--------------------------------|---------------------|
| **성능 - 초기 로딩** | ✅ 빠름 | ⚠️ 약간 느림 |
| **성능 - 메모리** | ✅ 적음 | ⚠️ 약간 많음 |
| **성능 - 백그라운드 동기화** | ❌ 불가능 | ✅ 가능 |
| **신뢰성 - 브라우저 호환성** | ✅ 우수 | ⚠️ 제한적 |
| **신뢰성 - 페이지 종료 후 동기화** | ❌ 불가능 | ✅ 가능 |
| **신뢰성 - 네트워크 제어** | ⚠️ 제한적 | ✅ 정교함 |
| **구현 복잡도** | ✅ 낮음 | ❌ 높음 |
| **HTTPS 요구사항** | ✅ 불필요 | ❌ 필수 |

---

### 최종 결정: Service Worker 사용하지 않음

**이유**:

1. **현재 요구사항 충족**
   - 오프라인 메모 작성: IndexedDB로 충분
   - 네트워크 복구 시 동기화: `online` 이벤트로 충분
   - 페이지가 열려있는 동안 동기화 가능하면 충분

2. **구현 복잡도 대비 이점 제한적**
   - 페이지 종료 후 동기화는 현재 요구사항에 없음
   - Background Sync는 브라우저 지원 제한적
   - 추가 복잡도 대비 실익이 작음

3. **유지보수성**
   - 단순한 구조로 디버깅과 유지보수 용이
   - HTTPS 필수 요구사항 없음

**Service Worker를 고려해야 하는 경우**:
- 페이지 종료 후에도 동기화가 필수인 경우
- 오프라인에서도 정적 리소스(이미지, CSS 등) 제공이 필요한 경우
- 네트워크 요청에 대한 세밀한 제어가 필요한 경우
- PWA(Progressive Web App) 기능 확장 계획이 있는 경우

---

## 캐싱 전략 분석

### 질문: 메모 작성 기능에서 Service Worker의 캐싱 응답 제공이 필요한가?

### 분석 결과

#### 메모 작성 (POST) - 캐싱 불필요

**이유**:
- POST는 생성 작업이므로 캐싱 대상이 아님
- 오프라인 메모 작성은 IndexedDB에 저장하고, 온라인 시 서버로 전송하는 구조
- Service Worker 캐싱은 주로 GET 요청에 유용

**현재 구현 방식**:
```javascript
// 오프라인 메모 작성
async createMemo(memoData) {
    // 1. IndexedDB에 즉시 저장
    const localMemo = await dbManager.saveMemo(memoData);
    
    // 2. 온라인 시 서버로 전송 (동기화 큐 사용)
    if (networkMonitor.isOnline) {
        await syncToServer(localMemo);
    }
    
    return localMemo; // 로컬 데이터 반환
}
```

**결론**: POST 요청은 캐싱하지 않음

---

#### 메모 조회 (GET) - 캐싱 유용하지만 선택적

**현재 구현 방식**:
```javascript
// 메모 목록 조회
async getMemos(userBookId, date) {
    // 1. 로컬 메모 조회 (IndexedDB)
    const localMemos = await offlineMemoService.getMemosByBook(userBookId);
    
    // 2. 온라인 상태면 서버에서도 조회하여 통합
    if (networkMonitor.isOnline) {
        const serverMemos = await apiClient.get(`/memos/books/${userBookId}`);
        return this.mergeMemos(localMemos, serverMemos);
    } else {
        // 오프라인: 로컬 메모만 반환
        return this.mapLocalMemosToResponse(localMemos);
    }
}
```

**캐싱을 추가할 때의 이점**:
1. 서버에서 받은 다른 사용자의 메모를 오프라인에서도 조회 가능
2. 네트워크 요청 감소로 성능 개선
3. 캐시된 데이터로 빠른 초기 로딩

**캐싱을 추가할 때의 단점**:
1. 캐시 무효화 관리 필요 (메모 추가/수정/삭제 시)
2. 구현 복잡도 증가
3. IndexedDB와 중복 저장

---

### 최종 권장사항

**POST 요청 (메모 작성/수정/삭제)**:
- 캐싱 불필요
- IndexedDB 기반 오프라인 처리로 충분

**GET 요청 (메모 조회)**:
- 선택적
- 현재 IndexedDB로 오프라인 조회가 가능하므로 필수는 아님
- 다만, 서버에서 받은 최신 메모를 오프라인에서도 보려면 유용

**구현 우선순위**:
1. 현재 방식 유지 (IndexedDB 기반) - 이미 충분
2. 필요 시 GET 요청 캐싱 추가 (서버 메모 오프라인 조회)
3. POST 요청은 캐싱하지 않음

**결론**: 메모 작성 기능에서는 Service Worker의 캐싱 응답 제공이 필수는 아님. 현재 IndexedDB 기반 방식으로 충분하며, GET 요청 캐싱은 선택적으로 고려할 수 있음.

---

## 오프라인 메모 조회 시나리오 분석

### 시나리오 가정

1. **오늘의 흐름 화면 진입 (온라인 상태)**
   - 서버에서 당일 작성한 메모 조회
   - 화면에 표시

2. **네트워크 연결 끊김**
   - 사용자가 계속 메모 작성

3. **질문**
   - 새로 작성한 메모가 즉시 화면에 표시되는가?
   - 초기에 불러온 기존 메모들이 온전히 출력되는가?

---

### 분석 결과

#### 질문 1: 새로 작성한 메모가 즉시 화면에 표시되는가?

**답변**: 구현 방식에 따라 다릅니다.

**Case A: 메모 작성 후 UI 자동 업데이트가 구현된 경우**
```javascript
// 메모 작성 후
const newMemo = await memoService.createMemo(memoData);

// UI에 즉시 추가
renderMemo(newMemo); // ✅ 즉시 표시됨
```

**결과**: ✅ 즉시 표시됨

**Case B: 메모 작성 후 UI 자동 업데이트가 없는 경우**
```javascript
// 메모 작성만 하고 UI 업데이트 없음
await memoService.createMemo(memoData);
// ❌ 화면에 표시되지 않음 (수동 새로고침 필요)
```

**결과**: ❌ 표시되지 않음

**권장**: 메모 작성 후 반환된 로컬 메모를 즉시 UI에 추가하도록 구현

---

#### 질문 2: 초기에 불러온 기존 메모들이 온전히 출력되는가?

**답변**: 현재 구현에서는 문제가 있을 수 있습니다.

**문제점 분석**:

1. **초기 진입 시 (온라인 상태)**
   ```
   서버에서 메모 조회 → 메모리/변수에 저장 → 화면 표시
   ```
   - 서버에서 받은 메모들이 IndexedDB에 저장되지 않음
   - 메모리/상태 관리에만 존재

2. **네트워크 끊김 후 메모 조회 시**
   ```javascript
   // 오프라인 상태
   if (networkMonitor.isOnline) {
       // ❌ 실행되지 않음
   } else {
       // 로컬 메모만 반환
       return this.mapLocalMemosToResponse(localMemos);
   }
   ```
   - IndexedDB의 로컬 메모만 반환
   - 초기에 서버에서 받은 메모들이 IndexedDB에 없으면 사라짐

**해결 방안**: 초기 서버 메모를 IndexedDB에 저장

```javascript
async getMemos(userBookId, date) {
    const localMemos = await offlineMemoService.getMemosByBook(userBookId);
    
    if (networkMonitor.isOnline) {
        const serverMemos = await apiClient.get(`/memos/books/${userBookId}`);
        
        // ✅ 서버 메모를 IndexedDB에 저장
        for (const serverMemo of serverMemos) {
            await offlineMemoService.saveServerMemoAsLocal(serverMemo);
        }
        
        return this.mergeMemos(localMemos, serverMemos);
    } else {
        // 오프라인: IndexedDB에서 모든 메모 조회
        return this.mapLocalMemosToResponse(localMemos);
    }
}
```

---

## 용량 및 성능 영향 분석

### 질문: 초기 서버 메모를 IndexedDB에 저장하면 과부하 영향이 있나?

### 용량 영향 분석

#### 메모 데이터 크기 계산

**단일 메모 크기**:
- 최소: ~11KB (짧은 메모)
- 평균: ~12KB (일반 메모)
- 최대: ~15KB (긴 메모 + 많은 태그)

**시나리오별 용량 계산**:

| 사용자 유형 | 하루 메모 수 | 하루 용량 | 한 달 용량 | 한 해 용량 |
|------------|------------|----------|-----------|-----------|
| 일반 사용자 | 10개 | 120KB | 3.6MB | 43.2MB |
| 활발한 사용자 | 50개 | 600KB | 18MB | 216MB |
| 매우 활발한 사용자 | 100개 | 1.2MB | 36MB | 432MB |

**IndexedDB 용량 제한**:
- Chrome/Edge: 디스크 여유 공간의 50% (최소 수백 MB ~ 수 GB)
- Firefox: 디스크 여유 공간의 50% (최소 수백 MB)
- Safari: 약 1GB

**결론**: 일반적인 사용 패턴에서는 용량 문제가 거의 없습니다.

---

### 성능 영향 분석

#### IndexedDB 성능 특성

**장점**:
- 비동기 API (논블로킹)
- 인덱싱 지원 (빠른 조회)
- 대용량 데이터 처리 가능

**잠재적 문제**:
- 메모 수가 매우 많을 때 (수만 개 이상)
  - 조회 속도 저하 가능
  - 초기 로딩 시간 증가

**성능 영향 시나리오**:

| 메모 수 | 조회 시간 | 저장 시간 | 평가 |
|--------|----------|----------|------|
| 1,000개 | ~50-100ms | ~10-20ms per memo | ✅ 문제 없음 |
| 10,000개 | ~200-500ms | ~10-20ms per memo | ⚠️ 약간 느려질 수 있음 |
| 100,000개 | ~1-3초 | ~10-20ms per memo | ❌ 성능 저하 발생 |

---

### 결론

**질문 1: 과부하 영향이 있나요?**

**답변**: 일반적인 사용 패턴에서는 문제 없습니다.

**이유**:
- 메모 1개: ~12KB
- 하루 50개: ~600KB
- 한 달: ~18MB
- IndexedDB 용량 제한: 수백 MB ~ 수 GB

**주의가 필요한 경우**:
- 메모 수가 수만 개 이상일 때 성능 저하 가능
- 해결: 데이터 정리 전략 적용

---

## 데이터 정리 전략 비교

### 전략 1: 동기화 완료 후 삭제

**구현 방식**:
```javascript
// 동기화 완료 후 로컬 메모 삭제
async syncSingleMemo(localMemo) {
    try {
        // 서버로 동기화
        const response = await apiClient.post('/memos', {...});
        
        // ✅ 동기화 완료 후 즉시 삭제
        await dbManager.deleteMemo(localMemo.localId);
        
        return response.data;
    } catch (error) {
        // 동기화 실패 시 유지
        throw error;
    }
}
```

**장점**:
- ✅ 용량 절약
- ✅ 성능 유지
- ✅ 동기화 완료된 메모는 서버에서 조회 가능

**단점**:
- ❌ 오프라인에서 동기화 완료된 메모 조회 불가 (서버 조회 필요)

**문제 시나리오**:
```
[시간 1] 오전 10:00 - 메모 A 작성 → IndexedDB 저장
[시간 2] 오전 10:05 - 동기화 완료 → IndexedDB에서 삭제 ❌
[시간 3] 오전 11:00 - 네트워크 끊김
[시간 4] 오전 11:30 - 오프라인 조회 → 메모 A 없음 ❌
```

---

### 전략 2: 오래된 메모 자동 삭제 (TTL 기반)

**구현 방식**:
```javascript
// 일정 기간(예: 30일) 이상 된 동기화 완료 메모 삭제
async cleanupOldSyncedMemos() {
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
    
    const syncedMemos = await dbManager.getSyncedMemos();
    
    for (const memo of syncedMemos) {
        const updatedAt = new Date(memo.updatedAt);
        if (updatedAt < thirtyDaysAgo) {
            await dbManager.deleteMemo(memo.localId);
        }
    }
}
```

**장점**:
- ✅ 최근 메모는 오프라인에서 조회 가능
- ✅ 용량 자동 관리
- ✅ 사용자 개입 불필요

**단점**:
- ⚠️ 오래된 메모는 오프라인에서 조회 불가

---

### 전략 3: 하이브리드 전략 (권장) ⭐

**구현 방식**:
```javascript
class MemoCleanupStrategy {
    /**
     * 동기화 완료 후 처리
     */
    async afterSyncComplete(localMemo, serverMemo) {
        // 1. 동기화 완료 상태로 업데이트
        await dbManager.updateMemoWithServerId(localMemo.localId, serverMemo.id);
        
        // 2. 최근 메모만 보관 (예: 최근 7일)
        const sevenDaysAgo = new Date();
        sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
        
        const memoDate = new Date(localMemo.memoStartTime);
        
        if (memoDate < sevenDaysAgo) {
            // 7일 이상 된 메모는 삭제 (서버에서 조회 가능)
            await dbManager.deleteMemo(localMemo.localId);
        } else {
            // 최근 7일 메모는 보관 (오프라인 조회용)
            // syncStatus는 'synced'로 유지
        }
    }
    
    /**
     * 주기적 정리 작업
     */
    async periodicCleanup() {
        // 1. 동기화 완료된 오래된 메모 삭제 (30일 이상)
        await this.cleanupOldSyncedMemos(30);
        
        // 2. 동기화 실패한 메모는 유지 (재시도 필요)
        // 3. 동기화 대기 중인 메모는 유지
    }
}
```

**장점**:
- ✅ 최근 메모는 오프라인에서 조회 가능
- ✅ 용량 자동 관리
- ✅ 성능 유지

---

## 하이브리드 전략 선택

### 최종 권장 전략: 하이브리드 전략 + 조건부 저장

**핵심 원칙**:
1. **최근 7일 메모 보관**: IndexedDB에 보관하여 오프라인 조회 지원
2. **오래된 메모 정리**: 30일 이상 된 동기화 완료 메모는 자동 삭제
3. **동기화 대기 메모 유지**: 재시도를 위해 유지

**구현 예시**:

```javascript
// 개선된 메모 조회 로직
async getMemos(userBookId, date) {
    const localMemos = await offlineMemoService.getMemosByBook(userBookId);
    
    if (networkMonitor.isOnline) {
        const serverMemos = await apiClient.get(`/memos/books/${userBookId}`);
        
        // ✅ 조건부 저장: 최근 메모만 IndexedDB에 저장
        const today = new Date();
        const sevenDaysAgo = new Date(today);
        sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
        
        for (const serverMemo of serverMemos) {
            const memoDate = new Date(serverMemo.memoStartTime || serverMemo.createdAt);
            
            // 최근 7일 메모만 IndexedDB에 저장
            if (memoDate >= sevenDaysAgo) {
                await offlineMemoService.saveServerMemoAsLocal(serverMemo);
            }
            // 오래된 메모는 저장하지 않음 (서버에서만 조회)
        }
        
        return this.mergeMemos(localMemos, serverMemos);
    } else {
        // 오프라인: IndexedDB에서 최근 메모만 조회
        return this.mapLocalMemosToResponse(localMemos);
    }
}

// 동기화 완료 후 정리
async afterSyncComplete(localMemo, serverMemo) {
    // 1. 서버 ID 업데이트
    await dbManager.updateMemoWithServerId(localMemo.localId, serverMemo.id);
    
    // 2. 최근 7일 메모만 보관, 나머지는 삭제
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
    const memoDate = new Date(localMemo.memoStartTime);
    
    if (memoDate < sevenDaysAgo) {
        await dbManager.deleteMemo(localMemo.localId);
    }
}
```

---

### 하이브리드 전략의 장점

1. **용량 관리**: 오래된 메모 자동 삭제
2. **오프라인 지원**: 최근 메모는 오프라인에서 조회 가능
3. **성능 유지**: IndexedDB 크기 제한

---

### 구현 권장사항 요약

1. **조건부 저장**: 최근 7일 메모만 IndexedDB에 저장
2. **자동 정리**: 30일 이상 된 동기화 완료 메모 삭제
3. **동기화 대기 메모**: 유지 (재시도 필요)
4. **주기적 정리**: 일일 또는 주간 정리 작업 실행

이 전략으로 용량과 성능을 유지하면서 오프라인 기능을 제공할 수 있습니다.

---

## 참고 자료

### 관련 문서
- [오프라인 메모 작성 및 동기화 설계](./OFFLINE_MEMO_SYNC.md): 하이브리드 전략이 반영된 상세 구현 가이드

### 외부 자료
- [IndexedDB API](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API)
- [Offline-First Architecture](https://offlinefirst.org/)
- [Service Workers](https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API)

---

## 결론

1. **Service Worker**: 사용하지 않음 (현재 요구사항 충족)
2. **캐싱 전략**: POST는 불필요, GET은 선택적
3. **초기 서버 메모 저장**: 조건부 저장 (최근 7일만)
4. **데이터 정리 전략**: 하이브리드 전략 채택
   - 최근 7일 메모 보관
   - 30일 이상 된 메모 자동 삭제
   - 동기화 대기 메모 유지

이러한 결정을 통해 용량과 성능을 유지하면서 오프라인 기능을 제공할 수 있습니다.

