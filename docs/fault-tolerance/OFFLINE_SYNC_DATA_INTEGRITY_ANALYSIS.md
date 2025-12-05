# 오프라인 동기화 데이터 무결성 분석

> **목적**: 오프라인 환경에서 메모 생성/수정/삭제 후 네트워크 복구 시 데이터 동기화 과정에서 발생할 수 있는 데이터 무결성 문제 및 중복 데이터 발생 가능성 분석  
> **범위**: 단일 디바이스 환경 (멀티 디바이스 제외)  
> **관련 문서**: [오프라인 메모 작성 및 동기화 설계](./OFFLINE_MEMO_SYNC.md)

---

## 📋 목차

1. [분석 개요](#분석-개요)
2. [잠재적 문제 시나리오](#잠재적-문제-시나리오)
3. [종합 분석](#종합-분석)
4. [권장 해결 방안](#권장-해결-방안)

---

## 분석 개요

### 분석 배경

오프라인 환경에서 메모를 생성/수정/삭제한 후 네트워크가 복구되었을 때, IndexedDB에 저장된 데이터를 서버 DB에 동기화하는 과정에서 다음과 같은 문제가 발생할 수 있는지 분석합니다:

1. **데이터 무결성 문제**: 데이터가 의도와 다르게 저장되거나 손실되는 경우
2. **중복 데이터 문제**: 동일한 데이터가 중복으로 저장되거나 표시되는 경우

### 분석 범위

- ✅ 메모 생성 (CREATE)
- ✅ 메모 수정 (UPDATE)
- ✅ 메모 삭제 (DELETE)
- ✅ 동기화 순서
- ✅ 동기화 중 네트워크 재차단
- ✅ 데이터 병합 로직

### 제외 사항

- ❌ 멀티 디바이스 환경 (단일 디바이스만 고려)
- ❌ 서버 측 로직 (클라이언트 측 동기화 로직만 분석)

---

## 잠재적 문제 시나리오

### 시나리오 1: 메모 생성 → 동기화 중 수정 시도

#### 상황 설명

1. **오프라인에서 메모 A 생성**
   - CREATE 큐 항목 추가
   - `serverId: null`
   - `syncStatus: 'pending'`

2. **네트워크 복구 → 동기화 시작**
   - CREATE 동기화 시작
   - `syncStatus: 'syncing'`
   - 서버에 메모 생성 요청 전송 중

3. **동기화 완료 전에 사용자가 메모 A 수정 시도**
   - `updateMemo()` 호출
   - `serverId` 확인 → `null` (아직 설정되지 않음)

#### 문서 로직 확인

```javascript
// 서버에 동기화된 메모만 수정 가능 (serverId가 있어야 함)
if (!localMemo.serverId) {
    throw new Error('아직 동기화되지 않은 메모는 수정할 수 없습니다. 먼저 동기화를 완료해주세요.');
}
```

#### 문제점

- 동기화가 거의 완료되어 `serverId`가 곧 설정될 예정인 경우, 타이밍 이슈 발생 가능
- 사용자가 수정을 시도했지만 실패하여, 동기화 완료 후 다시 수정해야 함

#### 실제 영향

- ✅ **데이터 무결성**: 문제 없음 (수정이 차단되므로)
- ✅ **중복 데이터**: 문제 없음
- ⚠️ **사용자 경험**: 동기화 완료 후 다시 수정해야 하므로 불편함

#### 심각도

**낮음** - 데이터 무결성에는 문제가 없으나, 사용자 경험 저하 가능

---

### 시나리오 2: 메모 수정 → 동기화 중 삭제

#### 상황 설명

1. **오프라인에서 메모 A 수정**
   - UPDATE 큐 항목 추가
   - `syncStatus: 'pending'`
   - `syncQueueId: 'queue-1'`

2. **네트워크 복구 → 동기화 시작**
   - UPDATE 동기화 시작
   - `syncStatus: 'syncing'`
   - 서버에 메모 수정 요청 전송 중

3. **동기화 완료 전에 사용자가 메모 A 삭제 시도**
   - `deleteMemo()` 호출
   - 기존 `syncQueueId` 확인 → UPDATE 항목에 연결됨
   - DELETE 항목 추가 (새로운 `syncQueueId: 'queue-2'`)

#### 문서 로직 확인

```javascript
// 메모 삭제 시
const queueItem = await syncQueueManager.enqueue({
    type: 'DELETE',
    localMemoId: localMemo.localId,
    serverMemoId: localMemo.serverId,
    data: { id: localMemo.serverId }
});

// syncQueueId 업데이트
localMemo.syncQueueId = queueItem.id; // 새로운 큐 항목 ID
await dbManager.saveMemo(localMemo);
```

#### 문제점

- UPDATE 항목이 처리 중인데 DELETE 항목이 추가됨
- UPDATE 동기화 완료 후 DELETE 동기화 시작
- 사용자는 삭제를 원했지만, 수정이 먼저 반영됨

#### 실제 영향

- ⚠️ **데이터 무결성**: 중간에 수정된 내용이 잠깐 서버에 존재
- ✅ **최종 상태**: 삭제가 반영되어 최종적으로는 의도대로 동작
- ⚠️ **사용자 의도**: 삭제를 원했지만 수정이 먼저 반영됨

#### 동작 흐름

```
[UPDATE 동기화 시작]
    ↓
[서버에 수정 반영] ← 사용자가 원하지 않은 수정
    ↓
[UPDATE 동기화 완료]
    ↓
[DELETE 동기화 시작]
    ↓
[서버에서 삭제] ← 사용자가 원한 최종 상태
```

#### 심각도

**낮음** - 최종적으로는 의도대로 삭제되지만, 중간에 의도하지 않은 수정이 반영됨

---

### 시나리오 3: 메모 수정 → 삭제 → 동기화 순서 문제

#### 상황 설명

1. **오프라인에서 메모 A 수정**
   - UPDATE 큐 항목 추가
   - `createdAt: 10:00`

2. **오프라인에서 메모 A 삭제**
   - UPDATE 항목 제거
   - DELETE 항목 추가
   - `createdAt: 10:01`

3. **네트워크 복구 → 동기화 시작**
   - 동기화 큐에서 PENDING 항목 조회
   - `createdAt` 기준 정렬

#### 문서 로직 확인

```javascript
// 메모 수정 시 기존 동기화 큐 항목 제거
if (localMemo.syncQueueId) {
    await syncQueueManager.removeQueueItem(localMemo.syncQueueId);
}

// 메모 삭제 시
const queueItem = await syncQueueManager.enqueue({
    type: 'DELETE',
    // ...
});

// 동기화 순서 보장
pendingQueueItems.sort((a, b) => {
    const timeA = new Date(a.createdAt);
    const timeB = new Date(b.createdAt);
    return timeA - timeB;
});
```

#### 문제점

- UPDATE 항목이 제거되고 DELETE 항목만 남음

#### 실제 영향

- ✅ **데이터 무결성**: 문제 없음 (UPDATE 항목이 제거되므로)
- ✅ **중복 데이터**: 문제 없음
- ✅ **동기화 순서**: DELETE만 존재하므로 순서 문제 없음

#### 심각도

**없음** - 문서 로직상 문제 없음

---

### 시나리오 4: 동기화 중 네트워크 재차단

#### 상황 설명

1. **오프라인에서 메모 작업 수행**
   - CREATE/UPDATE/DELETE 큐 항목 추가
   - `syncStatus: 'pending'`

2. **네트워크 복구 → 동기화 시작**
   - 동기화 시작
   - `syncStatus: 'syncing'`
   - 서버에 요청 전송

3. **API 호출 중 네트워크 재차단**
   - 서버 응답 수신 전 네트워크 끊김
   - 서버에 작업이 반영되었는지 불명확

#### 문서 로직 확인

```javascript
} catch (error) {
    // 동기화 실패 처리
    if (queueItem.localMemoId) {
        const localMemo = await dbManager.getMemoByLocalId(queueItem.localMemoId);
        if (localMemo) {
            localMemo.syncStatus = 'failed';
            await dbManager.saveMemo(localMemo);
        }
    }
    // 동기화 큐에 에러 기록 및 재시도 예약
    await syncQueueManager.markAsFailed(queueItem.id, error.message);
    throw error;
}
```

#### 서버 API 멱등성 분석

**현재 서버 구현 확인 결과**:

| 작업 타입 | HTTP 메서드 | 서버 구현 | 멱등성 보장 여부 | 재시도 시 영향 |
|----------|------------|----------|----------------|--------------|
| **메모 생성** | POST | `memoRepository.save(memo)` | ❌ **보장하지 않음** | ⚠️ 중복 메모 생성 가능 |
| **메모 수정** | PUT | `memoRepository.save(existingMemo)` | ✅ **보장함** | ✅ 문제 없음 |
| **메모 삭제** | DELETE | `memoRepository.delete(memo)` | ✅ **보장함** (수정 완료) | ✅ 문제 없음 |

**상세 분석**:

1. **POST /api/v1/memos (메모 생성)**
   - 구현: `memoRepository.save(memo)` - 새로운 엔티티 저장
   - ID 생성: `@GeneratedValue(strategy = GenerationType.IDENTITY)` - 서버에서 자동 생성
   - 멱등성: ❌ 보장하지 않음
   - 재시도 시: 동일한 요청을 여러 번 보내면 여러 개의 메모가 생성됨
   - 중복 방지 로직: 없음
   
   **멱등성 보장 개선 방안: 멱등성 키(Idempotency Key) 활용**
   
   **원칙**:
   - 클라이언트가 멱등성 키를 생성하여 `Idempotency-Key` 헤더에 담아 전송
   - 서버는 유니크한 키를 분산 저장소에 저장하고 처음이면 `PROCESSING` 상태로 설정
   - 요청 내용 처리가 완료되면 `COMPLETED`로 변경
   - 동일한 키에 대해 동일한 응답을 반환하여 효과를 한 번만 발생하게 함
   
   **구현 흐름**:
   1. **멱등성 키 확인**: 클라이언트가 `Idempotency-Key` 헤더로 전송한 키 확인
   2. **캐시된 응답 확인**: 분산 저장소에서 해당 키로 저장된 응답이 있는지 확인
      - `COMPLETED` 상태면 캐시된 응답 반환 (중복 요청 무시)
      - `PROCESSING` 상태면 처리 중 예외 발생 (재시도 또는 대기)
   3. **PROCESSING 상태로 저장**: 분산 저장소에 빠르게 `PROCESSING` 상태로 저장 (동시성 이슈 방지)
   4. **비즈니스 로직 실행**: 메모 생성 로직 실행
   5. **COMPLETED 상태로 저장**: 처리 완료 후 `COMPLETED` 상태로 저장 및 응답 캐싱
   
   **주요 구현 포인트**:
   - **분산 저장소 사용**: Redis 등 분산 저장소 사용 (싱글 스레드로 동시성 이슈 없음)
   - **처리 순서 중요**: `PROCESSING` 상태를 먼저 저장한 후 비즈니스 로직 실행
     - 로직 처리가 완료된 후 `COMPLETED`로 변경하면 오류 발생 가능
     - 빠르게 분산 저장소에 저장하고 로직 처리를 진행해야 함
   - **TTL 관리**: 키 생명주기는 TTL로 관리하며, 도메인에 따라 적절한 시점에 소멸
   - **동일 응답 반환**: 동일한 키에 대해 동일한 응답을 반환하여 멱등성 보장
   
   **필요한 컴포넌트**:
   - Redis 의존성 (Spring Data Redis)
   - `IdempotencyKeyService`: 멱등성 키 관리 서비스
   - Redis 설정 클래스
   - Controller에서 `Idempotency-Key` 헤더 처리
   
   **구현 예시 구조**:
   ```java
   // Controller
   @PostMapping("/memos")
   public ApiResponse<MemoResponse> createMemo(
       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
       @Valid @RequestBody MemoCreateRequest request) {
       
       // 1. 멱등성 키 검증 및 처리
       if (idempotencyKey != null) {
           ApiResponse<MemoResponse> cachedResponse = 
               idempotencyKeyService.getCachedResponse(idempotencyKey);
           if (cachedResponse != null) {
               return cachedResponse; // 동일한 응답 반환
           }
           
           // PROCESSING 상태로 저장 (분산 저장소에 빠르게 저장)
           idempotencyKeyService.markAsProcessing(idempotencyKey);
       }
       
       // 2. 기존 로직 실행
       // ... 메모 생성 로직 ...
       
       // 3. 멱등성 키가 있으면 COMPLETED로 저장 및 응답 캐싱
       if (idempotencyKey != null) {
           idempotencyKeyService.markAsCompleted(idempotencyKey, response);
       }
       
       return ApiResponse.success(response);
   }
   
   // IdempotencyKeyService
   @Service
   public class IdempotencyKeyService {
       // Redis에 저장할 구조
       // {
       //   "idempotency-key": "uuid-from-client",
       //   "status": "PROCESSING" | "COMPLETED",
       //   "response": MemoResponse (COMPLETED일 때만),
       //   "createdAt": timestamp,
       //   "ttl": expiration-time
       // }
       
       // 동일한 키에 대한 동시 요청 처리 (Redis는 싱글 스레드)
       public ApiResponse<MemoResponse> getCachedResponse(String idempotencyKey) {
           // COMPLETED 상태면 캐시된 응답 반환
           // PROCESSING 상태면 예외 발생
       }
       
       // PROCESSING 상태로 저장 (분산 저장소에 빠르게 저장)
       public void markAsProcessing(String idempotencyKey) {
           // Redis에 PROCESSING 상태로 저장
       }
       
       // COMPLETED 상태로 저장 및 응답 캐싱
       public void markAsCompleted(String idempotencyKey, MemoResponse response) {
           // Redis에 COMPLETED 상태 및 응답 저장
       }
   }
   ```
   
   **클라이언트 측 요구사항**:
   - 클라이언트가 UUID를 생성하여 `Idempotency-Key` 헤더에 포함
   - 동일한 요청에는 동일한 키 사용
   - 서버가 내부적으로 생성하지 않고 클라이언트가 생성
   
   **Redis 도입 및 역할**:
   
   **Redis의 목적**:
   - 본 프로젝트에서 Redis는 오직 **성능 최적화**와 **멱등성 보장**이라는 특수 목적을 위해 사용됩니다.
   - Redis는 **속도를 위한 중간 저장소**이자 **임시 상태 관리자** 역할을 합니다.
   
   **Redis가 담당하는 역할**:
   
   1. **멱등성 키 관리**
      - 수명이 짧고 접근 속도가 매우 빨라야 하는 멱등성 키를 저장하여 중복 메모 생성을 방지합니다.
      - TTL 설정으로 자동 만료 관리
      - 싱글 스레드로 동시성 이슈 없음
   
   2. **세션/인증 토큰**
      - 사용자 로그인 상태를 유지하는 토큰을 저장합니다.
      - TTL 설정으로 만료 자동 관리
      - 빠른 토큰 검증 및 갱신
   
   3. **빈번한 캐싱 (서버 측 Redis 캐싱)**
      - 자주 조회되지만 변경이 거의 없는 데이터를 Redis에 저장하여, 매번 MySQL까지 가지 않고 빠르게 응답합니다.
      - 캐싱 대상:
        - 태그 데이터 (대분류 및 각 대분류별 태그 종류)
        - 내 서재 정보
      - **참고**: 클라이언트 측(Service Worker)의 캐싱 전략은 OFFLINE_MEMO_SYNC.md 문서 참조
      
      **⚠️ 레이어별 용어 구분**:
      - **Cache-First, Stale-While-Revalidate**: 클라이언트(Service Worker)의 네트워크 요청 처리 로직 용어
      - **Redis 캐시의 TTL**: 서버(Redis)의 데이터 저장 및 관리 정책 용어
      - 두 레이어는 서로 다른 계층에서 동작하며 상호 보완적 관계
      
      **태그 데이터 (서버 측 Redis 캐싱 정책)**:
      
      **데이터 성격**:
      - 개발자 통제 공유 자원: 개발자가 직접 DB를 수정하지 않는 한 변동이 없음
      - 전역 공유 데이터: 모든 사용자가 동일한 태그 데이터를 사용
      - 변동성: 거의 없음 (개발자가 수동으로 변경할 때만 변동)
      
      **Redis 캐싱 전략: Purger-Driven Invalidation + Long TTL**
      
      - **TTL 설정**: 7일 (매우 긴 기간)
        - 변동이 거의 없으므로 캐시를 짧게 유지하거나 복잡한 무효화 로직을 자동화할 필요가 없음
        - 신뢰성과 최대 TTL이 중요
      
      - **캐시 무효화 전략: Purger-Driven Invalidation (수동 무효화)**
        - 태그 DB 변경 시, 개발자가 수동으로 Redis 캐시를 명시적으로 삭제(Delete)
        - 개발자가 직접 변경했을 때만 수동으로 Redis를 무효화(Purge)
        - Service Worker는 Cache-First, Fallback to Network 전략을 사용하면, Redis가 갱신된 이후 다음 네트워크 요청 시 자연스럽게 최신 데이터를 가져오게 됨
      
      - **구현 예시**:
        ```java
        // 태그 데이터 변경 시 Redis 캐시 무효화
        @Service
        public class TagService {
            @Autowired
            private RedisTemplate<String, Object> redisTemplate;
            
            public void updateTag(Tag tag) {
                // 1. DB 업데이트
                tagRepository.save(tag);
                
                // 2. Redis 캐시 수동 무효화 (Purger-Driven)
                redisTemplate.delete("tags:all");
                redisTemplate.delete("tags:category:" + tag.getCategory());
            }
        }
        ```
      
      **내 서재 정보 (서버 측 Redis 캐싱 정책)**:
      
      **데이터 성격**:
      - 사용자 통제 개인 자원: 사용자만이 개인의 내 서재 정보를 생성, 수정, 삭제할 수 있음
      - 개발자가 절대로 변경하면 안 되는 데이터
      - 변동성: 사용자 액션에 따라 빈번히 변경됨 (도서 추가/삭제)
      - 신속한 최신 상태 반영(Freshness)이 중요
      
      **Redis 캐싱 전략: Write-Through / Cache Aside Pattern + Short TTL**
      
      - **TTL 설정**: 5분~10분 (짧은 기간)
        - 데이터 무결성 최우선: 사용자가 방금 변경한 내용이 즉시 반영되어야 함
        - 안전망 역할: 혹시 무효화 로직에 문제가 생기더라도 캐시가 5분 후에 만료되어 다음 요청 시 최신 데이터를 가져오게 함
      
      - **캐시 무효화 전략: Write-Through / Cache Aside Pattern (쓰기 시 즉시 무효화)**
        - 쓰기(Write) 작업이 발생할 때마다 캐시를 삭제하는 것이 가장 확실하고 빠르게 최신 상태를 보장
        - 사용자가 책을 추가/삭제하는 API 호출 완료 시, 서버 애플리케이션에서 **즉시 Redis 캐시를 무효화(삭제)**
        - 데이터 무결성 최우선: 내 서재 정보는 사용자가 방금 변경한 내용이 즉시 반영되어야 함
      
      - **구현 예시**:
        ```java
        // 내 서재 정보 변경 시 Redis 캐시 즉시 무효화
        @Service
        public class BookShelfService {
            @Autowired
            private RedisTemplate<String, Object> redisTemplate;
            
            public UserShelfBook addBookToShelf(UserShelfBook userShelfBook) {
                // 1. DB 저장
                UserShelfBook saved = userShelfBookRepository.save(userShelfBook);
                
                // 2. Redis 캐시 즉시 무효화 (Write-Through)
                Long userId = saved.getUser().getId();
                redisTemplate.delete("user-shelf:" + userId);
                
                return saved;
            }
            
            public void removeBookFromShelf(Long userBookId, Long userId) {
                // 1. DB 삭제
                userShelfBookRepository.deleteById(userBookId);
                
                // 2. Redis 캐시 즉시 무효화 (Write-Through)
                redisTemplate.delete("user-shelf:" + userId);
            }
        }
        ```
   
   **Redis 영속성 방식: Hybrid Approach (AOF + RDB)**:
   
   - **AOF (Append Only File) 활성화** (기본 사용)
     - `appendonly yes` 설정
     - `appendfsync everysec` (1초 단위 기록) 설정으로 데이터 신뢰성을 극대화합니다.
     - 모든 쓰기 작업을 로그로 저장하여 데이터 손실 최소화
     - 서버 재시작 시 AOF 파일을 재생하여 데이터 복구
   
   - **RDB 백업 활성화** (백업 및 빠른 복구 수단)
     - AOF 파일이 너무 커져서 복구 시간이 길어지는 것을 방지하기 위해 RDB를 이용한 주기적인 스냅샷을 백업 용도로 함께 사용합니다.
     - 주기적인 스냅샷 생성으로 빠른 복구 가능
     - AOF와 RDB를 병행 사용하여 신뢰성과 복구 속도 모두 확보
   
   **Redis 운영 방식**:
   
   - **Docker Compose로 간단히 구성**
     - 개발 환경 및 운영 환경 모두 Docker Compose를 사용하여 Redis 인스턴스를 구성합니다.
     - 간단한 설정으로 Redis 서버 실행 및 관리
     - 포트 매핑 및 볼륨 마운트를 통한 데이터 영속성 보장
   
   **Redis 설정 예시**:
   ```yaml
   # docker-compose.yml
   redis:
     image: redis:7-alpine
     ports:
       - "6379:6379"
     volumes:
       - redis-data:/data
     command: redis-server --appendonly yes --appendfsync everysec
   
   volumes:
     redis-data:
   ```
   
   ```conf
   # redis.conf (선택적)
   # AOF 설정
   appendonly yes
   appendfsync everysec
   
   # RDB 백업 설정
   save 900 1      # 900초 동안 1개 이상 변경 시 저장
   save 300 10     # 300초 동안 10개 이상 변경 시 저장
   save 60 10000   # 60초 동안 10000개 이상 변경 시 저장
   ```
   
   **Redis 도입 시 서버 로직 변경사항**:
   
   **현재 서버 인증/세션 구조**:
   - JWT 토큰 방식 사용
   - Access Token: JWT 자체에 정보 포함, DB 조회 불필요
   - Refresh Token: MySQL DB에 저장 (RefreshToken 엔티티)
   - RefreshTokenRepository를 통해 조회/저장/삭제
   - `revoked` 플래그로 토큰 무효화 관리
   - `expiresAt`으로 만료 시간 관리
   
   **Redis 도입 시 변경 범위**:
   
   | 컴포넌트 | 변경 여부 | 변경 내용 |
   |---------|----------|----------|
   | **JwtService** | ✅ **수정 필요** | RefreshToken 저장/조회를 Redis로 변경 |
   | **RefreshTokenRepository** | ✅ **제거** | 더 이상 사용하지 않음 (제거) |
   | **RefreshToken 엔티티** | ✅ **제거** | Redis 사용 시 불필요 (제거) |
   | **JwtAuthenticationFilter** | ❌ **변경 불필요** | JWT 검증만 수행 (DB/Redis 조회 없음) |
   | **PasswordResetToken** | ❌ **변경 불필요** | 일회성 토큰이므로 DB에 그대로 유지 |
   | **UserDevice** | ❌ **변경 불필요** | 디바이스 정보는 영구 저장이 필요하므로 DB 유지 |
   
   **수정이 필요한 부분**:
   
   1. **JwtService 수정 (필수)**
      - `saveRefreshToken()`: DB 저장 → Redis 저장
      - `refreshTokens()`: DB 조회 → Redis 조회
      - `revokeRefreshTokens()`: DB 무효화 → Redis 삭제
      - `revokeAllRefreshTokens()`: DB 삭제 → Redis 삭제
      - Redis에 토큰 저장 시 TTL 설정으로 자동 만료 관리
   
   2. **RefreshTokenRepository 제거**
      - Redis 사용 시 더 이상 필요 없음
      - 엔티티와 함께 제거
   
   3. **RefreshToken 엔티티 제거**
      - Redis에 저장하므로 엔티티 불필요
      - 기존 DB 데이터는 자연 만료 대기 (RefreshToken 만료 시간: 7일)
      - 사용자 재로그인 시 새 토큰이 Redis에 저장됨
      - 마이그레이션 불필요 (만료 시간이 짧아 자연 만료됨)
   
   4. **Redis 서비스 추가**
      - `RefreshTokenRedisService` 생성
      - Redis에 토큰 저장/조회/삭제 로직 구현
   
   **수정이 필요 없는 부분**:
   
   1. **JwtAuthenticationFilter (변경 불필요)**
      - JWT 토큰 검증만 수행 (DB/Redis 조회 없음)
      - Access Token은 JWT 자체에 정보 포함
      - Redis 도입과 무관하게 그대로 사용 가능
   
   2. **PasswordResetToken (DB 유지)**
      - 일회성 토큰이므로 DB에 그대로 유지
      - Redis로 옮길 필요 없음
   
   3. **UserDevice (DB 유지)**
      - 디바이스 정보는 영구 저장이 필요하므로 DB 유지
      - Redis는 임시 상태 관리용이므로 부적합
   
   **결론**:
   
   - **전체적으로 JwtService만 수정하면 되며, 나머지는 그대로 사용 가능합니다.**
   - JwtService의 RefreshToken 관련 메서드들을 Redis 기반으로 변경
   - RefreshTokenRepository는 제거
   - RefreshToken 엔티티는 제거 (Redis에 저장하므로)
   - 기존 DB 데이터는 자연 만료 대기 (RefreshToken 만료 시간: 7일)
   - 사용자 재로그인 시 새 토큰이 Redis에 저장됨
   - 마이그레이션 불필요 (만료 시간이 짧아 자연 만료됨)
   - JwtAuthenticationFilter, PasswordResetToken, UserDevice는 변경 불필요

2. **PUT /api/v1/memos/{memoId} (메모 수정)**
   - 구현: 기존 메모 조회 후 `memoRepository.save(existingMemo)` - 기존 엔티티 업데이트
   - 멱등성: ✅ 보장함
   - 재시도 시: 동일한 요청을 여러 번 보내도 같은 메모를 같은 내용으로 업데이트
   - 문제 없음

3. **DELETE /api/v1/memos/{memoId} (메모 삭제)** ✅ **수정 완료**
   - 구현: 메모 조회 후 `memoRepository.delete(memo)`
   - 멱등성: ✅ **보장함** (수정 완료)
   - 재시도 시: 이미 삭제된 메모에 대해서도 성공 응답 반환
   - **수정 내용**: `findById().orElse(null)` 사용하여 이미 삭제된 경우에도 성공 응답 반환
   
   **멱등성 보장 방법**:
   - 첫 번째 삭제 요청: 메모를 찾아서 삭제하고 성공 응답 반환
   - 두 번째 삭제 요청 (이미 삭제된 경우): 메모가 없어도 성공 응답 반환
   - 이렇게 하면 동일한 요청을 여러 번 실행해도 결과가 동일하므로 멱등성이 보장됨
   
   **수정된 구현**:
   ```java
   public void deleteMemo(User user, Long memoId) {
       if (memoId == null) {
           throw new IllegalArgumentException("메모 ID는 필수입니다.");
       }
       Memo memo = memoRepository.findById(memoId).orElse(null);
       
       // 이미 삭제된 메모인 경우 성공으로 처리 (멱등성 보장)
       if (memo == null) {
           return;
       }
       
       if (!memo.getUser().getId().equals(user.getId())) {
           throw new IllegalArgumentException("권한이 없습니다.");
       }
       
       memoRepository.delete(memo);
   }
   ```
   
   **동작 시나리오**:
   - 첫 번째 삭제 요청: 메모 존재 → 삭제 성공 → 성공 응답
   - 두 번째 삭제 요청: 메모 없음 → `orElse(null)` → `return` → 성공 응답
   - 결과: 두 요청 모두 성공 응답 → 멱등성 보장 ✅

#### 문제점

- 서버에 작업이 반영되었는지 불명확
- 네트워크 재차단으로 응답을 받지 못함
- 재시도 시 작업 타입에 따라 문제 발생 가능:
  - **CREATE**: 중복 메모 생성 가능
  - **UPDATE**: 문제 없음 (멱등성 보장)
  - **DELETE**: 문제 없음 (멱등성 보장, 수정 완료)

#### 실제 영향

**작업 타입별 영향**:

- ✅ **메모 수정 (UPDATE)**: 멱등성이 보장되므로 재시도 시 문제 없음
- ❌ **메모 생성 (CREATE)**: 재시도 시 중복 메모 생성 가능
- ✅ **메모 삭제 (DELETE)**: 멱등성이 보장되므로 재시도 시 문제 없음 (수정 완료)

**전체적인 영향**:
- ⚠️ **데이터 무결성**: CREATE에서만 문제 발생 가능 (DELETE는 수정 완료)
- ⚠️ **중복 데이터**: CREATE 재시도 시 중복 메모 생성
- ✅ **재시도 로직**: Exponential Backoff로 재시도, DELETE는 멱등성 보장으로 문제 없음

#### 해결 방안

**서버 측 보완 필요**:

1. **POST (메모 생성) 멱등성 보장**
   - 요청 ID나 타임스탬프를 활용하여 중복 요청 감지
   - 또는 클라이언트에서 생성한 고유 ID를 서버에서 검증하여 중복 생성 방지
   - 예: `idempotency-key` 헤더 활용

2. **DELETE (메모 삭제) 멱등성 보장** ✅ **수정 완료**
   - 이미 삭제된 메모에 대해 예외를 던지지 않고 성공 응답 반환
   - **수정 완료**: `findById().orElse(null)` 사용하여 이미 삭제된 경우 성공으로 처리
   - 수정된 구현:
   ```java
   public void deleteMemo(User user, Long memoId) {
       if (memoId == null) {
           throw new IllegalArgumentException("메모 ID는 필수입니다.");
       }
       Memo memo = memoRepository.findById(memoId).orElse(null);
       
       // 이미 삭제된 메모인 경우 성공으로 처리 (멱등성 보장)
       if (memo == null) {
           return;
       }
       
       if (!memo.getUser().getId().equals(user.getId())) {
           throw new IllegalArgumentException("권한이 없습니다.");
       }
       
       memoRepository.delete(memo);
   }
   ```

3. **PUT (메모 수정)**
   - 현재 구현이 멱등성을 보장하므로 추가 보완 불필요

#### 심각도

**중간** - CREATE와 DELETE에서 문제가 발생할 수 있으므로 서버 측 보완 필요

---

### 시나리오 5: 메모 생성 → 동기화 완료 → 즉시 수정 → 동기화 중 삭제

#### 상황 설명

1. **오프라인에서 메모 A 생성**
   - CREATE 큐 항목 추가
   - `createdAt: 10:00`

2. **네트워크 복구 → CREATE 동기화 완료**
   - 서버에 메모 생성 성공
   - `serverId: 123` 설정
   - `syncStatus: 'synced'`

3. **즉시 메모 A 수정**
   - UPDATE 큐 항목 추가
   - `createdAt: 10:01`
   - `syncStatus: 'pending'`

4. **UPDATE 동기화 시작**
   - `syncStatus: 'syncing'`
   - 서버에 메모 수정 요청 전송 중

5. **동기화 완료 전에 메모 A 삭제 시도**
   - `deleteMemo()` 호출
   - 기존 `syncQueueId` 확인 → UPDATE 항목에 연결됨
   - DELETE 항목 추가 (새로운 `syncQueueId`)

#### 문서 로직 확인

```javascript
// 메모 삭제 시
// 기존 동기화 큐 항목이 있으면 제거하지 않음 (UPDATE가 처리 중)
const queueItem = await syncQueueManager.enqueue({
    type: 'DELETE',
    localMemoId: localMemo.localId,
    serverMemoId: localMemo.serverId,
    data: { id: localMemo.serverId }
});

// syncQueueId 업데이트
localMemo.syncQueueId = queueItem.id; // DELETE 항목 ID로 변경
```

#### 문제점

- UPDATE 항목이 처리 중인데 DELETE 항목이 추가됨
- UPDATE 동기화 완료 후 DELETE 동기화 시작
- 사용자는 삭제를 원했지만, 수정이 먼저 반영됨

#### 실제 영향

- ⚠️ **데이터 무결성**: 중간에 수정된 내용이 잠깐 서버에 존재
- ✅ **최종 상태**: 삭제가 반영되어 최종적으로는 의도대로 동작
- ⚠️ **사용자 의도**: 삭제를 원했지만 수정이 먼저 반영됨

#### 동작 흐름

```
[CREATE 동기화 완료] serverId: 123
    ↓
[UPDATE 동기화 시작] 수정 내용 전송 중
    ↓
[사용자가 삭제 시도] DELETE 항목 추가
    ↓
[UPDATE 동기화 완료] 서버에 수정 반영 ← 사용자가 원하지 않은 수정
    ↓
[DELETE 동기화 시작]
    ↓
[서버에서 삭제] ← 사용자가 원한 최종 상태
```

#### 심각도

**낮음** - 최종적으로는 의도대로 삭제되지만, 중간에 의도하지 않은 수정이 반영됨

---

### 시나리오 6: mergeMemos에서의 중복 표시 문제 ⚠️

#### 상황 설명

1. **오프라인에서 메모 A 생성**
   - CREATE 큐 항목 추가
   - `serverId: null`

2. **네트워크 복구 → CREATE 동기화 완료**
   - 서버에 메모 생성 성공
   - `serverId: 123` 설정
   - `syncStatus: 'synced'`

3. **오프라인으로 전환**

4. **메모 A 수정**
   - UPDATE 큐 항목 추가
   - `syncStatus: 'pending'` (수정 대기)
   - 로컬 메모 내용: 수정된 내용

5. **네트워크 복구 → 서버에서 메모 조회**
   - `getMemos()` 호출
   - 서버에서 메모 조회 (기존 내용)
   - `mergeMemos()` 호출

#### 문서 로직 확인

```javascript
mergeMemos(localMemos, serverMemos) {
    // 서버 메모를 맵으로 변환 (중복 제거용)
    const serverMemoMap = new Map();
    serverMemos.forEach(memo => {
        serverMemoMap.set(memo.id, memo);
    });
    
    // 로컬 메모 중 동기화 완료된 것은 서버 메모로 대체
    localMemos.forEach(localMemo => {
        if (localMemo.syncStatus === 'synced' && localMemo.serverId) {
            // 서버 메모가 있으면 사용, 없으면 로컬 메모 사용
            const serverMemo = serverMemoMap.get(localMemo.serverId);
            if (serverMemo) {
                result.push(serverMemo);
                serverMemoMap.delete(localMemo.serverId); // 이미 처리됨
            }
        } else {
            // 동기화 대기 중인 로컬 메모
            result.push(this.mapLocalMemoToResponse(localMemo));
        }
    });
    
    // 서버에만 있는 메모 추가
    serverMemoMap.forEach(memo => {
        result.push(memo);
    });
}
```

#### 문제점

**로컬 메모 상태**:
- `serverId: 123`
- `syncStatus: 'pending'` (수정 대기)
- 내용: 수정된 내용

**서버 메모 상태**:
- `id: 123`
- 내용: 기존 내용

**mergeMemos 처리 과정**:
1. 서버 메모를 맵으로 변환: `{ 123: 서버 메모 }`
2. 로컬 메모 처리:
   - `syncStatus === 'synced'`가 아니므로 조건 불만족
   - `else` 블록 실행: 로컬 메모(수정된 내용)를 result에 추가
3. 서버에만 있는 메모 추가:
   - `serverMemoMap`에 `id: 123`이 여전히 존재
   - 서버 메모(기존 내용)를 result에 추가

#### 실제 영향

- ❌ **중복 데이터**: 동일한 메모(`serverId: 123`)가 두 번 표시됨
  - 하나는 로컬 메모 (수정된 내용, `syncStatus: 'pending'`)
  - 하나는 서버 메모 (기존 내용)
- ⚠️ **데이터 무결성**: 사용자에게 혼란을 줄 수 있음

#### 예시

```
로컬 메모:
- serverId: 123
- syncStatus: 'pending'
- content: "수정된 내용"

서버 메모:
- id: 123
- content: "기존 내용"

mergeMemos 결과:
[
  { id: 123, content: "수정된 내용", syncStatus: 'pending' },  // 로컬 메모
  { id: 123, content: "기존 내용" }                              // 서버 메모
]
```

#### 심각도

**중간** - 동일한 메모가 중복으로 표시되어 사용자에게 혼란을 줄 수 있음

---

## 종합 분석

### 발생 가능한 문제 요약

| 시나리오 | 문제 유형 | 심각도 | 발생 가능성 | 최종 상태 |
|---------|----------|--------|------------|----------|
| 시나리오 1 | 사용자 경험 저하 | 낮음 | 낮음 | 정상 |
| 시나리오 2 | 작업 순서 문제 | 낮음 | 중간 | 정상 (의도대로 삭제) |
| 시나리오 3 | 순서 문제 | 없음 | 없음 | 정상 |
| 시나리오 4 | 중복 생성/삭제 예외 | **중간** | 중간 | **서버 보완 필요** |
| 시나리오 5 | 작업 순서 문제 | 낮음 | 중간 | 정상 (의도대로 삭제) |
| **시나리오 6** | **중복 표시** | **중간** | **높음** | **문제 있음** |

### 발생하지 않는 문제

1. ✅ **중복 생성**: 서버에서 ID를 생성하므로 중복 생성 불가
2. ✅ **중복 삭제**: 서버에서 삭제하므로 중복 삭제 불가
3. ✅ **동기화 순서**: `createdAt` 기준 정렬로 순서 보장

### 핵심 문제

**시나리오 6: mergeMemos에서의 중복 표시 문제**

- 동기화 대기 중인 메모(`syncStatus: 'pending'`)와 서버 메모를 모두 표시
- 동일한 메모(`serverId`가 동일)가 두 번 표시됨
- 사용자에게 혼란을 줄 수 있음

---

## 권장 해결 방안

### 시나리오 6 해결 방안

#### 방안 1: 동기화 대기 중인 메모 우선 표시 (권장)

**원칙**: 동기화 대기 중인 로컬 메모가 있으면 서버 메모를 제외

**구현**:
```javascript
mergeMemos(localMemos, serverMemos) {
    const serverMemoMap = new Map();
    serverMemos.forEach(memo => {
        serverMemoMap.set(memo.id, memo);
    });
    
    const result = [];
    const processedServerIds = new Set(); // 처리된 서버 ID 추적
    
    localMemos.forEach(localMemo => {
        if (localMemo.syncStatus === 'synced' && localMemo.serverId) {
            // 동기화 완료된 메모: 서버 메모로 대체
            const serverMemo = serverMemoMap.get(localMemo.serverId);
            if (serverMemo) {
                result.push(serverMemo);
                processedServerIds.add(localMemo.serverId);
                serverMemoMap.delete(localMemo.serverId);
            } else {
                result.push(this.mapLocalMemoToResponse(localMemo));
            }
        } else if (localMemo.serverId) {
            // 동기화 대기 중인 메모 (수정/삭제 대기)
            // 서버 메모는 제외하고 로컬 메모만 표시
            result.push(this.mapLocalMemoToResponse(localMemo));
            processedServerIds.add(localMemo.serverId);
            serverMemoMap.delete(localMemo.serverId);
        } else {
            // 아직 동기화되지 않은 메모 (생성 대기)
            result.push(this.mapLocalMemoToResponse(localMemo));
        }
    });
    
    // 서버에만 있는 메모 추가 (처리되지 않은 메모만)
    serverMemoMap.forEach(memo => {
        if (!processedServerIds.has(memo.id)) {
            result.push(memo);
        }
    });
    
    return result.sort((a, b) => {
        const timeA = new Date(a.memoStartTime || a.createdAt);
        const timeB = new Date(b.memoStartTime || b.createdAt);
        return timeA - timeB;
    });
}
```

**장점**:
- ✅ 중복 표시 문제 해결
- ✅ 사용자가 수정한 최신 내용을 우선 표시
- ✅ 구현 단순

**단점**:
- ⚠️ 서버의 최신 내용이 일시적으로 표시되지 않을 수 있음 (동기화 완료 후 반영)

#### 방안 2: 동기화 대기 중인 메모와 서버 메모 병합 표시

**원칙**: 동기화 대기 중인 메모와 서버 메모를 하나로 병합하여 표시

**구현**:
```javascript
mergeMemos(localMemos, serverMemos) {
    const serverMemoMap = new Map();
    serverMemos.forEach(memo => {
        serverMemoMap.set(memo.id, memo);
    });
    
    const result = [];
    
    localMemos.forEach(localMemo => {
        if (localMemo.syncStatus === 'synced' && localMemo.serverId) {
            // 동기화 완료된 메모: 서버 메모로 대체
            const serverMemo = serverMemoMap.get(localMemo.serverId);
            if (serverMemo) {
                result.push(serverMemo);
                serverMemoMap.delete(localMemo.serverId);
            } else {
                result.push(this.mapLocalMemoToResponse(localMemo));
            }
        } else if (localMemo.serverId) {
            // 동기화 대기 중인 메모: 로컬 메모를 우선 표시하되 서버 메모 정보 병합
            const serverMemo = serverMemoMap.get(localMemo.serverId);
            const mergedMemo = {
                ...this.mapLocalMemoToResponse(localMemo),
                // 서버 메모의 추가 정보 병합 (예: createdAt, updatedAt)
                serverCreatedAt: serverMemo?.createdAt,
                serverUpdatedAt: serverMemo?.updatedAt,
                // 동기화 대기 중임을 명시
                isPendingSync: true
            };
            result.push(mergedMemo);
            serverMemoMap.delete(localMemo.serverId);
        } else {
            // 아직 동기화되지 않은 메모
            result.push(this.mapLocalMemoToResponse(localMemo));
        }
    });
    
    // 서버에만 있는 메모 추가
    serverMemoMap.forEach(memo => {
        result.push(memo);
    });
    
    return result.sort((a, b) => {
        const timeA = new Date(a.memoStartTime || a.createdAt);
        const timeB = new Date(b.memoStartTime || b.createdAt);
        return timeA - timeB;
    });
}
```

**장점**:
- ✅ 중복 표시 문제 해결
- ✅ 서버 메모의 추가 정보도 활용 가능

**단점**:
- ⚠️ 구현 복잡도 증가
- ⚠️ UI에서 동기화 대기 상태를 명확히 표시해야 함

### 시나리오 2, 5 해결 방안

#### 방안: 동기화 중인 작업 취소

**원칙**: 동기화 중인 작업이 있으면 새로운 작업(수정/삭제)을 큐에 추가하지 않고, 기존 작업을 취소하고 새 작업으로 대체

**구현**:
```javascript
async deleteMemo(memoId) {
    // ... 기존 로직 ...
    
    // 동기화 중인 작업이 있으면 취소
    if (localMemo.syncQueueId) {
        const existingQueueItem = await syncQueueManager.getQueueItem(localMemo.syncQueueId);
        
        if (existingQueueItem && existingQueueItem.status === 'SYNCING') {
            // 동기화 중인 작업 취소 불가 → 사용자에게 알림
            throw new Error('메모가 동기화 중입니다. 잠시 후 다시 시도해주세요.');
        } else if (existingQueueItem && existingQueueItem.status === 'PENDING') {
            // 대기 중인 작업 취소 가능
            await syncQueueManager.removeQueueItem(localMemo.syncQueueId);
        }
    }
    
    // DELETE 항목 추가
    // ...
}
```

**장점**:
- ✅ 의도하지 않은 작업 순서 문제 방지

**단점**:
- ⚠️ 사용자가 동기화 완료를 기다려야 함
- ⚠️ 사용자 경험 저하 가능

### 시나리오 4 해결 방안

#### 방안: 서버 API 멱등성 보장

**원칙**: 서버 API가 멱등성(idempotent)을 보장하도록 구현

**구현**:
- PUT 요청은 일반적으로 멱등성을 보장
- 서버 측에서 동일한 요청을 여러 번 처리해도 결과가 동일해야 함
- 요청 ID나 타임스탬프를 활용하여 중복 요청 감지

**장점**:
- ✅ 중복 수정 문제 해결
- ✅ 네트워크 재차단 시에도 안전

---

## 결론

### 주요 발견 사항

1. **시나리오 6 (중복 표시 문제)**이 가장 중요한 문제입니다.
   - 동기화 대기 중인 메모와 서버 메모가 모두 표시됨
   - 사용자에게 혼란을 줄 수 있음
   - 해결 방안 1 (동기화 대기 중인 메모 우선 표시) 권장

2. **시나리오 2, 5 (작업 순서 문제)**는 최종적으로는 의도대로 동작하지만, 중간에 의도하지 않은 작업이 반영될 수 있습니다.
   - 해결 방안: 동기화 중인 작업 취소 또는 사용자 알림

3. **나머지 시나리오**는 문서 로직상 문제가 없거나, 서버 측 멱등성 보장으로 해결 가능합니다.

### 권장 사항

1. **즉시 해결 필요**: 시나리오 6의 `mergeMemos` 로직 개선
2. **서버 측 보완**: 
   - POST (메모 생성) API의 멱등성 보장 (idempotency-key 헤더 활용 등)
   - DELETE (메모 삭제) API의 멱등성 보장 (이미 삭제된 경우 성공 응답 반환)
   - PUT (메모 수정) API는 현재 멱등성이 보장되므로 추가 보완 불필요
3. **사용자 경험 개선**: 동기화 중인 작업에 대한 명확한 상태 표시

---

## 참고 자료

- [오프라인 메모 작성 및 동기화 설계](./OFFLINE_MEMO_SYNC.md)
- [Offline-First 아키텍처 설계 및 전략](./OFFLINE_FIRST.md)

