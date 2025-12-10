# 오프라인 상태에서 도서 상세 정보 조회 오류 해결

> **작성일**: 2025-12-09  
> **목적**: 오프라인 상태에서 내 서재 화면의 도서 상세 정보 조회 오류 해결  
> **적용 범위**: `book-service.js`, `offline-book-service.js`, `book-detail-view.js`

---

## 문제 원인

### 오류 상황

**오류 메시지**:
```
GET http://localhost:8080/api/v1/books/K722930969 500 (Internal Server Error)
서재 도서 상세 정보 로드 오류: Error: 서버 내부 오류가 발생했습니다
```

**발생 위치**:
- `book-detail-view.js:287`: `bookService.getBookDetail(this.isbn)` 호출
- `book-service.js:43`: `apiClient.get(API_ENDPOINTS.BOOKS.DETAIL/${isbn})` 호출

### 원인 분석

#### 1. `getUserBookDetail` 메서드 문제

**기존 로직**:
```javascript
async getUserBookDetail(userBookId) {
  // 항상 서버 API 호출
  const response = await apiClient.get(API_ENDPOINTS.BOOKS.USER_BOOKS, {});
  const books = response.books || [];
  const userBook = books.find(book => book.userBookId === parseInt(userBookId));
  
  if (!userBook) {
    throw new Error('서재에 저장된 도서를 찾을 수 없습니다.');
  }
  
  return userBook;
}
```

**문제점**:
- 하이브리드 전략이 적용되지 않음
- 오프라인 상태에서 서버 API 호출 시도 → 실패
- IndexedDB에 저장된 내 서재 정보를 활용하지 못함

#### 2. `getBookDetail` 메서드 문제

**기존 로직**:
```javascript
async getBookDetail(isbn) {
  const response = await apiClient.get(`${API_ENDPOINTS.BOOKS.DETAIL}/${isbn}`);
  return response;
}
```

**문제점**:
- 오프라인 지원이 없음
- 항상 서버 API 호출 시도 → 오프라인에서 실패
- 내 서재 정보에 도서 기본 정보가 포함되어 있지만 활용하지 못함

#### 3. IndexedDB 데이터 미활용

**IndexedDB에 저장된 내 서재 정보 구조** (`offline_books`):
- 도서 기본 정보: `title`, `author`, `publisher`, `description`, `coverUrl`, `totalPages`, `mainGenre`, `pubDate`
- 서재 저장 정보: `category`, `expectation`, `lastReadPage`, `rating`, `review` 등

**문제점**:
- IndexedDB에 도서 기본 정보가 저장되어 있지만 활용하지 못함
- 오프라인 상태에서도 도서 상세 정보를 표시할 수 있는 데이터가 있음

---

## 로직 개선 방향

### 해결책 1: `getUserBookDetail`에 하이브리드 전략 적용

**개선 내용**:
1. IndexedDB에서 먼저 조회
2. 온라인 상태이고 동기화 완료 후 서버에서 조회
3. 서버 데이터를 IndexedDB에 저장
4. 오프라인 상태에서는 IndexedDB 데이터만 반환

**동작 흐름**:
```
1. IndexedDB에서 로컬 내 서재 정보 조회
2. 온라인 상태 확인
   - 동기화 중이면 로컬 데이터만 반환
   - 동기화 완료 대기
3. 서버에서 조회
4. IndexedDB에 저장
5. 서버 데이터 반환 (서버가 최신)
```

### 해결책 2: `getBookDetail`에 오프라인 지원 추가

**개선 내용**:
1. 오프라인 상태에서는 내 서재 정보에서 도서 기본 정보 추출
2. 온라인 상태에서는 서버에서 조회
3. 서버 조회 실패 시 내 서재 정보에서 추출 (폴백)

**동작 흐름**:
```
1. 오프라인 상태 확인
   - 오프라인: IndexedDB에서 ISBN으로 검색하여 도서 기본 정보 추출
   - 온라인: 서버에서 조회
2. 서버 조회 실패 시 내 서재 정보에서 추출 (폴백)
```

### 해결책 3: `loadUserBookDetail`에서 오프라인 처리 개선

**개선 내용**:
1. `getBookDetail` 실패 시 내 서재 정보에서 도서 기본 정보 추출 (추가 폴백)
2. 에러 처리 개선

**동작 흐름**:
```
1. getUserBookDetail 호출 (하이브리드 전략 적용)
2. getBookDetail 호출 (오프라인 지원)
3. getBookDetail 실패 시 내 서재 정보에서 추출 (추가 폴백)
```

---

## 구현 내용

### 1. `getUserBookDetail` 메서드 개선

**변경 사항**:
- 하이브리드 전략 적용 (로컬 먼저 조회 → 서버 조회 → 통합)
- 동기화 중 체크 추가
- 동기화 완료 대기 추가
- 서버 응답 데이터를 IndexedDB에 저장하는 로직 추가

**코드 위치**: `js/services/book-service.js`

### 2. `getBookDetail` 메서드 개선

**변경 사항**:
- 오프라인 상태에서 내 서재 정보에서 도서 기본 정보 추출
- 서버 조회 실패 시 내 서재 정보에서 추출 (폴백)

**코드 위치**: `js/services/book-service.js`

### 3. `loadUserBookDetail` 메서드 개선

**변경 사항**:
- `getBookDetail` 실패 시 내 서재 정보에서 도서 기본 정보 추출 (추가 폴백)
- 에러 처리 개선

**코드 위치**: `js/views/pages/book-detail-view.js`

### 4. `offlineBookService`에 `getBookByServerId` 메서드 추가

**변경 사항**:
- 서버 ID로 내 서재 정보를 조회하는 메서드 추가

**코드 위치**: `js/services/offline-book-service.js`

---

## 개선 결과

### 개선 전

**오프라인 상태에서 도서 상세 정보 조회 시**:
1. `getUserBookDetail` 호출 → 서버 API 호출 시도 → 실패 ❌
2. `getBookDetail` 호출 → 서버 API 호출 시도 → 실패 ❌
3. 에러 메시지 표시

**문제점**:
- IndexedDB에 데이터가 있어도 사용하지 못함
- 오프라인 상태에서 도서 상세 정보를 볼 수 없음

### 개선 후

**오프라인 상태에서 도서 상세 정보 조회 시**:
1. `getUserBookDetail` 호출 → IndexedDB에서 조회 → 성공 ✅
2. `getBookDetail` 호출 → IndexedDB에서 도서 기본 정보 추출 → 성공 ✅
3. 도서 상세 정보 표시

**개선점**:
- ✅ 오프라인 상태에서도 도서 상세 정보 표시 가능
- ✅ IndexedDB 데이터 활용
- ✅ 하이브리드 전략 적용으로 다른 기능과 일관성 유지

---

## 동작 시나리오

### 시나리오 1: 오프라인 상태 (정상 동작)

1. 사용자가 내 서재 화면에서 도서 카드 선택
2. `getUserBookDetail` 호출
   - IndexedDB에서 내 서재 정보 조회 → 성공
   - 오프라인 상태 확인 → 로컬 데이터 반환
3. `getBookDetail` 호출
   - 오프라인 상태 확인
   - IndexedDB에서 ISBN으로 검색하여 도서 기본 정보 추출 → 성공
4. 도서 상세 정보 표시

**결과**: 오프라인 상태에서도 정상적으로 도서 상세 정보 표시

### 시나리오 2: 온라인 상태 (정상 동작)

1. 사용자가 내 서재 화면에서 도서 카드 선택
2. `getUserBookDetail` 호출
   - IndexedDB에서 내 서재 정보 조회
   - 온라인 상태 확인
   - 동기화 완료 확인
   - 서버에서 조회 → 성공
   - IndexedDB에 저장
   - 서버 데이터 반환
3. `getBookDetail` 호출
   - 온라인 상태 확인
   - 서버에서 조회 → 성공
4. 도서 상세 정보 표시

**결과**: 최신 서버 데이터로 도서 상세 정보 표시

### 시나리오 3: 동기화 중

1. 사용자가 내 서재 화면에서 도서 카드 선택
2. `getUserBookDetail` 호출
   - IndexedDB에서 내 서재 정보 조회 → 성공
   - 온라인 상태 확인
   - 동기화 중 확인 → 로컬 데이터 반환
3. `getBookDetail` 호출
   - 온라인 상태 확인
   - 서버에서 조회 → 성공
4. 도서 상세 정보 표시

**결과**: 동기화 중에도 로컬 데이터로 도서 상세 정보 표시

### 시나리오 4: 서버 조회 실패 (폴백)

1. 사용자가 내 서재 화면에서 도서 카드 선택
2. `getUserBookDetail` 호출
   - IndexedDB에서 내 서재 정보 조회 → 성공
   - 온라인 상태 확인
   - 서버에서 조회 → 실패
   - 로컬 데이터 반환 (폴백)
3. `getBookDetail` 호출
   - 온라인 상태 확인
   - 서버에서 조회 → 실패
   - IndexedDB에서 도서 기본 정보 추출 → 성공 (폴백)
4. 도서 상세 정보 표시

**결과**: 서버 조회 실패해도 로컬 데이터로 도서 상세 정보 표시

---

## 기술적 고려사항

### 1. 데이터 일관성

**보장 사항**:
- 동기화 중에는 서버 조회를 하지 않아 데이터 충돌 방지
- 동기화 완료 후 서버 조회하여 최신 데이터 반영
- 서버 데이터를 IndexedDB에 저장하여 다음 오프라인 접근 시 사용 가능

### 2. 폴백 전략

**다중 폴백 레이어**:
1. **1차**: `getBookDetail` 내부에서 오프라인 상태 확인 → IndexedDB에서 추출
2. **2차**: `getBookDetail` 서버 조회 실패 → IndexedDB에서 추출
3. **3차**: `loadUserBookDetail`에서 `getBookDetail` 실패 → 내 서재 정보에서 추출

### 3. 에러 처리

**에러 처리 방식**:
- 각 단계에서 폴백 메커니즘 제공
- 최종적으로 데이터를 찾을 수 없을 때만 에러 표시
- 사용자 경험 최우선

---

## 관련 파일

### 수정된 파일
- `js/services/book-service.js`
  - `getUserBookDetail()` 메서드: 하이브리드 전략 적용
  - `getBookDetail()` 메서드: 오프라인 지원 추가
- `js/services/offline-book-service.js`
  - `getBookByServerId()` 메서드: 서버 ID로 내 서재 정보 조회 추가
- `js/views/pages/book-detail-view.js`
  - `loadUserBookDetail()` 메서드: 오프라인 처리 개선

### 참조 파일
- `js/utils/sync-state-manager.js`: 동기화 상태 관리
- `js/utils/network-monitor.js`: 네트워크 상태 모니터링
- `js/utils/book-operation-helper.js`: 내 서재 정보 IndexedDB 저장 헬퍼
- `js/storage/indexeddb-manager.js`: IndexedDB 관리

---

## 테스트 시나리오

### 테스트 1: 오프라인 상태에서 도서 상세 정보 조회
- [ ] 와이파이 연결 해제
- [ ] 내 서재 화면에서 도서 카드 선택
- [ ] 도서 상세 정보가 정상적으로 표시되는지 확인

### 테스트 2: 온라인 상태에서 도서 상세 정보 조회
- [ ] 와이파이 연결
- [ ] 내 서재 화면에서 도서 카드 선택
- [ ] 최신 서버 데이터로 도서 상세 정보가 표시되는지 확인

### 테스트 3: 동기화 중 도서 상세 정보 조회
- [ ] 오프라인에서 도서 추가
- [ ] 네트워크 재연결 (동기화 시작)
- [ ] 동기화 중에 도서 카드 선택
- [ ] 로컬 데이터로 도서 상세 정보가 표시되는지 확인

### 테스트 4: 서버 조회 실패 시 폴백
- [ ] 서버 오류 시뮬레이션
- [ ] 내 서재 화면에서 도서 카드 선택
- [ ] 로컬 데이터로 도서 상세 정보가 표시되는지 확인

---

## 참고 사항

### 하이브리드 전략
- 최근 7일 메모만 IndexedDB에 저장
- 내 서재 정보는 모두 IndexedDB에 저장

### 동기화 상태 관리
- `syncStateManager.isSyncing`: 동기화 진행 중 여부
- `syncStateManager.waitForSyncComplete()`: 동기화 완료 대기 (최대 30초)

### 네트워크 상태 모니터링
- `networkMonitor.isOnline`: 온라인/오프라인 상태
- 2-Phase Health Check로 실제 서버 접근 가능 여부 확인

### IndexedDB 데이터 구조
- `offline_books`: 내 서재 정보 저장
  - 도서 기본 정보: `title`, `author`, `publisher`, `description`, `coverUrl`, `totalPages`, `mainGenre`, `pubDate`
  - 서재 저장 정보: `category`, `expectation`, `lastReadPage`, `rating`, `review` 등

---

**문서 버전**: 1.0  
**최종 업데이트**: 2025-12-09  
**작성자**: Development Team



