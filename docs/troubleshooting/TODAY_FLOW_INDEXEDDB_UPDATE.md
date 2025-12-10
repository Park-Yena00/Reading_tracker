# 오늘의 흐름 화면 IndexedDB 업데이트 개선

> **작성일**: 2025-12-09  
> **목적**: 오늘의 흐름 화면 접속 시 IndexedDB에 최신 메모 데이터를 저장하도록 개선  
> **적용 범위**: `memo-service.js`의 `getTodayFlow` 메서드

---

## 문제 원인

### 현재 상황

**기존 로직 (`getTodayFlow`)**:
- 서버 API를 직접 호출하여 오늘의 흐름 데이터를 조회
- 하이브리드 전략이 적용되지 않음
- 서버에서 조회한 메모 데이터를 IndexedDB에 저장하지 않음
- 오프라인 상태에서 오늘의 흐름 화면 접근 시 데이터 부재

**문제점**:
1. **IndexedDB 미갱신**: 서버에서 조회한 최신 메모 데이터가 IndexedDB에 저장되지 않아, 이후 오프라인 상태에서 해당 데이터를 사용할 수 없음
2. **오프라인 지원 부족**: 오늘의 흐름 화면이 오프라인 상태에서 동작하지 않음
3. **일관성 부족**: 다른 메모 조회 기능(`getMemosByBook`)은 하이브리드 전략을 사용하지만, 오늘의 흐름은 사용하지 않음

---

## 로직 개선 방향

### 개선 전략: 방법 2 (후처리 저장)

**선택 이유**:
- 기존 로직 구조를 최대한 유지하면서 IndexedDB 저장 기능만 추가
- 복잡도가 낮고 빠르게 적용 가능
- 기존 동작에 영향을 최소화

### 개선 내용

#### 1. 하이브리드 전략 적용

**동작 흐름**:
1. **로컬 메모 조회**: IndexedDB에서 날짜별 로컬 메모 조회
2. **네트워크 상태 확인**: 온라인/오프라인 상태 확인
3. **동기화 상태 확인**: 동기화 중이면 로컬 데이터만 반환
4. **동기화 완료 대기**: 동기화 완료 후 서버 조회 진행
5. **서버 조회**: 서버에서 오늘의 흐름 데이터 조회
6. **IndexedDB 저장**: 서버 응답 데이터를 IndexedDB에 저장 (후처리)
7. **데이터 반환**: 서버 데이터 반환 (서버 데이터가 최신이므로 우선)

#### 2. 동기화 중 처리

**동기화 중 체크 (`syncStateManager.isSyncing`)**:
- 동기화 중이면 서버 조회를 하지 않고 로컬 데이터만 반환
- 이유: 동기화 중에는 서버 데이터가 아직 완전하지 않을 수 있고, 로컬 데이터와 충돌 가능

**동기화 완료 대기 (`syncStateManager.waitForSyncComplete()`)**:
- 동기화 완료 후 서버 조회 진행
- 최대 30초 대기 (타임아웃 시 로컬 데이터 반환)

#### 3. IndexedDB 저장 실패 처리

**에러 처리**:
- IndexedDB 저장 실패 시에도 렌더링은 계속 진행
- 서버에서 조회한 데이터(`response`)는 이미 메모리에 있으므로, 저장 실패와 무관하게 렌더링 가능
- 에러는 로깅만 하고 사용자 경험에는 영향 없음

---

## 구현 내용

### 1. `getTodayFlow` 메서드 개선

**변경 사항**:
- 하이브리드 전략 적용 (로컬 먼저 조회 → 서버 조회 → 통합)
- 동기화 중 체크 추가
- 동기화 완료 대기 추가
- 서버 응답 데이터를 IndexedDB에 저장하는 로직 추가

**코드 위치**: `js/services/memo-service.js`

### 2. 헬퍼 메서드 추가

#### `getLocalMemosByDate(date)`
- 날짜별 로컬 메모 조회
- 모든 로컬 메모를 조회한 후 날짜로 필터링

#### `mapLocalMemosToTodayFlowResponse(localMemos, sortBy, tagCategory)`
- 로컬 메모를 `TodayFlowResponse` 형식으로 변환
- `sortBy`에 따라 `memosByBook` 또는 `memosByTag` 형식으로 그룹화

### 3. IndexedDB 저장 로직

**저장 시점**: 서버 조회 성공 후, 응답 데이터를 IndexedDB에 저장

**저장 대상**:
- `memosByBook`의 모든 메모
- `memosByTag`의 모든 메모

**저장 방식**:
- `MemoOperationHelper.saveServerMemoAsLocal()` 사용
- 하이브리드 전략: 최근 7일 메모만 저장

---

## 개선 결과

### 개선 전

```javascript
async getTodayFlow({ date, sortBy = 'SESSION', tagCategory } = {}) {
  const params = {};
  if (date) params.date = date;
  if (sortBy) params.sortBy = sortBy;
  if (tagCategory) params.tagCategory = tagCategory;
  
  const response = await apiClient.get(API_ENDPOINTS.MEMOS.TODAY_FLOW, params);
  return response; // IndexedDB 저장 없음
}
```

**문제점**:
- IndexedDB에 최신 데이터가 저장되지 않음
- 오프라인 상태에서 오늘의 흐름 화면 접근 불가
- 하이브리드 전략 미적용

### 개선 후

```javascript
async getTodayFlow({ date, sortBy = 'SESSION', tagCategory } = {}) {
  // 1. 로컬 메모 조회
  const localMemos = await this.getLocalMemosByDate(date);

  // 2. 온라인 상태 확인
  if (networkMonitor.isOnline) {
    // 3. 동기화 중 체크
    if (syncStateManager.isSyncing) {
      return this.mapLocalMemosToTodayFlowResponse(localMemos, sortBy, tagCategory);
    }

    // 4. 동기화 완료 대기
    const isSyncComplete = await syncStateManager.waitForSyncComplete();
    if (!isSyncComplete) {
      return this.mapLocalMemosToTodayFlowResponse(localMemos, sortBy, tagCategory);
    }

    // 5. 서버 조회
    const serverResponse = await apiClient.get(API_ENDPOINTS.MEMOS.TODAY_FLOW, params);

    // 6. IndexedDB 저장 (후처리)
    try {
      // 서버 응답의 모든 메모를 IndexedDB에 저장
      for (const bookGroup of Object.values(serverResponse.memosByBook)) {
        for (const memo of bookGroup.memos) {
          await MemoOperationHelper.saveServerMemoAsLocal(memo);
        }
      }
    } catch (error) {
      console.error('IndexedDB 저장 실패, 렌더링은 계속 진행:', error);
    }

    // 7. 서버 데이터 반환
    return serverResponse;
  } else {
    // 오프라인: 로컬 데이터만 반환
    return this.mapLocalMemosToTodayFlowResponse(localMemos, sortBy, tagCategory);
  }
}
```

**개선점**:
- ✅ IndexedDB에 최신 데이터 저장
- ✅ 오프라인 상태에서도 로컬 데이터로 오늘의 흐름 화면 접근 가능
- ✅ 하이브리드 전략 적용으로 다른 메모 조회 기능과 일관성 유지
- ✅ 동기화 중 처리로 데이터 일관성 보장

---

## 동작 시나리오

### 시나리오 1: 정상 동작 (온라인, 동기화 완료)

1. 사용자가 오늘의 흐름 화면 접속
2. 로컬 메모 조회 (IndexedDB)
3. 온라인 상태 확인
4. 동기화 완료 확인
5. 서버에서 오늘의 흐름 조회
6. 서버 응답 데이터를 IndexedDB에 저장
7. 서버 데이터로 렌더링

**결과**: 최신 서버 데이터가 IndexedDB에 저장되고 화면에 표시됨

### 시나리오 2: 동기화 중

1. 사용자가 오늘의 흐름 화면 접속
2. 로컬 메모 조회 (IndexedDB)
3. 온라인 상태 확인
4. 동기화 중 확인 (`isSyncing === true`)
5. 로컬 데이터만 반환하여 렌더링

**결과**: 동기화 중에는 로컬 데이터만 사용하여 데이터 일관성 유지

### 시나리오 3: 오프라인

1. 사용자가 오늘의 흐름 화면 접속
2. 로컬 메모 조회 (IndexedDB)
3. 오프라인 상태 확인
4. 로컬 데이터만 반환하여 렌더링

**결과**: 오프라인 상태에서도 로컬 데이터로 화면 표시 가능

### 시나리오 4: IndexedDB 저장 실패

1. 사용자가 오늘의 흐름 화면 접속
2. 서버에서 오늘의 흐름 조회 성공
3. IndexedDB 저장 시도 → 실패
4. 에러 로깅
5. 서버 데이터로 렌더링 계속 진행

**결과**: IndexedDB 저장 실패해도 서버 데이터로 정상 렌더링

---

## 기술적 고려사항

### 1. 성능

**현재 구현**:
- 대량 메모 처리는 고려하지 않음 (향후 개선 필요)
- 순차적으로 IndexedDB에 저장 (배치 처리 미적용)

**향후 개선 방향**:
- 대량 메모 처리 시 배치 저장 고려
- 병렬 처리 또는 청크 단위 저장

### 2. 데이터 일관성

**보장 사항**:
- 동기화 중에는 서버 조회를 하지 않아 데이터 충돌 방지
- 동기화 완료 후 서버 조회하여 최신 데이터 반영
- 하이브리드 전략: 최근 7일 메모만 IndexedDB에 저장

### 3. 에러 처리

**IndexedDB 저장 실패**:
- 에러 로깅만 수행
- 렌더링은 서버 데이터로 계속 진행
- 사용자 경험에 영향 없음

**서버 조회 실패**:
- 로컬 데이터로 폴백
- 오프라인 모드로 전환

---

## 관련 파일

### 수정된 파일
- `js/services/memo-service.js`
  - `getTodayFlow()` 메서드: 하이브리드 전략 적용
  - `getLocalMemosByDate()` 메서드: 날짜별 로컬 메모 조회 추가
  - `mapLocalMemosToTodayFlowResponse()` 메서드: 로컬 메모를 TodayFlowResponse 형식으로 변환 추가

### 참조 파일
- `js/utils/sync-state-manager.js`: 동기화 상태 관리
- `js/utils/network-monitor.js`: 네트워크 상태 모니터링
- `js/utils/memo-operation-helper.js`: 메모 IndexedDB 저장 헬퍼
- `js/services/offline-memo-service.js`: 오프라인 메모 서비스

---

## 테스트 시나리오

### 테스트 1: 정상 동작
- [ ] 온라인 상태에서 오늘의 흐름 화면 접속
- [ ] 서버 데이터가 IndexedDB에 저장되는지 확인
- [ ] 화면에 최신 메모가 표시되는지 확인

### 테스트 2: 동기화 중
- [ ] 오프라인에서 메모 생성
- [ ] 네트워크 재연결 (동기화 시작)
- [ ] 동기화 중에 오늘의 흐름 화면 접속
- [ ] 로컬 데이터만 표시되는지 확인

### 테스트 3: 오프라인
- [ ] 오프라인 상태에서 오늘의 흐름 화면 접속
- [ ] 로컬 데이터가 표시되는지 확인

### 테스트 4: IndexedDB 저장 실패
- [ ] IndexedDB 저장 실패 시뮬레이션
- [ ] 에러 로깅 확인
- [ ] 렌더링이 정상적으로 진행되는지 확인

---

## 참고 사항

### 하이브리드 전략
- 최근 7일 메모만 IndexedDB에 저장
- 7일 이상 된 메모는 서버에서만 조회

### 동기화 상태 관리
- `syncStateManager.isSyncing`: 동기화 진행 중 여부
- `syncStateManager.waitForSyncComplete()`: 동기화 완료 대기 (최대 30초)

### 네트워크 상태 모니터링
- `networkMonitor.isOnline`: 온라인/오프라인 상태
- 2-Phase Health Check로 실제 서버 접근 가능 여부 확인

---

**문서 버전**: 1.0  
**최종 업데이트**: 2025-12-09  
**작성자**: Development Team



