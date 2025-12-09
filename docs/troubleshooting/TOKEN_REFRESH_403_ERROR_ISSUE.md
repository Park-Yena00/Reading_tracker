# 토큰 갱신 실패 및 403 Forbidden 오류 해결

> **작성일**: 2025-12-09  
> **문제**: Access Token 만료 시 403 Forbidden 발생, 토큰 갱신 미시도  
> **원인**: 프론트엔드에서 403 에러 시 토큰 갱신 로직 미구현  
> **상태**: ✅ 해결 완료

---

## 문제 진단

### 증상

로그인 후 일정 시간이 지나면 다음 오류가 발생합니다:

**브라우저 F12 콘솔**:
```
GET http://localhost:8080/api/v1/users/me 403 (Forbidden)
[API Client] API 요청 오류: Error: 접근 권한이 없습니다.
[AuthState] 서버 연결 확인 실패: 접근 권한이 없습니다.
```

**서버 로그**:
```
ERROR ... JwtAuthenticationFilter : JWT 토큰 검증 실패: JWT expired at 2025-12-08T05:27:21Z. 
Current time: 2025-12-08T18:19:32Z, a difference of 46331893 milliseconds.
```

### 현재 상황 분석

#### 1. 백엔드: RefreshToken이 Redis에 저장됨 ✅

- `RefreshTokenRedisService`를 사용하여 RefreshToken을 Redis에 저장
- `/api/v1/auth/refresh` 엔드포인트가 정상 작동
- Token Rotation 방식으로 새 Access Token과 Refresh Token 발급

#### 2. 프론트엔드 문제점 ❌

**현재 구현**:
- `api-client.js`에서 **401 에러 시에만** 토큰 갱신 시도
- **403 에러 시 토큰 갱신을 시도하지 않음**
- Access Token이 만료되어 403이 발생하면 토큰 갱신을 하지 않음

**문제 코드 위치**:
- `api-client.js` 137-157줄: 403 에러 처리 시 토큰 갱신 미시도
- `auth-state.js` 49-70줄: `restoreAuthState()`에서 403 에러 시 토큰 갱신 미시도

#### 3. 서버 동작 방식

- JWT 토큰 만료 시 `JwtAuthenticationFilter`에서 예외를 잡아 로그만 출력
- 인증 정보가 없으면 Spring Security가 **403 Forbidden** 반환
- Access Token 만료는 **403**으로 처리됨 (401이 아님)

### 문제 원인

1. **Access Token 만료 시 서버가 403을 반환**
   - JWT 토큰이 만료되면 `JwtAuthenticationFilter`에서 인증 정보를 설정하지 않음
   - Spring Security가 인증되지 않은 요청을 403으로 처리

2. **프론트엔드가 403에서 토큰 갱신을 시도하지 않음**
   - 현재는 401 에러에서만 토큰 갱신 시도
   - 403 에러는 "권한 없음"으로만 처리하고 토큰 갱신을 하지 않음

3. **`restoreAuthState()`에서 403 발생 시 토큰 갱신 미시도**
   - 페이지 로드 시 토큰이 있으면 `/users/me`를 호출하여 인증 상태 복원
   - 403 에러 발생 시 토큰 갱신을 시도하지 않고 바로 로그아웃 처리

---

## 수정 계획

### 1. `api-client.js` 수정

**403 에러 처리 로직 변경**:
- 403 에러 발생 시 Access Token 만료 가능성을 고려하여 토큰 갱신 시도
- 토큰 갱신 성공 시 원래 요청을 재시도
- 토큰 갱신 실패 시 기존 403 에러 처리 로직 실행

**수정 위치**: `api-client.js` 137-157줄

**수정 내용**:
```javascript
// 403 에러 처리 (권한 없음)
if (response.status === 403) {
  // Access Token 만료로 인한 403일 수 있으므로 토큰 갱신 시도
  const refreshed = await this.handleTokenRefresh();
  if (refreshed) {
    // 토큰 갱신 성공 시 원래 요청 재시도
    const newToken = tokenManager.getAccessToken();
    config.headers['Authorization'] = `Bearer ${newToken}`;
    const retryResponse = await fetch(url, config);
    return this.handleResponse(retryResponse, isJson);
  } else {
    // 토큰 갱신 실패 시 기존 403 에러 처리 로직 실행
    // ... 기존 에러 처리 코드
  }
}
```

### 2. `auth-state.js` 수정

**`restoreAuthState()` 메서드 수정**:
- 403 에러 발생 시 토큰 갱신 시도
- 토큰 갱신 성공 시 사용자 정보 재조회
- 토큰 갱신 실패 시 로그아웃 처리

**수정 위치**: `auth-state.js` 49-70줄

**수정 내용**:
```javascript
catch (error) {
  // 403 에러인 경우 토큰 갱신 시도
  if (error.status === 403 || error.statusCode === 403) {
    try {
      // 토큰 갱신 시도
      const refreshed = await apiClient.handleTokenRefresh();
      if (refreshed) {
        // 갱신 성공 시 사용자 정보 재조회
        const userProfile = await userService.getProfile();
        if (userProfile) {
          this.setUser(userProfile);
          return;
        }
      }
    } catch (refreshError) {
      // 토큰 갱신 실패
      console.warn('[AuthState] 토큰 갱신 실패:', refreshError.message);
    }
  }
  
  // 토큰 갱신 실패 또는 다른 에러인 경우 로그아웃 처리
  // ... 기존 에러 처리 로직
}
```

### 3. `api-client.js`의 `handleTokenRefresh()` 메서드 확인

현재 구현이 Redis 기반 RefreshToken과 호환되는지 확인:
- `/auth/refresh` 엔드포인트 호출 방식 확인
- 응답 형식 처리 확인
- 새 토큰 저장 로직 확인

---

## 수정 파일 목록

1. **`분산2_프로젝트_프론트/js/services/api-client.js`**
   - 403 에러 처리 로직에 토큰 갱신 추가

2. **`분산2_프로젝트_프론트/js/state/auth-state.js`**
   - `restoreAuthState()`에서 403 에러 시 토큰 갱신 시도 추가

---

## Redis 기반 RefreshToken 호환성 분석

### 서버 측 구현 (Redis 기반)

#### 1. RefreshToken 저장 방식
- **저장소**: Redis (`RefreshTokenRedisService` 사용)
- **키 구조**: `refresh_token:{token}` → `RefreshTokenData`
- **TTL**: RefreshToken 만료 시간 (7일) 자동 관리
- **Token Rotation**: 새 토큰 발급 시 기존 토큰 무효화 (`revoked=true`)

#### 2. 토큰 갱신 API
- **엔드포인트**: `POST /api/v1/auth/refresh`
- **요청 형식**: `RefreshTokenRequest { refreshToken: string }`
- **응답 형식**: `ApiResponse<RefreshTokenResponse>`
  ```json
  {
    "ok": true,
    "data": {
      "accessToken": "...",
      "refreshToken": "...",
      "tokenType": "Bearer",
      "expiresIn": 3600000
    }
  }
  ```

#### 3. 토큰 갱신 로직
1. Redis에서 RefreshToken 조회
2. 토큰 유효성 검증 (revoked, expired 확인)
3. 기존 RefreshToken 무효화 (revoked=true)
4. 새 AccessToken과 RefreshToken 생성 (Token Rotation)
5. 새 RefreshToken을 Redis에 저장

### 클라이언트 측 구현 분석

#### 1. `api-client.js`의 `handleTokenRefresh()` 메서드

**현재 구현** (194-238줄):
```javascript
async handleTokenRefresh() {
  const refreshToken = tokenManager.getRefreshToken();
  if (!refreshToken) {
    return false;
  }

  try {
    // 토큰 갱신 API 호출 (인증 헤더 없이)
    const baseURL = this.getBaseURL();
    const response = await fetch(`${baseURL}/auth/refresh`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refreshToken }),
    });

    if (!response.ok) {
      return false;
    }

    const responseData = await response.json();
    
    // ApiResponse<T> 형식 처리
    if (responseData.ok && responseData.data) {
      const { accessToken, refreshToken: newRefreshToken } = responseData.data;
      
      // 새 토큰 저장
      tokenManager.setTokens(accessToken, newRefreshToken);
      
      // 토큰 갱신 이벤트 발행
      authState.publishTokenRefreshed({
        accessToken,
        refreshToken: newRefreshToken,
      });
      
      return true;
    }

    return false;
  } catch (error) {
    console.error('토큰 갱신 실패:', error);
    return false;
  }
}
```

#### 2. 호환성 확인 결과

| 항목 | 서버 | 클라이언트 | 호환성 |
|------|------|-----------|--------|
| **엔드포인트** | `POST /api/v1/auth/refresh` | `/auth/refresh` (baseURL 포함) | ✅ 일치 |
| **요청 형식** | `{ refreshToken: string }` | `{ refreshToken }` | ✅ 일치 |
| **응답 형식** | `ApiResponse<RefreshTokenResponse>` | `responseData.ok && responseData.data` | ✅ 일치 |
| **토큰 추출** | `data.accessToken`, `data.refreshToken` | `responseData.data.accessToken`, `responseData.data.refreshToken` | ✅ 일치 |
| **토큰 저장** | - | `tokenManager.setTokens(accessToken, newRefreshToken)` | ✅ 정상 |
| **Token Rotation** | 기존 토큰 무효화 후 새 토큰 발급 | 새 토큰을 받아서 저장 | ✅ 정상 |

### 결론

**✅ 현재 구현은 Redis 기반 RefreshToken과 완전히 호환됩니다.**

1. **엔드포인트 호출**: 정확히 일치
2. **요청/응답 형식**: `ApiResponse<T>` 형식으로 정확히 처리
3. **토큰 저장**: 새 AccessToken과 RefreshToken을 모두 저장
4. **Token Rotation**: 서버에서 기존 토큰을 무효화하고 새 토큰을 발급하는 방식과 호환

### 문제점

**현재 구현의 문제는 Redis 호환성과 무관합니다.**

실제 문제는:
1. **403 에러 시 토큰 갱신 미시도**: Access Token 만료로 403이 발생해도 토큰 갱신을 시도하지 않음
2. **`restoreAuthState()`에서 403 처리 부재**: 페이지 로드 시 403 발생 시 토큰 갱신 미시도

### 수정 필요 사항

Redis 호환성은 문제없으므로, **403 에러 처리 로직만 수정**하면 됩니다:

1. `api-client.js`: 403 에러 처리 시 토큰 갱신 시도 추가
2. `auth-state.js`: `restoreAuthState()`에서 403 에러 시 토큰 갱신 시도 추가

---

## 수정 완료 체크리스트

- [x] `api-client.js` - 403 에러 처리 로직에 토큰 갱신 추가 ✅
- [x] `auth-state.js` - `restoreAuthState()`에서 403 에러 시 토큰 갱신 시도 추가 ✅
- [x] `auth-state.js` - `apiClient` import 추가 ✅
- [x] 컴파일 오류 확인 ✅
- [ ] 테스트 검증 (수동 테스트 필요)

---

## 수정 내용 상세

### 1. `api-client.js` 수정 (137-166줄)

**변경 전**:
- 403 에러 발생 시 바로 에러 처리

**변경 후**:
- 403 에러 발생 시 먼저 토큰 갱신 시도
- 토큰 갱신 성공 시 원래 요청 재시도
- 토큰 갱신 실패 시 기존 403 에러 처리 로직 실행

### 2. `auth-state.js` 수정

**변경 사항**:
- `apiClient` import 추가 (10줄)
- `restoreAuthState()`에서 403 에러 시 토큰 갱신 시도 추가 (54-71줄)
- 토큰 갱신 성공 시 사용자 정보 재조회

**동작 흐름**:
1. `/users/me` 호출 시 403 에러 발생
2. 403 에러 감지 → 토큰 갱신 시도
3. 토큰 갱신 성공 → 사용자 정보 재조회
4. 사용자 정보 설정 완료

---

## 참고 문서

- [AUTH_SERVICE_READ_FAILOVER_ISSUE.md](./AUTH_SERVICE_READ_FAILOVER_ISSUE.md)
- [FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md](../fault-tolerance/FAULT_TOLERANCE_IMPLEMENTATION_ROADMAP.md)

