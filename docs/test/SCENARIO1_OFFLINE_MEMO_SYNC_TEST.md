# 시나리오 1: 오프라인 메모 동기화 테스트 가이드

> **목적**: 오프라인 환경에서 메모 작성 및 네트워크 복구 시 자동 동기화 기능을 시각적으로 검증  
> **필요 도구**: 브라우저(F12 개발자 도구), 서버 Console, 웹 UI  
> **예상 소요 시간**: 약 30분

---

## 📋 테스트 전 준비사항

### 1. 서버 실행 확인
- Spring Boot 서버가 정상적으로 실행 중인지 확인
- 서버 Console에서 에러가 없는지 확인

### 2. 웹 브라우저 준비
- Chrome 또는 Edge 브라우저 사용 (Service Worker 지원)
- F12 개발자 도구 열기 준비

### 3. 테스트 계정 준비
- 로그인된 사용자 계정 필요
- 내 서재에 책이 등록되어 있어야 함

---

## 테스트 1: 오프라인 메모 작성 및 로컬 저장 확인

### 목적
오프라인 상태에서 메모를 작성하면 IndexedDB에 저장되는지 확인

### 단계별 테스트 절차

#### 1단계: 네트워크 오프라인 모드 설정
1. **브라우저 F12 개발자 도구 열기**
   - `F12` 키 누르기
   - 또는 `우클릭 → 검사` 메뉴 선택

2. **Network 탭에서 오프라인 모드 활성화**
   - F12 개발자 도구 → **Network** 탭 클릭
   - 상단의 **"Offline"** 체크박스 선택
   - 또는 `Ctrl + Shift + P` → "Show Network conditions" → "Offline" 선택

3. **확인 사항**
   - 브라우저 주소창 옆에 오프라인 아이콘(⚡ 또는 🌐) 표시 확인
   - 웹 페이지 새로고침 시 "오프라인" 메시지 표시 확인

#### 2단계: 메모 작성
1. **웹 UI에서 메모 작성**
   - 내 서재 페이지로 이동
   - 책을 선택하여 메모 작성 페이지 열기
   - 메모 내용 입력 (예: "오프라인 테스트 메모")
   - **저장** 버튼 클릭

2. **확인 사항 (F12 Console 탭)**
   - Console 탭에서 다음과 같은 로그 확인:
     ```
     [OfflineMemoService] 메모를 로컬 저장소에 저장했습니다.
     [IndexedDBManager] 메모 저장 완료: localId=xxx
     ```
   - 에러 메시지가 없는지 확인

#### 3단계: IndexedDB 데이터 확인
1. **F12 개발자 도구 → Application 탭**
   - **Application** 탭 클릭
   - 왼쪽 사이드바에서 **IndexedDB** 확장
   - `reading_tracker_db` 데이터베이스 클릭
   - `offline_memos` ObjectStore 클릭

2. **저장된 메모 확인**
   - 오른쪽 패널에 방금 작성한 메모가 표시되는지 확인
   - 다음 필드들이 존재하는지 확인:
     - `localId`: UUID 형식의 로컬 ID
     - `content`: 작성한 메모 내용
     - `syncStatus`: "pending" 상태
     - `memoStartTime`: 작성 시간

3. **sync_queue 확인**
   - `sync_queue` ObjectStore 클릭
   - 동기화 큐 항목이 생성되었는지 확인
   - `status`: "PENDING" 상태 확인

#### 4단계: UI에서 메모 표시 확인
1. **웹 페이지에서 메모 확인**
   - 메모 목록에 방금 작성한 메모가 즉시 표시되는지 확인
   - 메모 카드에 **⏳ (대기 중)** 아이콘 표시 확인
   - 메모 내용이 정상적으로 표시되는지 확인

---

## 테스트 2: 네트워크 복구 시 자동 동기화 확인

### 목적
네트워크가 복구되면 오프라인에서 작성한 메모가 자동으로 서버에 동기화되는지 확인

### 단계별 테스트 절차

#### 1단계: 오프라인 상태에서 메모 작성 (테스트 1 반복)
1. **Network 탭에서 오프라인 모드 활성화** (테스트 1의 1단계 참조)
2. **메모 작성** (테스트 1의 2단계 참조)
3. **IndexedDB에 저장 확인** (테스트 1의 3단계 참조)

#### 2단계: 네트워크 복구
1. **F12 개발자 도구 → Network 탭**
   - **"Offline"** 체크박스 해제
   - 또는 `Ctrl + Shift + P` → "Show Network conditions" → "Online" 선택

2. **확인 사항**
   - 브라우저 주소창 옆의 오프라인 아이콘이 사라지는지 확인
   - F12 Console 탭에서 다음과 같은 로그 확인:
     ```
     [NetworkMonitor] 네트워크 상태 변경: online
     [NetworkMonitor] 2-Phase Health Check 시작...
     [NetworkMonitor] 로컬 서버 연결 확인: 성공
     [OfflineMemoService] 동기화 시작...
     ```

#### 3단계: 자동 동기화 실행 확인
1. **F12 Console 탭 모니터링**
   - 다음과 같은 로그가 순차적으로 나타나는지 확인:
     ```
     [OfflineMemoService] 동기화 시작: 1개의 메모
     [OfflineMemoService] 메모 동기화 중: localId=xxx
     [OfflineMemoService] 동기화 성공: localId=xxx, serverId=123
     [IndexedDBManager] 메모 업데이트: serverId=123
     ```

2. **Network 탭에서 HTTP 요청 확인**
   - Network 탭에서 `POST /api/v1/memos` 요청이 발생하는지 확인
   - 요청이 성공(200 OK)인지 확인
   - Response에서 서버에서 반환한 메모 ID 확인

#### 4단계: IndexedDB 업데이트 확인
1. **F12 개발자 도구 → Application 탭**
   - `offline_memos` ObjectStore 클릭
   - 방금 동기화한 메모를 클릭하여 상세 정보 확인

2. **확인 사항**
   - `syncStatus`: "synced"로 변경되었는지 확인
   - `serverId`: 서버에서 반환한 ID가 저장되었는지 확인
   - `localId`: 기존 로컬 ID가 유지되는지 확인

3. **sync_queue 확인**
   - `sync_queue` ObjectStore 클릭
   - 해당 큐 항목의 `status`가 "SUCCESS"로 변경되었는지 확인

#### 5단계: UI 업데이트 확인
1. **웹 페이지에서 메모 확인**
   - 메모 카드의 **⏳ (대기 중)** 아이콘이 사라졌는지 확인
   - 또는 **✅ (동기화 완료)** 아이콘이 표시되는지 확인
   - Toast 메시지가 표시되는지 확인:
     ```
     ✅ 1개의 메모 동기화 완료.
     ```

#### 6단계: 서버 Console 확인
1. **서버 Console 창 확인**
   - 다음과 같은 로그가 나타나는지 확인:
     ```
     [MemoController] POST /api/v1/memos 요청 수신
     [MemoService] 메모 생성: userId=xxx, userBookId=xxx
     [DualMasterWriteService] Primary DB 쓰기 성공
     [DualMasterWriteService] Secondary DB 쓰기 성공
     ```

---

## 테스트 3: 2-Phase Health Check 확인

### 목적
네트워크 복구 시 로컬 서버와 외부 서비스(Aladin API) 연결을 각각 확인하는지 검증

### 단계별 테스트 절차

#### 1단계: 네트워크 복구 시도
1. **오프라인 상태에서 메모 작성** (테스트 1 참조)
2. **Network 탭에서 오프라인 모드 해제**

#### 2단계: F12 Console에서 Health Check 로그 확인
1. **Console 탭 모니터링**
   - 다음과 같은 로그가 순차적으로 나타나는지 확인:
     ```
     [NetworkMonitor] 네트워크 상태 변경: online
     [NetworkMonitor] 2-Phase Health Check 시작...
     [NetworkMonitor] Phase 1: 로컬 서버 헬스체크 시작
     [NetworkMonitor] 로컬 서버 연결 확인: 성공
     [NetworkMonitor] Phase 2: 외부 서비스(Aladin API) 헬스체크 시작
     [NetworkMonitor] 외부 서비스 연결 확인: 성공 (또는 실패)
     ```

#### 3단계: Network 탭에서 Health Check 요청 확인
1. **Network 탭에서 HTTP 요청 확인**
   - `HEAD /api/v1/health` 요청이 먼저 발생하는지 확인
   - `GET /api/v1/health/aladin` 요청이 그 다음에 발생하는지 확인
   - 두 요청 모두 성공(200 OK)인지 확인

#### 4단계: 외부 서비스 연결 실패 시나리오 (선택)
1. **서버에서 Aladin API 연결 차단** (서버 설정 변경 필요)
2. **네트워크 복구 시도**
3. **Console 탭에서 로그 확인**
   - 다음과 같은 로그 확인:
     ```
     [NetworkMonitor] 외부 서비스(Aladin API) 연결 불가: ...
     [NetworkMonitor] 로컬 서버는 연결 가능하지만 외부 서비스는 연결 불가
     ```
4. **Toast 메시지 확인**
   - 다음과 같은 경고 메시지가 표시되는지 확인:
     ```
     ⚠️ 외부 서비스 연결 불가. 검색 제한됨.
     ```
5. **메모 동기화는 정상 작동하는지 확인**
   - 로컬 서버는 연결 가능하므로 메모 동기화는 정상 작동해야 함

---

## 테스트 4: Service Worker 백그라운드 동기화 확인

### 목적
Service Worker가 백그라운드에서 동기화를 처리하는지 확인

### 단계별 테스트 절차

#### 1단계: Service Worker 등록 확인
1. **F12 개발자 도구 → Application 탭**
   - 왼쪽 사이드바에서 **Service Workers** 클릭
   - `service-worker.js`가 등록되어 있고 **"activated and is running"** 상태인지 확인

#### 2단계: 오프라인 상태에서 메모 작성
1. **Network 탭에서 오프라인 모드 활성화**
2. **메모 작성 및 저장**

#### 3단계: 네트워크 복구 후 Service Worker 동기화 확인
1. **Network 탭에서 오프라인 모드 해제**
2. **F12 개발자 도구 → Application 탭 → Service Workers**
   - Service Worker가 활성화되어 있는지 확인
   - "Update" 또는 "Unregister" 버튼이 보이는지 확인

#### 4단계: Network 탭에서 Service Worker 요청 확인
1. **Network 탭에서 HTTP 요청 확인**
   - `POST /api/v1/memos` 요청이 발생하는지 확인
   - 요청의 **"Initiator"** 컬럼에서 `service-worker.js`가 표시되는지 확인

---

## 테스트 5: 동기화 중 메모 수정 허용 확인 (시나리오 1)

### 목적
동기화 중인 메모도 수정할 수 있는지 확인 (시나리오 1: 동기화 중 수정 허용)

### 단계별 테스트 절차

#### 1단계: 오프라인 상태에서 메모 작성
1. **Network 탭에서 오프라인 모드 활성화**
2. **메모 작성 및 저장**

#### 2단계: 네트워크 복구 및 동기화 시작
1. **Network 탭에서 오프라인 모드 해제**
2. **F12 Console 탭에서 동기화 시작 로그 확인**
   ```
   [OfflineMemoService] 동기화 시작...
   [OfflineMemoService] 메모 동기화 중: localId=xxx
   ```

#### 3단계: 동기화 중 메모 수정
1. **웹 UI에서 동기화 중인 메모 수정**
   - 메모 카드에서 **수정** 버튼 클릭
   - 메모 내용 수정 (예: "수정된 내용")
   - **저장** 버튼 클릭

2. **확인 사항**
   - 메모 수정이 정상적으로 저장되는지 확인
   - 에러 메시지가 나타나지 않는지 확인
   - F12 Console 탭에서 다음과 같은 로그 확인:
     ```
     [OfflineMemoService] 동기화 중인 메모 수정 허용: localId=xxx
     [IndexedDBManager] 메모 업데이트 완료
     ```

#### 4단계: IndexedDB에서 상태 확인
1. **F12 개발자 도구 → Application 탭**
   - `offline_memos` ObjectStore에서 해당 메모 확인
   - `syncStatus`: "syncing_create" 또는 "syncing_update" 상태 확인
   - 수정된 내용이 저장되었는지 확인

---

## 테스트 6: WAITING 상태 처리 확인 (시나리오 2, 5)

### 목적
UPDATE 동기화 중 DELETE 시도 시 WAITING 상태로 처리되는지 확인

### 단계별 테스트 절차

#### 1단계: 오프라인 상태에서 메모 작성
1. **Network 탭에서 오프라인 모드 활성화**
2. **메모 작성 및 저장**

#### 2단계: 네트워크 복구 및 UPDATE 동기화 시작
1. **Network 탭에서 오프라인 모드 해제**
2. **F12 Console 탭에서 동기화 시작 로그 확인**

#### 3단계: 동기화 중 메모 삭제 시도
1. **웹 UI에서 동기화 중인 메모 삭제**
   - 메모 카드에서 **삭제** 버튼 클릭
   - 확인 대화상자에서 **확인** 클릭

2. **확인 사항**
   - 메모가 UI에서 즉시 사라지는지 확인 (Optimistic Deletion)
   - F12 Console 탭에서 다음과 같은 로그 확인:
     ```
     [OfflineMemoService] UPDATE 동기화 중 DELETE 시도 감지
     [SyncQueueManager] WAITING 상태로 큐 항목 생성: originalQueueId=xxx
     ```

#### 4단계: IndexedDB에서 WAITING 상태 확인
1. **F12 개발자 도구 → Application 탭**
   - `sync_queue` ObjectStore 클릭
   - DELETE 큐 항목의 `status`가 "WAITING"인지 확인
   - `originalQueueId` 필드가 UPDATE 큐 항목의 ID와 일치하는지 확인

#### 5단계: UPDATE 완료 후 DELETE 실행 확인
1. **F12 Console 탭에서 로그 모니터링**
   - UPDATE 동기화가 완료되면 다음과 같은 로그 확인:
     ```
     [OfflineMemoService] UPDATE 동기화 완료: localId=xxx
     [SyncQueueManager] 원본 항목 완료: originalQueueId=xxx
     [SyncQueueManager] WAITING 항목을 PENDING으로 변경: queueId=yyy
     [OfflineMemoService] DELETE 동기화 시작: localId=xxx
     ```

2. **Network 탭에서 DELETE 요청 확인**
   - `DELETE /api/v1/memos/{memoId}` 요청이 UPDATE 요청 이후에 발생하는지 확인

---

## 테스트 7: 동기화 실패 및 재시도 확인

### 목적
동기화 실패 시 재시도 로직이 작동하는지 확인

### 단계별 테스트 절차

#### 1단계: 오프라인 상태에서 메모 작성
1. **Network 탭에서 오프라인 모드 활성화**
2. **메모 작성 및 저장**

#### 2단계: 서버 중지
1. **서버 Console에서 서버 중지**
   - `Ctrl + C`로 서버 종료
   - 또는 IDE에서 서버 중지

#### 3단계: 네트워크 복구 시도
1. **Network 탭에서 오프라인 모드 해제**
2. **F12 Console 탭에서 로그 확인**
   - 다음과 같은 에러 로그 확인:
     ```
     [OfflineMemoService] 동기화 실패: localId=xxx, error=...
     [SyncQueueManager] 재시도 예약: queueId=xxx, retryCount=1
     ```

#### 4단계: IndexedDB에서 실패 상태 확인
1. **F12 개발자 도구 → Application 탭**
   - `sync_queue` ObjectStore 클릭
   - 큐 항목의 `status`가 "FAILED"인지 확인
   - `retryCount` 필드가 증가했는지 확인

#### 5단계: 서버 재시작 후 자동 재시도 확인
1. **서버 재시작**
2. **F12 Console 탭에서 로그 확인**
   - 재시도 로그가 나타나는지 확인:
     ```
     [OfflineMemoService] 재시도 시작: queueId=xxx, retryCount=1
     [OfflineMemoService] 동기화 성공: localId=xxx
     ```

---

## 테스트 결과 확인 체크리스트

### ✅ 시나리오 1 구현 검증 항목

- [ ] 오프라인 상태에서 메모 작성 가능
- [ ] IndexedDB에 메모가 저장됨
- [ ] 네트워크 복구 시 자동 동기화 작동
- [ ] 2-Phase Health Check 정상 작동
- [ ] Service Worker 백그라운드 동기화 작동
- [ ] 동기화 중 메모 수정 허용 (시나리오 1)
- [ ] WAITING 상태 처리 작동 (시나리오 2, 5)
- [ ] 동기화 실패 시 재시도 작동
- [ ] UI에 동기화 상태 아이콘 표시
- [ ] Toast 메시지로 동기화 결과 피드백

---

## 문제 해결 가이드

### 문제 1: IndexedDB에 데이터가 저장되지 않음
- **확인 사항**: F12 Console 탭에서 에러 메시지 확인
- **해결 방법**: 브라우저 캐시 삭제 후 페이지 새로고침

### 문제 2: 자동 동기화가 작동하지 않음
- **확인 사항**: Network 탭에서 오프라인 모드가 해제되었는지 확인
- **확인 사항**: F12 Console 탭에서 Health Check 로그 확인
- **해결 방법**: 페이지 새로고침 후 다시 시도

### 문제 3: Service Worker가 등록되지 않음
- **확인 사항**: Application 탭 → Service Workers에서 등록 상태 확인
- **해결 방법**: Service Worker 등록 코드가 올바른지 확인

---

**테스트 완료 후**: 모든 테스트가 통과하면 시나리오 1 구현이 완료된 것으로 확인됩니다.

