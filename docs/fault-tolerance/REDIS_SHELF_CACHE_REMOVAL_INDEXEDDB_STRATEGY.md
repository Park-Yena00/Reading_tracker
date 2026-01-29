## 목적

내 서재 조회의 Redis 캐시를 제거하고, 네트워크 장애 허용 목적의 데이터 저장은 클라이언트 IndexedDB로 전환하기 위한 개선안을 정리합니다.

## 배경

- Redis 내 서재 캐시는 TTL(5분)로 인해 최신 데이터가 지연 반영될 수 있습니다.
- 네트워크 장애 허용 목적의 로컬 저장은 서버 캐시보다 클라이언트 저장소(IndexedDB)가 적합합니다.

## Redis에 저장되는 데이터 목록

### 1. 멱등성 키 (IdempotencyKeyService)

- 키 형식: `idempotency:{idempotencyKey}`
- TTL: 24시간
- 저장 데이터:
  - 상태: `PROCESSING` 또는 `COMPLETED`
  - 응답: `ApiResponse<MemoResponse>` (COMPLETED일 때만)
  - 생성 시간: timestamp
- 용도: 중복 요청 방지 (메모 생성 API)

### 2. Refresh Token (RefreshTokenRedisService)

- 키 형식 1: `refresh_token:{token}` → `RefreshTokenData`
- 키 형식 2: `refresh_tokens:user:{userId}:device:{deviceId}` → `Set<token>`
- TTL: RefreshToken 만료 시간 (7일)
- 저장 데이터:
  - userId: 사용자 ID
  - deviceId: 디바이스 ID
  - token: RefreshToken 문자열
  - expiresAt: 만료 시간
  - revoked: 무효화 여부
  - createdAt: 생성 시간
- 용도: JWT Refresh Token 관리

### 3. 내 서재 캐시 (BookService)

- 키 형식: `myShelf:{userId}:category:{category}:sort:{sortBy}`
- TTL: 5분
- 저장 데이터: `List<UserShelfBookCacheDTO>`
- 용도: 내 서재 조회 성능 최적화 (현재 문제의 원인)

### 4. 태그 캐시 (TagService)

- 키 형식 1: `tags:all` → 모든 태그
- 키 형식 2: `tags:category:{category}` → 카테고리별 태그 (type, topic)
- TTL: 7일
- 저장 데이터: `List<Tag>`
- 용도: 태그 조회 성능 최적화

### 요약

Redis에 저장되는 데이터는 총 4가지입니다:

- 멱등성 키 (24시간 TTL)
- Refresh Token (7일 TTL)
- 내 서재 캐시 (5분 TTL) ← 현재 문제의 원인
- 태그 캐시 (7일 TTL)

## 개선 목표

- 내 서재 조회 결과의 최신성 확보 (Redis 캐시 제거)
- 오프라인/네트워크 장애 시에도 내 서재 조회 가능 (IndexedDB 활용)
- 서버 부하 및 캐시 무효화 복잡도 감소

## 변경 대상 (백엔드)

### `BookService.getMyShelf()`

- Redis 캐시 조회/저장 로직 제거
- DB 조회 결과를 직접 반환

### `BookService` 캐시 관련 요소 제거

- `CACHE_KEY_PREFIX`, `TTL_MINUTES`
- `buildCacheKey()`
- `invalidateMyShelfCache()`
- `redisTemplate` 필드 (내 서재 캐시 용도)

### 캐시 무효화 호출 제거

- `addBookToShelf()`
- `updateBookDetail()` (두 메서드 모두)
- `finishReading()`
- `updateBookCategory()`

## 변경 대상 (프론트엔드)

### `book-service.js`

- 온라인: 서버 응답을 IndexedDB에 비동기 캐시 저장
- 오프라인: IndexedDB에서 내 서재 조회

현재 구현이 이미 위 전략을 충족하고 있으므로, 추가 변경은 최소화합니다.

## 적용 시 기대 효과

- 내 서재 카테고리 변경 직후 데이터 일관성 개선
- Redis TTL에 의한 stale 데이터 문제 제거
- 네트워크 장애 시 IndexedDB 기반의 안정적 조회 보장

## 주의 사항

- Redis를 완전히 제거하는 것이 아니라, **내 서재 캐시 용도만 제거**합니다.
- 다른 Redis 사용처(멱등성 키, Refresh Token, 태그 캐시)는 유지합니다.

## 검증 포인트

- 카테고리 변경 직후 내 서재 조회 결과의 즉시 반영 여부
- 오프라인 상태에서 IndexedDB 조회 정상 동작 여부
- Redis 관련 로그/키에서 `my_shelf` 패턴이 더 이상 생성되지 않는지 확인

## 결과 및 해결

- **내 서재 최신 조회 지연 해소**: 기존 **5분 이상** 걸리던 최신 조회 지연이 제거되어 즉시 반영됨.
- **중복 표시 문제 해결**: DB-Redis 정합성 불일치로 동일 도서가 여러 카테고리에 중복 노출되던 문제가 해소됨.
- **카테고리 변경 즉시 반영**: Redis TTL(5분)로 인한 지연 문제가 제거되어 변경 사항이 즉시 반영됨.

추가적인 수치 기반 개선 사항은 현재 별도 측정되지 않았습니다.
