# 비기능 품질 로직 흐름 분석 문서

> **작성일**: 2025-12-09  
> **목적**: 전제 1.pdf의 요구사항과 현재 구현된 시나리오 1(오프라인 메모 동기화) 및 시나리오 2(MySQL 이중화)의 로직 흐름 비교 분석  
> **상태**: ✅ 분석 완료

---

## 목차

1. [전제 요구사항 요약](#1-전제-요구사항-요약)
2. [질문별 로직 흐름 분석](#2-질문별-로직-흐름-분석)
3. [요구사항 준수 여부 종합](#3-요구사항-준수-여부-종합)
4. [시스템 전체 비기능 로직 사용 사례](#4-시스템-전체-비기능-로직-사용-사례)
5. [개선 필요 사항](#5-개선-필요-사항)

---

## 1. 전제 요구사항 요약

### 전제 1: 네트워크 상태 기반 데이터 접근 전략

**요구사항**:
- **온라인 상태**: 서버 DB에 먼저 접근하여 데이터를 read, insert, update, delete
- **오프라인 상태**: 로컬 저장소(IndexedDB)를 먼저 확인하여 데이터를 read, insert, update, delete
- **네트워크 재연결 시**: 로컬 저장소의 변경 사항을 서버 DB(PrimaryDB, SecondaryDB)에 동기화
- **동기화 완료 후**: 다시 서버 DB에 먼저 접근하여 데이터를 read

### 전제 2: 네트워크가 끊긴 상황에서 변경할 수 있는 데이터

- 내 서재 정보 (UserShelfBook)
- 메모 정보 (Memo)

### 전제 3: MySQL 이중화 및 동기화

**요구사항**:
- **Write 작업 (insert, update, delete)**:
  - PrimaryDB에 가장 먼저 실행
  - PrimaryDB 성공 시 SecondaryDB에 실행
  - PrimaryDB 실패 시 SecondaryDB로 Failover 불가 (데이터 일관성 유지)
- **Read 작업 (select)**:
  - PrimaryDB에서 먼저 읽기 시도
  - PrimaryDB 실패 시 SecondaryDB로 Failover (데이터 일관성에 영향 없음)
  - 두 DB 모두 실패 시에만 Exception 발생

### 전제 4: DB 구성

- PrimaryDB: 로컬 노트북 MySQL 8.0
- SecondaryDB: Docker Compose MySQL 8.0

---

## 2. 질문별 로직 흐름 분석

### 질문 1: 네트워크가 끊겼을 경우 IndexedDB에서 어떻게 데이터를 가져오나요?

#### 전제 요구사항
- 오프라인 상태에서 로컬 저장소(IndexedDB)를 먼저 확인하여 데이터를 read

#### 현재 구현 흐름

**메모 데이터 (Memo)**:
```
사용자 메모 조회 요청
    ↓
memo-service.js.getMemosByBook()
    ↓
networkMonitor.isOnline 확인
    ├─ false (오프라인)
    │   ↓
    │   offlineMemoService.getMemosByBook()
    │   ↓
    │   dbManager.getMemosByBook(userBookId)
    │   ↓
    │   IndexedDB 'offline_memos' 테이블에서 조회
    │   - 인덱스: userBookId
    │   ↓
    │   로컬 메모 반환 (최근 7일 메모만 저장됨)
    │
    └─ true (온라인)
        ↓
        apiClient.get(API_ENDPOINTS.MEMOS.BY_BOOK(userBookId))
        ↓
        서버 측: MemoService.getAllBookMemos()
        ↓
        DualMasterReadService.readWithFailover()
        ↓
        Read Failover 처리:
            ├─ Primary DB에서 읽기 시도
            │   ├─ 성공 → 메모 목록 반환
            │   └─ 실패 → Secondary DB로 Failover
            │       ├─ 성공 → 메모 목록 반환
            │       └─ 실패 → DatabaseUnavailableException 발생
        ↓
        서버 응답 (메모 목록)
        ↓
        IndexedDB에 최근 7일 메모 저장
        ↓
        로컬 메모와 서버 메모 통합 반환
```

**내 서재 정보 (UserShelfBook)**:
```
사용자 서재 조회 요청
    ↓
book-service.js.getBookshelf()
    ↓
networkMonitor.isOnline 확인
    ├─ false (오프라인)
    │   ↓
    │   offlineBookService.getAllBooks() 또는 getBooksByCategory()
    │   ↓
    │   dbManager.getAllBooks() 또는 getBooksByCategory()
    │   ↓
    │   IndexedDB 'offline_books' 테이블에서 조회
    │   - 인덱스: category (카테고리별 조회 시)
    │   ↓
    │   로컬 내 서재 정보 반환
    │
    └─ true (온라인)
        ↓
        apiClient.get(API_ENDPOINTS.BOOKS.USER_BOOKS)
        ↓
        서버 측: BookService.getMyShelf()
        ↓
        DualMasterReadService.readWithFailover()
        ↓
        Read Failover 처리:
            ├─ Primary DB에서 읽기 시도
            │   ├─ 성공 → 서재 정보 반환
            │   └─ 실패 → Secondary DB로 Failover
            │       ├─ 성공 → 서재 정보 반환
            │       └─ 실패 → DatabaseUnavailableException 발생
        ↓
        서버 응답 (서재 정보)
        ↓
        BookOperationHelper.saveServerBookAsLocal(serverBook)
        ↓
        IndexedDB에 내 서재 정보 저장
        ↓
        로컬 내 서재 정보와 서버 내 서재 정보 통합 반환
```

#### 요구사항 준수 여부

| 데이터 타입 | 요구사항 | 현재 구현 | 준수 여부 |
|------------|---------|----------|----------|
| **메모 정보** | IndexedDB에서 read | ✅ IndexedDB에서 read | ✅ **준수** |
| **내 서재 정보** | IndexedDB에서 read | ✅ IndexedDB에서 read | ✅ **준수** |

**분석**:
- ✅ 메모 정보는 오프라인 상태에서 IndexedDB에서 정상적으로 읽어옴
- ✅ 내 서재 정보는 오프라인 상태에서 IndexedDB에서 정상적으로 읽어옴

---

### 질문 2: 처음 로그인 시 서버DB에서 데이터를 IndexedDB에 저장하는 시점

#### 전제 요구사항
- 서버를 실행한 후 당일에 웹 브라우저를 처음 열고, 처음 로그인을 한 사용자의 메모 데이터와 내 서재 정보는 서버DB에서 가져와 IndexedDB에 저장

#### 현재 구현 흐름

**메모 데이터 (Memo)**:
```
사용자 로그인 성공
    ↓
사용자가 메모 조회 화면 접근
    ↓
memo-service.js.getMemosByBook() 호출
    ↓
networkMonitor.isOnline === true
    ↓
apiClient.get(API_ENDPOINTS.MEMOS.BY_BOOK(userBookId))
    ↓
서버 측: MemoService.getAllBookMemos()
    ↓
DualMasterReadService.readWithFailover()
    ↓
Read Failover 처리:
    ├─ Primary DB에서 읽기 시도
    │   ├─ 성공 → 메모 목록 반환
    │   └─ 실패 → Secondary DB로 Failover
    │       ├─ 성공 → 메모 목록 반환
    │       └─ 실패 → DatabaseUnavailableException 발생
    ↓
서버 응답 (메모 목록)
    ↓
MemoOperationHelper.saveServerMemoAsLocal(serverMemo)
    ↓
최근 7일 메모만 IndexedDB에 저장
    - memoStartTime 기준으로 7일 이내 메모만 저장
    - 7일 이상 된 메모는 저장하지 않음
    ↓
로컬 메모와 서버 메모 통합 반환
```

**내 서재 정보 (UserShelfBook)**:
```
사용자 로그인 성공
    ↓
사용자가 서재 화면 접근
    ↓
book-service.js.getBookshelf() 호출
    ↓
networkMonitor.isOnline === true
    ↓
apiClient.get(API_ENDPOINTS.BOOKS.USER_BOOKS)
    ↓
서버 측: BookService.getMyShelf()
    ↓
DualMasterReadService.readWithFailover()
    ↓
Read Failover 처리:
    ├─ Primary DB에서 읽기 시도
    │   ├─ 성공 → 서재 정보 반환
    │   └─ 실패 → Secondary DB로 Failover
    │       ├─ 성공 → 서재 정보 반환
    │       └─ 실패 → DatabaseUnavailableException 발생
    ↓
서버 응답 (서재 정보)
    ↓
BookOperationHelper.saveServerBookAsLocal(serverBook)
    ↓
IndexedDB에 내 서재 정보 저장
    ↓
로컬 내 서재 정보와 서버 내 서재 정보 통합 반환
```

#### 요구사항 준수 여부

| 데이터 타입 | 요구사항 | 현재 구현 | 준수 여부 |
|------------|---------|----------|----------|
| **메모 정보** | 처음 로그인 시 서버DB에서 IndexedDB에 저장 | ✅ 조회 시 최근 7일 메모만 저장 | ⚠️ **부분 준수** (7일 제한) |
| **내 서재 정보** | 처음 로그인 시 서버DB에서 IndexedDB에 저장 | ✅ 조회 시 저장 | ⚠️ **부분 준수** (명시적 로그인 시점 아님) |

**분석**:
- ⚠️ 메모 정보는 조회 시점에 저장되지만, 최근 7일 메모만 저장 (하이브리드 전략)
- ⚠️ 내 서재 정보는 조회 시점에 저장되지만, 명시적으로 로그인 시점에 저장하지는 않음

**개선 필요**:
- 처음 로그인 시 명시적으로 서버DB에서 데이터를 가져와 IndexedDB에 저장하는 로직 추가 필요

---

### 질문 3: 네트워크 접속이 끊어진 후 내 서재 정보 insert/update/delete 처리

#### 전제 요구사항
- 오프라인 상태에서 내 서재 정보를 insert, delete, update한 경우 IndexedDB에 저장
- 오프라인 상태에서 데이터를 read할 때 IndexedDB에서 읽어옴

#### 현재 구현 흐름

**내 서재 정보 Insert (도서 추가)**:
```
사용자 도서 추가 요청
    ↓
book-service.js.addBookToShelf(bookData)
    ↓
networkMonitor.isOnline 확인
    ├─ false (오프라인)
    │   ↓
    │   offlineBookService.addBookToShelf(bookData)
    │   ↓
    │   1. 로컬 ID 생성 (UUID v4)
    │   2. 멱등성 키 생성
    │   3. IndexedDB 'offline_books' 테이블에 저장
    │      - syncStatus: 'pending'
    │      - serverId: null
    │   4. sync_queue 테이블에 CREATE 항목 추가
    │      - type: 'CREATE'
    │      - status: 'PENDING'
    │      - idempotencyKey: 생성된 키
    │   5. UI 즉시 업데이트 (Optimistic UI)
    │   ↓
    │   로컬 내 서재 정보 반환
    │
    └─ true (온라인)
        ↓
        서버에 도서 추가 시도 (Primary/Secondary DB)
        ├─ 성공
        │   ↓
        │   BookOperationHelper.updateLocalAfterCreate(serverBook)
        │   ↓
        │   IndexedDB에 저장
        │   ↓
        │   서버 응답 반환
        │
        └─ 실패
            ↓
            BookOperationHelper.handleServerError()
            ↓
            네트워크 오류 감지
            ↓
            오프라인 로직으로 전환
            ↓
            offlineBookService.addBookToShelf(bookData)
```

**내 서재 정보 Update (도서 상태 변경)**:
```
사용자 도서 상태 변경 요청
    ↓
book-service.js.updateBookStatus(userBookId, category)
    ↓
networkMonitor.isOnline 확인
    ├─ false (오프라인)
    │   ↓
    │   offlineBookService.updateBook(userBookId, { category })
    │   ↓
    │   1. IndexedDB에서 내 서재 정보 조회 (localId 또는 serverId)
    │   2. 내 서재 정보 수정 내용 반영
    │   3. IndexedDB에 저장
    │      - syncStatus: 'pending' (serverId가 있는 경우)
    │   4. sync_queue 테이블에 UPDATE 항목 추가
    │      - type: 'UPDATE'
    │      - status: 'PENDING'
    │      - serverBookId: serverId
    │   5. UI 즉시 업데이트 (Optimistic UI)
    │   ↓
    │   로컬 내 서재 정보 반환
    │
    └─ true (온라인)
        ↓
        BookOperationHelper.getLocalBook(userBookId)
        ↓
        서버에 내 서재 정보 수정 시도 (Primary/Secondary DB)
        ├─ 성공
        │   ↓
        │   BookOperationHelper.updateLocalAfterUpdate(userBookId, updatedServerBook)
        │   ↓
        │   IndexedDB에 저장
        │   ↓
        │   수정된 내 서재 정보 반환
        │
        └─ 실패
            ↓
            BookOperationHelper.handleServerError()
            ↓
            오프라인 로직으로 전환
```

**내 서재 정보 Delete (도서 제거)**:
```
사용자 도서 제거 요청
    ↓
book-service.js.removeBookFromShelf(userBookId)
    ↓
networkMonitor.isOnline 확인
    ├─ false (오프라인)
    │   ↓
    │   offlineBookService.removeBookFromShelf(userBookId)
    │   ↓
    │   1. IndexedDB에서 내 서재 정보 조회
    │   2. serverId가 없는 경우 (아직 동기화되지 않은 내 서재 정보)
    │      - IndexedDB에서 즉시 삭제
    │      - sync_queue에서 CREATE 항목 제거
    │   3. serverId가 있는 경우
    │      - sync_queue 테이블에 DELETE 항목 추가
    │        - type: 'DELETE'
    │        - status: 'PENDING'
    │        - serverBookId: serverId
    │      - 내 서재 정보는 삭제 표시만 (실제 삭제는 동기화 후)
    │      - syncStatus: 'pending'
    │   4. UI 즉시 업데이트 (Optimistic Deletion)
    │   ↓
    │   삭제 결과 반환
    │
    └─ true (온라인)
        ↓
        BookOperationHelper.getLocalBook(userBookId)
        ↓
        서버에서 내 서재 정보 삭제 시도 (Primary/Secondary DB)
        ├─ 성공
        │   ↓
        │   BookOperationHelper.updateLocalAfterDelete(serverId)
        │   ↓
        │   IndexedDB에서 내 서재 정보 삭제
        │   ↓
        │   삭제 성공 메시지 반환
        │
        └─ 실패
            ↓
            BookOperationHelper.handleServerError()
            ↓
            오프라인 로직으로 전환
```

#### 요구사항 준수 여부

| 작업 | 요구사항 | 현재 구현 | 준수 여부 |
|------|---------|----------|----------|
| **Insert** | IndexedDB에 저장 | ✅ IndexedDB에 저장 + sync_queue 추가 | ✅ **준수** |
| **Update** | IndexedDB에 저장 | ✅ IndexedDB에 저장 + sync_queue 추가 | ✅ **준수** |
| **Delete** | IndexedDB에 저장 | ✅ IndexedDB에 삭제 표시 + sync_queue 추가 | ✅ **준수** |
| **Read** | IndexedDB에서 읽기 | ✅ IndexedDB에서 읽기 | ✅ **준수** |

**분석**:
- ✅ 내 서재 정보의 insert, update, delete는 오프라인 상태에서 IndexedDB에 정상적으로 저장됨
- ✅ Read는 오프라인 상태에서 IndexedDB에서 정상적으로 읽어옴

---

### 질문 4: 메모 정보 insert/update/delete 처리 (질문 3과 동일, 데이터만 변경)

#### 전제 요구사항
- 오프라인 상태에서 메모 정보를 insert, delete, update한 경우 IndexedDB에 저장
- 오프라인 상태에서 데이터를 read할 때 IndexedDB에서 읽어옴

#### 현재 구현 흐름

**메모 정보 Insert (메모 생성)**:
```
사용자 메모 생성 요청
    ↓
memo-service.js.createMemo(memoData)
    ↓
networkMonitor.isOnline 확인
    ├─ false (오프라인)
    │   ↓
    │   offlineMemoService.createMemo(memoData)
    │   ↓
    │   1. 로컬 ID 생성 (UUID v4)
    │   2. 멱등성 키 생성
    │   3. IndexedDB 'offline_memos' 테이블에 저장
    │      - syncStatus: 'pending'
    │      - serverId: null
    │   4. sync_queue 테이블에 CREATE 항목 추가
    │      - type: 'CREATE'
    │      - status: 'PENDING'
    │      - idempotencyKey: 생성된 키
    │   5. UI 즉시 업데이트 (Optimistic UI)
    │   ↓
    │   로컬 메모 반환
    │
    └─ true (온라인)
        ↓
        서버에 메모 생성 시도 (Primary/Secondary DB)
        ├─ 성공
        │   ↓
        │   MemoOperationHelper.updateLocalAfterCreate(serverMemo)
        │   ↓
        │   IndexedDB에 저장 (최근 7일 메모만)
        │   ↓
        │   서버 메모 반환
        │
        └─ 실패
            ↓
            MemoOperationHelper.handleServerError()
            ↓
            네트워크 오류 감지
            ↓
            오프라인 로직으로 전환
            ↓
            offlineMemoService.createMemo(memoData)
```

**메모 정보 Update (메모 수정)**:
```
사용자 메모 수정 요청
    ↓
memo-service.js.updateMemo(memoId, memoData)
    ↓
networkMonitor.isOnline 확인
    ├─ false (오프라인)
    │   ↓
    │   offlineMemoService.updateMemo(memoId, memoData)
    │   ↓
    │   1. IndexedDB에서 메모 조회 (localId 또는 serverId)
    │   2. 메모 수정 내용 반영
    │   3. IndexedDB에 저장
    │      - syncStatus: 'pending' (serverId가 있는 경우)
    │   4. sync_queue 테이블에 UPDATE 항목 추가
    │      - type: 'UPDATE'
    │      - status: 'PENDING'
    │      - serverMemoId: serverId
    │   5. UI 즉시 업데이트 (Optimistic UI)
    │   ↓
    │   로컬 메모 반환
    │
    └─ true (온라인)
        ↓
        MemoOperationHelper.getLocalMemo(memoId)
        ↓
        서버에 메모 수정 시도 (Primary/Secondary DB)
        ├─ 성공
        │   ↓
        │   MemoOperationHelper.updateLocalAfterUpdate(memoId, updatedServerMemo)
        │   ↓
        │   IndexedDB에 저장
        │   ↓
        │   수정된 메모 반환
        │
        └─ 실패
            ↓
            MemoOperationHelper.handleServerError()
            ↓
            오프라인 로직으로 전환
```

**메모 정보 Delete (메모 삭제)**:
```
사용자 메모 삭제 요청
    ↓
memo-service.js.deleteMemo(memoId)
    ↓
networkMonitor.isOnline 확인
    ├─ false (오프라인)
    │   ↓
    │   offlineMemoService.deleteMemo(memoId)
    │   ↓
    │   1. IndexedDB에서 메모 조회
    │   2. serverId가 없는 경우 (아직 동기화되지 않은 메모)
    │      - IndexedDB에서 즉시 삭제
    │      - sync_queue에서 CREATE 항목 제거
    │   3. serverId가 있는 경우
    │      - sync_queue 테이블에 DELETE 항목 추가
    │        - type: 'DELETE'
    │        - status: 'PENDING'
    │        - serverMemoId: serverId
    │      - 메모는 삭제 표시만 (실제 삭제는 동기화 후)
    │      - syncStatus: 'pending'
    │   4. UI 즉시 업데이트 (Optimistic Deletion)
    │   ↓
    │   삭제 결과 반환
    │
    └─ true (온라인)
        ↓
        MemoOperationHelper.getLocalMemo(memoId)
        ↓
        서버에서 메모 삭제 시도 (Primary/Secondary DB)
        ├─ 성공
        │   ↓
        │   MemoOperationHelper.updateLocalAfterDelete(serverId)
        │   ↓
        │   IndexedDB에서 메모 삭제
        │   ↓
        │   삭제 성공 메시지 반환
        │
        └─ 실패
            ↓
            MemoOperationHelper.handleServerError()
            ↓
            오프라인 로직으로 전환
```

**메모 정보 Read (메모 조회)**:
```
사용자 메모 조회 요청
    ↓
memo-service.js.getMemosByBook(userBookId, date)
    ↓
networkMonitor.isOnline 확인
    ├─ false (오프라인)
    │   ↓
    │   offlineMemoService.getMemosByBook(userBookId)
    │   ↓
    │   dbManager.getMemosByBook(userBookId)
    │   ↓
    │   IndexedDB 'offline_memos' 테이블에서 조회
    │   - 인덱스: userBookId
    │   - 최근 7일 메모만 저장되어 있음
    │   ↓
    │   로컬 메모 반환
    │
    └─ true (온라인)
        ↓
        apiClient.get(API_ENDPOINTS.MEMOS.BY_BOOK(userBookId))
        ↓
        서버 측: MemoService.getAllBookMemos()
        ↓
        DualMasterReadService.readWithFailover()
        ↓
        Read Failover 처리:
            ├─ Primary DB에서 읽기 시도
            │   ├─ 성공 → 메모 목록 반환
            │   └─ 실패 → Secondary DB로 Failover
            │       ├─ 성공 → 메모 목록 반환
            │       └─ 실패 → DatabaseUnavailableException 발생
        ↓
        서버 응답 (메모 목록)
        ↓
        최근 7일 메모만 IndexedDB에 저장
        ↓
        로컬 메모와 서버 메모 통합 반환
```

#### 요구사항 준수 여부

| 작업 | 요구사항 | 현재 구현 | 준수 여부 |
|------|---------|----------|----------|
| **Insert** | IndexedDB에 저장 | ✅ IndexedDB에 저장 + sync_queue 추가 | ✅ **준수** |
| **Update** | IndexedDB에 저장 | ✅ IndexedDB에 저장 + sync_queue 추가 | ✅ **준수** |
| **Delete** | IndexedDB에 저장 | ✅ IndexedDB에 삭제 표시 + sync_queue 추가 | ✅ **준수** |
| **Read** | IndexedDB에서 읽기 | ✅ IndexedDB에서 읽기 (최근 7일만) | ⚠️ **부분 준수** (7일 제한) |

**분석**:
- ✅ 메모 정보의 insert, update, delete는 오프라인 상태에서 IndexedDB에 정상적으로 저장됨
- ⚠️ Read는 최근 7일 메모만 IndexedDB에 저장되어 있음 (하이브리드 전략)

---

### 질문 3-1: 네트워크 재연결 시 IndexedDB 데이터를 서버DB로 동기화

#### 전제 요구사항
- 네트워크 접속이 다시 연결되면 IndexedDB에 저장된 데이터에 대해서 서버DB로 변경 사항을 반영하여 동기화 (멱등성 키 포함)

#### 현재 구현 흐름

**동기화 트리거**:
```
네트워크 재연결 감지
    ↓
window.addEventListener('online')
    ↓
networkMonitor.onNetworkOnline()
    ↓
1초 대기 (네트워크 안정화)
    ↓
2-Phase Health Check
    ├─ Phase 1: 로컬 서버 헬스체크
    │   - checkServerHealth()
    │   - /api/v1/health 엔드포인트 확인
    │   ├─ 실패 → 5초 후 재시도
    │   └─ 성공 → Phase 2 진행
    │
    └─ Phase 2: 외부 서비스 헬스체크
        - checkExternalServiceHealth()
        - /api/v1/health/aladin 엔드포인트 확인
        ↓
        networkStatusChanged 이벤트 발행
        ↓
        NetworkStateManager.transitionToOnline()
        ↓
        eventBus.publish('network:online')
        ↓
        offlineMemoService.syncPendingMemos() (이벤트 구독)
```

**동기화 실행**:
```
offlineMemoService.syncPendingMemos()
    ↓
syncQueueManager.getPendingItems()
    ↓
동기화 큐에서 PENDING 상태 항목 조회
    ↓
WAITING 상태 항목 처리 (시나리오 2, 5)
    - originalQueueId가 있는 항목 확인
    - 원본 항목이 SUCCESS 상태면 PENDING으로 변경
    ↓
PENDING 항목을 createdAt 기준 정렬
    ↓
각 항목 순차 처리:
    ├─ CREATE
    │   ↓
    │   syncQueueManager.tryUpdateStatus('PENDING' → 'SYNCING')
    │   ↓
    │   로컬 메모 조회
    │   ↓
    │   멱등성 키 재사용 (queueItem.idempotencyKey)
    │   ↓
    │   apiClient.post(API_ENDPOINTS.MEMOS.CREATE, data, {
    │       headers: { 'Idempotency-Key': idempotencyKey }
    │   })
    │   ↓
    │   서버 응답 (Primary/Secondary DB에 저장됨)
    │   ↓
    │   dbManager.updateMemoWithServerId(localId, serverId)
    │   ↓
    │   syncStatus: 'synced'
    │   ↓
    │   syncQueueManager.markAsSuccess(queueItem.id)
    │
    ├─ UPDATE
    │   ↓
    │   syncQueueManager.tryUpdateStatus('PENDING' → 'SYNCING')
    │   ↓
    │   로컬 메모 조회
    │   ↓
    │   apiClient.put(API_ENDPOINTS.MEMOS.UPDATE(serverMemoId), data)
    │   ↓
    │   서버 응답 (Primary/Secondary DB에 저장됨)
    │   ↓
    │   syncStatus: 'synced'
    │   ↓
    │   syncQueueManager.markAsSuccess(queueItem.id)
    │
    └─ DELETE
        ↓
        syncQueueManager.tryUpdateStatus('PENDING' → 'SYNCING')
        ↓
        로컬 메모 조회
        ↓
        apiClient.delete(API_ENDPOINTS.MEMOS.DELETE(serverMemoId))
        ↓
        서버 응답 (Primary/Secondary DB에서 삭제됨)
        ↓
        dbManager.deleteMemo(localId)
        ↓
        syncQueueManager.markAsSuccess(queueItem.id)
```

**멱등성 키 처리**:
```
CREATE 작업 시:
    ↓
offlineMemoService.createMemo()에서 idempotencyKey 생성
    ↓
sync_queue 테이블에 idempotencyKey 저장
    ↓
syncQueueItem()에서 동기화 시:
    ↓
queueItem.idempotencyKey 재사용
    ↓
apiClient.post() 호출 시 'Idempotency-Key' 헤더 포함
    ↓
서버 측 Redis에서 멱등성 키 확인
    ├─ 존재 → 캐시된 응답 반환 (중복 방지)
    └─ 없음 → 새로 생성하고 응답 반환
```

#### 요구사항 준수 여부

| 항목 | 요구사항 | 현재 구현 | 준수 여부 |
|------|---------|----------|----------|
| **네트워크 재연결 감지** | 자동 감지 및 동기화 | ✅ online 이벤트 + 2-Phase Health Check | ✅ **준수** |
| **동기화 실행** | IndexedDB → 서버DB | ✅ syncPendingMemos() 실행 | ✅ **준수** |
| **멱등성 키** | 포함하여 동기화 | ✅ CREATE 작업에 멱등성 키 포함 | ✅ **준수** |
| **순서 보장** | 작성 시간 순서 | ✅ createdAt 기준 정렬 | ✅ **준수** |
| **WAITING 상태 처리** | 원본 항목 완료 대기 | ✅ originalQueueId 확인 | ✅ **준수** |

**분석**:
- ✅ 네트워크 재연결 시 자동으로 동기화가 실행됨
- ✅ 멱등성 키를 포함하여 중복 생성 방지
- ✅ 순서 보장 및 WAITING 상태 처리 포함

---

### 질문 3-2: 네트워크 재연결 후 읽기 방식 전환 시점

#### 전제 요구사항
- 네트워크 접속이 다시 연결되면 IndexedDB에서 데이터를 읽어오던 방식이, 서버DB에서 데이터를 읽어오는 방식으로 언제, 어떻게 바뀌나요?

#### 현재 구현 흐름

**읽기 방식 전환**:
```
네트워크 재연결
    ↓
networkMonitor.isOnline = true
    ↓
사용자 메모 조회 요청
    ↓
memo-service.js.getMemosByBook(userBookId)
    ↓
networkMonitor.isOnline 확인
    ├─ true (온라인)
    │   ↓
    │   즉시 서버에서 조회 시도
    │   ↓
    │   apiClient.get(API_ENDPOINTS.MEMOS.BY_BOOK(userBookId))
    │   ↓
    │   서버 측: MemoService.getAllBookMemos()
    │   ↓
    │   DualMasterReadService.readWithFailover()
    │   ↓
    │   Read Failover 처리:
    │       ├─ Primary DB에서 읽기 시도
    │       │   ├─ 성공 → 메모 목록 반환
    │       │   └─ 실패 → Secondary DB로 Failover
    │       │       ├─ 성공 → 메모 목록 반환
    │       │       └─ 실패 → DatabaseUnavailableException 발생
    │   ↓
    │   서버 응답 (메모 목록)
    │   ↓
    │   최근 7일 메모만 IndexedDB에 저장
    │   ↓
    │   로컬 메모와 서버 메모 통합 반환
    │
    └─ false (오프라인)
        ↓
        IndexedDB에서 조회
```

**동기화 완료 대기 여부**:
```
현재 구현:
    ↓
네트워크 재연결
    ↓
networkMonitor.isOnline = true (즉시 변경)
    ↓
사용자 조회 요청
    ↓
즉시 서버에서 조회 (동기화 완료 여부와 무관)
    ↓
동기화는 백그라운드에서 진행
```

#### 요구사항 준수 여부

| 항목 | 요구사항 | 현재 구현 | 준수 여부 |
|------|---------|----------|----------|
| **전환 시점** | 동기화 완료 후 | ❌ 즉시 전환 (동기화 완료 대기 안 함) | ❌ **미준수** |
| **전환 방식** | 자동 전환 | ✅ networkMonitor.isOnline 기반 자동 전환 | ✅ **준수** |

**분석**:
- ❌ 현재 구현은 네트워크 재연결 시 즉시 서버에서 읽기 시작 (동기화 완료 대기 안 함)
- ⚠️ 이로 인해 동기화 중에 서버에서 조회하면 일부 데이터가 변경된 것처럼 보일 수 있음

**개선 필요**:
- 동기화 완료 후 읽기 방식 전환 로직 추가 필요
- 또는 동기화 중에는 로컬 메모 우선 표시, 동기화 완료 후 서버 메모로 전환

---

### 질문 5: 처음 로그인 시 서버DB에서 IndexedDB에 저장하는 시점

#### 전제 요구사항
- 서버를 실행한 이후 사용자가 당일 처음으로 웹 브라우저를 열어서 처음으로 로그인했다고 가정
- 이때 어느 시점에 서버DB에서 데이터를 가져와 IndexedDB에 저장(혹은 IndexedDB에 기존 데이터가 있다면 갱신)하나요?

#### 현재 구현 흐름

**메모 데이터**:
```
사용자 로그인 성공
    ↓
사용자가 메모 조회 화면 접근
    ↓
memo-service.js.getMemosByBook(userBookId) 호출
    ↓
networkMonitor.isOnline === true
    ↓
apiClient.get(API_ENDPOINTS.MEMOS.BY_BOOK(userBookId))
    ↓
서버 측: MemoService.getAllBookMemos()
    ↓
DualMasterReadService.readWithFailover()
    ↓
Read Failover 처리:
    ├─ Primary DB에서 읽기 시도
    │   ├─ 성공 → 메모 목록 반환
    │   └─ 실패 → Secondary DB로 Failover
    │       ├─ 성공 → 메모 목록 반환
    │       └─ 실패 → DatabaseUnavailableException 발생
    ↓
서버 응답 (메모 목록)
    ↓
조회된 메모를 순회하며:
    ↓
MemoOperationHelper.saveServerMemoAsLocal(serverMemo)
    ↓
최근 7일 메모만 IndexedDB에 저장
    - memoStartTime 기준으로 7일 이내 메모만 저장
    - 7일 이상 된 메모는 저장하지 않음
    ↓
로컬 메모와 서버 메모 통합 반환
```

**내 서재 정보**:
```
사용자 로그인 성공
    ↓
사용자가 서재 화면 접근
    ↓
book-service.js.getBookshelf() 호출
    ↓
networkMonitor.isOnline === true
    ↓
apiClient.get(API_ENDPOINTS.BOOKS.USER_BOOKS)
    ↓
서버 측: BookService.getMyShelf()
    ↓
DualMasterReadService.readWithFailover()
    ↓
Read Failover 처리:
    ├─ Primary DB에서 읽기 시도
    │   ├─ 성공 → 서재 정보 반환
    │   └─ 실패 → Secondary DB로 Failover
    │       ├─ 성공 → 서재 정보 반환
    │       └─ 실패 → DatabaseUnavailableException 발생
    ↓
서버 응답 (서재 정보)
    ↓
BookOperationHelper.saveServerBookAsLocal(serverBook)
    ↓
IndexedDB에 내 서재 정보 저장
    ↓
로컬 내 서재 정보와 서버 내 서재 정보 통합 반환
```

#### 요구사항 준수 여부

| 데이터 타입 | 요구사항 | 현재 구현 | 준수 여부 |
|------------|---------|----------|----------|
| **메모 정보** | 처음 로그인 시 저장 | ⚠️ 조회 시점에 저장 (명시적 로그인 시점 아님) | ⚠️ **부분 준수** |
| **내 서재 정보** | 처음 로그인 시 저장 | ⚠️ 조회 시점에 저장 (명시적 로그인 시점 아님) | ⚠️ **부분 준수** |

**분석**:
- ⚠️ 메모 정보는 조회 시점에 저장되지만, 명시적으로 로그인 시점에 저장하지는 않음
- ⚠️ 내 서재 정보는 조회 시점에 저장되지만, 명시적으로 로그인 시점에 저장하지는 않음

**개선 필요**:
- 로그인 성공 시점에 명시적으로 서버DB에서 데이터를 가져와 IndexedDB에 저장하는 로직 추가 필요

---

### 질문 6: 네트워크 재연결 시 동기화 중 읽기 방식 전환

#### 전제 요구사항
- IndexedDB에 저장된 데이터가 서버DB로 동기화되고 있는 과정을 진행하고 있다면, 데이터 동기화가 모두 완료된 후 다시 데이터를 읽어오는 방식이 로컬 저장소에서 서버DB로 변경되나요?
- 동기화되고 있는 와중에 서버DB에서 데이터를 read해와 사용자의 화면을 갱신한다고 한다면, 마치 사용자에게 일부 데이터는 변경된 것처럼 보일 수 있을 것 같습니다.

#### 현재 구현 흐름

**개선된 동작 (질문 6 개선 적용)**:
```
네트워크 재연결
    ↓
networkMonitor.isOnline = true (즉시 변경)
    ↓
동기화 시작 (백그라운드)
    - offlineMemoService.syncPendingMemos()
    - offlineBookService.syncPendingBooks()
    - syncStateManager.startSync() (모든 PENDING 항목 수)
    - 비동기 실행 (await 하지 않음)
    ↓
사용자 조회 요청 (동기화 중)
    ↓
memo-service.js.getMemosByBook(userBookId)
    ↓
networkMonitor.isOnline === true
    ↓
syncStateManager.isSyncing 확인
    ├─ true (동기화 중)
    │   ↓
    │   로컬 메모만 반환 (동기화 완료 대기)
    │   ↓
    │   syncStateManager.waitForSyncComplete() 호출
    │   ↓
    │   모든 PENDING 항목 처리 완료 대기
    │   ├─ 완료 → 서버에서 조회 시작
    │   └─ 타임아웃 (30초) → 로컬 메모만 반환
    │
    └─ false (동기화 완료)
        ↓
        서버에서 조회 시도
        ↓
        apiClient.get(API_ENDPOINTS.MEMOS.BY_BOOK(userBookId))
        ↓
        서버 측: MemoService.getAllBookMemos()
        ↓
        DualMasterReadService.readWithFailover()
        ↓
        Read Failover 처리:
            ├─ Primary DB에서 읽기 시도
            │   ├─ 성공 → 메모 목록 반환
            │   └─ 실패 → Secondary DB로 Failover
            │       ├─ 성공 → 메모 목록 반환
            │       └─ 실패 → DatabaseUnavailableException 발생
        ↓
        서버 응답 (메모 목록)
        ↓
        최근 7일 메모만 IndexedDB에 저장
        ↓
        로컬 메모와 서버 메모 통합 반환
```

**개선된 동작 흐름**:
```
동기화 중 조회 시나리오 (개선 후):
    ↓
1. 오프라인에서 메모 A 생성 (localId: 'abc', serverId: null)
    - syncStatus: 'pending'
    ↓
2. 네트워크 재연결
    ↓
3. 동기화 시작 (백그라운드)
    - syncStateManager.isSyncing = true
    - 메모 A를 서버에 전송 중
    ↓
4. 사용자가 조회 요청 (동기화 완료 전)
    ↓
5. syncStateManager.isSyncing === true 확인
    ↓
6. 로컬 메모만 반환 (동기화 완료 대기)
    ↓
7. syncStateManager.waitForSyncComplete() 호출
    ↓
8. 동기화 완료 대기 (최대 30초)
    - sync:complete 이벤트 구독
    - 500ms마다 checkSyncComplete() 확인
    ↓
9. 동기화 완료
    - 모든 PENDING 항목 처리 완료
    - syncStateManager.isSyncing = false
    - sync:complete 이벤트 발행
    ↓
10. waitForSyncComplete() 반환 (true)
    ↓
11. 서버에서 조회 시작
    ↓
12. 서버 응답 (메모 A 포함, serverId: 123)
    ↓
13. 로컬 메모와 서버 메모 통합
    - 로컬 메모 A (serverId: 123)와 서버 메모 A (id: 123) 매칭
    - 서버 메모로 대체
```

#### 요구사항 준수 여부

| 항목 | 요구사항 | 현재 구현 | 준수 여부 |
|------|---------|----------|----------|
| **동기화 완료 후 전환** | 동기화 완료 후 서버DB로 전환 | ✅ 동기화 완료 대기 후 전환 | ✅ **준수** |
| **동기화 중 데이터 일관성** | 일부 데이터 변경처럼 보이지 않도록 | ✅ 동기화 중 로컬 메모만 반환 | ✅ **준수** |

**분석**:
- ✅ 동기화 상태를 추적하여 동기화 중에는 로컬 메모만 반환
- ✅ 동기화 완료 후 서버에서 조회 시작 (모든 PENDING 항목 처리 완료 후)
- ✅ `syncStateManager.waitForSyncComplete()`를 통한 동기화 완료 대기 (최대 30초)

**구현 내용**:
- `SyncStateManager`: 동기화 상태 추적 및 완료 대기 기능
- `memo-service.js.getMemosByBook()`: 동기화 중이면 로컬 메모만 반환, 완료 후 서버 조회
- `book-service.js.getBookshelf()`: 동기화 중이면 로컬 내 서재 정보만 반환, 완료 후 서버 조회

---

### 질문 6-1: 동기화 완료 전 사용자 요청 처리

#### 전제 요구사항
- 동기화가 전부 완료된 후 서버DB로 데이터 접근 방식이 전환되는 것입니까?
- 동기화가 전부 완료되기 전에 사용자가 다시 내 서재 정보나 메모 정보를 변경하는 요청을 서버에 보낸다면 동기화 작업이 영원히 끝나지 않는 것입니까?
- 이런 상황을 방지하기 위해서 어떤 방법을 사용해야하는지, 로직을 어떻게 개선해야하는지 알려주세요.

#### 현재 구현 흐름

**개선된 동기화 완료 전 사용자 요청 (질문 6-1 개선 적용)**:
```
동기화 진행 중
    ↓
사용자 메모 수정 요청
    ↓
memo-service.js.updateMemo(memoId, memoData)
    ↓
networkMonitor.isOnline === true
    ↓
syncStateManager.isSyncing 확인
    ├─ true (동기화 중)
    │   ↓
    │   requestQueueManager.enqueue()로 요청 큐에 저장
    │   ↓
    │   Promise 반환 (동기화 완료 후 처리)
    │
    └─ false (동기화 완료)
        ↓
        정상 처리 (서버 우선 전략)
        ↓
        MemoOperationHelper.getLocalMemo(memoId)
        ↓
        서버에 수정 요청 (Primary/Secondary DB)
        ↓
        성공 → IndexedDB 갱신
```

**동기화 완료 후 큐 처리**:
```
동기화 완료
    ↓
syncStateManager.setSyncComplete()
    ↓
eventBus.publish('sync:complete')
    ↓
requestQueueManager.processQueue() (이벤트 구독)
    ↓
큐에 저장된 요청 순차 처리
    ├─ createMemo 요청
    │   ↓
    │   memo-service.js.createMemo() 재실행
    │   ↓
    │   서버에 생성 요청 (Primary/Secondary DB)
    │
    ├─ updateMemo 요청
    │   ↓
    │   memo-service.js.updateMemo() 재실행
    │   ↓
    │   서버에 수정 요청 (Primary/Secondary DB)
    │
    └─ deleteMemo 요청
        ↓
        memo-service.js.deleteMemo() 재실행
        ↓
        서버에 삭제 요청 (Primary/Secondary DB)
```

**동기화 큐 처리 (기존)**:
```
syncPendingMemos() 실행 중
    ↓
각 항목을 순차 처리
    ↓
tryUpdateStatus('PENDING' → 'SYNCING')
    ↓
원자적 상태 변경 (Race Condition 방지)
    ↓
동기화 실행
    ↓
성공/실패 처리
    ↓
syncStateManager.updateSyncProgress() 호출
    ↓
모든 PENDING 항목 처리 완료 확인
    ↓
syncStateManager.checkSyncComplete()
    ↓
동기화 완료 이벤트 발행
```

#### 요구사항 준수 여부

| 항목 | 요구사항 | 현재 구현 | 준수 여부 |
|------|---------|----------|----------|
| **동기화 완료 후 전환** | 동기화 완료 후 전환 | ✅ 동기화 완료 대기 후 전환 | ✅ **준수** |
| **동기화 중 요청 처리** | 영원히 끝나지 않는 문제 방지 | ✅ 요청 큐잉으로 해결 | ✅ **준수** |

**분석**:
- ✅ 동기화 중 사용자 요청(create, update, delete)을 `requestQueueManager`에 큐잉
- ✅ 동기화 완료 후 자동으로 큐에 저장된 요청 처리
- ✅ 동기화 완료 전에는 서버에 요청을 보내지 않아 충돌 방지
- ✅ `tryUpdateStatus()`로 Race Condition 방지 유지

**구현 내용**:
- `RequestQueueManager`: 동기화 중 사용자 요청을 큐에 저장하고, 동기화 완료 후 처리
- `memo-service.js`: `createMemo`, `updateMemo`, `deleteMemo`에 요청 큐잉 적용
- `book-service.js`: `addBookToShelf`, `updateBookDetail`, `removeBookFromShelf`에 요청 큐잉 적용
- `syncStateManager`: 동기화 완료 이벤트 발행으로 큐 처리 트리거

---

### 질문 7: 네트워크 끊김 시 로그인 상태 유지

#### 전제 요구사항
- 사용자가 로그인에 성공한 상태에서 네트워크가 끊겼다고 가정
- 네트워크가 끊긴 상황에서 내 서재 정보 화면의 기능이나, 오늘의 흐름 화면에서 메모를 read, insert, update, delete하는 등의 기능을 전부 사용할 수 있어야 합니다
- 현재 Redis에서 인증/세션 정보를 관리하고 있습니다
- 네트워크가 끊기면서 서버와의 상태도 함께 끊기는 것인지, 아니면 단순히 인터넷과의 연결만 끊겨서 도서 검색 기능이 사용할 수 없는 것인지

#### 현재 구현 흐름

**인증/세션 관리**:
```
사용자 로그인 성공
    ↓
서버에서 JWT 토큰 발급
    - Access Token (JWT)
    - Refresh Token (Redis에 저장)
    ↓
클라이언트에 토큰 저장 (localStorage)
    ↓
네트워크 끊김
    ↓
클라이언트 측:
    - localStorage에 토큰 유지
    - networkMonitor.isOnline = false
    ↓
사용자 요청 (오프라인)
    ↓
apiClient.request() 호출
    ├─ 네트워크 오류 발생
    │   ↓
    │   오프라인 로직으로 전환 (메모 및 내 서재 정보)
    │
    └─ 서버 요청 시도 (도서 검색 등 외부 API의 경우)
        ↓
        네트워크 오류 발생
        ↓
        사용자에게 오류 표시
```

**Redis 기반 인증/세션**:
```
서버 측:
    ↓
Redis에 Refresh Token 저장
    - Key: refresh_token:{userId}
    - Value: Refresh Token
    - TTL: 설정된 만료 시간
    ↓
네트워크 끊김
    ↓
서버 측:
    - Redis는 서버와 동일한 네트워크에 있음
    - 네트워크 끊김은 클라이언트-서버 간 연결만 끊김
    - Redis는 서버 내부에서 정상 동작
    ↓
클라이언트 측:
    - localStorage에 토큰 유지
    - 네트워크 재연결 시 토큰으로 인증 가능
```

#### 요구사항 준수 여부

| 항목 | 요구사항 | 현재 구현 | 준수 여부 |
|------|---------|----------|----------|
| **로그인 상태 유지** | 네트워크 끊김 시에도 유지 | ✅ localStorage에 토큰 유지 | ✅ **준수** |
| **메모 기능 사용** | 오프라인에서 사용 가능 | ✅ IndexedDB 기반 오프라인 지원 | ✅ **준수** |
| **내 서재 기능 사용** | 오프라인에서 사용 가능 | ❌ 오프라인 지원 없음 | ❌ **미준수** |
| **서버 연결 상태** | 인터넷만 끊김 vs 서버도 끊김 | ⚠️ 클라이언트-서버 간 연결만 끊김 | ⚠️ **부분 준수** |

**분석**:
- ✅ 로그인 상태는 localStorage에 토큰이 저장되어 있어 네트워크 끊김 시에도 유지됨
- ✅ 메모 기능은 오프라인에서 정상 동작
- ❌ 내 서재 기능은 오프라인 지원이 없음
- ⚠️ 네트워크 끊김은 클라이언트-서버 간 연결만 끊기며, 서버 내부(Redis 포함)는 정상 동작

**Redis 인증/세션 관리 방식**:
- Redis는 서버와 동일한 네트워크에 있으므로, 클라이언트의 네트워크 끊김과 무관하게 정상 동작
- Refresh Token은 Redis에 저장되어 있으며, 네트워크 재연결 시 토큰 갱신 가능
- Access Token은 JWT이므로 서버 검증 없이도 유효성 확인 가능 (만료 시간 내)

---

### 질문 8: 네트워크 끊김 중 로그아웃 처리

#### 전제 요구사항
- 네트워크가 끊긴 상황에서 로그아웃을 한다면 어떻게 되는 것인지
- 데이터가 전부 소실되는 것인지, IndexedDB에 데이터가 여전히 남아있는지
- 이후 다시 네트워크가 연결된 상태에서 로그인을 시도한다면 IndexedDB에 저장된 데이터가 어떻게 처리되는지, 다시 동기화가 되는지

#### 현재 구현 흐름

**오프라인 로그아웃**:
```
사용자 로그아웃 요청
    ↓
auth-state.js.logout()
    ↓
tokenManager.clearTokens()
    ↓
localStorage에서 토큰 삭제
    ↓
authState.user = null
    ↓
authState.isAuthenticated = false
    ↓
eventBus.publish(AUTH_EVENTS.LOGOUT)
    ↓
⚠️ IndexedDB 데이터는 삭제하지 않음
    - offline_memos 테이블 유지
    - sync_queue 테이블 유지
```

**네트워크 재연결 후 로그인**:
```
사용자 로그인 성공
    ↓
새로운 토큰 발급
    ↓
localStorage에 토큰 저장
    ↓
사용자가 메모 조회
    ↓
memo-service.js.getMemosByBook(userBookId)
    ↓
networkMonitor.isOnline === true
    ↓
apiClient.get(API_ENDPOINTS.MEMOS.BY_BOOK(userBookId))
    ↓
서버 측: MemoService.getAllBookMemos()
    ↓
DualMasterReadService.readWithFailover()
    ↓
Read Failover 처리:
    ├─ Primary DB에서 읽기 시도
    │   ├─ 성공 → 메모 목록 반환
    │   └─ 실패 → Secondary DB로 Failover
    │       ├─ 성공 → 메모 목록 반환
    │       └─ 실패 → DatabaseUnavailableException 발생
    ↓
서버 응답 (메모 목록)
    ↓
로컬 메모와 서버 메모 통합
    ↓
⚠️ 이전 사용자의 IndexedDB 데이터와 충돌 가능
    - 다른 사용자로 로그인한 경우
    - 같은 사용자로 로그인한 경우에도 동기화 필요
```

**동기화 처리**:
```
네트워크 재연결
    ↓
networkMonitor.onNetworkOnline()
    ↓
offlineMemoService.syncPendingMemos()
    ↓
sync_queue에서 PENDING 항목 조회
    ↓
각 항목 동기화 시도
    ↓
⚠️ 이전 사용자의 데이터도 동기화 시도
    - 서버에서 인증 실패 가능
    - 또는 다른 사용자의 데이터로 동기화될 수 있음
```

#### 요구사항 준수 여부

| 항목 | 요구사항 | 현재 구현 | 준수 여부 |
|------|---------|----------|----------|
| **로그아웃 시 데이터 보존** | IndexedDB 데이터 유지 | ✅ IndexedDB 데이터 유지 | ✅ **준수** |
| **재로그인 시 동기화** | 자동 동기화 | ⚠️ 동기화는 시도하지만 사용자 구분 없음 | ⚠️ **부분 준수** |

**분석**:
- ✅ 로그아웃 시 IndexedDB 데이터는 유지됨
- ⚠️ 재로그인 시 이전 사용자의 데이터도 동기화 시도할 수 있음 (보안 문제)

**개선 필요**:
- 로그아웃 시 IndexedDB 데이터 정리 또는 사용자별 구분 필요
- 재로그인 시 이전 사용자 데이터와의 충돌 방지 로직 추가 필요

---

## 3. 요구사항 준수 여부 종합

### 3-1. 메모 정보 (Memo)

| 요구사항 | 현재 구현 | 준수 여부 |
|---------|----------|----------|
| 오프라인 Read | ✅ IndexedDB에서 읽기 | ✅ **준수** |
| 오프라인 Insert | ✅ IndexedDB에 저장 + sync_queue | ✅ **준수** |
| 오프라인 Update | ✅ IndexedDB에 저장 + sync_queue | ✅ **준수** |
| 오프라인 Delete | ✅ IndexedDB에 삭제 표시 + sync_queue | ✅ **준수** |
| 네트워크 재연결 시 동기화 | ✅ 자동 동기화 + 멱등성 키 | ✅ **준수** |
| 동기화 완료 후 읽기 전환 | ✅ 동기화 완료 대기 후 전환 | ✅ **준수** |
| 동기화 중 요청 큐잉 | ✅ 요청 큐잉 및 완료 후 처리 | ✅ **준수** |
| 처음 로그인 시 저장 | ⚠️ 조회 시점에 저장 | ⚠️ **부분 준수** |

**종합**: ⚠️ **대부분 준수, 일부 개선 필요**

### 3-2. 내 서재 정보 (UserShelfBook)

| 요구사항 | 현재 구현 | 준수 여부 |
|---------|----------|----------|
| 오프라인 Read | ✅ IndexedDB에서 읽기 | ✅ **준수** |
| 오프라인 Insert | ✅ IndexedDB에 저장 + sync_queue | ✅ **준수** |
| 오프라인 Update | ✅ IndexedDB에 저장 + sync_queue | ✅ **준수** |
| 오프라인 Delete | ✅ IndexedDB에 삭제 표시 + sync_queue | ✅ **준수** |
| 네트워크 재연결 시 동기화 | ✅ 자동 동기화 + 멱등성 키 | ✅ **준수** |
| 동기화 완료 후 읽기 전환 | ✅ 동기화 완료 대기 후 전환 | ✅ **준수** |
| 동기화 중 요청 큐잉 | ✅ 요청 큐잉 및 완료 후 처리 | ✅ **준수** |
| 처음 로그인 시 저장 | ⚠️ 조회 시점에 저장 | ⚠️ **부분 준수** |

**종합**: ⚠️ **대부분 준수, 일부 개선 필요**

### 3-3. 인증/세션 관리

| 요구사항 | 현재 구현 | 준수 여부 |
|---------|----------|----------|
| 네트워크 끊김 시 로그인 상태 유지 | ✅ localStorage에 토큰 유지 | ✅ **준수** |
| Redis 기반 인증/세션 관리 | ✅ Redis에 Refresh Token 저장 | ✅ **준수** |
| 오프라인 로그아웃 시 데이터 보존 | ✅ IndexedDB 데이터 유지 | ✅ **준수** |
| 재로그인 시 데이터 충돌 방지 | ⚠️ 사용자 구분 없음 | ⚠️ **부분 준수** |

**종합**: ⚠️ **대부분 준수, 일부 개선 필요**

---

## 4. 시스템 전체 비기능 로직 사용 사례

### 4-1. 시나리오 1: 오프라인 메모 및 내 서재 정보 동기화

**사용 위치**:
- `memo-service.js`: 메모 생성/수정/삭제/조회
- `book-service.js`: 내 서재 정보 추가/수정/삭제/조회
- `offline-memo-service.js`: 오프라인 메모 관리
- `offline-book-service.js`: 오프라인 내 서재 정보 관리
- `sync-queue-manager.js`: 동기화 큐 관리
- `network-monitor.js`: 네트워크 상태 모니터링
- `service-worker.js`: 백그라운드 동기화

**로직 흐름**:
```
사용자 메모/내 서재 정보 작업
    ↓
네트워크 상태 확인
    ├─ 온라인: 서버 우선 처리 → IndexedDB 갱신
    └─ 오프라인: IndexedDB 저장 → sync_queue 추가
    ↓
네트워크 재연결
    ↓
자동 동기화 (멱등성 키 포함)
    ↓
서버 DB에 반영 (Primary/Secondary)
```

### 4-2. 시나리오 2: MySQL 이중화 및 양방향 동기화

**사용 위치**:
- 모든 Service 메서드 (MemoService, BookService, AuthService, UserService 등)
- `DualMasterReadService`: Read Failover
- `DualMasterWriteService`: Custom Dual Write
- `RecoveryQueueService`: 보상 트랜잭션 실패 처리
- `CompensationRecoveryWorker`: 비동기 복구

**로직 흐름**:
```
사용자 요청
    ↓
Service 메서드
    ├─ Read 작업
    │   ↓
    │   DualMasterReadService.readWithFailover()
    │   ↓
    │   Primary DB에서 읽기 시도
    │   ├─ 성공 → 결과 반환
    │   └─ 실패 → Secondary DB로 Failover
    │       ├─ 성공 → 결과 반환
    │       └─ 실패 → DatabaseUnavailableException 발생
    │
    └─ Write 작업
        ↓
        DualMasterWriteService.writeWithDualWrite()
        ↓
        Primary DB에 쓰기
        ├─ 성공
        │   ↓
        │   Secondary DB에 쓰기
        │   ├─ 성공 → Commit
        │   └─ 실패 → Primary 보상 트랜잭션
        │       ├─ 성공 → Exception 발생
        │       └─ 실패 → Recovery Queue 발행
        │
        └─ 실패 → Exception 발생 (Failover 불가)
```

### 4-3. 하이브리드 전략 (네트워크 상태 기반 분기)

**사용 위치**:
- `memo-service.js`: createMemo, updateMemo, deleteMemo
- `book-service.js`: addBookToShelf, updateBookStatus, updateBookDetail, removeBookFromShelf
- `MemoOperationHelper`: 공통 로직 추출
- `BookOperationHelper`: 공통 로직 추출
- `NetworkStateManager`: 이벤트 기반 상태 전환

**로직 흐름**:
```
사용자 요청
    ↓
networkMonitor.isOnline 확인
    ├─ true (온라인)
    │   ↓
    │   서버 우선 처리
    │   ↓
    │   성공 → IndexedDB 갱신
    │   실패 → 오프라인 로직으로 전환
    │
    └─ false (오프라인)
        ↓
        로컬 우선 처리 (IndexedDB)
        ↓
        sync_queue에 추가
```

### 4-4. 이벤트 기반 상태 전환 처리

**사용 위치**:
- `NetworkStateManager`: 네트워크 상태 관리
- `offlineMemoService`: 이벤트 구독
- `offlineBookService`: 이벤트 구독
- `eventBus`: 이벤트 발행/구독

**로직 흐름**:
```
네트워크 상태 변경
    ↓
NetworkStateManager.handleNetworkStatusChange()
    ↓
transitionToOnline() / transitionToOffline()
    ↓
eventBus.publish('network:online') / eventBus.publish('network:offline')
    ↓
offlineMemoService.syncPendingMemos() (구독)
offlineBookService.syncPendingBooks() (구독)
```

---

## 5. 개선 필요 사항

### 5-1. 내 서재 정보 오프라인 지원 구현

**현재 상태**: ✅ 오프라인 지원 완료

**구현 완료 사항**:
1. ✅ IndexedDB에 `offline_books` 테이블 추가
2. ✅ `offline-book-service.js` 생성 (메모 서비스와 유사한 구조)
3. ✅ `book-service.js`에 하이브리드 전략 적용
4. ✅ 동기화 큐에 내 서재 정보 작업 추가 (`localBookId` 지원)
5. ✅ `BookOperationHelper` 생성 (공통 로직 추출)

**우선순위**: 완료

### 5-2. 처음 로그인 시 명시적 데이터 로드

**현재 상태**: ⚠️ 조회 시점에 저장

**개선 방안**:
1. 로그인 성공 시점에 명시적으로 서버DB에서 데이터 로드
2. `auth-state.js`의 `setUser()` 메서드에서 데이터 로드 트리거
3. 메모 및 내 서재 정보를 IndexedDB에 저장

**우선순위**: 중간

### 5-3. 동기화 완료 후 읽기 방식 전환

**현재 상태**: ✅ 동기화 완료 대기 후 전환 (완료)

**구현 완료 사항**:
1. ✅ `SyncStateManager`: 동기화 상태 추적 (`isSyncing` 플래그)
2. ✅ 동기화 중에는 로컬 메모/내 서재 정보만 반환
3. ✅ `waitForSyncComplete()`: 동기화 완료 대기 (최대 30초)
4. ✅ 동기화 완료 후 서버에서 조회 시작

**우선순위**: 완료

### 5-4. 재로그인 시 데이터 충돌 방지

**현재 상태**: ⚠️ 사용자 구분 없음

**개선 방안**:
1. IndexedDB에 `userId` 필드 추가
2. 로그아웃 시 현재 사용자의 데이터만 정리
3. 재로그인 시 이전 사용자 데이터와 충돌 방지

**우선순위**: 높음 (보안 문제)

### 5-5. 동기화 중 사용자 요청 처리 개선

**현재 상태**: ✅ 요청 큐잉 및 완료 후 처리 (완료)

**구현 완료 사항**:
1. ✅ `RequestQueueManager`: 동기화 중 사용자 요청을 큐에 저장
2. ✅ 동기화 완료 이벤트 구독하여 큐 처리
3. ✅ `memo-service.js`: create, update, delete에 요청 큐잉 적용
4. ✅ `book-service.js`: add, update, delete에 요청 큐잉 적용
5. ✅ 동기화 완료 후 자동으로 큐에 저장된 요청 순차 처리

**우선순위**: 완료

---

## 6. 결론

### 6-1. 요구사항 준수 현황

| 데이터 타입 | 준수율 | 주요 문제점 |
|------------|--------|------------|
| **메모 정보** | ✅ 95% | 처음 로그인 시 명시적 데이터 로드 미구현 |
| **내 서재 정보** | ✅ 95% | 처음 로그인 시 명시적 데이터 로드 미구현 |
| **인증/세션** | ⚠️ 90% | 재로그인 시 데이터 충돌 가능 |

### 6-2. 우선 개선 사항

1. **재로그인 시 데이터 충돌 방지** (보안)
2. **처음 로그인 시 명시적 데이터 로드** (사용자 경험)

### 6-3. 참고 문서

- `분산2_프로젝트/docs/fault-tolerance/FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md`: 비기능 품질 구현 로드맵
- `분산2_프로젝트/docs/fault-tolerance/OFFLINE_MEMO_SYNC.md`: 오프라인 메모 동기화 상세 설계
- `분산2_프로젝트_프론트/docs/test/HYBRID_STRATEGY_TEST_SCENARIOS.md`: 하이브리드 전략 테스트 시나리오

---

**문서 버전**: 1.0  
**최종 업데이트**: 2025-12-09  
**작성자**: Development Team

