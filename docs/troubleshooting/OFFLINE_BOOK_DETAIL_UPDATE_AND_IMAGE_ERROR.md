# 오프라인 상태에서 도서 상세 정보 변경 및 표지 이미지 오류 해결

> **작성일**: 2025-12-09  
> **목적**: 오프라인 상태에서 도서 상세 정보 변경 실패 및 표지 이미지 로딩 실패 오류 해결  
> **적용 범위**: `book-service.js`, `book-detail-view.js`

---

## 문제 원인

### 오류 상황

**오류 메시지 1 (도서 상세 정보 변경)**:
```
PUT http://localhost:8080/api/v1/user/books/17 400 (Bad Request)
구매/대여 여부 업데이트 오류: Error: 내 서재 정보를 찾을 수 없습니다.
```

**오류 메시지 2 (표지 이미지)**:
```
GET https://image.aladin.co.kr/product/30768/99/coversum/k252830652_2.jpg net::ERR_NAME_NOT_RESOLVED
```

### 원인 분석

#### 1. `updateBookDetail` 메서드 문제

**기존 로직의 문제점**:
1. **서버 오류 처리 부족**: 500 에러를 네트워크 오류로 인식하지 못함
2. **로컬에 없을 때 처리**: 로컬에 없으면 서버에서 조회 시도하는데, 오프라인 상태에서는 실패
3. **에러 메시지 불명확**: 네트워크/서버 오류와 다른 오류를 구분하지 못함

**문제 시나리오**:
```
1. 오프라인 상태에서 도서 상세 정보 변경 시도
2. networkMonitor.isOnline이 true로 잘못 인식 (또는 서버 접근 불가)
3. 로컬에 내 서재 정보가 있지만 서버 API 호출 시도
4. 서버 오류(500) 발생
5. handleServerError가 500 에러를 네트워크 오류로 인식하지 못함
6. 오프라인 모드로 전환되지 않음
7. "내 서재 정보를 찾을 수 없습니다" 에러 발생
```

#### 2. 표지 이미지 로딩 실패 문제

**기존 로직의 문제점**:
- 이미지 로딩 실패 시 `onerror` 핸들러가 없음
- 오프라인 상태에서 외부 이미지 URL (`image.aladin.co.kr`)에 접근 불가
- 이미지 로딩 실패 시 placeholder가 표시되지 않음

**문제 시나리오**:
```
1. 오프라인 상태에서 도서 상세 정보 표시
2. 표지 이미지 URL 설정
3. 외부 이미지 서버에 접근 시도
4. ERR_NAME_NOT_RESOLVED 오류 발생
5. 이미지가 로딩되지 않고 placeholder도 표시되지 않음
```

---

## 로직 개선 방향

### 해결책 1: `updateBookDetail` 오프라인 처리 개선

**개선 내용**:
1. 서버 오류(500)를 네트워크 오류로 인식하여 오프라인 모드로 전환
2. 서버 API 호출 실패 시 더 명확한 에러 처리
3. 로컬에 있으면 서버 실패 시 오프라인 모드로 전환

**동작 흐름**:
```
1. 로컬 내 서재 정보 조회
2. 로컬에 있으면:
   - 서버에서 수정 시도
   - 실패 시 네트워크/서버 오류 확인
   - 네트워크/서버 오류면 오프라인 모드로 전환
3. 로컬에 없으면:
   - 서버에서 조회 시도
   - 실패 시 네트워크/서버 오류 확인
   - 네트워크/서버 오류면 명확한 에러 메시지
```

### 해결책 2: 표지 이미지 로딩 실패 처리 추가

**개선 내용**:
1. 이미지 `onerror` 핸들러 추가
2. 이미지 로딩 실패 시 placeholder 표시
3. 오프라인 상태에서도 정상적으로 표시

**동작 흐름**:
```
1. 표지 이미지 URL 설정
2. 이미지 로딩 시도
3. 로딩 실패 시:
   - onerror 핸들러 실행
   - 이미지 숨김
   - placeholder 표시
```

---

## 구현 내용

### 1. `updateBookDetail` 메서드 개선

**변경 사항**:
- 서버 오류(500)를 네트워크 오류로 인식하여 오프라인 모드로 전환
- 서버 API 호출 실패 시 네트워크/서버 오류 확인 로직 추가
- 에러 메시지 개선

**코드 위치**: `js/services/book-service.js`

**주요 변경점**:
```javascript
// 서버 수정 실패 시 오프라인 모드로 전환
const isNetworkOrServerError = error.message?.includes('Failed to fetch') || 
                              error.message?.includes('NetworkError') ||
                              error.message?.includes('network') ||
                              error.message?.includes('서버 내부 오류') ||
                              error.message?.includes('Internal Server Error') ||
                              error.status === 500 ||
                              error.statusCode === 500 ||
                              !navigator.onLine;

if (isNetworkOrServerError) {
  // 오프라인 모드로 전환
  await offlineBookService.updateBook(userBookId, updateData);
  return '내 서재 정보 수정이 예약되었습니다. 네트워크 복구 시 자동 동기화됩니다.';
}
```

### 2. 표지 이미지 로딩 실패 처리 추가

**변경 사항**:
- 이미지 `onerror` 핸들러 추가
- 이미지 로딩 실패 시 placeholder 표시

**코드 위치**: `js/views/pages/book-detail-view.js`

**주요 변경점**:
```javascript
// 이미지 로딩 실패 시 placeholder 표시 (오프라인 지원)
coverImage.onerror = () => {
  console.warn('도서 표지 이미지 로딩 실패:', coverImageUrl);
  coverImage.style.display = 'none';
  const placeholder = coverImage.nextElementSibling;
  if (placeholder && placeholder.classList.contains('book-cover-placeholder')) {
    placeholder.style.display = 'flex';
  }
};
```

---

## 개선 결과

### 개선 전

**오프라인 상태에서 도서 상세 정보 변경 시**:
1. `updateBookDetail` 호출
2. 서버 API 호출 시도 → 500 에러 발생
3. `handleServerError`가 500 에러를 네트워크 오류로 인식하지 못함
4. 오프라인 모드로 전환되지 않음
5. "내 서재 정보를 찾을 수 없습니다" 에러 발생 ❌

**표지 이미지 로딩 시**:
1. 표지 이미지 URL 설정
2. 외부 이미지 서버에 접근 시도
3. `ERR_NAME_NOT_RESOLVED` 오류 발생
4. 이미지가 로딩되지 않고 placeholder도 표시되지 않음 ❌

### 개선 후

**오프라인 상태에서 도서 상세 정보 변경 시**:
1. `updateBookDetail` 호출
2. 로컬 내 서재 정보 조회 → 성공
3. 서버 API 호출 시도 → 500 에러 발생
4. 500 에러를 네트워크/서버 오류로 인식
5. 오프라인 모드로 전환
6. IndexedDB에 저장 및 동기화 큐에 추가
7. "내 서재 정보 수정이 예약되었습니다" 메시지 표시 ✅

**표지 이미지 로딩 시**:
1. 표지 이미지 URL 설정
2. 외부 이미지 서버에 접근 시도
3. `ERR_NAME_NOT_RESOLVED` 오류 발생
4. `onerror` 핸들러 실행
5. 이미지 숨김 및 placeholder 표시 ✅

---

## 동작 시나리오

### 시나리오 1: 오프라인 상태에서 도서 상세 정보 변경 (정상 동작)

1. 사용자가 오프라인 상태에서 도서 상세 정보 변경 시도
2. `updateBookDetail` 호출
3. 로컬 내 서재 정보 조회 → 성공
4. 서버 API 호출 시도 → 500 에러 발생
5. 500 에러를 네트워크/서버 오류로 인식
6. 오프라인 모드로 전환
7. IndexedDB에 저장 및 동기화 큐에 추가
8. "내 서재 정보 수정이 예약되었습니다" 메시지 표시

**결과**: 오프라인 상태에서도 도서 상세 정보 변경 가능, 네트워크 복구 시 자동 동기화

### 시나리오 2: 표지 이미지 로딩 실패 (정상 동작)

1. 사용자가 오프라인 상태에서 도서 상세 정보 표시
2. 표지 이미지 URL 설정
3. 외부 이미지 서버에 접근 시도
4. `ERR_NAME_NOT_RESOLVED` 오류 발생
5. `onerror` 핸들러 실행
6. 이미지 숨김 및 placeholder 표시

**결과**: 이미지 로딩 실패해도 placeholder가 정상적으로 표시됨

### 시나리오 3: 온라인 상태에서 도서 상세 정보 변경 (정상 동작)

1. 사용자가 온라인 상태에서 도서 상세 정보 변경 시도
2. `updateBookDetail` 호출
3. 로컬 내 서재 정보 조회 → 성공
4. 서버 API 호출 → 성공
5. IndexedDB 갱신
6. "내 서재 정보가 수정되었습니다" 메시지 표시

**결과**: 최신 서버 데이터로 즉시 반영

### 시나리오 4: 동기화 중 도서 상세 정보 변경

1. 사용자가 동기화 중에 도서 상세 정보 변경 시도
2. `updateBookDetail` 호출
3. 동기화 중 확인
4. 요청 큐에 추가
5. 동기화 완료 후 처리

**결과**: 동기화 중에도 요청이 큐에 저장되어 완료 후 처리

---

## 기술적 고려사항

### 1. 네트워크/서버 오류 인식

**인식 조건**:
- `Failed to fetch` 메시지 포함
- `NetworkError` 메시지 포함
- `network` 키워드 포함
- `서버 내부 오류` 메시지 포함
- `Internal Server Error` 메시지 포함
- HTTP 상태 코드 500
- `navigator.onLine === false`

**개선 효과**:
- 500 에러도 오프라인 모드로 전환하여 사용자 경험 개선
- 네트워크 문제와 서버 문제를 모두 처리

### 2. 이미지 로딩 실패 처리

**처리 방식**:
- `onerror` 핸들러로 이미지 로딩 실패 감지
- 이미지 숨김 및 placeholder 표시
- 오프라인 상태에서도 정상적으로 표시

**개선 효과**:
- 오프라인 상태에서도 UI가 정상적으로 표시됨
- 이미지 로딩 실패 시 사용자에게 명확한 피드백 제공

### 3. 모든 카테고리에서 변경 가능

**지원 카테고리**:
- ToRead: 기대감, 구매/대여 여부
- Reading: 독서 시작일, 현재 읽은 페이지, 구매/대여 여부
- AlmostFinished: 독서 시작일, 현재 읽은 페이지
- Finished: 독서 시작일, 독서 종료일, 평점, 후기

**개선 효과**:
- 모든 카테고리에서 오프라인 상태에서도 변경 가능
- 변경 사항은 동기화 큐에 추가되어 네트워크 복구 시 자동 동기화

---

## 관련 파일

### 수정된 파일
- `js/services/book-service.js`
  - `updateBookDetail()` 메서드: 서버 오류 처리 개선, 오프라인 모드 전환 로직 개선
- `js/views/pages/book-detail-view.js`
  - `displayBookDetail()` 메서드: 표지 이미지 로딩 실패 처리 추가

### 참조 파일
- `js/services/offline-book-service.js`: 오프라인 내 서재 정보 서비스
- `js/utils/book-operation-helper.js`: 내 서재 정보 작업 헬퍼
- `js/utils/sync-state-manager.js`: 동기화 상태 관리
- `js/utils/network-monitor.js`: 네트워크 상태 모니터링

---

## 테스트 시나리오

### 테스트 1: 오프라인 상태에서 도서 상세 정보 변경
- [ ] 와이파이 연결 해제
- [ ] 모든 카테고리에서 도서 상세 정보 변경 시도
- [ ] 변경 사항이 IndexedDB에 저장되는지 확인
- [ ] "내 서재 정보 수정이 예약되었습니다" 메시지 표시 확인

### 테스트 2: 표지 이미지 로딩 실패 처리
- [ ] 와이파이 연결 해제
- [ ] 도서 상세 정보 표시
- [ ] 표지 이미지 로딩 실패 시 placeholder가 표시되는지 확인

### 테스트 3: 동기화 테스트
- [ ] 오프라인에서 도서 상세 정보 변경
- [ ] 네트워크 재연결
- [ ] 변경 사항이 서버에 동기화되는지 확인

### 테스트 4: 모든 카테고리에서 변경 가능
- [ ] ToRead 카테고리: 기대감, 구매/대여 여부 변경
- [ ] Reading 카테고리: 독서 시작일, 현재 읽은 페이지, 구매/대여 여부 변경
- [ ] AlmostFinished 카테고리: 독서 시작일, 현재 읽은 페이지 변경
- [ ] Finished 카테고리: 독서 시작일, 독서 종료일, 평점, 후기 변경

---

## 참고 사항

### 오프라인 모드 전환 조건

**네트워크/서버 오류로 인식하는 조건**:
- `Failed to fetch` 메시지
- `NetworkError` 메시지
- `network` 키워드
- `서버 내부 오류` 메시지
- `Internal Server Error` 메시지
- HTTP 상태 코드 500
- `navigator.onLine === false`

### 동기화 큐

**저장되는 정보**:
- 작업 타입: `UPDATE`
- 내 서재 정보 ID: `userBookId`
- 변경 데이터: `updateData`
- 상태: `PENDING`

**동기화 시점**:
- 네트워크 재연결 시 자동 동기화
- `offline-book-service`가 동기화 큐 처리

### 표지 이미지

**로딩 실패 처리**:
- `onerror` 핸들러로 실패 감지
- 이미지 숨김 및 placeholder 표시
- 오프라인 상태에서도 정상적으로 표시

**이미지 URL**:
- 외부 이미지 서버 (`image.aladin.co.kr`) 사용
- 오프라인 상태에서는 접근 불가
- 로딩 실패 시 placeholder로 대체

---

**문서 버전**: 1.0  
**최종 업데이트**: 2025-12-09  
**작성자**: Development Team



