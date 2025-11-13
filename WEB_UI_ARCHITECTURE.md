# 웹 UI 아키텍처 문서

## 개요

- **클라이언트 타입**: 웹 애플리케이션 (Web Application)
- **위치**: `src/main/java/com/readingtracker/client/web`
- **목적**: 독서 기록 사이트의 웹 클라이언트 구현
- **서버 통신**: Spring Boot REST API (`/api/v1`)

## 웹과 앱의 구분

### 패키지 구조
```
com.readingtracker.client
├── web          # 웹 클라이언트 (이 문서)
└── app          # 모바일 앱 클라이언트 (별도 문서)
```

### 공통점
- **동일한 API 사용**: 웹과 앱 모두 동일한 REST API 엔드포인트 사용
- **동일한 DTO 형식**: 서버에서 제공하는 RequestDTO와 ResponseDTO를 동일하게 사용
- **동일한 인증 방식**: JWT 기반 인증 (Access Token + Refresh Token)

### 차이점
- **플랫폼 특성**: 웹은 브라우저 환경, 앱은 네이티브 모바일 환경
- **UI/UX 패턴**: 각 플랫폼의 디자인 가이드라인에 맞춘 UI 구현
- **데이터 포맷팅**: 각 클라이언트에서 플랫폼에 맞게 날짜, 시간 등 포맷팅 처리
- **상태 관리**: 웹은 브라우저 스토리지, 앱은 모바일 스토리지 활용

## 기술 스택

### 프론트엔드 기술
- **HTML**: 정적 레이아웃 및 구조 정의
- **CSS**: 스타일링 및 레이아웃 디자인
- **JavaScript (ES6+)**: 순수 JavaScript로 UI 로직 구현
  - 모듈 시스템 (ES Modules) 사용
  - 최신 JavaScript 문법 활용

### 서버 연동
- **Spring MVC**: 서버 측 애플리케이션 계층
- **RESTful API**: Spring MVC의 `@RestController`를 통한 JSON 기반 API 제공
- **AJAX (비동기 통신)**: JavaScript의 Fetch API를 사용한 서버 통신

### 개발 도구
- **ESLint**: JavaScript 코드 품질 관리
- **Prettier**: 코드 포맷팅
- **Live Server** 또는 **간단한 HTTP 서버**: 개발 환경에서 정적 파일 서빙

## 웹 클라이언트 아키텍처 (JavaScript)

순수 JavaScript로 UI를 구현하더라도, 코드의 복잡성을 관리하기 위해 최소한의 구조를 갖추는 것이 중요합니다.

### JavaScript 모듈 분리

#### 1. View Layer (DOM 조작)
- **역할**: HTML 요소 조작, 이벤트 리스너 등록 등 UI 업데이트를 담당
- **책임**:
  - DOM 요소 선택 및 조작
  - 이벤트 리스너 등록 및 처리
  - UI 상태 업데이트 (로딩, 에러, 성공 상태 표시)
  - 사용자 입력 수집 및 검증

#### 2. Service Layer (Data Fetching)
- **역할**: 서버의 REST API 엔드포인트에 요청(Fetch API)을 보내고 응답(JSON)을 받아오는 역할만 전담
- **책임**:
  - HTTP 요청 생성 및 전송
  - 응답 데이터 파싱 (JSON)
  - 에러 처리 및 HTTP 상태 코드 확인
  - 인증 토큰 관리 (요청 헤더에 추가)

#### 3. State Management
- **역할**: 클라이언트 상태(현재 페이지의 데이터 등)를 관리하는 간단한 객체를 정의하여 View와 Service 간의 데이터 흐름을 조정
- **책임**:
  - 애플리케이션 상태 저장 및 관리
  - 상태 변경 알림 (Observer 패턴 또는 이벤트 기반)
  - 로컬 스토리지와의 동기화

## 디렉토리 구조

### 제안 구조 (HTML + CSS + JavaScript)

```
client/web/
├── index.html                  # 메인 HTML 파일
├── pages/                      # 페이지별 HTML 파일
│   ├── home.html
│   ├── login.html
│   ├── register.html
│   ├── book-search.html
│   ├── book-detail.html
│   ├── bookshelf.html
│   └── profile.html
│
├── css/                        # 스타일시트
│   ├── common/                # 공통 스타일
│   │   ├── reset.css
│   │   ├── variables.css      # CSS 변수 (색상, 폰트 등)
│   │   └── layout.css         # 레이아웃 관련
│   ├── components/            # 컴포넌트별 스타일
│   │   ├── button.css
│   │   ├── input.css
│   │   ├── modal.css
│   │   └── card.css
│   └── pages/                 # 페이지별 스타일
│       ├── login.css
│       ├── book-search.css
│       └── bookshelf.css
│
├── js/
│   ├── services/              # Service Layer (Data Fetching)
│   │   ├── api-client.js     # Fetch API 래퍼 (인증 토큰 자동 추가)
│   │   ├── auth-service.js   # 인증 관련 API 호출
│   │   ├── book-service.js   # 도서 관련 API 호출
│   │   └── user-service.js    # 사용자 관련 API 호출
│   │
│   ├── views/                # View Layer (DOM 조작)
│   │   ├── common/           # 공통 뷰 컴포넌트
│   │   │   ├── header.js
│   │   │   ├── footer.js
│   │   │   ├── modal.js
│   │   │   └── loading.js
│   │   └── pages/            # 페이지별 뷰
│   │       ├── login-view.js
│   │       ├── book-search-view.js
│   │       ├── bookshelf-view.js
│   │       └── profile-view.js
│   │
│   ├── state/                # State Management
│   │   ├── auth-state.js     # 인증 상태 관리
│   │   ├── app-state.js      # 애플리케이션 전역 상태
│   │   └── book-state.js     # 도서 관련 상태
│   │
│   ├── utils/                # 유틸리티 함수
│   │   ├── token-manager.js  # JWT 토큰 관리
│   │   ├── date-formatter.js # 날짜/시간 포맷팅
│   │   ├── validators.js     # 입력 검증
│   │   └── router.js         # 간단한 라우팅 (SPA 방식)
│   │
│   ├── constants/            # 상수 정의
│   │   ├── api-endpoints.js  # API 엔드포인트 URL
│   │   └── routes.js         # 라우트 경로
│   │
│   └── main.js               # 애플리케이션 진입점
│
├── assets/                    # 정적 자원
│   ├── images/
│   ├── icons/
│   └── fonts/
│
└── README.md
```

## Spring MVC와의 연동 방식

### RESTful API 연동 (권장)

UI와 서버의 연동을 위해 **비동기 통신(AJAX)**을 사용하는 방식입니다.

#### 서버 측 (Spring MVC)

**Spring Controller**: `@RestController`를 사용하여 JSON 응답을 반환하는 REST API 엔드포인트를 정의합니다.

```java
@RestController
@RequestMapping("/api/v1")
public class BookController {
    
    @GetMapping("/books/search")
    public ApiResponse<BookSearchResponse> searchBooks(
            @RequestParam String query) {
        // 비즈니스 로직 처리
        BookSearchResponse response = bookService.searchBooks(query);
        return ApiResponse.success(response);
    }
}
```

#### 클라이언트 측 (JavaScript)

**JavaScript의 Fetch API**를 사용하여 이 REST API에 비동기적으로 요청을 보내고, 받은 JSON 데이터를 파싱하여 UI를 동적으로 업데이트합니다.

```javascript
// services/book-service.js
export const bookService = {
    async searchBooks(query) {
        const response = await fetch('/api/v1/books/search?query=' + encodeURIComponent(query), {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${tokenManager.getAccessToken()}`
            }
        });
        
        if (!response.ok) {
            throw new Error('도서 검색 실패');
        }
        
        const apiResponse = await response.json();
        return apiResponse.data; // BookSearchResponse 반환
    }
};
```

#### 장점

- **서버와 클라이언트 완전 분리**: 서버는 UI 렌더링에 관여하지 않고 순수하게 데이터만 제공
- **유지보수 용이**: 웹 UI 코드와 서버 코드가 완전히 분리되어 유지보수가 용이
- **재사용성**: 추후 앱 연동에도 동일한 API를 활용할 수 있음
- **확장성**: 다양한 클라이언트(웹, 앱, 데스크톱)에서 동일한 API 사용 가능

## 아키텍처 계층 구조

### 데이터 흐름

```
View Layer (views/)
  ↓ (사용자 입력 수집, UI 업데이트)
State Management (state/)
  ↓ (상태 변경 요청)
Service Layer (services/)
  ↓ (HTTP 요청)
Spring MVC REST API (/api/v1)
  ↓ (JSON 응답)
Service Layer
  ↓ (데이터 파싱)
State Management
  ↓ (상태 업데이트)
View Layer
  ↓ (UI 렌더링)
```

### 계층별 책임

1. **View Layer (views/)**
   - DOM 요소 선택 및 조작
   - 이벤트 리스너 등록 및 처리
   - UI 상태 표시 (로딩, 에러, 성공)
   - 사용자 입력 수집 및 검증

2. **State Management (state/)**
   - 애플리케이션 상태 저장 및 관리
   - 상태 변경 알림 (Observer 패턴)
   - View와 Service 간 데이터 흐름 조정

3. **Service Layer (services/)**
   - HTTP 요청 생성 및 전송 (Fetch API)
   - 응답 데이터 파싱 (JSON)
   - 에러 처리 및 HTTP 상태 코드 확인
   - 인증 토큰 관리

### 모듈 설계 원칙

1. **단일 책임 원칙 (SRP)**
   - 각 모듈은 하나의 명확한 역할만 담당
   - 예: `book-service.js`는 도서 관련 API 호출만 담당

2. **모듈 분리**
   - View: DOM 조작 및 UI 업데이트만 담당
   - Service: 데이터 페칭만 담당
   - State: 상태 관리만 담당

3. **명명 규칙**
   - 파일명: kebab-case (예: `book-service.js`, `login-view.js`)
   - 클래스/함수명: camelCase (예: `renderBookList`, `handleLogin`)
   - 상수명: UPPER_SNAKE_CASE (예: `API_BASE_URL`)

## 상태 관리

### 상태 관리 패턴

순수 JavaScript 환경에서 상태 관리를 위해 **Observer 패턴** 또는 **이벤트 기반** 방식을 사용합니다.

### 인증 상태 관리 예시

```javascript
// state/auth-state.js
class AuthState {
    constructor() {
        this.user = null;
        this.isAuthenticated = false;
        this.listeners = []; // Observer 패턴을 위한 리스너 배열
    }

    // 상태 변경 구독
    subscribe(listener) {
        this.listeners.push(listener);
        return () => {
            this.listeners = this.listeners.filter(l => l !== listener);
        };
    }

    // 상태 변경 알림
    notify() {
        this.listeners.forEach(listener => listener(this.user, this.isAuthenticated));
    }

    // 사용자 설정
    setUser(user) {
        this.user = user;
        this.isAuthenticated = !!user;
        this.notify();
    }

    // 로그아웃
    logout() {
        this.user = null;
        this.isAuthenticated = false;
        this.notify();
    }

    // 현재 상태 반환
    getState() {
        return {
            user: this.user,
            isAuthenticated: this.isAuthenticated
        };
    }
}

// 싱글톤 인스턴스
export const authState = new AuthState();
```

### 애플리케이션 상태 관리 예시

```javascript
// state/app-state.js
class AppState {
    constructor() {
        this.state = {
            loading: false,
            error: null,
            currentPage: 'home'
        };
        this.listeners = [];
    }

    subscribe(listener) {
        this.listeners.push(listener);
        return () => {
            this.listeners = this.listeners.filter(l => l !== listener);
        };
    }

    notify() {
        this.listeners.forEach(listener => listener(this.state));
    }

    setState(updates) {
        this.state = { ...this.state, ...updates };
        this.notify();
    }

    getState() {
        return { ...this.state };
    }
}

export const appState = new AppState();
```

### 로컬 스토리지 활용

```javascript
// utils/storage.js
export const storage = {
    // JWT 토큰 저장
    setToken(key, token) {
        localStorage.setItem(key, token);
    },

    getToken(key) {
        return localStorage.getItem(key);
    },

    removeToken(key) {
        localStorage.removeItem(key);
    },

    // 사용자 설정 저장
    setUserPreferences(preferences) {
        localStorage.setItem('userPreferences', JSON.stringify(preferences));
    },

    getUserPreferences() {
        const data = localStorage.getItem('userPreferences');
        return data ? JSON.parse(data) : null;
    },

    // 세션 데이터 (페이지 새로고침 시 유지되지 않음)
    setSessionData(key, value) {
        sessionStorage.setItem(key, JSON.stringify(value));
    },

    getSessionData(key) {
        const data = sessionStorage.getItem(key);
        return data ? JSON.parse(data) : null;
    }
};
```

### 상태 사용 예시

```javascript
// views/login-view.js
import { authState } from '../state/auth-state.js';
import { authService } from '../services/auth-service.js';

class LoginView {
    constructor() {
        this.form = document.getElementById('login-form');
        this.init();
    }

    init() {
        // 폼 제출 이벤트 리스너
        this.form.addEventListener('submit', async (e) => {
            e.preventDefault();
            await this.handleLogin();
        });

        // 인증 상태 변경 구독
        authState.subscribe((user, isAuthenticated) => {
            if (isAuthenticated) {
                this.handleLoginSuccess(user);
            }
        });
    }

    async handleLogin() {
        const formData = new FormData(this.form);
        const loginData = {
            loginId: formData.get('loginId'),
            password: formData.get('password')
        };

        try {
            const response = await authService.login(loginData);
            authState.setUser(response.user);
        } catch (error) {
            this.showError(error.message);
        }
    }

    handleLoginSuccess(user) {
        // 로그인 성공 시 페이지 이동
        window.location.href = '/bookshelf.html';
    }

    showError(message) {
        // 에러 메시지 표시
        const errorElement = document.getElementById('error-message');
        errorElement.textContent = message;
        errorElement.style.display = 'block';
    }
}

export default LoginView;
```

## API 통신

### 인증 토큰 자동 포함

모든 API 요청에 JWT 토큰을 자동으로 포함하기 위해 공통 API 클라이언트를 사용합니다. 이 클라이언트는 `localStorage`에서 토큰을 가져와 `Authorization` 헤더에 자동으로 추가합니다.

### HTTP 클라이언트 설정 (Fetch API 래퍼)

**API 클라이언트 설정**:

모든 API 요청은 이 클라이언트를 통해 이루어지며, JWT 토큰이 자동으로 `Authorization` 헤더에 포함됩니다.

```javascript
// services/api-client.js
import { tokenManager } from '../utils/token-manager.js';
import { authState } from '../state/auth-state.js';

const API_BASE_URL = 'http://localhost:8080/api/v1';

class ApiClient {
    constructor(baseURL) {
        this.baseURL = baseURL;
    }

    // 공통 요청 메서드
    async request(endpoint, options = {}) {
        const url = `${this.baseURL}${endpoint}`;
        // localStorage에서 Access Token 가져오기
        const token = tokenManager.getAccessToken();

        const config = {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                // JWT 토큰을 Authorization 헤더에 Bearer 형식으로 포함
                // 형식: "Bearer {token}"
                ...(token && { 'Authorization': `Bearer ${token}` }),
                ...options.headers
            }
        };

        // 요청 본문이 있으면 JSON으로 변환
        if (config.body && typeof config.body === 'object') {
            config.body = JSON.stringify(config.body);
        }

        try {
            const response = await fetch(url, config);

            // 401 에러 처리 (토큰 갱신 시도)
            if (response.status === 401) {
                const refreshed = await tokenManager.refreshToken();
                if (refreshed) {
                    // 토큰 갱신 후 재시도
                    config.headers['Authorization'] = `Bearer ${tokenManager.getAccessToken()}`;
                    return fetch(url, config).then(this.handleResponse.bind(this));
                } else {
                    // 토큰 갱신 실패 시 로그아웃
                    authState.logout();
                    throw new Error('인증이 만료되었습니다. 다시 로그인해주세요.');
                }
            }

            return this.handleResponse(response);
        } catch (error) {
            throw new Error(`API 요청 실패: ${error.message}`);
        }
    }

    // 응답 처리
    async handleResponse(response) {
        const data = await response.json();

        if (!response.ok) {
            const error = data.error || { message: '알 수 없는 오류가 발생했습니다.' };
            throw new Error(error.message || '요청 처리 중 오류가 발생했습니다.');
        }

        return data;
    }

    // HTTP 메서드 래퍼
    async get(endpoint, options = {}) {
        return this.request(endpoint, { ...options, method: 'GET' });
    }

    async post(endpoint, body, options = {}) {
        return this.request(endpoint, { ...options, method: 'POST', body });
    }

    async put(endpoint, body, options = {}) {
        return this.request(endpoint, { ...options, method: 'PUT', body });
    }

    async delete(endpoint, options = {}) {
        return this.request(endpoint, { ...options, method: 'DELETE' });
    }
}

export const apiClient = new ApiClient(API_BASE_URL);
```

### API 서비스 함수 구조

```javascript
// services/auth-service.js
import { apiClient } from './api-client.js';

export const authService = {
    // 로그인
    async login(loginData) {
        const response = await apiClient.post('/auth/login', loginData);
        return response.data; // LoginResponse 반환
    },

    // 회원가입
    async register(registerData) {
        const response = await apiClient.post('/auth/signup', registerData);
        return response.data; // RegisterResponse 반환
    },

    // 로그아웃
    async logout() {
        // 서버에 로그아웃 요청 (선택사항)
        // 클라이언트에서는 토큰만 삭제하면 됨
    },

    // 토큰 갱신
    async refreshToken(refreshToken) {
        const response = await apiClient.post('/auth/refresh', { refreshToken });
        return response.data;
    }
};
```

```javascript
// services/book-service.js
import { apiClient } from './api-client.js';

export const bookService = {
    // 도서 검색
    async searchBooks(query, queryType = 'TITLE', start = 1, maxResults = 10) {
        const params = new URLSearchParams({
            query,
            queryType,
            start: start.toString(),
            maxResults: maxResults.toString()
        });
        const response = await apiClient.get(`/books/search?${params}`);
        return response.data; // BookSearchResponse 반환
    },

    // 도서 상세 정보 조회
    async getBookDetail(isbn) {
        const response = await apiClient.get(`/books/${isbn}`);
        return response.data;
    },

    // 서재에 책 추가
    async addBookToShelf(bookData) {
        const response = await apiClient.post('/user/books', bookData);
        return response.data;
    },

    // 서재에서 책 삭제
    async removeBookFromShelf(userBookId) {
        const response = await apiClient.delete(`/user/books/${userBookId}`);
        return response.data;
    }
};
```

### API 응답 구조

서버의 `ApiResponse<T>` 구조와 일치:

```javascript
// 서버 응답 예시
{
  "ok": true,
  "data": {
    // 실제 데이터 (LoginResponse, BookSearchResponse 등)
  },
  "error": null
}

// 에러 응답 예시
{
  "ok": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "입력값이 올바르지 않습니다.",
    "fieldErrors": [
      {
        "field": "email",
        "message": "올바른 이메일 형식이 아닙니다."
      }
    ]
  }
}
```

## 인증/인가 처리

### JWT 기반 인증 방식

웹과 앱 모두 동일한 JWT 기반 인증 방식을 사용합니다. 웹에서의 인증 흐름은 다음과 같습니다:

#### 1. 인증 요청 (로그인)

사용자가 로그인하면, JavaScript는 서버의 로그인 API에 사용자 정보(로그인 ID, 비밀번호)를 POST 요청으로 전송하고, 응답으로 JWT 토큰(Access Token, Refresh Token)을 받습니다.

```javascript
// 사용자 로그인 예시
const loginData = {
    loginId: 'user123',
    password: 'password123'
};

// 서버에 POST 요청
const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json'
    },
    body: JSON.stringify(loginData)
});

const apiResponse = await response.json();
// 응답: { ok: true, data: { accessToken: "...", refreshToken: "...", user: {...} } }
```

#### 2. 토큰 저장

받은 JWT 토큰은 JavaScript 내에서 안전하게 저장되어야 합니다. 일반적으로 `localStorage` 또는 `sessionStorage`를 사용합니다.

- **localStorage**: 브라우저를 닫아도 토큰이 유지됨 (자동 로그인 기능)
- **sessionStorage**: 브라우저 탭을 닫으면 토큰이 삭제됨 (세션 기반)

```javascript
// 토큰 저장 예시
const { accessToken, refreshToken } = apiResponse.data;

// localStorage에 저장 (영구 저장)
localStorage.setItem('accessToken', accessToken);
localStorage.setItem('refreshToken', refreshToken);

// 또는 sessionStorage에 저장 (세션 종료 시 삭제)
sessionStorage.setItem('accessToken', accessToken);
sessionStorage.setItem('refreshToken', refreshToken);
```

#### 3. API 요청 시 토큰 포함

이후 모든 데이터 요청(API 호출) 시, JavaScript는 HTTP 헤더의 `Authorization` 필드에 이 JWT 토큰을 포함하여 서버에 전송해야 합니다.

```javascript
// API 요청 시 토큰 포함 예시
const accessToken = localStorage.getItem('accessToken');

const response = await fetch('/api/v1/user/books', {
    method: 'GET',
    headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${accessToken}`  // JWT 토큰을 Authorization 헤더에 포함
    }
});
```

서버는 `Authorization` 헤더에서 토큰을 추출하여 검증하고, 유효한 경우 요청을 처리합니다.

#### 인증 흐름 요약

```
1. 사용자 로그인
   ↓
2. JavaScript가 서버에 POST 요청 (/api/v1/auth/login)
   ↓
3. 서버가 JWT 토큰 반환 (accessToken, refreshToken)
   ↓
4. 토큰을 localStorage/sessionStorage에 저장
   ↓
5. 이후 모든 API 요청 시:
   - localStorage에서 토큰 읽기
   - Authorization 헤더에 "Bearer {token}" 형식으로 포함
   - 서버가 토큰 검증 후 요청 처리
```

### 토큰 관리

```javascript
// utils/token-manager.js
import { authService } from '../services/auth-service.js';
import { authState } from '../state/auth-state.js';

class TokenManager {
    constructor() {
        this.ACCESS_TOKEN_KEY = 'accessToken';
        this.REFRESH_TOKEN_KEY = 'refreshToken';
    }

    getAccessToken() {
        return localStorage.getItem(this.ACCESS_TOKEN_KEY);
    }

    getRefreshToken() {
        return localStorage.getItem(this.REFRESH_TOKEN_KEY);
    }

    setTokens(accessToken, refreshToken) {
        localStorage.setItem(this.ACCESS_TOKEN_KEY, accessToken);
        localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
    }

    clearTokens() {
        localStorage.removeItem(this.ACCESS_TOKEN_KEY);
        localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    }

    async refreshToken() {
        const refreshToken = this.getRefreshToken();
        if (!refreshToken) return false;

        try {
            const response = await authService.refreshToken(refreshToken);
            this.setTokens(response.accessToken, response.refreshToken);
            return true;
        } catch (error) {
            this.clearTokens();
            authState.logout();
            return false;
        }
    }

    isTokenExpired(token) {
        if (!token) return true;
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            const exp = payload.exp * 1000; // 초를 밀리초로 변환
            return Date.now() >= exp;
        } catch (error) {
            return true;
        }
    }
}

export const tokenManager = new TokenManager();
```

### 인증 헬퍼 함수

```javascript
// utils/auth-helper.js
import { authState } from '../state/auth-state.js';
import { tokenManager } from './token-manager.js';

export const authHelper = {
    // 인증 상태 확인
    isAuthenticated() {
        return authState.isAuthenticated && !!tokenManager.getAccessToken();
    },

    // 보호된 페이지 접근 확인
    checkAuth() {
        if (!this.isAuthenticated()) {
            window.location.href = '/login.html';
            return false;
        }
        return true;
    },

    // 로그인 처리
    async handleLogin(loginData) {
        try {
            const response = await authService.login(loginData);
            tokenManager.setTokens(response.accessToken, response.refreshToken);
            authState.setUser(response.user);
            return { success: true, user: response.user };
        } catch (error) {
            return { success: false, error: error.message };
        }
    },

    // 로그아웃 처리
    handleLogout() {
        tokenManager.clearTokens();
        authState.logout();
        window.location.href = '/login.html';
    }
};
```

### 보호된 페이지 처리

```javascript
// 각 보호된 페이지에서 사용
// 예: bookshelf.html의 스크립트
import { authHelper } from './utils/auth-helper.js';

// 페이지 로드 시 인증 확인
document.addEventListener('DOMContentLoaded', () => {
    if (!authHelper.checkAuth()) {
        return; // 인증되지 않으면 로그인 페이지로 리다이렉트됨
    }
    
    // 인증된 사용자만 접근 가능한 로직 실행
    initBookshelf();
});
```

## 라우팅

### 페이지 기반 라우팅 (MPA 방식)

순수 HTML/CSS/JavaScript 환경에서는 **Multi-Page Application (MPA)** 방식을 사용합니다.

#### 페이지 구조

```
client/web/
├── index.html              # 홈 페이지 (/)
├── pages/
│   ├── login.html          # 로그인 페이지 (/login.html)
│   ├── register.html       # 회원가입 페이지 (/register.html)
│   ├── book-search.html    # 도서 검색 페이지 (/book-search.html)
│   ├── book-detail.html    # 도서 상세 페이지 (/book-detail.html)
│   ├── bookshelf.html      # 서재 페이지 (/bookshelf.html) - 보호됨
│   └── profile.html        # 프로필 페이지 (/profile.html) - 보호됨
```

#### 페이지 간 이동

```javascript
// 일반적인 페이지 이동
window.location.href = '/book-search.html';

// 쿼리 파라미터와 함께 이동
window.location.href = `/book-detail.html?isbn=${isbn}`;

// 뒤로 가기
window.history.back();
```

### 간단한 SPA 라우팅 (선택사항)

필요한 경우 간단한 SPA 라우팅을 구현할 수 있습니다.

```javascript
// utils/router.js
class Router {
    constructor() {
        this.routes = {};
        this.currentRoute = null;
        this.init();
    }

    init() {
        // 해시 변경 이벤트 리스너
        window.addEventListener('hashchange', () => this.handleRoute());
        // 초기 라우트 처리
        this.handleRoute();
    }

    // 라우트 등록
    register(path, handler) {
        this.routes[path] = handler;
    }

    // 라우트 처리
    handleRoute() {
        const hash = window.location.hash.slice(1) || '/';
        const route = this.routes[hash];
        
        if (route) {
            route();
            this.currentRoute = hash;
        } else {
            // 404 처리
            this.handle404();
        }
    }

    // 라우트 이동
    navigate(path) {
        window.location.hash = path;
    }

    // 404 처리
    handle404() {
        console.error('Route not found');
        // 404 페이지로 이동하거나 기본 페이지로 리다이렉트
    }
}

export const router = new Router();

// 사용 예시
router.register('/', () => {
    // 홈 페이지 렌더링
    document.getElementById('app').innerHTML = '<h1>홈</h1>';
});

router.register('/books/search', () => {
    // 도서 검색 페이지 렌더링
    import('./views/book-search-view.js').then(module => {
        new module.default();
    });
});
```

## 데이터 포맷팅

### 날짜/시간 포맷팅

**원칙**: 서버는 ISO 8601 형식으로 데이터 제공, 클라이언트에서 포맷팅

```javascript
// utils/date-formatter.js
export const dateFormatter = {
    // 날짜만 포맷팅 (예: 2024년 1월 15일)
    formatDate(dateString, locale = 'ko-KR') {
        const date = new Date(dateString);
        return new Intl.DateTimeFormat(locale, {
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        }).format(date);
    },

    // 날짜와 시간 포맷팅 (예: 2024년 1월 15일 오후 2시 30분)
    formatDateTime(dateString, locale = 'ko-KR') {
        const date = new Date(dateString);
        return new Intl.DateTimeFormat(locale, {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        }).format(date);
    },

    // 상대 시간 포맷팅 (예: 2시간 전, 3일 전)
    formatRelativeTime(dateString) {
        const date = new Date(dateString);
        const now = new Date();
        const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

        if (diffInSeconds < 60) return '방금 전';
        if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}분 전`;
        if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}시간 전`;
        if (diffInSeconds < 604800) return `${Math.floor(diffInSeconds / 86400)}일 전`;
        
        return this.formatDate(dateString);
    },

    // 커스텀 포맷팅
    formatCustom(dateString, format) {
        const date = new Date(dateString);
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hour = String(date.getHours()).padStart(2, '0');
        const minute = String(date.getMinutes()).padStart(2, '0');

        return format
            .replace('YYYY', year)
            .replace('MM', month)
            .replace('DD', day)
            .replace('HH', hour)
            .replace('mm', minute);
    }
};
```

### 숫자 포맷팅

```javascript
// utils/number-formatter.js
export const numberFormatter = {
    // 천 단위 구분자 추가 (예: 1,000)
    formatNumber(number) {
        return new Intl.NumberFormat('ko-KR').format(number);
    },

    // 통화 포맷팅 (예: ₩1,000)
    formatCurrency(amount) {
        return new Intl.NumberFormat('ko-KR', {
            style: 'currency',
            currency: 'KRW'
        }).format(amount);
    }
};
```

## 스타일링

### CSS 구조

순수 CSS를 사용하여 스타일링합니다. CSS 변수(Custom Properties)를 활용하여 일관된 디자인 시스템을 구축합니다.

#### CSS 변수 정의

```css
/* css/common/variables.css */
:root {
    /* 색상 */
    --color-primary: #3B82F6;
    --color-primary-dark: #2563EB;
    --color-primary-light: #60A5FA;
    --color-secondary: #6B7280;
    --color-danger: #EF4444;
    --color-success: #10B981;
    
    /* 텍스트 */
    --color-text-primary: #111827;
    --color-text-secondary: #6B7280;
    --color-text-muted: #9CA3AF;
    
    /* 배경 */
    --color-bg-primary: #FFFFFF;
    --color-bg-secondary: #F9FAFB;
    --color-bg-muted: #F3F4F6;
    
    /* 간격 */
    --spacing-xs: 0.25rem;
    --spacing-sm: 0.5rem;
    --spacing-md: 1rem;
    --spacing-lg: 1.5rem;
    --spacing-xl: 2rem;
    
    /* 폰트 */
    --font-family-base: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    --font-size-sm: 0.875rem;
    --font-size-base: 1rem;
    --font-size-lg: 1.125rem;
    --font-size-xl: 1.25rem;
    
    /* 테두리 */
    --border-radius-sm: 0.25rem;
    --border-radius-md: 0.5rem;
    --border-radius-lg: 0.75rem;
}
```

#### 컴포넌트 스타일 예시

```css
/* css/components/button.css */
.btn {
    padding: var(--spacing-sm) var(--spacing-md);
    border-radius: var(--border-radius-md);
    font-weight: 500;
    font-size: var(--font-size-base);
    cursor: pointer;
    transition: background-color 0.2s, color 0.2s;
    border: none;
}

.btn-primary {
    background-color: var(--color-primary);
    color: white;
}

.btn-primary:hover {
    background-color: var(--color-primary-dark);
}

.btn-secondary {
    background-color: var(--color-bg-muted);
    color: var(--color-text-primary);
}

.btn-secondary:hover {
    background-color: var(--color-bg-secondary);
}

.btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}
```

#### HTML에서 사용

```html
<!-- HTML 파일 -->
<link rel="stylesheet" href="/css/common/variables.css">
<link rel="stylesheet" href="/css/components/button.css">

<button class="btn btn-primary" onclick="handleClick()">클릭</button>
```

### CSS 모듈화

- **공통 스타일**: `css/common/` - 변수, 리셋, 레이아웃
- **컴포넌트 스타일**: `css/components/` - 버튼, 입력, 카드 등
- **페이지 스타일**: `css/pages/` - 페이지별 특화 스타일

## 환경 변수 및 설정

### 설정 파일 방식

순수 JavaScript 환경에서는 별도의 설정 파일을 사용합니다.

```javascript
// js/config/config.js
const config = {
    development: {
        API_BASE_URL: 'http://localhost:8080/api/v1',
        APP_NAME: 'Reading Tracker (Dev)'
    },
    production: {
        API_BASE_URL: 'https://api.readingtracker.com/api/v1',
        APP_NAME: 'Reading Tracker'
    }
};

// 환경 감지 (간단한 방법)
const isDevelopment = window.location.hostname === 'localhost' || 
                      window.location.hostname === '127.0.0.1';

const currentConfig = isDevelopment ? config.development : config.production;

export default currentConfig;
```

### 설정 사용

```javascript
// services/api-client.js
import config from '../config/config.js';

const API_BASE_URL = config.API_BASE_URL;
```

## 빌드 및 배포

### 개발 환경

순수 HTML/CSS/JavaScript는 별도의 빌드 과정 없이 바로 실행 가능합니다.

#### 로컬 개발 서버 실행

```bash
# Python 3 사용
python -m http.server 8000

# Node.js http-server 사용
npx http-server -p 8000

# Live Server (VS Code 확장) 사용
# VS Code에서 "Go Live" 클릭
```

#### 브라우저에서 직접 열기

```bash
# 파일 시스템에서 직접 열기 (CORS 이슈 주의)
# file:// 프로토콜로 열면 Fetch API가 제대로 작동하지 않을 수 있음
# 반드시 HTTP 서버를 통해 실행해야 함
```

### 프로덕션 배포

#### 정적 파일 배포

1. **Nginx 배포**
   ```nginx
   server {
       listen 80;
       server_name readingtracker.com;
       root /var/www/readingtracker/client/web;
       index index.html;
       
       location / {
           try_files $uri $uri/ /index.html;
       }
   }
   ```

2. **Apache 배포**
   - `.htaccess` 파일 사용하여 라우팅 설정

3. **정적 호스팅 서비스**
   - GitHub Pages
   - Netlify
   - Vercel
   - AWS S3 + CloudFront

### CORS 설정

서버의 `CorsConfig`에서 웹 클라이언트 도메인 허용 필요:

```java
// 개발 환경
configuration.setAllowedOriginPatterns(Arrays.asList(
    "http://localhost:8000",  // 로컬 개발 서버
    "http://127.0.0.1:8000"
));

// 프로덕션 환경
configuration.setAllowedOriginPatterns(Arrays.asList(
    "https://readingtracker.com",
    "https://www.readingtracker.com"
));
```

## 개발 가이드라인

### 코드 스타일

1. **파일명**: kebab-case (예: `book-service.js`, `login-view.js`)
2. **클래스명**: PascalCase (예: `BookService`, `LoginView`)
3. **함수/변수명**: camelCase (예: `handleSubmit`, `userName`)
4. **상수명**: UPPER_SNAKE_CASE (예: `API_BASE_URL`)
5. **CSS 클래스명**: kebab-case (예: `book-card`, `search-form`)

### 모듈 작성 규칙

1. **ES Modules 사용**: `import`/`export` 문법 사용
2. **단일 책임 원칙**: 각 모듈은 하나의 명확한 역할만 담당
3. **명확한 함수 이름**: 함수 이름만으로 기능을 알 수 있도록 작성
4. **에러 처리**: try-catch를 통한 명시적 에러 처리

### API 통신 규칙

1. **모든 API 호출은 `services/` 디렉토리의 함수를 통해 수행**
2. **공통 API 클라이언트 사용**: `api-client.js`를 통한 일관된 요청 처리
3. **에러 처리**: 명시적인 에러 처리 및 사용자에게 에러 메시지 표시
4. **로딩 상태**: UI에 로딩 상태 표시 (로딩 스피너 등)

### 상태 관리 규칙

1. **전역 상태**: `state/` 디렉토리의 상태 관리 클래스 사용
2. **로컬 상태**: 각 View 클래스 내부에서 관리
3. **Observer 패턴**: 상태 변경 시 자동으로 UI 업데이트
4. **로컬 스토리지**: 영구 저장이 필요한 데이터만 사용

### View 작성 규칙

1. **클래스 기반**: 각 View는 클래스로 구현
2. **초기화 메서드**: `init()` 메서드에서 이벤트 리스너 등록
3. **DOM 조작**: 명확한 DOM 요소 선택 및 조작
4. **이벤트 처리**: 이벤트 위임(Event Delegation) 활용 권장

## 주요 기능별 구현 가이드

### 1. 인증 기능

- **로그인**: `pages/login.html` + `views/login-view.js`
- **회원가입**: `pages/register.html` + `views/register-view.js`
- **로그아웃**: 헤더 또는 프로필 메뉴에서 `authHelper.handleLogout()` 호출
- **토큰 갱신**: `api-client.js`의 인터셉터에서 자동 처리

### 2. 도서 검색 기능

- **검색 페이지**: `pages/book-search.html` + `views/book-search-view.js`
- **검색 API**: `bookService.searchBooks()`
- **검색 결과 표시**: DOM 조작을 통한 동적 렌더링
- **페이지네이션**: 수동으로 페이지 번호 관리 및 API 호출

### 3. 서재 관리 기능

- **서재 페이지**: `pages/bookshelf.html` + `views/bookshelf-view.js`
- **책 추가**: `bookService.addBookToShelf()`
- **책 상태 변경**: `bookService.updateBookStatus()`
- **책 삭제**: `bookService.removeBookFromShelf()`

### 4. 사용자 프로필 기능

- **프로필 페이지**: `pages/profile.html` + `views/profile-view.js`
- **프로필 조회**: `userService.getProfile()`
- **프로필 수정**: `userService.updateProfile()`

## 테스트 전략

### 단위 테스트

- **유틸리티 함수 테스트**: Jest 또는 Vitest
- **API 함수 테스트**: MSW (Mock Service Worker) 또는 fetch mock
- **View 클래스 테스트**: JSDOM을 사용한 DOM 테스트

### 통합 테스트

- **E2E 테스트**: Playwright 또는 Cypress
- **주요 사용자 플로우 테스트**: 로그인 → 검색 → 책 추가 등

## 성능 최적화

1. **이미지 최적화**: WebP 형식 사용, lazy loading (`loading="lazy"` 속성)
2. **코드 분할**: ES Modules의 동적 import 활용
3. **캐싱**: 서버 응답 데이터를 메모리에 캐싱하여 중복 요청 방지
4. **디바운싱/스로틀링**: 검색 입력 등에 디바운싱 적용

## 보안 고려사항

1. **XSS 방지**: 
   - 사용자 입력을 DOM에 삽입할 때 `textContent` 사용 (innerHTML 대신)
   - HTML 삽입이 필요한 경우 DOMPurify 같은 라이브러리 사용
   - 서버에서 받은 데이터도 신뢰하지 않고 검증

2. **CSRF 방지**: 
   - SameSite 쿠키 설정 (서버 측)
   - CSRF 토큰 사용 (필요한 경우)

3. **토큰 보안**: 
   - localStorage에 저장된 토큰은 XSS 공격에 취약
   - 가능하면 httpOnly 쿠키 사용 고려 (서버 측 설정 필요)
   - 토큰 만료 시간 확인 및 자동 갱신

4. **API 보안**: 
   - HTTPS 사용 (프로덕션 환경)
   - 민감한 정보는 서버에서만 관리
   - API 키나 비밀번호는 클라이언트 코드에 포함하지 않음

5. **입력 검증**: 
   - 클라이언트 측 검증은 UX를 위한 것이며, 서버 측 검증이 필수
   - 모든 사용자 입력은 서버에서 재검증

## 참고 자료

- [MDN Web Docs - JavaScript](https://developer.mozilla.org/ko/docs/Web/JavaScript)
- [MDN Web Docs - Fetch API](https://developer.mozilla.org/ko/docs/Web/API/Fetch_API)
- [ES Modules](https://developer.mozilla.org/ko/docs/Web/JavaScript/Guide/Modules)
- [Web Security](https://developer.mozilla.org/ko/docs/Web/Security)

---

**최종 업데이트**: 2024년
**버전**: 1.0

