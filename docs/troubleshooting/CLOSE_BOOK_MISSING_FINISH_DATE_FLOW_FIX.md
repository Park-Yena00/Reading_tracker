## 문제 요약

오늘의 흐름 페이지에서 **메모 작성 0개 상태**로 책을 선택한 뒤  
`책 덮기`를 진행하면, 진행률 입력 후 확인 시 다음 오류가 발생합니다.

- `책 덮기 중 오류가 발생했습니다: 독서 종료일은 필수 입력 항목입니다.`

## 재현 절차

1. 오늘의 흐름 페이지 진입
2. `메모 작성하기` → 책 선택 (해당 책의 메모 0개)
3. 자동으로 메모 작성 섹션 오픈
4. 메모 작성 없이 `책 덮기` 클릭
5. 현재 읽은 페이지 수 입력 후 확인
6. 오류 알림 발생

## 원인 분석

### 1) 프론트의 완료 여부 판단이 불완전

`flow-view.js`에서 `isFinished`는 **프론트에 로딩된 `selectedBook.totalPages`** 기준으로 계산됩니다.

```2060:2106:c:\Users\woori\Desktop\분산2_프로젝트_프론트\js\views\pages\flow-view.js
const totalPages = this.selectedBook.totalPages || 0;
const isFinished = totalPages > 0 && lastReadPage >= totalPages;
if (isFinished) {
  requestData.readingFinishedDate = this.closeBookFinishedDate?.value;
  requestData.rating = parseInt(this.closeBookRating?.value) || 0;
}
```

메모 0개 상태에서는 `selectedBook.totalPages`가 비어 있거나 0으로 들어올 수 있어  
`isFinished`가 false로 판정되고, `readingFinishedDate`가 전송되지 않습니다.

### 2) 백엔드에서는 Finished일 때 종료일이 필수

백엔드 `closeBook()`은 진행률로 카테고리를 계산해 **Finished가 되면 종료일을 필수로 요구**합니다.

```846:852:c:\Users\woori\Desktop\분산2_프로젝트\src\main\java\com\readingtracker\server\service\MemoService.java
if (newCategory == BookCategory.Finished) {
    if (request.getReadingFinishedDate() == null) {
        throw new IllegalArgumentException("독서 종료일은 필수 입력 항목입니다.");
    }
    userShelfBook.setReadingFinishedDate(request.getReadingFinishedDate());
}
```

즉, **프론트와 백엔드의 Finished 판단 기준이 불일치**하여  
프론트는 종료일을 보내지 않지만, 백엔드는 종료일을 필수로 요구합니다.

## DB 저장 형식 확인

독서 종료일은 `LocalDate`로 저장됩니다.  
따라서 프론트에서는 **`YYYY-MM-DD` 형식**으로 전송해야 합니다.

```14:18:c:\Users\woori\Desktop\분산2_프로젝트\src\main\java\com\readingtracker\server\dto\requestDTO\FinishReadingRequest.java
@NotNull(message = "독서 종료일은 필수 입력 항목입니다.")
private LocalDate readingFinishedDate;
```

```13:26:c:\Users\woori\Desktop\분산2_프로젝트\src\main\java\com\readingtracker\server\dto\requestDTO\CloseBookRequest.java
private LocalDate readingFinishedDate;  // 독서 종료일 (필수, Finished일 때만)
```

## 변경 필요 로직 (수정 대상)

### 1) `flow-view.js` → `handleCloseBookConfirm()`

- `selectedBook.totalPages`가 없거나 0인 경우  
  **서버 상세 조회(`bookService.getUserBookDetail`)로 totalPages 보완**
- 해당 값으로 `isFinished` 재계산
- `isFinished`인데 `readingFinishedDate`가 비어 있다면  
  **`getTodayDateString()`으로 자동 입력**

### 2) `flow-view.js` → `handleCloseBookProgressChange()`

- 진행률 100%일 때만 날짜 자동 입력이 아니라,  
  `totalPages`가 없는 케이스에도 기본값을 안정적으로 설정하도록 보완

## 결론

**원인:** 프론트에서 `isFinished` 판단을 `totalPages`에 의존하면서  
`totalPages`가 비어 있는 경우 종료일이 전송되지 않고,  
백엔드는 Finished로 판단해 종료일을 필수로 요구해 오류가 발생합니다.

**해결 방향:**  
프론트에서 **`totalPages` 보완 조회** 후 Finished 판단을 정확히 하고,  
