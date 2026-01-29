## 목적

오늘의 흐름 페이지에서 **책 덮기(현재 읽은 페이지 수 변경)**를 수행할 때,  
독서 진행률 퍼센티지 변화에 따라 **카테고리가 변경되는 로직의 위치와 호출 흐름**을 정리합니다.

## 핵심 결론

- **카테고리 변경 기준 로직은 백엔드 `MemoService.closeBook()`에 위치**합니다.
- 오늘의 흐름 페이지는 **해당 메서드를 직접 재사용하지 않고**,  
  **`/memos/books/{userBookId}/close` API 호출을 통해 간접 호출**합니다.
- 카테고리 변경은 **프론트에서 결정하지 않고**,  
  백엔드에서 진행률 퍼센티지 계산 후 자동 결정됩니다.

## 카테고리 변경 기준 로직 위치

### 클래스/메서드

- **클래스**: `com.readingtracker.server.service.MemoService`
- **메서드**: `closeBook(User user, Long userBookId, CloseBookRequest request)`

### 로직 요약

```802:825:c:\Users\woori\Desktop\분산2_프로젝트\src\main\java\com\readingtracker\server\service\MemoService.java
// 진행률 계산: (lastReadPage / totalPages) * 100
if (progressPercentage == 0) -> ToRead
1~80 -> Reading
81~99 -> AlmostFinished
100 -> Finished
userShelfBook.setCategory(newCategory);
```

## 오늘의 흐름 페이지 호출 흐름

### 1) 프론트엔드 호출

```2110:2124:c:\Users\woori\Desktop\분산2_프로젝트_프론트\js\views\pages\flow-view.js
const requestData = { lastReadPage };
await memoService.closeBook(this.selectedBookId, requestData);
```

### 2) API 경로

```54:54:c:\Users\woori\Desktop\분산2_프로젝트_프론트\js\constants\api-endpoints.js
CLOSE_BOOK: (userBookId) => `/memos/books/${userBookId}/close`
```

### 3) 백엔드 컨트롤러 → 서비스

```374:381:c:\Users\woori\Desktop\분산2_프로젝트\src\main\java\com\readingtracker\server\controller\v1\MemoController.java
public ApiResponse<String> closeBook(...)
    memoService.closeBook(user, userBookId, request);
```

## 카테고리 변경 메서드의 재사용 여부

### 사용하지 않는 메서드

`BookService.updateBookCategory()`와 같은 **별도의 카테고리 변경 메서드는 사용되지 않습니다.**

### 실제 적용 방식

- 오늘의 흐름의 책 덮기 로직은 **`MemoService.closeBook()` 내부에서 직접 카테고리를 계산/설정**합니다.
- 즉, **기존 카테고리 변경 메서드를 호출하지 않고 별도 로직을 사용**합니다.

## 정리

- **카테고리 변경 기준 계산**: 백엔드 `MemoService.closeBook()`
- **호출 경로**: `flow-view.js` → `memoService.closeBook()` → `MemoController.closeBook()` → `MemoService.closeBook()`
- **재사용 여부**: `BookService.updateBookCategory()` 미사용 (재사용 없음)
