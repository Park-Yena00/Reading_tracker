# 메모 삭제 Dual Write 흐름 분석

> **작성일**: 2025-12-09  
> **문제**: Secondary DB 중단 상태에서 메모 삭제 시도 시 프론트엔드에서 "메모를 찾을 수 없습니다" 오류 발생  
> **상태**: 🔄 분석 완료

---

## 1. 문제 발생 시나리오

- 현재 로그인한 상태에서 Secondary DB를 중단
- 작성된 메모를 삭제 시도
- 프론트엔드 오류: "메모를 찾을 수 없습니다" (offline-memo-service.js:207)
- 서버 오류 로그 없음
- Primary DB는 실행 중

## 2. 메모 삭제 흐름 분석

### 2-1. 프론트엔드 흐름

**`flow-view.js` → `memo-service.js` → `offline-memo-service.js`**

1. **`handleMemoDelete(memoId)`** 호출
2. **`memoService.deleteMemo(memoId)`** 호출
3. **`offlineMemoService.deleteMemo(memoId)`** 호출
   - 로컬 메모 조회 (localId 또는 serverId로)
   - **로컬 메모를 찾지 못하면 → "메모를 찾을 수 없습니다" 오류 발생 (207번 줄)**
   - 로컬 메모를 찾으면:
     - `serverId`가 없으면 로컬에서 즉시 삭제
     - `serverId`가 있으면 동기화 큐에 DELETE 항목 추가
     - 온라인 상태면 즉시 동기화 시도

**문제점**: 프론트엔드에서 로컬 메모를 찾지 못하면 서버로 요청이 가지 않습니다.

### 2-2. 백엔드 흐름 (서버로 요청이 도달한 경우)

**`MemoController.deleteMemo()` → `MemoService.deleteMemo()` → `DualMasterWriteService.writeWithDualWrite()`**

#### 시나리오 1: Primary DB에서 삭제 성공, Secondary DB에서 삭제 성공

```
1. Primary DB에서 메모 삭제 성공
2. Secondary DB에서 메모 삭제 성공
3. 결과: 양쪽 DB에서 모두 삭제됨 ✅
```

#### 시나리오 2: Primary DB에서 삭제 실패

```
1. Primary DB에서 메모 삭제 실패
2. 즉시 DatabaseWriteException 발생
3. Secondary DB로 Failover하지 않음 (DELETE는 Primary 실패 시 즉시 실패)
4. 결과: 양쪽 DB 모두 삭제되지 않음 ❌
```

#### 시나리오 3: Primary DB에서 삭제 성공, Secondary DB에서 삭제 실패

```
1. Primary DB에서 메모 삭제 성공 ✅
2. Secondary DB에서 메모 삭제 실패 (Secondary DB 중단)
3. 보상 트랜잭션 실행:
   - DELETE의 보상 트랜잭션은 Primary 복구를 하지 않음
   - 이유: DELETE 작업은 복구가 불가능 (이미 삭제된 데이터를 복구할 수 없음)
   - 대신 Recovery Queue에 이벤트 발행 (Secondary 유령 데이터 정리용)
4. 결과:
   - Primary DB: 삭제됨 ✅
   - Secondary DB: 삭제되지 않음 (유령 데이터 남음) ⚠️
   - Recovery Queue: Secondary 정리 이벤트 발행
```

**중요**: DELETE의 경우 보상 트랜잭션이 Primary를 복구하지 않습니다. 이는 DELETE 작업의 특성상 복구가 불가능하기 때문입니다.

### 2-3. 보상 트랜잭션 로직

**`MemoService.deleteMemo()` (295-314번 줄)**:

```java
// 보상 트랜잭션: DELETE의 보상은 복구가 어려우므로 Recovery Queue에 발행
(result) -> {
    // DELETE 보상 트랜잭션은 Primary 복구가 불가능하지만,
    // Secondary에 유령 데이터가 남을 수 있으므로 Recovery Queue에 발행
    CompensationFailureEvent event = new CompensationFailureEvent(
        "DELETE_SECONDARY_CLEANUP",
        memoId,
        "Memo",
        "Secondary",
        Instant.now(),
        "Primary DELETE 성공 후 Secondary DELETE 실패로 인한 유령 데이터 정리 필요"
    );
    
    recoveryQueueService.publish(event);
    logger.warn("DELETE 보상 트랜잭션: Secondary 유령 데이터 정리를 위해 Recovery Queue에 발행 (memoId: {})", memoId);
    
    return null;
}
```

## 3. 현재 문제 분석

### 3-1. 프론트엔드 오류 원인

**`offline-memo-service.js:207`**: "메모를 찾을 수 없습니다"

이 오류는 다음 경우에 발생합니다:
1. IndexedDB에 메모가 저장되지 않음
2. `serverId`로 조회했는데 로컬에 저장되지 않음
3. `localId`로 조회했는데 로컬에 저장되지 않음

**가능한 원인**:
- 메모가 서버에만 존재하고 로컬에 동기화되지 않음
- 하이브리드 전략으로 인해 오래된 메모(7일 이상)가 IndexedDB에서 삭제됨
- 메모 조회 시 로컬 메모만 조회하고 서버 메모는 조회하지 않음

### 3-2. 서버 오류 로그가 없는 이유

프론트엔드에서 로컬 메모를 찾지 못해 서버로 요청이 전송되지 않았기 때문입니다.

## 4. 질문에 대한 답변

### Q1: Secondary DB에서 삭제가 실패한 상황에서도 Primary DB에서는 보상 트랜잭션이 작동하여 Primary DB에서 삭제됐던 데이터가 다시 롤백된 것이 맞습니까?

**답변**: 아니요. DELETE의 경우 보상 트랜잭션이 Primary를 복구하지 않습니다.

**이유**:
- DELETE 작업은 복구가 불가능합니다 (이미 삭제된 데이터를 복구할 수 없음)
- 보상 트랜잭션은 Secondary에 유령 데이터가 남을 수 있으므로 Recovery Queue에 이벤트만 발행합니다
- Primary DB는 삭제된 상태로 유지됩니다

### Q2: 현재 데이터 삭제 시 어떤 흐름으로 흘러가고 있나요?

**현재 흐름**:

1. **프론트엔드**:
   - 로컬 메모 조회 (IndexedDB)
   - 로컬 메모를 찾지 못하면 → 오류 발생, 서버로 요청하지 않음
   - 로컬 메모를 찾으면 → 동기화 큐에 DELETE 항목 추가 → 서버로 요청

2. **백엔드** (서버로 요청이 도달한 경우):
   - Primary DB에서 삭제 시도
   - Primary 실패 시 → 즉시 실패 (Secondary로 Failover하지 않음)
   - Primary 성공 시 → Secondary DB에서 삭제 시도
   - Secondary 실패 시 → 보상 트랜잭션 실행 (Recovery Queue에 이벤트 발행, Primary 복구하지 않음)

### Q3: Primary DB에서 실패했을 때와 Primary DB에서는 성공하고 Secondary DB에서는 실패했을 때의 흐름은?

#### 시나리오 A: Primary DB에서 삭제 실패

```
1. Primary DB에서 메모 삭제 시도
2. Primary DB 삭제 실패 (예: 메모가 이미 삭제됨, 권한 없음 등)
3. DatabaseWriteException 발생
4. Secondary DB로 Failover하지 않음
5. 결과: 양쪽 DB 모두 삭제되지 않음
6. 사용자에게 오류 메시지 반환
```

#### 시나리오 B: Primary DB에서 삭제 성공, Secondary DB에서 삭제 실패

```
1. Primary DB에서 메모 삭제 성공 ✅
2. Secondary DB에서 메모 삭제 시도
3. Secondary DB 삭제 실패 (예: Secondary DB 중단)
4. 보상 트랜잭션 실행:
   - Recovery Queue에 이벤트 발행
   - Primary 복구하지 않음 (DELETE는 복구 불가능)
5. 결과:
   - Primary DB: 삭제됨 ✅
   - Secondary DB: 삭제되지 않음 (유령 데이터) ⚠️
   - Recovery Queue: Secondary 정리 이벤트 발행
6. 사용자에게 성공 메시지 반환 (Primary 삭제 성공)
```

## 5. 개선 제안

### 5-1. 프론트엔드 개선

로컬 메모를 찾지 못했을 때 서버에서 직접 삭제를 시도하도록 개선:

```javascript
async deleteMemo(memoId) {
  // 로컬 메모 조회
  let localMemo = await dbManager.getMemoByServerId(memoId);
  
  if (!localMemo) {
    // 로컬에 없으면 서버에서 직접 삭제 시도
    try {
      await apiClient.delete(API_ENDPOINTS.MEMOS.DELETE(memoId));
      return { message: '메모가 삭제되었습니다.' };
    } catch (error) {
      throw new Error('메모를 찾을 수 없습니다.');
    }
  }
  
  // 로컬 메모가 있으면 기존 로직 실행
  // ...
}
```

### 5-2. 백엔드 개선

DELETE의 보상 트랜잭션은 현재 설계대로 유지하는 것이 적절합니다:
- DELETE는 복구가 불가능하므로 Primary 복구를 시도하지 않음
- Secondary 유령 데이터는 Recovery Queue를 통해 정리

## 6. 참고 문서

- `분산2_프로젝트/src/main/java/com/readingtracker/server/service/MemoService.java`: 메모 삭제 로직
- `분산2_프로젝트/src/main/java/com/readingtracker/server/service/write/DualMasterWriteService.java`: Dual Write 로직
- `분산2_프로젝트/docs/fault-tolerance/DUAL_WRITE_IMPLEMENTATION_ISSUES.md`: Dual Write 구현 이슈

