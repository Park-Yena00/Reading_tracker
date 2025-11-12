# Mapper 클래스 위치 분석

## 질문

**Mapper 클래스는 Controller와 Service의 경계에 있는 것이 맞는가?**

---

## 아키텍처 관점 분석

### 현재 프로젝트의 3-tier Architecture

```
┌─────────────────────────────────────────┐
│  Client ↔ Server 경계                   │
│  - server.controller                    │
│  - server.dto.clientserverDTO           │
└─────────────────────────────────────────┘
           ↓ ↑
┌─────────────────────────────────────────┐
│  서버 내부                                │
│  - server.service                       │
└─────────────────────────────────────────┘
           ↓ ↑
┌─────────────────────────────────────────┐
│  Server ↔ DBMS 경계                      │
│  - dbms.repository                      │
│  - dbms.entity                          │
└─────────────────────────────────────────┘
```

### Mapper의 역할

Mapper는 다음 변환을 수행합니다:
1. **RequestDTO → Entity**: Controller에서 Service로 데이터 전달 시
2. **Entity → ResponseDTO**: Service에서 Controller로 데이터 반환 시

### Mapper의 호출 위치

```java
// Controller에서 호출
@RestController
public class BookShelfController {
    @Autowired
    private BookMapper bookMapper;  // ✅ Controller에서 주입
    
    public ApiResponse<BookAdditionResponse> addBookToShelf(
        @RequestBody BookAdditionRequest request) {
        
        // ✅ Controller에서 Mapper 호출
        UserShelfBook entity = bookMapper.toEntity(request);
        
        // Service 호출
        UserShelfBook saved = bookService.addBookToShelf(entity);
        
        // ✅ Controller에서 Mapper 호출
        BookAdditionResponse response = bookMapper.toResponse(saved);
        
        return ApiResponse.success(response);
    }
}
```

---

## 위치 분석

### 옵션 1: Controller와 Service의 경계 (경계 계층)

**위치**: `server.mapper`

**특징**:
- Controller와 Service 사이에 위치
- 두 계층 간의 변환을 담당
- 독립적인 변환 계층으로 존재

**장점**:
- 경계 역할이 명확함
- Controller와 Service 모두에서 접근 가능
- 변환 로직이 한 곳에 집중

**단점**:
- Service에서도 Mapper에 접근 가능 (하지만 사용하지 않음)

### 옵션 2: Client ↔ Server 경계의 일부

**위치**: `server.dto.mapper` 또는 `server.controller.mapper`

**특징**:
- Controller와 같은 경계에 위치
- DTO와 관련된 변환 로직

**장점**:
- DTO와 함께 위치하여 응집도 높음
- Controller에서만 사용하므로 위치가 명확

**단점**:
- Service와의 경계 역할이 덜 명확함

### 옵션 3: 서버 공통 요소

**위치**: `server.common.mapper`

**특징**:
- 공통 유틸리티처럼 사용
- 여러 계층에서 접근 가능

**장점**:
- 공통 기능으로 인식

**단점**:
- 경계 역할이 불명확함
- Service에서도 접근 가능 (하지만 사용하지 않음)

---

## 권장 위치: `server.mapper` (경계 계층)

### 이유

#### 1. 역할이 경계에 해당
- Mapper는 **Controller와 Service 사이의 변환**을 담당
- Client ↔ Server 경계(DTO)와 서버 내부(Entity) 사이의 변환

#### 2. 호출 위치
- Controller에서만 호출됨
- Service는 Mapper를 사용하지 않음

#### 3. 의존성 방향
```
Controller → Mapper → (Entity)
Service → (Entity)
```

#### 4. 아키텍처 일관성
- 현재 프로젝트는 경계 중심 구조
- Mapper도 경계 역할을 수행

---

## 패키지 구조 제안

### 권장 구조

```
server/
  ├── controller/          # Client ↔ Server 경계
  │   └── v1/
  │       ├── AuthController.java
  │       └── BookShelfController.java
  │
  ├── mapper/              # ✅ Controller ↔ Service 경계
  │   ├── UserMapper.java
  │   └── BookMapper.java
  │
  ├── service/             # 서버 내부
  │   ├── AuthService.java
  │   └── BookService.java
  │
  └── dto/                 # Client ↔ Server 경계
      └── clientserverDTO/
```

### 대안 구조 (경계 명확화)

```
server/
  ├── controller/          # Client ↔ Server 경계
  │   └── v1/
  │
  ├── mapper/              # ✅ Controller ↔ Service 경계
  │   ├── UserMapper.java
  │   └── BookMapper.java
  │
  ├── service/             # 서버 내부
  │
  └── dto/                 # Client ↔ Server 경계
```

---

## 아키텍처 다이어그램

### 현재 구조 (변경 전)

```
┌─────────────────────────────────────┐
│  Client                             │
└─────────────────────────────────────┘
           ↓ RequestDTO ↑ ResponseDTO
┌─────────────────────────────────────┐
│  Controller (Client ↔ Server 경계)   │
│  - 변환 작업 수행                    │
└─────────────────────────────────────┘
           ↓ RequestDTO ↑ Entity
┌─────────────────────────────────────┐
│  Service (서버 내부)                 │
│  - RequestDTO 사용                   │
│  - Entity 반환                       │
└─────────────────────────────────────┘
```

### 변경 후 구조 (Mapper 추가)

```
┌─────────────────────────────────────┐
│  Client                             │
└─────────────────────────────────────┘
           ↓ RequestDTO ↑ ResponseDTO
┌─────────────────────────────────────┐
│  Controller (Client ↔ Server 경계)   │
│  - 변환 작업 없음                    │
└─────────────────────────────────────┘
           ↓ RequestDTO ↑ ResponseDTO
┌─────────────────────────────────────┐
│  Mapper (Controller ↔ Service 경계) │ ← ✅ 여기!
│  - RequestDTO ↔ Entity 변환         │
│  - Entity ↔ ResponseDTO 변환         │
└─────────────────────────────────────┘
           ↓ Entity ↑ Entity
┌─────────────────────────────────────┐
│  Service (서버 내부)                 │
│  - Entity만 사용                     │
└─────────────────────────────────────┘
```

---

## 결론

### ✅ Mapper는 Controller와 Service의 경계에 위치하는 것이 맞습니다

**이유**:
1. **역할**: Controller와 Service 사이의 변환을 담당
2. **위치**: `server.mapper` 패키지가 적절
3. **의존성**: Controller에서만 호출, Service는 사용하지 않음
4. **아키텍처**: 경계 중심 구조와 일치

### 권장 패키지 구조

```
server/
  ├── controller/     # Client ↔ Server 경계
  ├── mapper/         # ✅ Controller ↔ Service 경계
  ├── service/        # 서버 내부
  └── dto/            # Client ↔ Server 경계
```

### Mapper의 특성

- **위치**: Controller와 Service 사이의 경계 계층
- **호출**: Controller에서만 호출
- **역할**: DTO ↔ Entity 변환
- **의존성**: Controller → Mapper (단방향)

---

**작성일**: 2024년
**버전**: 1.0

