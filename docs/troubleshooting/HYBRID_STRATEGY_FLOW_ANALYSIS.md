# 하이브리드 전략 실제 동작 흐름 분석

> **작성일**: 2026-01-07  
> **목적**: 현재 구현된 하이브리드 전략의 실제 동작 흐름 확인 및 사용자 설명과의 비교  
> **상태**: ✅ 분석 완료

---

## 목차

1. [사용자 설명](#1-사용자-설명)
2. [실제 구현된 흐름](#2-실제-구현된-흐름)
3. [사용자 설명 vs 실제 구현 비교](#3-사용자-설명-vs-실제-구현-비교)
4. [차이점 및 주의사항](#4-차이점-및-주의사항)
5. [결론](#5-결론)
6. [권장 사항](#6-권장-사항)
7. [참고 파일](#7-참고-파일)

---

## 1. 사용자 설명

> "네트워크가 활성화되어 알라딘api 호출이 가능한 상태일 때는, DB의 데이터를 먼저 Write/Read합니다. 그리고 네트워크가 비활성화되어 알라딘 api호출이 불가능한 상황에서는, 오프라인 저장소인 IndexedDB를 먼저 사용합니다."

---

## 2. 실제 구현된 흐름

### 2-1. 네트워크 상태 판단 기준

**현재 구현**: `networkMonitor.isOnline` 사용
- `networkMonitor.isOnline`: 로컬 백엔드 서버 연결 가능 여부
- `networkMonitor.isExternalServiceReachable`: 알라딘 API 연결 가능 여부 (현재 서비스 로직에서 미사용)

**주의사항**: 
- 실제 서비스 로직(`book-service.js`, `memo-service.js`)에서는 **알라딘 API 호출 가능 여부(`isExternalServiceReachable`)를 확인하지 않음**
- **로컬 서버 연결 가능 여부(`isOnline`)만 확인**하여 온라인/오프라인을 판단

---

### 2-2. 실제 동작 흐름

#### 2-2-1. 온라인 상태 (`networkMonitor.isOnline === true`)

**Write 작업 (생성/수정/삭제)**:
```
1. 서버(백엔드 DB)에 먼저 Write 시도
   ↓
2. 성공 시 → IndexedDB 갱신 (캐시 저장)
   ↓
3. 서버 응답 반환
```

**Read 작업 (조회)**:
```
1. 서버(백엔드 DB)에서 먼저 Read 시도
   ↓
2. 성공 시 → IndexedDB에 캐시로 저장 (오프라인 대비)
   ↓
3. 서버 데이터 반환 (IndexedDB 읽기 안 함)
   ↓
4. 서버 실패 시에만 → IndexedDB에서 폴백 조회
```

**구현 예시** (`book-service.js`, `memo-service.js`):
```javascript
if (networkMonitor.isOnline) {
  // 온라인: 서버 우선 전략
  try {
    // 1. 서버에서 먼저 처리
    const serverResponse = await apiClient.post(...);
    // 2. 성공 시 IndexedDB 갱신
    await BookOperationHelper.updateLocalAfterCreate(serverResponse);
    return serverResponse;
  } catch (error) {
    // 서버 실패 시 오프라인 모드로 전환
    return await offlineBookService.addBookToShelf(bookData);
  }
}
```

---

#### 2-2-2. 오프라인 상태 (`networkMonitor.isOnline === false`)

**Write 작업 (생성/수정/삭제)**:
```
1. IndexedDB에 먼저 저장
   ↓
2. 동기화 큐에 추가 (온라인 복구 시 서버로 전송)
   ↓
3. 로컬 데이터 반환
```

**Read 작업 (조회)**:
```
1. IndexedDB에서만 조회
   ↓
2. 로컬 데이터 반환
```

**구현 예시**:
```javascript
else {
  // 오프라인: 로컬 우선 전략
  const localBook = await offlineBookService.addBookToShelf(bookData);
  return this.mapLocalBookToResponse(localBook);
}
```

---

## 3. 사용자 설명 vs 실제 구현 비교

| 항목 | 사용자 설명 | 실제 구현 | 일치 여부 |
|------|------------|---------|----------|
| **온라인 상태 기준** | 알라딘 API 호출 가능 여부 | 로컬 서버 연결 가능 여부 (`isOnline`) | ⚠️ **부분 일치** |
| **온라인 Write** | DB 먼저 Write | ✅ 서버(백엔드 DB) 먼저 Write → IndexedDB 갱신 | ✅ **일치** |
| **온라인 Read** | DB 먼저 Read | ✅ 서버(백엔드 DB) 먼저 Read → IndexedDB 캐시 | ✅ **일치** |
| **오프라인 Write** | IndexedDB 먼저 사용 | ✅ IndexedDB 먼저 저장 | ✅ **일치** |
| **오프라인 Read** | IndexedDB 먼저 사용 | ✅ IndexedDB에서만 조회 | ✅ **일치** |

---

## 4. 차이점 및 주의사항

### 4-1. 네트워크 상태 판단 기준의 차이

**사용자 설명**: "알라딘 API 호출이 가능한 상태"  
**실제 구현**: "로컬 백엔드 서버 연결 가능 여부"

**영향**:
- 알라딘 API는 연결 불가하지만 로컬 서버는 연결 가능한 경우:
  - 현재 구현: **온라인 상태로 판단** → 서버(DB) 우선 사용
  - 사용자 설명: **오프라인 상태로 판단** → IndexedDB 우선 사용

**예시 시나리오**:
```
상황: 로컬 서버는 접근 가능하지만 알라딘 API는 접근 불가
- networkMonitor.isOnline = true
- networkMonitor.isExternalServiceReachable = false

현재 동작:
- Write: 서버(DB) 먼저 Write → IndexedDB 갱신 ✅
- Read: 서버(DB) 먼저 Read → IndexedDB 캐시 ✅
- 도서 검색: 알라딘 API 호출 불가 ❌ (검색 기능 제한)

사용자 설명에 따르면:
- Write: IndexedDB 먼저 저장 ❌ (실제와 다름)
- Read: IndexedDB 먼저 조회 ❌ (실제와 다름)
```

---

### 4-2. 알라딘 API 상태 확인 미사용

**현재 상황**:
- `networkMonitor`는 `isExternalServiceReachable`로 알라딘 API 상태를 확인함
- 하지만 실제 서비스 로직(`book-service.js`, `memo-service.js`)에서는 이 값을 사용하지 않음
- 오직 `isOnline` (로컬 서버 연결 가능 여부)만 사용

**코드 위치**:
- `network-monitor.js`: `checkExternalServiceHealth()` 메서드로 알라딘 API 상태 확인
- `book-service.js`, `memo-service.js`: `networkMonitor.isOnline`만 확인

**구현 세부사항**:
```javascript
// network-monitor.js
async onNetworkOnline() {
  // 1단계: 로컬 백엔드 서버 연결 가능 여부 확인
  this.isLocalServerReachable = await this.checkServerHealth();
  
  // 2단계: 외부 서비스(알라딘 API) 연결 가능 여부 확인
  this.isExternalServiceReachable = await this.checkExternalServiceHealth();
  
  // isOnline은 isLocalServerReachable과 동기화됨
  this.isOnline = this.isLocalServerReachable;
}

// book-service.js, memo-service.js
if (networkMonitor.isOnline) {  // ← isExternalServiceReachable 미사용
  // 서버 우선 처리
}
```

---

## 5. 결론

### 5-1. 사용자 설명과 실제 구현의 일치 여부

**✅ 일치하는 부분**:
1. 온라인 상태에서 Write: 서버(DB) 먼저 → IndexedDB 갱신
2. 온라인 상태에서 Read: 서버(DB) 먼저 → IndexedDB 캐시
3. 오프라인 상태에서 Write: IndexedDB 먼저 저장
4. 오프라인 상태에서 Read: IndexedDB 먼저 조회

**⚠️ 차이가 있는 부분**:
1. **네트워크 상태 판단 기준**:
   - 사용자 설명: "알라딘 API 호출 가능 여부"
   - 실제 구현: "로컬 서버 연결 가능 여부"

### 5-2. 실제 동작 흐름 요약

```
온라인 상태 (isOnline === true):
  → 서버(백엔드 DB) 먼저 Write/Read → IndexedDB 갱신/캐시

오프라인 상태 (isOnline === false):
  → IndexedDB 먼저 사용
```

---

## 6. 권장 사항

### 6-1. 옵션 1: 현재 구현 유지 (권장) ⭐

**이유**:
- 로컬 서버 연결 가능 여부가 더 중요한 기준
- 알라딘 API는 도서 검색에만 필요 (데이터 저장/조회와 무관)
- 현재 구현이 더 합리적

**수정 필요 없음**: 현재 구현이 올바름

---

### 6-2. 옵션 2: 알라딘 API 상태도 고려 (선택적)

**수정 방안**:
- Write/Read 작업은 `isOnline` 기준 유지 (로컬 서버 연결 가능 여부)
- 도서 검색 기능만 `isExternalServiceReachable` 확인

**현재 상태**: 이미 도서 검색에서 알라딘 API 상태를 확인함 (`network-monitor.js`)

**구현 예시**:
```javascript
// 도서 검색 기능에서만 알라딘 API 상태 확인
if (networkMonitor.isExternalServiceReachable) {
  // 알라딘 API 호출 가능 → 검색 기능 활성화
} else {
  // 알라딘 API 호출 불가 → 검색 기능 제한
  showToast('⚠️ 외부 서비스 연결 불가. 검색 제한됨.', 'warning');
}
```

---

## 7. 참고 파일

### 프론트엔드 파일

- `js/utils/network-monitor.js`: 네트워크 상태 모니터링 (2단계 헬스체크)
- `js/utils/network-state-manager.js`: 네트워크 상태 관리 (이벤트 기반)
- `js/services/book-service.js`: 도서 서비스 (하이브리드 전략 적용)
- `js/services/memo-service.js`: 메모 서비스 (하이브리드 전략 적용)
- `js/services/offline-book-service.js`: 오프라인 도서 서비스
- `js/services/offline-memo-service.js`: 오프라인 메모 서비스

### 관련 문서

- `docs/troubleshooting/OFFLINE_HYBRID_STRATEGY_COMPLETE_ANALYSIS.md`: 하이브리드 전략 완전 분석
- `docs/troubleshooting/OFFLINE_ONLINE_HYBRID_STRATEGY_ANALYSIS.md`: 오프라인/온라인 하이브리드 전략 분석

---

## 부록: 코드 흐름 다이어그램

### 온라인 상태 Write 작업 흐름

```
사용자 요청
    ↓
networkMonitor.isOnline === true?
    ↓ YES
서버(백엔드 DB) Write 시도
    ↓
성공?
    ↓ YES
IndexedDB 갱신 (캐시 저장)
    ↓
서버 응답 반환
    ↓
실패?
    ↓ YES
오프라인 모드로 전환
    ↓
IndexedDB에 저장
    ↓
동기화 큐에 추가
```

### 오프라인 상태 Write 작업 흐름

```
사용자 요청
    ↓
networkMonitor.isOnline === false?
    ↓ YES
IndexedDB에 먼저 저장
    ↓
동기화 큐에 추가
    ↓
로컬 데이터 반환
```

### 온라인 상태 Read 작업 흐름

```
사용자 요청
    ↓
networkMonitor.isOnline === true?
    ↓ YES
서버(백엔드 DB) Read 시도
    ↓
성공?
    ↓ YES
IndexedDB에 캐시 저장 (비동기)
    ↓
서버 데이터 반환
    ↓
실패?
    ↓ YES
IndexedDB에서 폴백 조회
    ↓
로컬 데이터 반환
```

### 오프라인 상태 Read 작업 흐름

```
사용자 요청
    ↓
networkMonitor.isOnline === false?
    ↓ YES
IndexedDB에서만 조회
    ↓
로컬 데이터 반환
```
