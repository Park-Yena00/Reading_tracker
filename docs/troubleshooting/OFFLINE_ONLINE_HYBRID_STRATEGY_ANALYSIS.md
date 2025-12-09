# OFFLINE_MEMO_SYNC와 MySQL 이중화 충돌 해결 방안 분석

> **작성일**: 2025-12-09  
> **문제**: OFFLINE_MEMO_SYNC와 MySQL 이중화 비기능 간의 충돌  
> **상태**: 🔄 분석 완료, 권장 방안 제시

---

## 1. 문제 분석

### 1-1. 현재 상황

**현재 프론트엔드 로직** (`offline-memo-service.js`):
- 네트워크 연결 상태와 무관하게 **항상 로컬(IndexedDB)을 먼저 확인**
- 로컬에 메모가 없으면 서버로 요청하지 않고 오류 발생
- 예: `deleteMemo()`에서 로컬 메모를 찾지 못하면 "메모를 찾을 수 없습니다" 오류

**문제점**:
1. **서버에만 존재하는 메모 처리 불가**: 하이브리드 전략(최근 7일 메모만 IndexedDB 보관)으로 인해 오래된 메모는 로컬에 없을 수 있음
2. **MySQL 이중화와 충돌**: 서버에서 먼저 처리해야 하는 Dual Write 로직이 작동하지 않음
3. **데이터 일관성 문제**: 로컬과 서버 간 데이터 불일치 가능성

### 1-2. 두 비기능 품질의 요구사항

#### OFFLINE_MEMO_SYNC (시나리오 1)
- **핵심 원칙**: Offline-First 아키텍처
- **로컬 우선**: 항상 로컬 저장소에 먼저 저장
- **백그라운드 동기화**: 네트워크 상태와 무관하게 동작
- **낙관적 업데이트**: 즉시 UI 업데이트, 나중에 서버 동기화

#### MySQL 이중화 (시나리오 2)
- **핵심 원칙**: Dual Write를 통한 데이터 일관성 보장
- **서버 우선**: 서버에서 먼저 처리 (Primary → Secondary)
- **보상 트랜잭션**: Secondary 실패 시 Primary 롤백 (DELETE 제외)
- **즉시 동기화**: 서버에서 즉시 처리하고 결과 반환

### 1-3. 충돌 지점

| 작업 | OFFLINE_MEMO_SYNC 요구사항 | MySQL 이중화 요구사항 | 충돌 여부 |
|------|---------------------------|---------------------|----------|
| **생성** | 로컬 먼저 저장 → 백그라운드 동기화 | 서버에서 먼저 처리 → 즉시 동기화 | ⚠️ 충돌 |
| **수정** | 로컬 먼저 수정 → 백그라운드 동기화 | 서버에서 먼저 처리 → 즉시 동기화 | ⚠️ 충돌 |
| **삭제** | 로컬 먼저 삭제 표시 → 백그라운드 동기화 | 서버에서 먼저 처리 → 즉시 동기화 | ⚠️ 충돌 |
| **조회** | 로컬 먼저 조회 → 서버 조회 (통합) | 서버에서 먼저 조회 (Read Failover) | ✅ 양립 가능 |

## 2. 하이브리드 전략 분석

### 2-1. 제안된 전략

**네트워크 상태에 따른 다른 전략 적용**:

#### 오프라인 상태 (네트워크 연결 없음)
- **현재 로직 유지**: Offline-First 전략
- 로컬 저장소를 먼저 확인
- 로컬에 없으면 오류 발생 (서버 접근 불가)
- 동기화 큐에 추가하여 네트워크 복구 시 자동 동기화

#### 온라인 상태 (네트워크 연결됨)
- **서버 우선 전략**: 서버에서 먼저 처리
- 서버에서 생성/수정/삭제 시도 → Dual Write 실행
- 성공 시 IndexedDB 갱신
- 실패 시 로컬 처리 (오프라인 모드로 전환)

### 2-2. 하이브리드 전략의 장점

1. **두 비기능 품질 모두 만족**:
   - 오프라인: Offline-First 원칙 유지
   - 온라인: MySQL 이중화 Dual Write 작동

2. **데이터 일관성 보장**:
   - 온라인 상태에서는 서버가 Single Source of Truth
   - 로컬은 서버 데이터의 캐시 역할

3. **사용자 경험 향상**:
   - 온라인: 즉시 서버 동기화로 최신 데이터 보장
   - 오프라인: 기존 오프라인 기능 유지

4. **충돌 해결**:
   - 네트워크 상태에 따라 명확한 전략 분리
   - 혼란 최소화

### 2-3. 하이브리드 전략의 단점 및 고려사항

1. **복잡도 증가**:
   - 네트워크 상태에 따른 분기 로직 필요
   - 두 가지 전략을 모두 유지해야 함

2. **상태 전환 처리**:
   - 오프라인 → 온라인 전환 시 동기화 큐 처리
   - 온라인 → 오프라인 전환 시 로컬 저장소로 전환

3. **데이터 불일치 가능성**:
   - 온라인 상태에서 서버 처리 중 네트워크 끊김
   - 부분 완료 상태 처리 필요

4. **성능 영향**:
   - 온라인 상태에서도 IndexedDB 조회 필요 (서버 데이터와 통합)
   - 서버 우선 처리로 인한 지연 가능성

## 3. 권장 방안

### 3-1. 하이브리드 전략 권장 ✅

**이유**:
1. **두 비기능 품질 모두 구현 가능**: 각각의 요구사항을 네트워크 상태에 따라 충족
2. **명확한 책임 분리**: 온라인/오프라인 상태에 따라 전략이 명확히 구분됨
3. **실용적 접근**: 실제 사용 시나리오에 맞는 전략
4. **확장성**: 향후 다른 비기능 품질 추가 시에도 유연하게 대응 가능

### 3-2. 구현 전략

#### 전략 A: 네트워크 상태 기반 분기 (권장)

```javascript
async deleteMemo(memoId) {
  if (networkMonitor.isOnline) {
    // 온라인: 서버 우선 전략
    try {
      // 1. 서버에서 먼저 삭제 시도
      await apiClient.delete(API_ENDPOINTS.MEMOS.DELETE(memoId));
      
      // 2. 성공 시 IndexedDB 갱신
      const localMemo = await dbManager.getMemoByServerId(memoId);
      if (localMemo) {
        await dbManager.deleteMemo(localMemo.localId);
      }
      
      return { message: '메모가 삭제되었습니다.' };
    } catch (error) {
      // 서버 실패 시 오프라인 모드로 전환
      return await this.deleteMemoOffline(memoId);
    }
  } else {
    // 오프라인: 로컬 우선 전략 (현재 로직)
    return await this.deleteMemoOffline(memoId);
  }
}
```

**장점**:
- 명확한 분기 로직
- 각 상태에 최적화된 처리
- 오류 처리 용이

**단점**:
- 코드 중복 가능성
- 상태 전환 시 처리 필요

#### 전략 B: 통합 전략 (대안)

```javascript
async deleteMemo(memoId) {
  // 1. 로컬 메모 조회 (캐시 확인)
  let localMemo = await dbManager.getMemoByServerId(memoId);
  
  // 2. 네트워크 상태 확인
  if (networkMonitor.isOnline) {
    // 온라인: 서버에서 먼저 삭제 시도
    try {
      await apiClient.delete(API_ENDPOINTS.MEMOS.DELETE(memoId));
      
      // 성공 시 로컬 삭제
      if (localMemo) {
        await dbManager.deleteMemo(localMemo.localId);
      }
      
      return { message: '메모가 삭제되었습니다.' };
    } catch (error) {
      // 서버 실패 시 로컬 처리로 전환
      if (!localMemo) {
        throw new Error('메모를 찾을 수 없습니다.');
      }
      // 오프라인 로직으로 전환
    }
  }
  
  // 3. 오프라인 또는 서버 실패 시: 로컬 처리
  if (!localMemo) {
    // 로컬에 없으면 서버에서 조회 시도 (온라인인 경우)
    if (networkMonitor.isOnline) {
      // 서버에서 메모 존재 확인
      try {
        await apiClient.get(API_ENDPOINTS.MEMOS.GET(memoId));
        // 존재하면 삭제 시도
        await apiClient.delete(API_ENDPOINTS.MEMOS.DELETE(memoId));
        return { message: '메모가 삭제되었습니다.' };
      } catch (error) {
        throw new Error('메모를 찾을 수 없습니다.');
      }
    } else {
      throw new Error('메모를 찾을 수 없습니다.');
    }
  }
  
  // 기존 오프라인 로직 실행
  // ...
}
```

**장점**:
- 단일 진입점
- 로컬 캐시 활용

**단점**:
- 복잡한 분기 로직
- 오류 처리 복잡

### 3-3. 최종 권장 사항

**전략 A (네트워크 상태 기반 분기)를 권장합니다.**

**이유**:
1. **명확성**: 각 상태에 대한 처리가 명확히 구분됨
2. **유지보수성**: 각 전략을 독립적으로 수정 가능
3. **테스트 용이성**: 각 전략을 독립적으로 테스트 가능
4. **확장성**: 향후 다른 네트워크 상태 기반 로직 추가 용이

## 4. 구현 시 고려사항

### 4-1. 네트워크 상태 전환 처리

**오프라인 → 온라인 전환**:
- 동기화 큐에 있는 항목들을 자동으로 처리
- 서버와 로컬 데이터 통합

**온라인 → 오프라인 전환**:
- 진행 중인 서버 요청 처리
- 실패한 요청을 동기화 큐에 추가

### 4-2. 데이터 일관성 보장

**온라인 상태**:
- 서버가 Single Source of Truth
- IndexedDB는 서버 데이터의 캐시
- 서버 처리 성공 후 IndexedDB 갱신

**오프라인 상태**:
- IndexedDB가 Single Source of Truth
- 서버는 동기화 대상
- 로컬 처리 후 동기화 큐에 추가

### 4-3. 오류 처리

**서버 요청 실패 시**:
- 네트워크 오류: 오프라인 모드로 전환
- 서버 오류 (4xx, 5xx): 오류 메시지 반환 또는 오프라인 모드로 전환

**로컬 처리 실패 시**:
- IndexedDB 오류: 오류 메시지 반환
- 동기화 큐 오류: 재시도 로직

## 5. 결론

### 5-1. 권장 사항

**하이브리드 전략을 권장합니다.**

- ✅ **온라인 상태**: 서버 우선 전략 (MySQL 이중화 Dual Write 작동)
- ✅ **오프라인 상태**: 로컬 우선 전략 (Offline-First 유지)
- ✅ **두 비기능 품질 모두 만족**: 각 상태에 최적화된 전략 적용

### 5-2. 구현 우선순위

1. **네트워크 상태 기반 분기 로직 구현**
2. **온라인 상태 처리 로직 구현** (서버 우선)
3. **오프라인 상태 처리 로직 유지** (현재 로직)
4. **상태 전환 처리 로직 구현**
5. **통합 테스트**

### 5-3. 참고 문서

- `분산2_프로젝트/docs/fault-tolerance/OFFLINE_MEMO_SYNC.md`: 오프라인 메모 동기화 전략
- `분산2_프로젝트/docs/fault-tolerance/FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md`: 비기능 품질 구현 로드맵
- `분산2_프로젝트/docs/troubleshooting/MEMO_DELETE_DUAL_WRITE_FLOW_ANALYSIS.md`: 메모 삭제 Dual Write 흐름 분석

