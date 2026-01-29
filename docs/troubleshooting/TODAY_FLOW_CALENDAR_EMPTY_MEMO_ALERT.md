## 문제 요약

오늘의 흐름 페이지에서 캘린더를 연 뒤 다른 날짜를 선택했다가 다시 오늘 날짜를 선택하면,  
오늘 날짜에 메모가 0개임에도 **"해당 날짜에 작성된 메모가 없습니다"** 알림이 출력됩니다.

## 재현 절차

1. 오늘의 흐름 페이지 진입 (오늘 날짜 기준, 메모 0개)
2. 캘린더 열기
3. 다른 날짜 선택 (메모가 있는 날짜라면 정상 로드)
4. 다시 오늘 날짜 선택
5. **"해당 날짜에 작성된 메모가 없습니다"** 알림 출력

## 관련 로직

### 1) 캘린더 날짜 클릭 처리 (오늘의 흐름 인라인 캘린더)

`js/views/pages/flow-view.js`의 `handleCalendarDateClick()` 로직:

```1320:1334:c:\Users\woori\Desktop\분산2_프로젝트_프론트\js\views\pages\flow-view.js
  async handleCalendarDateClick(date) {
    const hasMemo = this.calendarMemoDates.includes(date);
    
    if (hasMemo) {
      await this.loadMemoFlow(date);
      await this.renderInlineCalendar();
    } else {
      alert('해당 날짜에 작성된 메모가 없습니다.');
    }
  }
```

### 2) 캘린더 날짜 목록 생성

`calendarMemoDates`는 다음 API에서 받아온 **메모가 있는 날짜만** 포함합니다.

```1230:1233:c:\Users\woori\Desktop\분산2_프로젝트_프론트\js\views\pages\flow-view.js
  async loadCalendarMemoDates() {
    this.calendarMemoDates = await memoService.getMemoDates(this.calendarYear, this.calendarMonth);
  }
```

```94:97:c:\Users\woori\Desktop\분산2_프로젝트_프론트\js\services\memo-service.js
  async getMemoDates(year, month) {
    const params = { year, month };
    const response = await apiClient.get(API_ENDPOINTS.MEMOS.DATES, params);
    return response; // List<String> 반환
  }
```

## 원인 분석

- 캘린더 클릭 로직은 **"메모가 있는 날짜"만 선택 가능**하도록 구성되어 있습니다.
- 오늘 날짜에 메모가 0개면 `calendarMemoDates`에 포함되지 않으므로,
  오늘 날짜 클릭 시 **무조건 알림(없음)** 분기로 이동합니다.
- 문제는 **오늘 날짜라도 메모가 0개일 수 있는데**,  
  현재 로직이 이를 **선택 불가**로 처리한다는 점입니다.

## 왜 "다른 날짜 선택 후"에 문제가 드러나나?

- 페이지 최초 진입 시에는 `loadMemoFlow()`가 기본으로 호출되어  
  **오늘 날짜의 빈 상태**를 정상 표시합니다.
- 그러나 캘린더에서 다른 날짜로 이동한 뒤 다시 오늘 날짜를 클릭하면,  
  **캘린더 클릭 분기 로직**을 타면서  
  오늘 날짜도 **"메모 없음" 알림만 출력**하고 로드되지 않습니다.

## 결론

**원인:** 캘린더 날짜 클릭 처리에서 **메모가 있는 날짜만 선택 가능**하도록 제한되어 있으며,  
오늘 날짜에 메모가 0개인 경우도 동일하게 차단되기 때문입니다.

**결과:** 오늘 날짜로 다시 돌아가려 해도 `loadMemoFlow()`가 실행되지 않고  
알림만 출력되어 사용자 기대와 다른 동작이 발생합니다.
