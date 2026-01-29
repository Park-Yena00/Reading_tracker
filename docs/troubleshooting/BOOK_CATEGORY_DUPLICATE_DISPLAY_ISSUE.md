# 도서 카테고리 중복 표시 문제 분석

> **작성일**: 2026-01-07  
> **목적**: 독서 시작하기 버튼으로 카테고리 변경 시 이전 카테고리와 변경된 카테고리 둘 다에 동일한 책이 표시되는 문제 원인 분석  
> **상태**: 🔍 분석 완료

---

## 목차

1. [문제 상황](#1-문제-상황)
2. [원인 분석](#2-원인-분석)
3. [문제 발생 시나리오](#3-문제-발생-시나리오)
4. [근본 원인](#4-근본-원인)
5. [해결 방안](#5-해결-방안)
6. [해결 방안 1 지연 시간 분석](#6-해결-방안-1-지연-시간-분석)
7. [참고 파일](#7-참고-파일)

---

## 1. 문제 상황

### 1-1. 문제 설명

**증상**:
1. 도서 검색을 통해 내 서재에 책을 저장 (카테고리: '읽을 예정' = `ToRead`)
2. 독서 시작하기 버튼을 통해 해당 책의 카테고리를 변경 (`ToRead` → `Reading`)
3. **결과**: '읽을 예정' 카테고리와 '읽는 중' 카테고리 둘 다에 동일한 책이 나타남

### 1-2. 예상되는 정상 동작

- 카테고리 변경 후에는 **변경된 카테고리(`Reading`)에만** 책이 표시되어야 함
- 이전 카테고리(`ToRead`)에서는 책이 제거되어야 함

---

## 2. 원인 분석

### 2-1. 하이브리드 전략의 동작 흐름

**온라인 상태에서의 동작**:
1. 서버(백엔드 DB)에 먼저 Write/Read
2. 성공 시 IndexedDB에 캐시 저장 (오프라인 대비)

**서재 조회 시**:
- 온라인: 서버에서 조회 → IndexedDB에 캐시 저장 (비동기)
- 오프라인: IndexedDB에서만 조회

### 2-2. 카테고리 변경 흐름

**독서 시작하기 버튼 클릭 시**:
```javascript
// book-detail-view.js
await bookService.startReading(this.userBookId, requestData);
// → 서버에 카테고리 변경 요청 (ToRead → Reading)

// 도서 상세 정보 다시 로드
await this.loadUserBookDetail();
// → 서버에서 최신 데이터 조회 → IndexedDB 업데이트
```

**서재 목록 조회 시** (`getBookshelf`):
```javascript
// book-service.js
if (networkMonitor.isOnline) {
  // 서버에서 조회 (카테고리 필터 적용)
  const serverResponse = await apiClient.get(API_ENDPOINTS.BOOKS.USER_BOOKS, params);
  
  // IndexedDB에 캐시 저장 (비동기, await 하지 않음)
  Promise.all(serverBooks.map(serverBook => 
    BookOperationHelper.saveServerBookAsLocal(serverBook)
  )).catch(() => {});
}
```

---

## 3. 문제 발생 시나리오

### 3-1. 상세 시나리오

**Step 1: 도서 검색 및 저장**
```
1. 도서 검색 → 내 서재에 저장
2. 서버에 저장 (category: ToRead)
3. IndexedDB에 캐시 저장 (category: ToRead)
   - localId: "local-xxx"
   - serverId: 123
   - category: "ToRead"
```

**Step 2: 독서 시작하기 버튼 클릭**
```
1. startReading API 호출
   - 서버에서 카테고리 변경 (ToRead → Reading)
   
2. loadUserBookDetail() 호출
   - 서버에서 최신 데이터 조회
   - updateLocalAfterUpdate() 호출
   - IndexedDB 업데이트 (category: Reading)
```

**Step 3: 서재 목록 조회 (문제 발생 지점)**

**시나리오 A: 온라인 상태에서 조회**
```
1. getBookshelf({ category: 'ToRead' }) 호출
   - 서버에서 조회 (카테고리 필터 적용)
   - 서버 응답: [] (빈 배열, 카테고리가 이미 Reading으로 변경됨)
   
2. IndexedDB에 캐시 저장 (비동기)
   - Promise.all(...).catch(() => {}) // await 하지 않음
   - 하지만 서버 응답이 빈 배열이므로 저장할 데이터 없음
   
3. 서버 실패 시 IndexedDB 폴백 조회
   - getBooksByCategory('ToRead') 호출
   - IndexedDB에서 이전 카테고리(ToRead)로 저장된 데이터 조회
   - 결과: 이전 데이터가 반환됨 ❌
```

**시나리오 B: 타이밍 이슈**
```
1. 카테고리 변경 후 즉시 서재 목록 조회
   - updateLocalAfterUpdate()가 완료되기 전에 조회
   - IndexedDB에 이전 카테고리(ToRead) 데이터가 남아있음
   
2. getBooksByCategory('ToRead') 호출
   - IndexedDB에서 이전 카테고리로 저장된 데이터 조회
   - 결과: 이전 데이터가 반환됨 ❌
```

**시나리오 C: IndexedDB 중복 데이터**
```
1. getBookshelf에서 서버 데이터를 IndexedDB에 저장할 때
   - saveServerBookAsLocal() 호출
   - getAllBooksByServerId()로 중복 확인
   - 하지만 카테고리별 조회 시 이전 카테고리 데이터가 남아있을 수 있음
   
2. 카테고리 변경 후
   - updateLocalAfterUpdate()가 기존 데이터를 업데이트
   - 하지만 IndexedDB 인덱스는 카테고리별로 구성되어 있음
   - 이전 카테고리 인덱스에 남아있는 데이터가 조회될 수 있음
```

---

## 4. 근본 원인

### 4-1. 주요 원인

#### 원인 1: IndexedDB 업데이트 타이밍 이슈 ⚠️

**문제점**:
- 카테고리 변경 후 `updateLocalAfterUpdate()`가 호출되지만, **비동기 처리로 인해 완료되기 전에 서재 목록을 조회**할 수 있음
- `getBookshelf`에서 서버 데이터를 IndexedDB에 저장할 때 **`Promise.all`을 await하지 않음**

**코드 위치**:
```javascript
// book-service.js:217-221
Promise.all(serverBooks.map(serverBook => 
  BookOperationHelper.saveServerBookAsLocal(serverBook).catch(err => 
    console.warn('[BookService] IndexedDB 캐시 저장 실패 (무시):', err)
  )
)).catch(() => {}); // ← await 하지 않음!
```

#### 원인 2: IndexedDB 인덱스 기반 조회의 한계 ⚠️

**문제점**:
- `getBooksByCategory`는 IndexedDB의 `category` 인덱스를 사용하여 조회
- 카테고리 변경 시 인덱스가 즉시 업데이트되지 않을 수 있음
- 또는 이전 카테고리로 저장된 데이터가 인덱스에 남아있을 수 있음

**코드 위치**:
```javascript
// indexeddb-manager.js:308-317
async getBooksByCategory(category) {
  const transaction = this.db.transaction(['offline_books'], 'readonly');
  const store = transaction.objectStore('offline_books');
  const index = store.index('category');
  const request = index.getAll(category); // ← 카테고리 인덱스로 조회
  // ...
}
```

#### 원인 3: 서버 실패 시 IndexedDB 폴백 조회 ⚠️

**문제점**:
- 서버 조회 실패 시 IndexedDB에서 폴백 조회
- 하지만 IndexedDB에 이전 카테고리 데이터가 남아있으면 잘못된 데이터가 반환됨

**코드 위치**:
```javascript
// book-service.js:228-246
catch (error) {
  console.error('서버 내 서재 정보 조회 실패, IndexedDB 폴백 시도:', error);
  
  // 서버 실패 시에만 IndexedDB에서 조회 (오프라인 폴백)
  let localBooks = [];
  if (category) {
    localBooks = await offlineBookService.getBooksByCategory(category);
    // ← 이전 카테고리 데이터가 조회될 수 있음
  }
}
```

#### 원인 4: 중복 데이터 제거 로직의 한계 ⚠️

**문제점**:
- `saveServerBookAsLocal`에서 `getAllBooksByServerId`로 중복을 제거하지만
- 카테고리별 조회 시에는 `getBooksByCategory`를 사용하므로 중복 제거 로직이 적용되지 않음

**코드 위치**:
```javascript
// book-operation-helper.js:159
const existingBooks = await dbManager.getAllBooksByServerId(serverBook.userBookId);
// ← 중복 제거는 serverId 기준으로만 수행
// 카테고리별 조회 시에는 적용되지 않음
```

---

### 4-2. 문제 발생 조건

1. **온라인 상태**에서 카테고리 변경
2. 카테고리 변경 후 **즉시 서재 목록 조회**
3. 서버 조회 실패 또는 **IndexedDB 업데이트 완료 전 조회**
4. **IndexedDB에 이전 카테고리 데이터가 남아있는 경우**

---

## 5. 해결 방안

### 5-1. 해결 방안 1: IndexedDB 업데이트 완료 대기 (권장) ⭐

**개념**: 카테고리 변경 후 IndexedDB 업데이트가 완료될 때까지 대기

**구현**:
```javascript
// book-service.js:updateBookDetail
async updateBookDetail(userBookId, updateData) {
  if (networkMonitor.isOnline) {
    try {
      await apiClient.put(API_ENDPOINTS.BOOKSHELF.UPDATE(localBook.serverId), updateData);
      
      // IndexedDB 업데이트 완료 대기
      const updatedServerBook = await this.getUserBookDetail(localBook.serverId);
      await BookOperationHelper.updateLocalAfterUpdate(
        localBook.serverId,
        updatedServerBook
      );
      
      // 업데이트 완료 후 반환
      return '내 서재 정보가 수정되었습니다.';
    } catch (error) {
      // ...
    }
  }
}
```

**장점**:
- 간단한 구현
- 데이터 일관성 보장

**단점**:
- 약간의 지연 발생 가능

---

### 5-2. 해결 방안 2: 서재 목록 조회 시 서버 데이터만 사용

**개념**: 온라인 상태에서는 IndexedDB 폴백을 사용하지 않고 서버 데이터만 반환

**구현**:
```javascript
// book-service.js:getBookshelf
async getBookshelf({ category, sortBy } = {}) {
  if (networkMonitor.isOnline) {
    try {
      const serverResponse = await apiClient.get(API_ENDPOINTS.BOOKS.USER_BOOKS, params);
      const serverBooks = serverResponse.books || [];

      // IndexedDB 캐시 저장 (비동기, await 하지 않음)
      Promise.all(serverBooks.map(serverBook => 
        BookOperationHelper.saveServerBookAsLocal(serverBook).catch(err => 
          console.warn('[BookService] IndexedDB 캐시 저장 실패 (무시):', err)
        )
      )).catch(() => {});

      // 서버 데이터만 반환 (IndexedDB 읽기 안 함)
      return {
        totalCount: serverBooks.length,
        books: serverBooks
      };
    } catch (error) {
      // 서버 실패 시에도 IndexedDB 폴백 사용 안 함
      // 대신 에러를 다시 던지거나 빈 배열 반환
      console.error('서버 내 서재 정보 조회 실패:', error);
      throw error; // 또는 return { totalCount: 0, books: [] };
    }
  } else {
    // 오프라인 상태에서만 IndexedDB 조회
    // ...
  }
}
```

**장점**:
- 서버 데이터의 정확성 보장
- IndexedDB 동기화 문제 회피

**단점**:
- 서버 실패 시 데이터 조회 불가

---

### 5-3. 해결 방안 3: IndexedDB 업데이트 시 이전 카테고리 데이터 삭제

**개념**: 카테고리 변경 시 이전 카테고리로 저장된 데이터를 명시적으로 삭제

**구현**:
```javascript
// book-operation-helper.js:updateLocalAfterUpdate
static async updateLocalAfterUpdate(bookId, serverBook) {
  await dbManager.init();
  
  // 기존 데이터 조회 (serverId로)
  const existingBooks = await dbManager.getAllBooksByServerId(serverBook.userBookId);
  
  if (existingBooks && existingBooks.length > 0) {
    // 카테고리가 변경된 경우 이전 카테고리 데이터 정리
    const updatedCategory = serverBook.category;
    const booksToDelete = existingBooks.filter(book => book.category !== updatedCategory);
    
    // 이전 카테고리 데이터 삭제
    for (const book of booksToDelete) {
      await dbManager.deleteBook(book.localId);
    }
    
    // 첫 번째 책 업데이트
    const existingBook = existingBooks.find(book => book.category === updatedCategory) || existingBooks[0];
    // ... 업데이트 로직
  }
}
```

**장점**:
- 중복 데이터 완전 제거
- IndexedDB 일관성 보장

**단점**:
- 구현 복잡도 증가

---

### 5-4. 해결 방안 4: 서재 목록 조회 전 IndexedDB 동기화 확인

**개념**: 서재 목록 조회 전에 IndexedDB 동기화 상태 확인 및 대기

**구현**:
```javascript
// book-service.js:getBookshelf
async getBookshelf({ category, sortBy } = {}) {
  if (networkMonitor.isOnline) {
    // 동기화 중이면 대기
    if (syncStateManager.isSyncing) {
      await syncStateManager.waitForSyncComplete();
    }
    
    // IndexedDB 캐시 저장 완료 대기
    try {
      const serverResponse = await apiClient.get(API_ENDPOINTS.BOOKS.USER_BOOKS, params);
      const serverBooks = serverResponse.books || [];

      // IndexedDB 캐시 저장 (await로 완료 대기)
      await Promise.all(serverBooks.map(serverBook => 
        BookOperationHelper.saveServerBookAsLocal(serverBook).catch(err => 
          console.warn('[BookService] IndexedDB 캐시 저장 실패 (무시):', err)
        )
      ));

      return {
        totalCount: serverBooks.length,
        books: serverBooks
      };
    } catch (error) {
      // ...
    }
  }
}
```

**장점**:
- IndexedDB 동기화 보장
- 데이터 일관성 보장

**단점**:
- 약간의 성능 저하 (await로 인한 지연)

---

### 5-5. 권장 해결 방안

**종합 권장**: **해결 방안 1 + 해결 방안 2 조합** ⭐

1. **카테고리 변경 시**: IndexedDB 업데이트 완료 대기 (해결 방안 1)
2. **서재 목록 조회 시**: 온라인 상태에서는 서버 데이터만 사용, IndexedDB 폴백 제거 (해결 방안 2)

**이유**:
- 온라인 상태에서는 서버가 Single Source of Truth
- IndexedDB는 오프라인 대비 캐시로만 사용
- 서버 데이터의 정확성 보장

---

## 6. 해결 방안 1 지연 시간 분석

### 6-1. 현재 구현 상태

**현재 코드** (`book-service.js:494-503`):
```javascript
await apiClient.put(API_ENDPOINTS.BOOKSHELF.UPDATE(localBook.serverId), updateData);

// 3. 성공 시 서버에서 최신 데이터를 다시 조회하여 IndexedDB 갱신
try {
  const updatedServerBook = await this.getUserBookDetail(localBook.serverId);
  await BookOperationHelper.updateLocalAfterUpdate(
    localBook.serverId,
    updatedServerBook
  );
}
```

**현재 구현**: 이미 해결 방안 1이 구현되어 있음 ✅

---

### 6-2. 지연 시간 분석

#### 비교 시나리오

**시나리오 A: 현재 구현 (해결 방안 1 적용)**
```
1. 서버 PUT 요청 (카테고리 변경)
   ↓
2. 서버 GET 요청 (최신 데이터 조회)
   ↓
3. IndexedDB 업데이트
   ↓
4. 반환
```

**시나리오 B: 대안 (서버 PUT만 하고 즉시 반환)**
```
1. 서버 PUT 요청 (카테고리 변경)
   ↓
2. 반환 (IndexedDB는 나중에 업데이트)
```

---

### 6-3. 각 단계별 예상 지연 시간

#### 1. 서버 PUT 요청
- **네트워크 왕복 시간**: 20-100ms (로컬 서버 기준)
- **서버 처리 시간**: 30-100ms (DB 업데이트 포함)
- **총 예상 시간**: **50-200ms**

#### 2. 서버 GET 요청 (최신 데이터 조회)
- **네트워크 왕복 시간**: 20-100ms (로컬 서버 기준)
- **서버 처리 시간**: 20-80ms (DB 조회 포함)
- **총 예상 시간**: **40-180ms**

#### 3. IndexedDB 업데이트
- **IndexedDB 초기화 확인**: 0-5ms (이미 초기화된 경우 0ms)
- **데이터 조회 (getBookByServerId)**: 1-5ms
- **데이터 업데이트 (saveBook)**: 1-5ms
- **총 예상 시간**: **2-15ms**

---

### 6-4. 총 지연 시간 계산

**해결 방안 1 적용 시 (현재 구현)**:
```
총 지연 시간 = 서버 PUT + 서버 GET + IndexedDB 업데이트
            = (50-200ms) + (40-180ms) + (2-15ms)
            = 92-395ms
```

**대안 (서버 PUT만 하고 즉시 반환)**:
```
총 지연 시간 = 서버 PUT만
            = 50-200ms
```

**추가 지연 시간**:
```
추가 지연 = 해결 방안 1 - 대안
         = (92-395ms) - (50-200ms)
         = 42-195ms
```

---

### 6-5. 지연 시간 상세 분석

#### 최적 상황 (Fast Network, Low Latency)
- 서버 PUT: 50ms
- 서버 GET: 40ms
- IndexedDB: 2ms
- **총 지연**: **92ms**
- **추가 지연**: **42ms** (대안 대비)

#### 일반적인 상황 (Normal Network)
- 서버 PUT: 100ms
- 서버 GET: 80ms
- IndexedDB: 5ms
- **총 지연**: **185ms**
- **추가 지연**: **85ms** (대안 대비)

#### 최악의 상황 (Slow Network, High Latency)
- 서버 PUT: 200ms
- 서버 GET: 180ms
- IndexedDB: 15ms
- **총 지연**: **395ms**
- **추가 지연**: **195ms** (대안 대비)

---

### 6-6. 사용자 경험 관점 평가

#### 지연 시간 평가 기준
- **0-100ms**: 사용자가 지연을 거의 느끼지 않음 ✅
- **100-300ms**: 약간의 지연을 느낄 수 있음 ⚠️
- **300ms 이상**: 명확한 지연을 느낌 ❌

#### 해결 방안 1의 사용자 경험
- **최적 상황**: 92ms → 사용자가 지연을 거의 느끼지 않음 ✅
- **일반적인 상황**: 185ms → 약간의 지연을 느낄 수 있음 ⚠️
- **최악의 상황**: 395ms → 명확한 지연을 느낌 ❌

#### 대안의 사용자 경험
- **최적 상황**: 50ms → 사용자가 지연을 거의 느끼지 않음 ✅
- **일반적인 상황**: 100ms → 사용자가 지연을 거의 느끼지 않음 ✅
- **최악의 상황**: 200ms → 약간의 지연을 느낄 수 있음 ⚠️

---

### 6-7. 결론 및 권장사항

#### 지연 시간 요약
- **추가 지연 시간**: **약 42-195ms** (평균 약 85ms)
- **총 응답 시간**: **약 92-395ms** (평균 약 185ms)

#### 사용자 경험 평가
- **일반적인 네트워크 환경**: 약간의 지연을 느낄 수 있지만 **허용 가능한 수준** ⚠️
- **빠른 네트워크 환경**: 지연을 거의 느끼지 않음 ✅
- **느린 네트워크 환경**: 명확한 지연을 느낄 수 있음 ❌

#### 권장사항
1. **해결 방안 1 적용 권장** ⭐
   - 추가 지연 시간이 **평균 85ms**로 허용 가능한 수준
   - 데이터 일관성 보장이 더 중요
   - 사용자 경험에 큰 영향을 주지 않음

2. **성능 최적화 방안** (선택적)
   - 서버 GET 요청 최적화 (캐싱, 응답 시간 단축)
   - IndexedDB 업데이트를 비동기로 처리하되, 완료 여부를 추적
   - 로딩 인디케이터 표시로 사용자에게 진행 상황 알림

3. **대안 고려 사항**
   - 서버 PUT만 하고 즉시 반환하는 경우, IndexedDB 업데이트가 나중에 이루어짐
   - 이 경우 서재 목록 조회 시 이전 카테고리 데이터가 표시될 수 있음
   - **데이터 일관성 문제가 발생할 수 있으므로 권장하지 않음**

---

### 6-8. 해결 방안 2의 IndexedDB 사용 시점 및 업데이트 타이밍

#### 6-8-1. 해결 방안 2의 핵심 원칙

**IndexedDB 사용 정책**:
- **온라인 상태**: IndexedDB에서 데이터를 **조회하지 않음** (서버가 Single Source of Truth)
- **오프라인 상태**: IndexedDB에서만 데이터 조회
- **IndexedDB 업데이트**: 온라인 상태에서도 수행하되, **오프라인 대비 캐시 목적**으로만 사용

---

#### 6-8-2. 온라인 상태에서의 동작

**서재 목록 조회 시** (`getBookshelf`):
```javascript
// 해결 방안 2 적용 후
async getBookshelf({ category, sortBy } = {}) {
  if (networkMonitor.isOnline) {
    try {
      // 1. 서버에서만 조회 (IndexedDB 읽기 안 함)
      const serverResponse = await apiClient.get(API_ENDPOINTS.BOOKS.USER_BOOKS, params);
      const serverBooks = serverResponse.books || [];

      // 2. IndexedDB에 캐시 저장 (비동기, await 하지 않음)
      // 목적: 오프라인 대비 캐시, 응답 지연 방지
      Promise.all(serverBooks.map(serverBook => 
        BookOperationHelper.saveServerBookAsLocal(serverBook).catch(err => 
          console.warn('[BookService] IndexedDB 캐시 저장 실패 (무시):', err)
        )
      )).catch(() => {});

      // 3. 서버 데이터만 반환 (IndexedDB 읽기 안 함)
      return {
        totalCount: serverBooks.length,
        books: serverBooks
      };
    } catch (error) {
      // 4. 서버 실패 시에도 IndexedDB 폴백 사용 안 함
      // 해결 방안 2: 폴백 제거
      console.error('서버 내 서재 정보 조회 실패:', error);
      throw error; // 또는 return { totalCount: 0, books: [] };
    }
  } else {
    // 오프라인 상태에서만 IndexedDB 조회
    // ...
  }
}
```

**IndexedDB 업데이트 시점**:
1. **서버 조회 성공 직후**: 서버 응답을 받은 즉시 IndexedDB에 캐시 저장 시작
2. **비동기 처리**: `Promise.all`을 await하지 않아 응답 지연 없음
3. **백그라운드 실행**: 사용자 응답과 무관하게 백그라운드에서 실행

---

#### 6-8-3. 오프라인 상태에서의 동작

**서재 목록 조회 시**:
```javascript
else {
  // 오프라인 상태면 IndexedDB에서만 조회
  let localBooks = [];
  if (category) {
    localBooks = await offlineBookService.getBooksByCategory(category);
  } else {
    localBooks = await offlineBookService.getAllBooks();
  }
  
  return {
    totalCount: localBooks.length,
    books: this.mapLocalBooksToResponse(localBooks)
  };
}
```

**IndexedDB 조회 시점**:
- 오프라인 상태로 전환된 후 서재 목록 조회 시
- IndexedDB가 유일한 데이터 소스

---

#### 6-8-4. IndexedDB 업데이트 흐름 다이어그램

**온라인 상태 - 서재 목록 조회**:
```
사용자 요청 (getBookshelf)
    ↓
서버 GET 요청
    ↓
서버 응답 수신
    ↓
서버 데이터 반환 (즉시) ✅
    ↓
[백그라운드] IndexedDB 캐시 저장 시작
    ↓
[백그라운드] 각 도서별로 saveServerBookAsLocal() 실행
    ↓
[백그라운드] IndexedDB 업데이트 완료
```

**온라인 상태 - 카테고리 변경**:
```
사용자 요청 (카테고리 변경)
    ↓
서버 PUT 요청
    ↓
서버 GET 요청 (최신 데이터 조회)
    ↓
IndexedDB 업데이트 (await로 완료 대기) ✅
    ↓
반환
```

**오프라인 상태 - 서재 목록 조회**:
```
사용자 요청 (getBookshelf)
    ↓
IndexedDB 조회 (getBooksByCategory)
    ↓
IndexedDB 데이터 반환
```

---

#### 6-8-5. 해결 방안 2의 장단점

**장점**:
1. **데이터 일관성 보장**: 온라인 상태에서는 항상 서버 데이터 사용
2. **중복 표시 문제 해결**: IndexedDB 폴백 제거로 이전 카테고리 데이터 조회 방지
3. **응답 속도 유지**: IndexedDB 캐시 저장을 비동기로 처리하여 응답 지연 없음
4. **오프라인 지원**: 오프라인 상태에서 IndexedDB 데이터 사용 가능

**단점**:
1. **서버 실패 시 데이터 조회 불가**: IndexedDB 폴백을 사용하지 않으므로 서버 실패 시 빈 결과 반환
2. **네트워크 의존성 증가**: 온라인 상태에서는 항상 서버 연결 필요

---

#### 6-8-6. 해결 방안 2 적용 시 주의사항

**서버 실패 처리**:
- 서버 조회 실패 시 에러를 던지거나 빈 배열 반환
- IndexedDB 폴백을 사용하지 않으므로 사용자에게 명확한 에러 메시지 표시 필요

**IndexedDB 캐시 관리**:
- 온라인 상태에서도 IndexedDB를 업데이트하여 오프라인 전환 시 최신 데이터 사용 가능
- 비동기 처리로 응답 지연 없이 캐시 업데이트

**데이터 동기화**:
- 카테고리 변경 시 IndexedDB 업데이트 완료 대기 (해결 방안 1)
- 서재 목록 조회 시 서버 데이터만 사용 (해결 방안 2)
- 두 방안을 조합하여 데이터 일관성 보장

---

#### 6-8-7. 해결 방안 1 + 해결 방안 2 조합 시나리오

**카테고리 변경 → 서재 목록 조회 흐름**:
```
1. 독서 시작하기 버튼 클릭
   ↓
2. 서버 PUT 요청 (카테고리 변경)
   ↓
3. 서버 GET 요청 (최신 데이터 조회)
   ↓
4. IndexedDB 업데이트 완료 대기 (해결 방안 1) ✅
   ↓
5. 반환
   ↓
6. 서재 목록 조회
   ↓
7. 서버 GET 요청 (카테고리 필터 적용)
   ↓
8. 서버 데이터 반환 (IndexedDB 읽기 안 함) ✅
   ↓
9. [백그라운드] IndexedDB 캐시 저장
   ↓
10. 정상 표시 (중복 없음) ✅
```

**결과**:
- 카테고리 변경 후 IndexedDB가 최신 상태로 업데이트됨
- 서재 목록 조회 시 서버 데이터만 사용하므로 이전 카테고리 데이터 조회 안 함
- 중복 표시 문제 해결 ✅

---

#### 6-8-8. startReading에 해결 방안 1 적용 시 구현 위치 분석

##### 6-8-8-1. 현재 구현 상태

**startReading 메서드** (`book-service.js:421-424`):
```javascript
async startReading(userBookId, startReadingData) {
  const response = await apiClient.post(API_ENDPOINTS.BOOKSHELF.START_READING(userBookId), startReadingData);
  return response; // 성공 메시지 반환
}
```

**특징**:
- 서버 POST 요청만 수행
- IndexedDB 업데이트 없음
- 즉시 반환

**독서 시작하기 버튼 클릭 시 흐름** (`book-detail-view.js:1765-1789`):
```javascript
1. await bookService.startReading(this.userBookId, requestData);
   // → 서버 POST 요청 (카테고리 변경)
   // → 즉시 반환 (IndexedDB 업데이트 없음)

2. bookState.updateBookStatus(...) 
   // → 로컬 상태 업데이트 (즉시)

3. eventBus.publish(BOOKSHELF_UPDATED, ...)
   // → 이벤트 발행 (즉시)

4. await this.loadUserBookDetail();
   // → 서버에서 최신 데이터 조회
   // → IndexedDB에 캐시 저장 (비동기, await 하지 않음)
```

**비교: updateBookDetail 메서드** (읽은 페이지 수 변경 시 사용):
- 이미 해결 방안 1이 구현되어 있음
- 서버 PUT → 서버 GET → IndexedDB 업데이트 완료 대기

---

##### 6-8-8-2. 해결 방안 1 적용 옵션 비교

#### 옵션 1: startReading 내부에서 IndexedDB 업데이트 포함

**구현 방식**:
```javascript
async startReading(userBookId, startReadingData) {
  // 1. 서버 POST 요청 (카테고리 변경)
  await apiClient.post(API_ENDPOINTS.BOOKSHELF.START_READING(userBookId), startReadingData);
  
  // 2. 서버에서 최신 데이터 조회
  const updatedServerBook = await this.getUserBookDetail(userBookId);
  
  // 3. IndexedDB 업데이트 완료 대기 (해결 방안 1)
  await BookOperationHelper.updateLocalAfterUpdate(
    userBookId,
    updatedServerBook
  );
  
  return '독서를 시작했습니다.';
}
```

**장점**:
1. **책임 분리 명확**: startReading이 완전한 트랜잭션을 담당
2. **일관성**: updateBookDetail과 동일한 패턴
3. **재사용성**: 다른 곳에서 startReading 호출 시에도 IndexedDB 동기화 보장
4. **테스트 용이성**: startReading 단위 테스트에서 IndexedDB 동기화까지 검증 가능

**단점**:
1. **메서드 복잡도 증가**: startReading이 더 많은 책임을 가짐
2. **의존성 증가**: getUserBookDetail과 BookOperationHelper에 의존

---

#### 옵션 2: startReading 후 loadUserBookDetail()에서 IndexedDB 업데이트 await

**구현 방식**:
```javascript
// book-service.js:startReading (변경 없음)
async startReading(userBookId, startReadingData) {
  const response = await apiClient.post(API_ENDPOINTS.BOOKSHELF.START_READING(userBookId), startReadingData);
  return response;
}

// book-detail-view.js:submitStartReadingForm
async submitStartReadingForm() {
  // ...
  await bookService.startReading(this.userBookId, requestData);
  
  // 도서 상세 정보 다시 로드 (IndexedDB 업데이트 포함)
  await this.loadUserBookDetail(); // ← 여기서 IndexedDB 업데이트 await
}

// book-service.js:getUserBookDetail (수정 필요)
async getUserBookDetail(userBookId) {
  // ...
  if (serverBook) {
    // IndexedDB에 캐시로 저장 (await로 완료 대기)
    await BookOperationHelper.saveServerBookAsLocal(serverBook); // ← await 추가
    return serverBook;
  }
}
```

**장점**:
1. **startReading 단순성 유지**: startReading은 서버 요청만 담당
2. **View 레이어에서 제어**: UI 업데이트와 IndexedDB 동기화를 함께 관리

**단점**:
1. **책임 분리 모호**: getUserBookDetail이 조회와 저장을 모두 담당
2. **재사용성 문제**: 다른 곳에서 getUserBookDetail 호출 시 불필요한 IndexedDB 업데이트 대기
3. **일관성 부족**: updateBookDetail과 다른 패턴
4. **의도 불명확**: getUserBookDetail이 "조회"인데 "저장"도 수행

---

##### 6-8-8-3. UI 업데이트 시점 분석

#### 옵션 1 적용 시 UI 업데이트 흐름

```
1. 독서 시작하기 버튼 클릭
   ↓
2. startReading() 호출
   ↓
3. 서버 POST 요청 (카테고리 변경) ✅
   ↓
4. 서버 GET 요청 (최신 데이터 조회) ✅
   ↓
5. IndexedDB 업데이트 완료 대기 ✅
   ↓
6. startReading() 반환
   ↓
7. bookState.updateBookStatus() (로컬 상태 업데이트)
   ↓
8. eventBus.publish(BOOKSHELF_UPDATED) (이벤트 발행)
   ↓
9. bookshelf-view에서 이벤트 수신 → loadBookshelf() 호출
   ↓
10. 서버 GET 요청 (카테고리 필터 적용) ✅
   ↓
11. 서버 데이터 반환 (IndexedDB 읽기 안 함) ✅
   ↓
12. UI 렌더링 ✅
   ↓
13. loadUserBookDetail() 호출 (도서 상세 정보 갱신)
   ↓
14. 서버 GET 요청 (최신 데이터 조회)
   ↓
15. IndexedDB 캐시 저장 (비동기)
   ↓
16. UI 업데이트 ✅
```

**결과**: 
- ✅ 카테고리 변경 후 IndexedDB가 최신 상태로 업데이트됨
- ✅ 서재 목록 조회 시 서버 데이터만 사용하므로 이전 카테고리 데이터 조회 안 함
- ✅ UI가 바로 업데이트됨 (이벤트 기반 자동 갱신)

---

#### 옵션 2 적용 시 UI 업데이트 흐름

```
1. 독서 시작하기 버튼 클릭
   ↓
2. startReading() 호출
   ↓
3. 서버 POST 요청 (카테고리 변경) ✅
   ↓
4. startReading() 반환
   ↓
5. bookState.updateBookStatus() (로컬 상태 업데이트)
   ↓
6. eventBus.publish(BOOKSHELF_UPDATED) (이벤트 발행)
   ↓
7. bookshelf-view에서 이벤트 수신 → loadBookshelf() 호출
   ↓
8. 서버 GET 요청 (카테고리 필터 적용) ✅
   ↓
9. 서버 데이터 반환 (IndexedDB 읽기 안 함) ✅
   ↓
10. UI 렌더링 ✅
   ↓
11. loadUserBookDetail() 호출
   ↓
12. 서버 GET 요청 (최신 데이터 조회) ✅
   ↓
13. IndexedDB 업데이트 완료 대기 ✅
   ↓
14. UI 업데이트 ✅
```

**결과**:
- ✅ 카테고리 변경 후 IndexedDB가 최신 상태로 업데이트됨
- ✅ 서재 목록 조회 시 서버 데이터만 사용하므로 이전 카테고리 데이터 조회 안 함
- ✅ UI가 바로 업데이트됨 (이벤트 기반 자동 갱신)
- ⚠️ 하지만 IndexedDB 업데이트가 loadUserBookDetail()에서 이루어지므로, 도서 상세 화면을 보지 않으면 IndexedDB가 업데이트되지 않을 수 있음

---

##### 6-8-8-4. 권장 방안

**권장: 옵션 1 (startReading 내부에서 IndexedDB 업데이트 포함)** ⭐

**이유**:
1. **일관성**: `updateBookDetail`과 동일한 패턴으로 구현 일관성 유지
2. **책임 분리**: startReading이 완전한 트랜잭션을 담당하여 책임이 명확
3. **재사용성**: 다른 곳에서 startReading 호출 시에도 IndexedDB 동기화 보장
4. **데이터 일관성**: 카테고리 변경 직후 IndexedDB가 즉시 업데이트되어 일관성 보장
5. **UI 업데이트**: 이벤트 기반으로 서재 목록이 자동 갱신되어 즉시 반영

**구현 예시**:
```javascript
async startReading(userBookId, startReadingData) {
  // 1. 서버 POST 요청 (카테고리 변경)
  await apiClient.post(API_ENDPOINTS.BOOKSHELF.START_READING(userBookId), startReadingData);
  
  // 2. 서버에서 최신 데이터 조회
  const updatedServerBook = await this.getUserBookDetail(userBookId);
  
  // 3. IndexedDB 업데이트 완료 대기 (해결 방안 1)
  await BookOperationHelper.updateLocalAfterUpdate(
    userBookId,
    updatedServerBook
  );
  
  return '독서를 시작했습니다.';
}
```

**옵션 2의 문제점**:
- `getUserBookDetail`이 "조회" 메서드인데 "저장"도 수행하여 책임이 모호함
- 다른 곳에서 `getUserBookDetail` 호출 시 불필요한 IndexedDB 업데이트 대기 발생
- 도서 상세 화면을 보지 않으면 IndexedDB가 업데이트되지 않을 수 있음

---

##### 6-8-8-5. 최종 결론

**해결 방안 1 + 해결 방안 2 조합 적용 시**:
- ✅ **카테고리 변경 사항이 서버 DB에 저장됨** (서버 PUT/POST 요청)
- ✅ **IndexedDB 업데이트 완료 대기** (해결 방안 1)
- ✅ **서재 목록 조회 시 서버 데이터만 사용** (해결 방안 2)
- ✅ **UI가 바로 업데이트됨** (이벤트 기반 자동 갱신)

**startReading에 해결 방안 1 적용 시**:
- **권장**: **옵션 1 (startReading 내부에서 IndexedDB 업데이트 포함)**
- `updateBookDetail`과 동일한 패턴으로 구현 일관성 유지
- 카테고리 변경 직후 IndexedDB가 즉시 업데이트되어 데이터 일관성 보장

---

## 7. 참고 파일

### 프론트엔드 파일

- `js/services/book-service.js`: 도서 서비스 (카테고리 변경, 서재 조회)
- `js/services/offline-book-service.js`: 오프라인 도서 서비스
- `js/utils/book-operation-helper.js`: 도서 작업 헬퍼 (IndexedDB 업데이트)
- `js/storage/indexeddb-manager.js`: IndexedDB 관리자 (카테고리별 조회)
- `js/views/pages/book-detail-view.js`: 도서 상세 화면 (독서 시작하기 버튼)

### 관련 문서

- `docs/troubleshooting/HYBRID_STRATEGY_FLOW_ANALYSIS.md`: 하이브리드 전략 흐름 분석
- `docs/troubleshooting/OFFLINE_HYBRID_STRATEGY_COMPLETE_ANALYSIS.md`: 하이브리드 전략 완전 분석

---

## 부록: 문제 발생 흐름 다이어그램

### 정상 흐름

```
도서 저장 (ToRead)
    ↓
서버 저장 (category: ToRead)
    ↓
IndexedDB 캐시 저장 (category: ToRead)
    ↓
독서 시작하기 클릭
    ↓
서버 카테고리 변경 (ToRead → Reading)
    ↓
IndexedDB 업데이트 (category: Reading) ✅
    ↓
서재 목록 조회
    ↓
서버에서 조회 (category: Reading) ✅
    ↓
정상 표시
```

### 문제 발생 흐름

```
도서 저장 (ToRead)
    ↓
서버 저장 (category: ToRead)
    ↓
IndexedDB 캐시 저장 (category: ToRead)
    ↓
독서 시작하기 클릭
    ↓
서버 카테고리 변경 (ToRead → Reading)
    ↓
IndexedDB 업데이트 시작 (비동기) ⏳
    ↓
서재 목록 조회 (업데이트 완료 전)
    ↓
서버 조회 실패 또는 IndexedDB 폴백
    ↓
IndexedDB에서 이전 카테고리(ToRead) 조회 ❌
    ↓
중복 표시 (ToRead + Reading)
```
