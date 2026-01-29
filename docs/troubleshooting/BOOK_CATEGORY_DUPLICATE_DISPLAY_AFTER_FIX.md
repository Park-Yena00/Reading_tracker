# 도서 카테고리 중복 표시 문제 재발생 분석

> **작성일**: 2026-01-12  
> **문제**: 해결방안 적용 후에도 카테고리 변경 시 두 카테고리에 동일한 책이 표시되는 문제 재발생  
> **상태**: 🔍 분석 완료

---

## 문제 상황

### 증상

1. 새로운 도서를 검색하여 내 서재에 저장 (카테고리: '읽을 예정' = `ToRead`)
2. 독서 시작하기 버튼을 통해 카테고리를 변경 (`ToRead` → `Reading`)
3. **결과**: '읽을 예정' 카테고리와 '읽는 중' 카테고리 둘 다에 동일한 책이 나타남 ❌

### 예상되는 정상 동작

- 카테고리 변경 후에는 **변경된 카테고리(`Reading`)에만** 책이 표시되어야 함
- 이전 카테고리(`ToRead`)에서는 책이 제거되어야 함

### 적용된 해결방안

이전에 다음 해결방안을 적용했습니다:
- **해결방안 1**: `startReading` 내부에서 IndexedDB 업데이트 완료 대기
- **해결방안 2**: `getBookshelf`에서 온라인 상태일 때 서버 데이터만 사용, IndexedDB 폴백 제거

하지만 문제가 재발생했습니다.

---

## 원인 분석

### 1. 전체 흐름 추적

#### Step 1: 독서 시작하기 버튼 클릭

**코드 위치**: `book-detail-view.js:1717-1799`

```javascript
async submitStartReadingForm() {
  // ...
  // 1. 독서 시작하기 API 호출
  await bookService.startReading(this.userBookId, requestData);
  
  // 2. 상태 업데이트 (Event-Driven 패턴)
  bookState.updateBookStatus(this.userBookId, {
    category: 'Reading',
    // ...
  });
  
  // 3. 서재 목록 갱신을 위한 이벤트 발행
  eventBus.publish(BOOK_EVENTS.BOOKSHELF_UPDATED, {
    timestamp: new Date(),
  });
  
  // 4. 도서 상세 정보 다시 로드
  await this.loadUserBookDetail();
}
```

**동작**:
1. `startReading` 호출 → 서버 POST 요청 (카테고리 변경)
2. 서버 GET 요청 (최신 데이터 조회)
3. IndexedDB 업데이트 완료 대기 ✅
4. `bookState.updateBookStatus` 호출 (로컬 상태 업데이트)
5. `BOOKSHELF_UPDATED` 이벤트 발행
6. `loadUserBookDetail` 호출

#### Step 2: 서재 목록 갱신 (이벤트 수신)

**코드 위치**: `bookshelf-view.js:98-100`

```javascript
const unsubscribeBookshelfUpdated = eventBus.subscribe(BOOK_EVENTS.BOOKSHELF_UPDATED, () => {
  this.loadBookshelf();
});
```

**동작**: `BOOKSHELF_UPDATED` 이벤트 수신 시 `loadBookshelf()` 호출

#### Step 3: 전체 서재 조회

**코드 위치**: `bookshelf-view.js:107-152`

```javascript
async loadBookshelf() {
  // ...
  // 전체 서재 조회 (카테고리 목록 추출용)
  const response = await bookService.getBookshelf({
    sortBy: this.currentSortBy,
  });
  
  this.allBooks = response.books || []; // ← 전체 서재 목록 저장
  
  // 카테고리 목록 추출
  this.extractCategories();
  this.renderCategorySidebar();
  
  // 첫 번째 카테고리를 기본값으로 선택하고 해당 카테고리의 책들만 로드
  if (this.availableCategories.length > 0) {
    if (this.currentCategory === null) {
      this.currentCategory = this.availableCategories[0]; // ← 'ToRead'가 첫 번째
    }
    await this.loadBooksByCategory(this.currentCategory);
  }
}
```

**문제점**:
- `getBookshelf({ sortBy })` 호출 시 **category 파라미터가 없음**
- 서버에서 **전체 서재 목록**을 반환
- 서버 DB에는 이미 카테고리가 `Reading`으로 변경되어 있으므로, 전체 조회 시 `Reading` 카테고리에만 나타나야 함
- 하지만 `allBooks`에 저장된 후, `currentCategory`가 `null`이면 첫 번째 카테고리(`ToRead`)를 선택
- `loadBooksByCategory('ToRead')` 호출 → 서버에서 빈 배열 반환되어야 함

#### Step 4: 카테고리별 도서 목록 로드

**코드 위치**: `bookshelf-view.js:224-255`

```javascript
async loadBooksByCategory(category) {
  // ...
  try {
    const response = await bookService.getBookshelf({
      category: category, // ← 카테고리 필터 적용
      sortBy: this.currentSortBy,
    });
    
    const books = response.books || [];
    this.displayBookshelf(books);
  } catch (error) {
    // ...
  }
}
```

**동작**:
- `getBookshelf({ category: 'ToRead' })` 호출
- 서버에서 `ToRead` 카테고리로 필터링된 결과 조회
- 서버 DB에는 이미 `Reading`으로 변경되어 있으므로 빈 배열 반환되어야 함 ✅

### 2. 문제 발생 지점 분석

#### 가능한 원인 1: 서버 응답 지연 또는 캐싱

**시나리오**:
1. `startReading` 호출 → 서버 DB 업데이트 (ToRead → Reading)
2. `BOOKSHELF_UPDATED` 이벤트 발행
3. `loadBookshelf` 호출 → 서버에서 전체 서재 조회
4. **서버 응답이 지연되거나 이전 데이터를 반환** ❌
5. `allBooks`에 이전 카테고리(`ToRead`) 데이터가 포함됨

**확인 필요**: 서버 API의 응답 시간 및 캐싱 정책

#### 가능한 원인 2: 타이밍 이슈

**시나리오**:
1. `startReading` 호출 → 서버 POST 요청 완료
2. `BOOKSHELF_UPDATED` 이벤트 발행 (비동기)
3. `loadBookshelf` 호출 → 서버 GET 요청 시작
4. **서버 POST 요청이 완전히 커밋되기 전에 GET 요청이 실행됨** ❌
5. 이전 카테고리 데이터가 반환됨

**확인 필요**: 서버 트랜잭션 처리 시간

#### 가능한 원인 3: `allBooks` 업데이트 문제

**시나리오**:
1. `loadBookshelf`에서 전체 서재 조회 → `allBooks`에 저장
2. 카테고리 변경 후 `BOOKSHELF_UPDATED` 이벤트 발행
3. `loadBookshelf` 다시 호출 → 서버에서 최신 데이터 조회
4. **하지만 `allBooks`가 업데이트되기 전에 다른 로직이 실행됨** ❌

**코드 확인**: `bookshelf-view.js:316`
```javascript
const book = this.allBooks.find(b => b.userBookId === userBookId);
```
- `allBooks`를 사용하는 다른 로직이 있을 수 있음

#### 가능한 원인 4: UI 렌더링 시 `allBooks` 사용

**시나리오**:
1. `loadBookshelf`에서 전체 서재 조회 → `allBooks`에 저장
2. 카테고리 변경 후 `BOOKSHELF_UPDATED` 이벤트 발행
3. `loadBooksByCategory` 호출 → 서버에서 카테고리별 조회 (정확한 데이터)
4. **하지만 UI 렌더링 시 `allBooks`를 참조하여 이전 데이터가 표시됨** ❌

**코드 확인**: `bookshelf-view.js:261-287`
```javascript
displayBookshelf(books) {
  // API에서 이미 카테고리별로 필터링된 결과를 받으므로 추가 필터링 불필요
  this.bookshelfList.innerHTML = '';
  
  if (books && books.length > 0) {
    books.forEach((book) => {
      const cardHtml = BookshelfBookCard.render(book);
      // ...
    });
  }
}
```
- `displayBookshelf`는 파라미터로 받은 `books`만 사용하므로 문제 없어 보임

### 3. 실제 문제 원인 (추정)

**가장 가능성 높은 원인**: **서버 응답 지연 또는 트랜잭션 처리 타이밍 이슈**

**상세 분석**:

1. **`startReading` 내부 흐름**:
   ```javascript
   // 1. 서버 POST 요청 (카테고리 변경)
   await apiClient.post(API_ENDPOINTS.BOOKSHELF.START_READING(userBookId), startReadingData);
   
   // 2. 서버 GET 요청 (최신 데이터 조회)
   const updatedServerBook = await this.getUserBookDetail(userBookId);
   
   // 3. IndexedDB 업데이트 완료 대기
   await BookOperationHelper.updateLocalAfterUpdate(userBookId, updatedServerBook);
   ```

2. **`getUserBookDetail` 내부 흐름** (`book-service.js:359-410`):
   ```javascript
   async getUserBookDetail(userBookId) {
     // 서버에서 전체 서재 목록을 가져온 후 userBookId로 필터링
     const response = await apiClient.get(API_ENDPOINTS.BOOKS.USER_BOOKS, {});
     const books = response.books || [];
     const serverBook = books.find(book => /* userBookId 매칭 */);
     // ...
   }
   ```

3. **문제점**:
   - `getUserBookDetail`은 **전체 서재 목록을 조회**한 후 필터링
   - POST 요청 직후 GET 요청이 실행되면, **서버 트랜잭션이 완전히 커밋되기 전**에 조회할 수 있음
   - 결과적으로 이전 카테고리 데이터가 반환될 수 있음

4. **`BOOKSHELF_UPDATED` 이벤트 발행 후**:
   - `loadBookshelf` 호출 → 전체 서재 조회
   - 만약 서버 트랜잭션이 아직 완전히 커밋되지 않았다면, 이전 카테고리 데이터가 포함될 수 있음

### 4. 근본 원인

**핵심 문제**: **서버 트랜잭션 처리 시간과 클라이언트 요청 타이밍 불일치**

1. **POST 요청 완료 ≠ 트랜잭션 커밋 완료**
   - HTTP 응답이 반환되어도 서버 내부에서 트랜잭션이 완전히 커밋되기까지 시간이 걸릴 수 있음
   - 특히 Dual Write 전략을 사용하는 경우, Primary DB와 Secondary DB 모두 업데이트하는 시간이 필요

2. **GET 요청 타이밍**
   - POST 요청 직후 즉시 GET 요청을 실행하면, 아직 커밋되지 않은 데이터를 조회할 수 있음
   - `getUserBookDetail`에서 전체 서재 목록을 조회하므로, 이전 카테고리 데이터가 포함될 수 있음

3. **이벤트 기반 갱신의 타이밍 이슈**
   - `BOOKSHELF_UPDATED` 이벤트가 `startReading` 완료 직후 발행됨
   - 이벤트 수신 시 `loadBookshelf`가 즉시 호출되면, 서버 트랜잭션이 완전히 커밋되기 전에 조회할 수 있음

---

## 해결 방안

### 방안 1: 서버 응답 대기 시간 추가 (임시 해결책)

**구현**:
```javascript
async startReading(userBookId, startReadingData) {
  // 1. 서버 POST 요청 (카테고리 변경)
  await apiClient.post(API_ENDPOINTS.BOOKSHELF.START_READING(userBookId), startReadingData);
  
  // 2. 서버 트랜잭션 커밋 대기 (임시 해결책)
  await new Promise(resolve => setTimeout(resolve, 100)); // 100ms 대기
  
  // 3. 서버에서 최신 데이터 조회
  const updatedServerBook = await this.getUserBookDetail(userBookId);
  
  // 4. IndexedDB 업데이트 완료 대기
  await BookOperationHelper.updateLocalAfterUpdate(userBookId, updatedServerBook);
  
  return '독서를 시작했습니다.';
}
```

**장점**: 간단한 구현
**단점**: 고정된 대기 시간으로 인한 성능 저하, 근본적인 해결책이 아님

### 방안 2: 서버에서 최신 데이터 확인 후 반환 (권장)

**구현**:
```javascript
async startReading(userBookId, startReadingData) {
  // 1. 서버 POST 요청 (카테고리 변경)
  await apiClient.post(API_ENDPOINTS.BOOKSHELF.START_READING(userBookId), startReadingData);
  
  // 2. 서버에서 최신 데이터 조회 (재시도 로직 포함)
  let updatedServerBook;
  let retryCount = 0;
  const maxRetries = 3;
  
  while (retryCount < maxRetries) {
    updatedServerBook = await this.getUserBookDetail(userBookId);
    
    // 카테고리가 변경되었는지 확인
    if (updatedServerBook.category === 'Reading') {
      break; // 변경 완료
    }
    
    // 변경되지 않았으면 재시도
    retryCount++;
    if (retryCount < maxRetries) {
      await new Promise(resolve => setTimeout(resolve, 50)); // 50ms 대기 후 재시도
    }
  }
  
  // 3. IndexedDB 업데이트 완료 대기
  await BookOperationHelper.updateLocalAfterUpdate(userBookId, updatedServerBook);
  
  return '독서를 시작했습니다.';
}
```

**장점**: 서버 데이터 변경을 확인하여 정확성 보장
**단점**: 재시도 로직으로 인한 약간의 지연

### 방안 3: 서버 API 개선 (백엔드 수정 필요)

**구현**:
- `startReading` API가 성공 응답을 반환하기 전에 트랜잭션이 완전히 커밋되도록 보장
- 또는 `startReading` API 응답에 업데이트된 도서 정보를 포함하여 추가 GET 요청 불필요

**장점**: 근본적인 해결책
**단점**: 백엔드 수정 필요

### 방안 4: 이벤트 발행 지연

**구현**:
```javascript
async submitStartReadingForm() {
  // ...
  await bookService.startReading(this.userBookId, requestData);
  
  // 상태 업데이트
  bookState.updateBookStatus(this.userBookId, {
    category: 'Reading',
    // ...
  });
  
  // 서버 트랜잭션 커밋 대기 후 이벤트 발행
  await new Promise(resolve => setTimeout(resolve, 150));
  
  // 서재 목록 갱신을 위한 이벤트 발행
  eventBus.publish(BOOK_EVENTS.BOOKSHELF_UPDATED, {
    timestamp: new Date(),
  });
  
  // ...
}
```

**장점**: 클라이언트 측에서만 수정
**단점**: 고정된 대기 시간으로 인한 성능 저하

---

## 권장 해결 방안

**권장: 방안 2 (서버에서 최신 데이터 확인 후 반환)** ⭐

**이유**:
1. 서버 데이터 변경을 확인하여 정확성 보장
2. 백엔드 수정 없이 프론트엔드에서만 해결 가능
3. 재시도 로직으로 트랜잭션 처리 시간 변동에 대응 가능

**구현 예시**:
```javascript
async startReading(userBookId, startReadingData) {
  // 1. 서버 POST 요청 (카테고리 변경)
  await apiClient.post(API_ENDPOINTS.BOOKSHELF.START_READING(userBookId), startReadingData);
  
  // 2. 서버에서 최신 데이터 조회 (카테고리 변경 확인)
  let updatedServerBook;
  let retryCount = 0;
  const maxRetries = 5;
  const retryDelay = 50; // 50ms
  
  while (retryCount < maxRetries) {
    updatedServerBook = await this.getUserBookDetail(userBookId);
    
    // 카테고리가 'Reading'으로 변경되었는지 확인
    if (updatedServerBook && updatedServerBook.category === 'Reading') {
      break; // 변경 완료 확인
    }
    
    // 변경되지 않았으면 재시도
    retryCount++;
    if (retryCount < maxRetries) {
      await new Promise(resolve => setTimeout(resolve, retryDelay));
    } else {
      console.warn('[BookService] startReading: 카테고리 변경 확인 실패, 최신 데이터로 진행');
    }
  }
  
  // 3. IndexedDB 업데이트 완료 대기 (해결 방안 1)
  await BookOperationHelper.updateLocalAfterUpdate(
    userBookId,
    updatedServerBook
  );
  
  return '독서를 시작했습니다.';
}
```

---

## 원인 1 vs 원인 2 구분 방법

### 핵심 단서: 새로고침 시 정확한 데이터 표시

**관찰된 현상**: 새로고침을 통해 내 서재 정보를 조회하니 DB에 저장된 데이터대로 카테고리에 도서 정보 카드가 정확히 출력됨

이 정보는 **원인 2 (타이밍 이슈)**일 가능성을 높입니다.

### 원인별 특징 비교

#### 원인 1: 서버 응답 지연 또는 캐싱
- **특징**: 서버가 이전 데이터를 캐시하거나 지연된 응답을 반환
- **새로고침 시**: 동일한 문제가 지속될 수 있음 (캐시가 만료되지 않은 경우)
- **확인 방법**: 
  - 서버 로그에서 동일한 요청에 대해 다른 응답이 반환되는지 확인
  - 캐시 헤더 확인 (`Cache-Control`, `ETag` 등)

#### 원인 2: 타이밍 이슈 (트랜잭션 커밋 전 조회)
- **특징**: POST 요청 완료 후 트랜잭션이 완전히 커밋되기 전에 GET 요청 실행
- **새로고침 시**: 시간이 지나 트랜잭션이 커밋된 후이므로 정확한 데이터 반환 ✅
- **확인 방법**:
  - POST 요청과 GET 요청 사이의 시간 간격 측정
  - 서버 로그에서 트랜잭션 커밋 시간과 GET 요청 시간 비교

### 원인 확인 방법

#### 방법 1: 브라우저 개발자 도구 네트워크 탭 분석

**단계**:
1. 브라우저 개발자 도구 열기 (F12)
2. Network 탭 활성화
3. 독서 시작하기 버튼 클릭
4. 다음 요청들의 시간을 확인:
   - `POST /api/bookshelf/{userBookId}/start-reading` (카테고리 변경)
   - `GET /api/books/user-books` (전체 서재 조회 - getUserBookDetail 내부)
   - `GET /api/books/user-books` (전체 서재 조회 - loadBookshelf)

**확인 사항**:
- POST 요청 완료 시간
- 첫 번째 GET 요청 시작 시간 (POST 완료 후 얼마나 빠르게 시작되는지)
- 두 번째 GET 요청 시작 시간 (이벤트 발행 후)
- 각 요청의 응답 데이터 (카테고리 값)

**예상 결과 (원인 2인 경우)**:
```
POST 요청 완료: 10:00:00.100
GET 요청 1 시작: 10:00:00.105 (5ms 후) ← 너무 빠름
GET 요청 1 응답: category: "ToRead" (이전 카테고리) ❌
GET 요청 2 시작: 10:00:00.110 (10ms 후)
GET 요청 2 응답: category: "ToRead" (이전 카테고리) ❌

새로고침 후:
GET 요청 시작: 10:00:01.000 (약 1초 후)
GET 요청 응답: category: "Reading" (정확한 카테고리) ✅
```

#### 방법 2: 프론트엔드 로그 추가

**코드 수정** (`book-service.js`):
```javascript
async startReading(userBookId, startReadingData) {
  const postStartTime = Date.now();
  console.log('[BookService] startReading 시작:', new Date().toISOString());
  
  // 1. 서버 POST 요청 (카테고리 변경)
  const postResponse = await apiClient.post(API_ENDPOINTS.BOOKSHELF.START_READING(userBookId), startReadingData);
  const postEndTime = Date.now();
  console.log('[BookService] POST 요청 완료:', postEndTime - postStartTime, 'ms');
  
  // 2. 서버에서 최신 데이터 조회
  const getStartTime = Date.now();
  const updatedServerBook = await this.getUserBookDetail(userBookId);
  const getEndTime = Date.now();
  console.log('[BookService] GET 요청 완료:', getEndTime - getStartTime, 'ms');
  console.log('[BookService] POST-GET 간격:', getEndTime - postEndTime, 'ms');
  console.log('[BookService] 반환된 카테고리:', updatedServerBook?.category);
  
  // 3. IndexedDB 업데이트 완료 대기
  await BookOperationHelper.updateLocalAfterUpdate(userBookId, updatedServerBook);
  
  return '독서를 시작했습니다.';
}
```

**코드 수정** (`bookshelf-view.js`):
```javascript
async loadBookshelf() {
  const loadStartTime = Date.now();
  console.log('[BookshelfView] loadBookshelf 시작:', new Date().toISOString());
  
  try {
    const response = await bookService.getBookshelf({
      sortBy: this.currentSortBy,
    });
    
    const loadEndTime = Date.now();
    console.log('[BookshelfView] loadBookshelf 완료:', loadEndTime - loadStartTime, 'ms');
    console.log('[BookshelfView] 반환된 책 수:', response.books?.length);
    console.log('[BookshelfView] 카테고리별 책 수:', 
      response.books?.reduce((acc, book) => {
        acc[book.category] = (acc[book.category] || 0) + 1;
        return acc;
      }, {})
    );
    
    this.allBooks = response.books || [];
    // ...
  }
}
```

**확인 사항**:
- POST 요청 완료 후 GET 요청까지의 시간 간격
- GET 요청에서 반환된 카테고리 값
- `loadBookshelf`에서 반환된 카테고리별 책 수

#### 방법 3: 서버 로그 확인 (백엔드)

**확인할 로그**:
1. `startReading` API 호출 시간
2. 트랜잭션 시작/커밋 시간
3. `getUserBooks` API 호출 시간 및 반환된 데이터

**예상 결과 (원인 2인 경우)**:
```
10:00:00.100 - POST /api/bookshelf/123/start-reading 시작
10:00:00.150 - 트랜잭션 시작
10:00:00.200 - Primary DB 업데이트 완료
10:00:00.250 - Secondary DB 업데이트 완료
10:00:00.300 - 트랜잭션 커밋 완료
10:00:00.305 - POST 응답 반환

10:00:00.310 - GET /api/books/user-books 호출 (트랜잭션 커밋 후 10ms)
10:00:00.320 - DB 조회: category = "Reading" ✅

10:00:00.105 - GET /api/books/user-books 호출 (트랜잭션 커밋 전) ❌
10:00:00.115 - DB 조회: category = "ToRead" (이전 데이터) ❌
```

### Network 탭 분석 결과

**관찰된 요청 순서**:
1. `books?category=ToRead&sortBy=TITLE` - 첫 번째 요청 (423ms)
2. `books?category=Reading&sortBy=TITLE` - 두 번째 요청 (1.02s)
3. `books?category=ToRead&sortBy=TITLE` - 세 번째 요청 (300ms) ❌ **문제 발생**

**중요한 발견**:
- `POST /api/bookshelf/{userBookId}/start-reading` 요청이 Network 탭에 보이지 않음
  - 이미 완료되었거나 스크린샷 범위 밖일 수 있음
- `Reading` 카테고리 조회 후 다시 `ToRead` 카테고리 조회가 발생
- 이는 `loadBookshelf`에서 `currentCategory`가 `null`이면 첫 번째 카테고리(`ToRead`)를 선택하기 때문

**요청 흐름 분석**:
```
1. 독서 시작하기 버튼 클릭
   ↓
2. POST /api/bookshelf/{userBookId}/start-reading (카테고리 변경)
   ↓ (트랜잭션 커밋 중...)
3. GET /api/books/user-books (getUserBookDetail 내부)
   → 전체 서재 조회 → 이전 카테고리(ToRead) 데이터 반환 ❌
   ↓
4. IndexedDB 업데이트 (이전 카테고리 데이터로 업데이트) ❌
   ↓
5. BOOKSHELF_UPDATED 이벤트 발행
   ↓
6. loadBookshelf() 호출
   ↓
7. GET /api/books/user-books (전체 서재 조회, category 없음)
   → 이전 카테고리(ToRead) 데이터 포함 ❌
   ↓
8. currentCategory = null → 첫 번째 카테고리(ToRead) 선택
   ↓
9. GET /api/books/user-books?category=ToRead
   → 이전 카테고리 데이터 반환 (트랜잭션 미커밋) ❌
   ↓
10. UI에 ToRead 카테고리로 표시 ❌
```

### 결론: 원인 2 (타이밍 이슈) 확정

**근거**:
1. **새로고침 시 정확한 데이터 표시**: 시간이 지나 트랜잭션이 커밋된 후이므로 정확한 데이터가 반환됨
2. **즉시 조회 시 잘못된 데이터**: POST 요청 직후 즉시 GET 요청하면 트랜잭션이 커밋되기 전에 조회할 수 있음
3. **Dual Write 전략 사용**: Primary DB와 Secondary DB 모두 업데이트하는 시간이 필요하여 타이밍 이슈 발생 가능
4. **5분 후 정상 반영**: 트랜잭션이 완전히 커밋되고 모든 DB 복제본이 동기화된 후 정확한 데이터 반환
5. **Network 탭 요청 순서**: `Reading` 조회 후 다시 `ToRead` 조회가 발생하는 것은 서버 트랜잭션이 아직 커밋되지 않아 이전 데이터가 반환되기 때문

### IndexedDB 대기 때문인가?

**답변: 아니요, IndexedDB 대기 때문이 아닙니다.**

**이유**:
1. **해결방안 1이 이미 적용됨**: `startReading` 내부에서 `await BookOperationHelper.updateLocalAfterUpdate()`로 IndexedDB 업데이트를 기다리고 있습니다.
2. **문제의 근본 원인**: IndexedDB가 아니라 **서버에서 반환하는 데이터 자체가 잘못됨**
   - `getUserBookDetail`에서 서버 전체 서재 조회 시 이전 카테고리(`ToRead`) 데이터가 반환됨
   - 이 잘못된 데이터가 IndexedDB에 저장됨
3. **5분 후 정상 반영**: IndexedDB 문제라면 새로고침 후에도 문제가 지속되어야 하지만, 5분 후 정상 반영되는 것은 서버 트랜잭션이 커밋된 후이기 때문

**실제 문제**:
- **서버 트랜잭션 커밋 타이밍 이슈**
- POST 요청 완료 ≠ 트랜잭션 커밋 완료
- POST 직후 GET 요청 시 아직 커밋되지 않은 데이터 조회
- Dual Write 전략으로 인한 추가 지연 (Primary + Secondary DB 업데이트)

### 트랜잭션 커밋 타이밍 이슈 상세 설명

#### HTTP 트랜잭션 vs DB 트랜잭션

**중요한 구분**:
- **HTTP 트랜잭션**: 클라이언트가 요청을 보내고 서버가 응답을 반환하는 하나의 완전한 교환 과정을 의미합니다 (RFC 2616 HTTP/1.1에서 정의). 요청-응답이 모두 처리될 때까지 걸리는 하나의 과정입니다. HTTP는 stateless 프로토콜이므로 각 HTTP 트랜잭션은 독립적이며, 이전 요청의 상태를 유지하지 않습니다.
- **DB 트랜잭션**: 데이터베이스에서 데이터의 일관성을 보장하기 위한 작업 단위입니다. ACID 속성(원자성, 일관성, 격리성, 지속성)을 가집니다.

**핵심 차이점**:
- **HTTP 트랜잭션**: 요청-응답 교환 과정 자체는 원자적입니다 (요청이 보내지고 응답이 받아지거나 실패하는 둘 중 하나). 하지만 HTTP 트랜잭션은 **서버 내부의 DB 트랜잭션과는 독립적**입니다. HTTP 트랜잭션이 완료되었다고 해서 서버 내부에서 처리되는 DB 트랜잭션이 완료되었다는 보장은 없습니다. 또한 HTTP는 stateless이므로, 여러 HTTP 트랜잭션 간에는 DB 트랜잭션의 격리성(Isolation)과 같은 메커니즘이 없습니다. 각 HTTP 트랜잭션은 독립적으로 처리되며, 서버 내부의 DB 트랜잭션 상태와는 별개로 완료될 수 있습니다.
- **DB 트랜잭션**: 데이터의 일관성을 보장하기 위한 작업 단위이며, ACID 속성을 가집니다. 특히 격리성(Isolation)은 여러 DB 트랜잭션이 동시에 실행될 때 서로 간섭하지 않도록 보장합니다. 커밋되기 전까지는 다른 트랜잭션에서 변경사항을 볼 수 없습니다.

**구체적인 의미**:
- **HTTP 트랜잭션의 원자성**: HTTP 요청-응답 교환 자체는 원자적입니다. 하지만 이것은 "네트워크 레벨의 원자성"이며, "서버 내부 DB 작업의 원자성"과는 다릅니다.
- **HTTP 트랜잭션과 DB 트랜잭션의 관계**: 하나의 HTTP 트랜잭션(POST 요청)이 완료되어 HTTP 응답이 반환되어도, 서버 내부에서 시작된 DB 트랜잭션이 아직 커밋되지 않았을 수 있습니다. 다음 HTTP 트랜잭션(GET 요청)이 실행될 때, 이전 HTTP 트랜잭션에서 시작된 DB 트랜잭션이 아직 커밋되지 않은 상태에서 DB 조회가 이루어질 수 있습니다.
- **격리성의 차이**: DB 트랜잭션의 격리성은 여러 DB 트랜잭션이 동시에 실행될 때 서로 간섭하지 않도록 보장합니다. HTTP 트랜잭션은 서로 독립적이지만, 이것은 "격리성"이라기보다는 "stateless" 특성입니다. HTTP 트랜잭션 간에는 DB 트랜잭션의 격리성과 같은 메커니즘이 없으므로, 서버 내부의 DB 트랜잭션 상태를 HTTP 트랜잭션 완료 여부로 판단할 수 없습니다.

**실제 문제는 DB 트랜잭션 커밋 타이밍 이슈**입니다. HTTP 트랜잭션 자체가 문제가 아니라, HTTP 요청 처리 과정에서 시작된 DB 트랜잭션이 아직 완전히 커밋되지 않은 상태에서 다음 HTTP 요청이 실행되어 DB 조회가 이루어질 때 발생하는 문제입니다.

#### 구체적인 동작 흐름 및 문제 발생 지점

**정상적인 흐름 (이상적)**:
```
1. 클라이언트: POST /api/bookshelf/{userBookId}/start-reading 요청
   ↓
2. 서버: HTTP 요청 수신
   ↓
3. 서버: DB 트랜잭션 시작
   ↓
4. 서버: Primary DB에 카테고리 변경 (ToRead → Reading)
   ↓
5. 서버: Secondary DB에 카테고리 변경 (Dual Write)
   ↓
6. 서버: DB 트랜잭션 커밋 완료 ✅
   ↓
7. 서버: HTTP 200 OK 응답 반환
   ↓
8. 클라이언트: HTTP 응답 수신
   ↓
9. 클라이언트: GET /api/books/user-books 요청
   ↓
10. 서버: DB에서 최신 데이터 조회 (category = "Reading") ✅
   ↓
11. 서버: HTTP 200 OK 응답 반환 (정확한 데이터)
```

**실제 발생하는 문제 흐름**:
```
1. 클라이언트: POST /api/bookshelf/{userBookId}/start-reading 요청
   ↓
2. 서버: HTTP 요청 수신
   ↓
3. 서버: DB 트랜잭션 시작
   ↓
4. 서버: Primary DB에 카테고리 변경 (ToRead → Reading)
   ↓
5. 서버: Secondary DB에 카테고리 변경 시작 (Dual Write)
   ↓
6. 서버: HTTP 200 OK 응답 반환 ⚠️ (트랜잭션은 아직 커밋 중...)
   ↓
7. 클라이언트: HTTP 응답 수신 (POST 요청 완료로 인식)
   ↓
8. 클라이언트: 즉시 GET /api/books/user-books 요청 시작 ⚠️
   ↓
9. 서버: HTTP 요청 수신
   ↓
10. 서버: DB 조회 시도
    ↓
11. 서버: DB 트랜잭션이 아직 완전히 커밋되지 않음 ⚠️
    - Primary DB: 변경 완료 (category = "Reading")
    - Secondary DB: 변경 중 또는 커밋 대기 중
    - 또는 DB 복제본/인덱스가 아직 업데이트되지 않음
    ↓
12. 서버: 이전 데이터 조회 (category = "ToRead") ❌
    - 트랜잭션 격리 수준에 따라 커밋되지 않은 변경사항이 보이지 않음
    - 또는 DB 복제본/인덱스가 아직 동기화되지 않음
    ↓
13. 서버: HTTP 200 OK 응답 반환 (잘못된 데이터 포함) ❌
    ↓
14. 클라이언트: 잘못된 데이터 수신 및 IndexedDB 저장 ❌
```

#### 문제 발생의 핵심 원인

**1. HTTP 요청/응답과 DB 트랜잭션의 비동기성**

- **HTTP 응답 반환 시점**: 서버가 HTTP 응답을 반환하는 시점과 DB 트랜잭션이 완전히 커밋되는 시점이 다를 수 있습니다.
- **서버 구현 방식**: 
  - 일부 서버는 DB 트랜잭션 커밋을 기다리지 않고 HTTP 응답을 먼저 반환할 수 있습니다.
  - 특히 비동기 처리나 Dual Write 전략을 사용하는 경우, HTTP 응답은 즉시 반환하되 DB 업데이트는 백그라운드에서 처리할 수 있습니다.

**2. Dual Write 전략으로 인한 추가 지연**

- **Primary DB 업데이트**: 즉시 완료될 수 있음
- **Secondary DB 업데이트**: 추가 시간 필요
- **트랜잭션 커밋**: 모든 DB 업데이트가 완료된 후 커밋
- **총 소요 시간**: Primary + Secondary DB 업데이트 시간 + 커밋 시간

**3. DB 트랜잭션 격리 수준**

- **트랜잭션 격리 수준**: 다른 트랜잭션에서 커밋되지 않은 변경사항을 볼 수 없도록 보장
- **문제 발생**: POST 요청의 트랜잭션이 아직 커밋되지 않은 상태에서 GET 요청이 실행되면, 이전 데이터를 조회할 수 있습니다.

**4. DB 복제본/인덱스 동기화 지연**

- **DB 복제본**: Primary DB 변경사항이 Secondary DB나 읽기 전용 복제본에 반영되는 데 시간이 걸릴 수 있습니다.
- **인덱스 업데이트**: 카테고리 변경 시 인덱스가 업데이트되는 데 시간이 걸릴 수 있습니다.
- **조회 시점**: 인덱스가 업데이트되기 전에 조회하면 이전 카테고리 데이터가 반환될 수 있습니다.

#### 정확한 문제 발생 지점

**문제 발생 지점**: **서버 내부의 DB 트랜잭션 커밋 완료 전에 GET 요청이 실행되어 DB 조회가 이루어지는 시점**

**상세 설명**:
1. **POST 요청 처리 중**: 서버가 DB 트랜잭션을 시작하고 데이터 변경을 수행합니다.
2. **HTTP 응답 반환**: 서버가 HTTP 200 OK 응답을 반환합니다. 이 시점에 DB 트랜잭션이 완전히 커밋되었는지는 보장되지 않습니다.
3. **클라이언트의 즉시 GET 요청**: 클라이언트는 HTTP 응답을 받으면 POST 요청이 완료되었다고 판단하고, 즉시 GET 요청을 실행합니다.
4. **서버의 DB 조회**: 서버가 GET 요청을 받아 DB를 조회합니다.
5. **문제 발생**: 이 시점에 DB 트랜잭션이 아직 완전히 커밋되지 않았거나, DB 복제본/인덱스가 아직 동기화되지 않아 이전 데이터(`ToRead`)를 조회합니다.
6. **잘못된 데이터 반환**: 서버가 이전 카테고리 데이터를 포함한 응답을 반환합니다.

**5분 후 정상 반영되는 이유**:
- DB 트랜잭션이 완전히 커밋되었습니다.
- 모든 DB 복제본이 동기화되었습니다.
- 인덱스가 업데이트되었습니다.
- 이후 GET 요청 시 정확한 데이터(`Reading`)를 조회할 수 있습니다.

#### 결론

**문제의 정확한 원인**:
- HTTP 트랜잭션이 아니라 **DB 트랜잭션 커밋 타이밍 이슈**입니다.
- HTTP 요청/응답과는 직접적인 연관이 없으며, **서버 내부에서 DB에 데이터를 저장하고 조회하는 DB 트랜잭션 처리 과정**에서 발생하는 문제입니다.
- 구체적으로는: **HTTP POST 요청 처리 중 시작된 DB 트랜잭션이 아직 완전히 커밋되지 않은 상태에서 HTTP GET 요청이 실행되어 DB 조회가 이루어질 때, 커밋되지 않은 변경사항이나 아직 동기화되지 않은 DB 복제본/인덱스에서 이전 데이터를 조회**하는 것이 문제입니다.

**확인 방법 우선순위**:
1. ✅ **방법 1 (브라우저 개발자 도구)**: 가장 빠르고 간단한 확인 방법
2. ✅ **방법 2 (프론트엔드 로그)**: 상세한 타이밍 정보 확인 가능
3. ⚠️ **방법 3 (서버 로그)**: 백엔드 접근이 필요한 경우

---

## 추가 확인 사항

### 1. 서버 API 응답 시간 측정

서버 API의 실제 응답 시간을 측정하여 적절한 재시도 간격을 결정해야 합니다.

### 2. Dual Write 전략 확인

백엔드에서 Dual Write 전략을 사용하는 경우, Primary DB와 Secondary DB 모두 업데이트하는 시간이 필요합니다. 이 시간을 고려하여 재시도 로직을 조정해야 합니다.

#### Dual Write 전략 상세 분석

**코드 확인 결과:**

1. **Dual Write 전략 사용 확인**
   - `startReading` API는 `updateUserShelfBookWithDualWrite` 메서드를 통해 `DualMasterWriteService.writeWithDualWrite`를 호출합니다.
   - Primary DB와 Secondary DB에 순차적으로 쓰기 작업을 수행합니다.

2. **트랜잭션 처리 방식**
   ```java
   // Phase 1: Primary DB 트랜잭션
   TransactionTemplate primaryTx = new TransactionTemplate(primaryTxManager);
   primaryResult = primaryTx.execute(status -> primaryWriteOperation.get());
   
   // Phase 2: Secondary DB 트랜잭션 (Primary 완료 후)
   TransactionTemplate secondaryTx = new TransactionTemplate(secondaryTxManager);
   secondaryTx.execute(status -> {
       secondaryWriteOperation.apply(secondaryJdbcTemplate, primaryResult);
       return null;
   });
   ```
   - Primary 트랜잭션이 **먼저 커밋**된 후, Secondary 트랜잭션이 시작됩니다.
   - 두 트랜잭션이 **모두 완료된 후**에 HTTP 응답이 반환됩니다.

3. **중요한 발견 사항**

   **✅ Dual Write로 인한 지연 가능성: 부분적으로 맞음**
   
   - Dual Write 전략 자체는 Primary와 Secondary DB 모두 업데이트하는 시간이 필요합니다.
   - 하지만 **HTTP 응답은 두 트랜잭션이 모두 완료된 후에 반환**됩니다.
   - 따라서 HTTP 응답이 반환되었다면, Primary와 Secondary DB 트랜잭션은 모두 커밋된 상태입니다.

   **❌ Dual Write가 직접적인 원인은 아닐 가능성이 높음**
   
   - HTTP 응답이 반환되었다면 DB 트랜잭션은 이미 커밋된 상태입니다.
   - 그럼에도 불구하고 5분 후에 정상 반영되는 것은 **다른 원인**일 가능성이 높습니다.

4. **가능한 다른 원인**

   **a) DB 복제본/읽기 전용 인스턴스 동기화 지연**
   - Primary/Secondary DB의 변경사항이 **읽기 전용 복제본**에 반영되는 데 시간이 걸릴 수 있습니다.
   - `getUserBookDetail` 또는 `getBookshelf` API가 읽기 전용 복제본에서 조회하는 경우, 이전 데이터를 볼 수 있습니다.
   - **확인 필요**: 백엔드에서 `getUserBooks` API가 어떤 DB에서 조회하는지 확인 필요
     - Primary DB에서 직접 조회하는가?
     - Secondary DB에서 조회하는가?
     - 읽기 전용 복제본에서 조회하는가?

   **b) 트랜잭션 격리 수준**
   - 트랜잭션 격리 수준에 따라 다른 트랜잭션에서 커밋된 변경사항을 즉시 보지 못할 수 있습니다.
   - 특히 `REPEATABLE READ` 또는 `SERIALIZABLE` 격리 수준을 사용하는 경우, 이미 시작된 트랜잭션은 커밋된 변경사항을 보지 못할 수 있습니다.

   **c) DB 인덱스 업데이트 지연**
   - 카테고리 변경 시 인덱스가 업데이트되는 데 시간이 걸릴 수 있습니다.
   - 인덱스가 업데이트되기 전에 조회하면 이전 카테고리 데이터가 반환될 수 있습니다.

5. **결론**

   - Dual Write 전략 자체는 HTTP 응답 반환 전에 완료되므로, **직접적인 원인은 아닐 가능성이 높습니다**.
   - 하지만 Dual Write로 인한 추가 처리 시간은 전체 응답 시간에 영향을 미칠 수 있습니다.
   - **실제 원인 파악을 위해서는**:
     1. 백엔드에서 `getUserBooks` API가 어떤 DB에서 조회하는지 확인
     2. DB 복제본 동기화 지연 여부 확인
     3. 트랜잭션 격리 수준 확인
     4. 인덱스 업데이트 지연 여부 확인

#### `getUserBooks` API DB 조회 분석 결과

**API 호출 흐름:**
1. **Controller**: `BookShelfController.getMyShelf()` → `GET /api/v1/user/books`
2. **Service**: `BookService.getMyShelf()`
3. **Read Strategy**: `DualMasterReadService.readWithFailover()`

**DB 조회 전략:**

백엔드는 **Dual Read Failover 전략**을 사용합니다:

```java
// BookService.getMyShelf() 내부
List<UserShelfBook> books = dualMasterReadService.readWithFailover(
    () -> {
        // Primary DB 조회 (JPA Repository 사용)
        if (finalCategory != null) {
            return getMyShelfByCategoryAndSort(userId, finalCategory, finalSortBy);
        } else {
            return getMyShelfBySort(userId, finalSortBy);
        }
    },
    () -> {
        // Secondary DB 조회 (JdbcTemplate DAO 사용)
        if (finalCategory != null) {
            return getMyShelfByCategoryAndSortSecondary(userId, finalCategory, finalSortBy);
        } else {
            return getMyShelfBySortSecondary(userId, finalSortBy);
        }
    }
);
```

**조회 순서:**

1. **Redis 캐시 확인** (캐시 키: `myShelf:{userId}:category:{category}:sort:{sortBy}`)
   - 캐시가 있으면 즉시 반환 (DB 조회 없음)
   - TTL: 5분

2. **Primary DB에서 먼저 조회 시도**
   - `UserShelfBookRepository` (JPA Repository) 사용
   - Primary DB에 직접 연결

3. **Primary DB 실패 시 Secondary DB로 Failover**
   - `SecondaryUserShelfBookDao` (JdbcTemplate) 사용
   - Secondary DB에 직접 연결

**결론:**

- **정상 동작 시**: **Primary DB에서 조회**합니다.
- **Primary DB 실패 시**: Secondary DB에서 조회합니다.

**문제 원인 분석:**

`getUserBooks` API는:
- 정상 시 **Primary DB에서 조회**합니다.
- Primary DB 실패 시에만 Secondary DB로 Failover합니다.

따라서:
- Dual Write로 Primary와 Secondary DB에 모두 쓰기가 완료된 후 HTTP 응답이 반환됩니다.
- 이후 `getUserBooks` 호출 시 **Primary DB에서 조회**합니다.
- 그럼에도 불구하고 5분 후에 정상 반영되는 것은 **다른 원인**일 가능성이 높습니다.

**가능한 원인:**

1. **Redis 캐시 TTL (5분)로 인한 이전 데이터 반환**
   - `getMyShelf`는 Redis 캐시를 먼저 확인하고, 캐시가 있으면 DB 조회 없이 캐시 데이터를 반환합니다.
   - 캐시 TTL이 5분이므로, 이전 카테고리 데이터가 캐시에 남아있을 수 있습니다.
   - **가장 유력한 원인**: Redis 캐시에 이전 카테고리 데이터가 남아있어, 캐시가 만료되기 전(5분)까지 이전 데이터를 반환하는 것으로 보입니다.
   <!-- 개선안 참고 문헌: REDIS_SHELF_CACHE_REMOVAL_INDEXEDDB_STRATEGY.md -->
   <! -- 이거 DB와 Redis 사이에 발생하는 데이터 정합성 문제였음 / 관련 문서 : https://seungjjun.tistory.com/246 --> 

2. **트랜잭션 격리 수준 문제**
   - 트랜잭션 격리 수준에 따라 다른 트랜잭션에서 커밋된 변경사항을 즉시 보지 못할 수 있습니다.

3. **DB 인덱스 업데이트 지연**
   - 카테고리 변경 시 인덱스가 업데이트되는 데 시간이 걸릴 수 있습니다.

### 4. Redis 캐시 vs IndexedDB 비교 분석

네트워크 장애 허용을 위한 임시 저장소로 Redis 캐시와 IndexedDB 중 어떤 것이 더 적합한지 비교 분석합니다.

#### 1. 네트워크 장애 허용 측면

| 항목 | Redis 캐시 | IndexedDB |
|------|-----------|-----------|
| 저장 위치 | 서버 측 (Redis 서버) | 클라이언트 측 (브라우저) |
| 네트워크 장애 시 접근 | ❌ 불가능 (서버 접근 필요) | ✅ 가능 (로컬 저장소) |
| 목적 부합도 | ❌ 부적합 | ✅ 적합 |

**결론**: 네트워크 장애 허용 목적에는 **IndexedDB가 적합**합니다.

#### 2. 효율성 측면

| 항목 | Redis 캐시 | IndexedDB |
|------|-----------|-----------|
| 저장소 위치 | 서버 메모리 | 클라이언트 디스크 |
| 네트워크 요청 | 필요 (서버 통신) | 불필요 (로컬 접근) |
| 서버 부하 | 증가 (Redis 메모리 사용) | 감소 (클라이언트 처리) |
| 확장성 | 서버 확장 필요 | 클라이언트 자동 확장 |
| 데이터 일관성 | 높음 (서버 DB와 동기화) | 중간 (클라이언트별 독립적) |

**결론**: 효율성 측면에서 **IndexedDB가 유리**합니다.

#### 3. 지연 시간 측면

| 항목 | Redis 캐시 | IndexedDB |
|------|-----------|-----------|
| 접근 속도 | 네트워크 지연 포함 (수십~수백 ms) | 로컬 접근 (1~10 ms) |
| 네트워크 장애 시 | 타임아웃 발생 (수 초) | 즉시 응답 (로컬) |
| 초기 로딩 | 서버 연결 필요 | 즉시 가능 |

**결론**: 지연 시간 측면에서 **IndexedDB가 유리**합니다.

#### 4. 현재 구현 분석

**Redis 캐시 사용 현황:**
```java
// BookService.getMyShelf()
// 1. Redis 캐시 확인 (서버 측)
List<UserShelfBookCacheDTO> cachedDtos = redisTemplate.opsForValue().get(cacheKey);
if (cachedDtos != null) {
    return bookMapper.toUserShelfBookEntityList(cachedDtos, userRepository);
}

// 2. DB 조회 후 Redis에 저장 (TTL: 5분)
redisTemplate.opsForValue().set(cacheKey, dtos, TTL_MINUTES, TimeUnit.MINUTES);
```

**문제점:**
- ❌ 네트워크 장애 시 Redis 접근 불가
- ❌ TTL 5분으로 인한 데이터 지연 (현재 문제의 원인)
- ❌ 서버 측 리소스 사용

**IndexedDB 사용 현황:**
```javascript
// book-service.js - getBookshelf()
if (networkMonitor.isOnline) {
    // 온라인: 서버에서 조회 후 IndexedDB에 캐시 저장
    const serverResponse = await apiClient.get(API_ENDPOINTS.BOOKS.USER_BOOKS, params);
    Promise.all(serverBooks.map(serverBook => 
        BookOperationHelper.saveServerBookAsLocal(serverBook)
    ));
} else {
    // 오프라인: IndexedDB에서만 조회
    localBooks = await offlineBookService.getAllBooks();
}
```

**장점:**
- ✅ 네트워크 장애 시에도 접근 가능
- ✅ 로컬 접근으로 빠른 응답
- ✅ 클라이언트 측 처리로 서버 부하 감소

#### 5. 종합 비교 및 판단

| 평가 기준 | Redis 캐시 | IndexedDB | 승자 |
|----------|-----------|-----------|------|
| 네트워크 장애 허용 | 불가능 | 가능 | IndexedDB |
| 효율성 | 중간 | 높음 | IndexedDB |
| 지연 시간 | 높음 (네트워크) | 낮음 (로컬) | IndexedDB |
| 목적 부합도 | 부적합 | 적합 | IndexedDB |
| 데이터 일관성 | 높음 (서버 동기) | 중간 (클라이언트별) | Redis |
| 서버 부하 | 높음 | 낮음 | IndexedDB |

#### 6. 최종 판단 및 권장사항

**결론**: 네트워크 장애 허용 목적에는 **IndexedDB가 더 적합**합니다.

**이유:**
1. ✅ 네트워크 장애 시에도 접근 가능
2. ✅ 로컬 접근으로 지연 시간이 낮음
3. ✅ 클라이언트 측 처리로 서버 부하 감소
4. ✅ 현재 프론트엔드에서 이미 IndexedDB 사용 중

**Redis 캐시의 문제점:**
1. ❌ 네트워크 장애 시 접근 불가
2. ❌ TTL 5분으로 인한 데이터 지연 (현재 문제의 원인)
3. ❌ 서버 측 리소스 사용

**권장사항:**
1. **Redis 캐시 제거 또는 용도 변경**
   - 네트워크 장애 허용 목적이 아닌 **성능 최적화 목적으로만 사용**
   - TTL을 단축하거나 Write-Through 패턴으로 즉시 무효화
2. **IndexedDB를 네트워크 장애 허용의 주 저장소로 사용**
   - 이미 구현되어 있으므로 현재 구조 유지
   - 오프라인 우선(Offline-First) 전략 강화

#### 7. 현재 문제 해결 방안

**현재 문제(5분 후 정상 반영)의 원인:**
- Redis 캐시 TTL 5분으로 인해 이전 데이터가 캐시에 남아있음
- `getMyShelf`가 Redis 캐시를 먼저 확인하여 DB 조회 없이 캐시 데이터 반환

**해결 방안:**
1. **즉시 해결**: `startReading` 후 Redis 캐시 즉시 무효화 (이미 구현됨)
2. **근본 해결**: Redis 캐시를 네트워크 장애 허용 목적에서 제외하고, IndexedDB만 사용

### 3. 로그 추가

디버깅을 위해 다음 로그를 추가하는 것이 좋습니다:
- `startReading` 시작/완료 시간
- `getUserBookDetail` 호출 시간 및 반환된 카테고리
- `loadBookshelf` 호출 시간 및 반환된 데이터

---

## 관련 파일

### 프론트엔드 파일

- `js/services/book-service.js`: `startReading`, `getBookshelf`, `getUserBookDetail` 메서드
- `js/views/pages/book-detail-view.js`: `submitStartReadingForm` 메서드
- `js/views/pages/bookshelf-view.js`: `loadBookshelf`, `loadBooksByCategory` 메서드
- `js/state/book-state.js`: `updateBookStatus` 메서드
- `js/utils/book-operation-helper.js`: `updateLocalAfterUpdate` 메서드

### 관련 문서

- `docs/troubleshooting/BOOK_CATEGORY_DUPLICATE_DISPLAY_ISSUE.md`: 초기 문제 분석 및 해결방안
- `docs/fault-tolerance/DUAL_WRITE_IMPLEMENTATION_ISSUES.md`: Dual Write 전략 관련 문서

---

## 결론

해결방안 1과 2를 적용했음에도 불구하고 문제가 재발생한 이유는 **서버 트랜잭션 처리 시간과 클라이언트 요청 타이밍 불일치** 때문입니다.

**핵심 문제**:
- POST 요청 완료 ≠ 트랜잭션 커밋 완료
- POST 요청 직후 GET 요청이 실행되면, 아직 커밋되지 않은 데이터를 조회할 수 있음
- 이벤트 기반 갱신으로 인해 트랜잭션 커밋 전에 서재 목록이 조회될 수 있음

**권장 해결책**:
- `startReading` 메서드에서 서버 데이터 변경을 확인하는 재시도 로직 추가
- 카테고리가 `Reading`으로 변경되었는지 확인한 후 IndexedDB 업데이트 및 반환
