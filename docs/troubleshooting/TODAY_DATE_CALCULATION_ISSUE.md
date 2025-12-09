# 오늘의 흐름 화면 날짜 계산 오류 진단 및 해결 계획

> **작성일**: 2025-12-09  
> **문제**: 오늘의 흐름 화면에서 오늘 날짜가 하루 전 날짜로 표시됨  
> **원인**: `toISOString()` 사용으로 인한 UTC 시간대 변환 문제  
> **상태**: ✅ 수정 완료

---

## 1. 문제 발생 시나리오

- 현재 시간: 2025년 12월 9일 오전 4시 22분 (한국 시간, KST)
- 화면 표시: 2025년 12월 8일
- 결과: 오늘 날짜의 메모가 표시되지 않음

## 2. 예상되는 동작

- 오늘의 흐름 화면은 기본적으로 오늘 날짜에 작성된 메모들을 DB에서 불러와 메모 섹션에 정렬 규칙에 따라 표시해야 함
- 화면에 표시되는 날짜는 현재 한국 시간대 기준 오늘 날짜여야 함

## 3. 현재 동작 진단

### 3-1. 날짜 계산 로직

**`flow-view.js` 49번 줄**:
```javascript
this.currentDate = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
```

**문제점**:
- `toISOString()`은 UTC 시간을 기준으로 ISO 문자열을 반환합니다
- 한국 시간대(KST, UTC+9)에서 오전 4시 22분이면, UTC로는 전날 19시 22분이 됩니다

**예시**:
- 한국 시간: 2025-12-09 04:22:00 (KST)
- UTC 시간: 2025-12-08 19:22:00 (UTC)
- `toISOString()` 결과: `"2025-12-08T19:22:00.000Z"`
- `.split('T')[0]`: `"2025-12-08"` ← **하루 전 날짜!**

### 3-2. 동일한 문제가 발생하는 위치

다음 위치에서도 `new Date().toISOString().split('T')[0]`를 사용하고 있어 동일한 문제가 발생할 수 있습니다:

1. **`flow-view.js` 49번 줄**: `this.currentDate` 초기화
2. **`flow-view.js` 331번 줄**: `handleSelectBookClick()` 메서드
3. **`flow-view.js` 1388번 줄**: `handleHomeClick()` 메서드
4. **`flow-view.js` 1456번 줄**: `handleMemoSave()` 메서드 (메모 작성 날짜 검증)
5. **`flow-view.js` 1667번 줄**: 다른 메서드

## 4. 해결 방법

### 4-1. 로컬 시간대 기준 날짜 계산 함수 사용

`date-formatter.js`에 이미 `formatDate()` 함수가 있으며, 이 함수는 로컬 시간대를 사용합니다:

```javascript
export function formatDate(date) {
  if (!date) return '';
  
  const d = date instanceof Date ? date : new Date(date);
  if (isNaN(d.getTime())) return '';
  
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  
  return `${year}-${month}-${day}`;
}
```

또는 직접 로컬 시간대 기준으로 날짜를 계산할 수 있습니다:

```javascript
function getTodayDateString() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
```

## 5. 수정 계획

### 5-1. `date-formatter.js`에 유틸리티 함수 추가

`getTodayDateString()` 함수를 추가하여 오늘 날짜를 로컬 시간대 기준으로 반환합니다.

### 5-2. `flow-view.js` 수정

모든 `new Date().toISOString().split('T')[0]` 사용을 `getTodayDateString()` 또는 `formatDate(new Date())`로 변경합니다.

**수정 위치**:
1. 49번 줄: `this.currentDate` 초기화
2. 331번 줄: `handleSelectBookClick()` 메서드
3. 1388번 줄: `handleHomeClick()` 메서드
4. 1456번 줄: `handleMemoSave()` 메서드
5. 1667번 줄: 기타 메서드

## 6. 예상되는 개선 효과

- 오늘의 흐름 화면에서 정확한 오늘 날짜가 표시됨
- 오늘 날짜에 작성된 메모가 정상적으로 표시됨
- 한국 시간대 기준으로 일관된 날짜 계산

## 7. 수정 파일 목록

1. `분산2_프로젝트_프론트/js/utils/date-formatter.js`
   - `getTodayDateString()` 함수 추가 (로컬 시간대 기준 오늘 날짜 반환)

2. `분산2_프로젝트_프론트/js/views/pages/flow-view.js`
   - `getTodayDateString` import 추가
   - 모든 `new Date().toISOString().split('T')[0]` 사용을 `getTodayDateString()`로 변경
     - 49번 줄: `this.currentDate` 초기화
     - 332번 줄: `handleSelectBookClick()` 메서드
     - 1389번 줄: `handleBookSelect()` 메서드
     - 1456번 줄: `handleMemoSave()` 메서드
     - 1668번 줄: `startDateChangeDetection()` 메서드
     - 1908번 줄: `closeBookFinishedDate` 설정

## 8. 수정 완료 체크리스트

- [x] `분산2_프로젝트_프론트/js/utils/date-formatter.js` 수정 완료
  - `getTodayDateString()` 함수 추가 (로컬 시간대 기준 오늘 날짜 반환)
- [x] `분산2_프로젝트_프론트/js/views/pages/flow-view.js` 수정 완료
  - `getTodayDateString` import 추가
  - 모든 `new Date().toISOString().split('T')[0]` 사용을 `getTodayDateString()`로 변경

## 9. 참고 문서

- `분산2_프로젝트_프론트/js/utils/date-formatter.js`: 날짜 포맷팅 유틸리티

