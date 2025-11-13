# DTO 구현 가이드

## 개요

이 문서는 외부 API와의 통신 시 DTO와 Entity 변환에 대한 구현 가이드입니다. 특히 알라딘 Open API를 통해 책 정보를 검색해오는 경우의 데이터 변환 원칙과 흐름을 설명합니다.

## 외부 API 응답 데이터의 DTO → Entity 변환

### 1. 🔄 DTO → Entity 변환이 필요한 이유

알라딘 Open API를 통해 책 정보를 검색해오는 경우, DTO를 최종적인 도메인 모델(Entity)로 변환해야 합니다. 외부 API에서 수신하는 응답 데이터는 시스템 아키텍처에서 Entity/Domain Model로 변환해야 합니다.

외부 API에서 받은 JSON 데이터를 서버 내부의 Entity/Domain Model로 변환하는 이유는 데이터의 독립성과 신뢰성을 확보하기 위함입니다.

#### A. 의존성 분리 (Decoupling)

알라딘 API가 반환하는 JSON 구조는 언제든지 변경될 수 있습니다.

**변환 안 할 경우**: 
- 외부 API 응답 구조(`AladinBookResponseDTO`)가 변경되면, 그 데이터를 사용하는 서버의 모든 계층(Service, Controller) 코드를 수정해야 합니다.

**변환 할 경우**: 
- 외부 데이터를 내부의 `ExternalBook` Entity나 Domain Model로 변환하는 Mapper만 수정하면 되므로, 서버의 **핵심 비즈니스 로직(Service)**은 외부 변화로부터 보호됩니다.

#### B. 데이터 정제 및 유효성 확보

외부 API 데이터는 우리 서비스가 정의한 규칙을 따르지 않을 수 있습니다.

- **데이터 정제**: 알라딘 API의 필드명(`itemTitle`, `pubDate`)을 우리 서비스의 표준 필드명(`title`, `publishedAt`)으로 통일합니다.
- **유효성 검증**: 필수 필드가 누락되었는지 확인하고, 데이터 타입(예: 가격이 숫자인지)을 명확히 보장합니다.

#### C. 도메인 모델 활용

외부에서 가져온 책 정보일지라도, 우리 서비스의 로직(예: 책 검색 결과 저장, 사용자 서재에 추가)에서 사용되려면 도메인 모델의 구조를 따라야 합니다.

### 2. 📝 데이터 흐름 권장 시퀀스

알라딘 API 검색 결과의 데이터 흐름은 다음과 같아야 합니다.

```
1. BookSearchController
   ↓ Controller로부터 검색 조건을 받음 (String query, BookSearchFilter queryType 등)
   
2. BookSearchService
   ↓ AladinApiService를 호출하여 외부 데이터를 가져옴
   
3. AladinApiService
   ↓ 알라딘 API 호출 후, 수신된 JSON을 AladinBookResponseDTO (외부 DTO)로 파싱
   
4. BookSearchService (계속)
   ↓ AladinBookResponseDTO를 Mapper를 통해 ExternalBook Entity로 변환
   ↓ 검색 결과 검증 및 정제 수행
   ↓ ExternalBook Entity 리스트를 Controller로 반환
   
5. BookSearchController (계속)
   ↓ ExternalBook Entity를 클라이언트에게 보낼 BookSearchResponseDTO로 최종 변환하여 JSON 응답으로 반환
```

이 구조는 **알라딘 API 응답 구조(외부 DTO)**와 우리 서비스의 응답 구조(내부 DTO) 사이에 Entity라는 방어막을 두어 시스템의 안정성을 높여줍니다.

### 3. 📊 데이터 변환 계층 구조

```
┌─────────────────────────────────────────────────────────────┐
│              BookSearchController                            │
│  - 검색 조건 수신 (String query, BookSearchFilter 등)      │
│  - BookSearchService 호출                                   │
│  - ExternalBook Entity → BookSearchResponseDTO 변환         │
│  - 클라이언트 응답 형식으로 최종 변환                        │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ↓
┌─────────────────────────────────────────────────────────────┐
│              BookSearchService                               │
│  (핵심 비즈니스 로직 - Internal)                            │
│  - Controller로부터 검색 조건 수신                          │
│  - AladinApiService 호출                                    │
│  - 검색 결과 검증 및 정제                                   │
│  - Mapper를 통한 DTO → Entity 변환 제어                     │
│  - ExternalBook Entity 리스트 반환                          │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ↓
┌─────────────────────────────────────────────────────────────┐
│              AladinApiService                                │
│  (외부 시스템 통합 - External)                              │
│  - 알라딘 API HTTP 요청 생성 및 전송                        │
│  - JSON 응답 수신                                           │
│  - JSON → AladinBookResponseDTO (외부 DTO) 파싱            │
│  - 외부 DTO 반환 (변환 로직 포함 안 함)                     │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ↓
┌─────────────────────────────────────────────────────────────┐
│                    외부 API (알라딘)                         │
│              JSON 응답 (외부 데이터 구조)                     │
└─────────────────────────────────────────────────────────────┘

                        ↑ (반환 흐름)
                        │
┌─────────────────────────────────────────────────────────────┐
│              Mapper (AladinBookMapper)                       │
│  - AladinBookResponseDTO → ExternalBook Entity 변환         │
│  - 데이터 정제 및 유효성 검증                               │
│  - 필드명 통일 (itemTitle → title, pubDate → publishedAt)  │
│  (BookSearchService에서 호출)                               │
└─────────────────────────────────────────────────────────────┘
```

### 4. 📦 서비스 계층 분리: 단일 책임 원칙(SRP) 준수

현재 상황을 고려했을 때, `AladinApiService`와 `BookSearchService` 두 개의 서비스로 분리하는 것을 강력히 권장합니다.

이는 객체지향 설계 원칙 중 **단일 책임 원칙(SRP)**을 준수하고 아키텍처의 계층을 명확하게 구분하여 유지보수성을 극대화하는 표준적인 방법입니다.

#### 4.1. 🎯 권장 역할 분리

| 서비스 명 | 책임 범위 | 역할 |
|---------|---------|------|
| **AladinApiService** | 외부 시스템 통합 (External) | 오직 알라딘 API 통신 및 데이터 파싱만 전담합니다.<br>1. HTTP 요청 생성 및 전송<br>2. JSON 응답 수신 및 **외부 DTO (AladinBookResponseDTO)**로 파싱<br>3. 변환 로직은 포함하지 않습니다. (혹은 외부 DTO를 내부 Entity로 변환하는 Mapper를 호출하는 최소한의 책임만 가짐) |
| **BookSearchService** | 핵심 비즈니스 로직 (Internal) | 책 검색 관련 비즈니스 로직 및 흐름 제어를 전담합니다.<br>1. Controller로부터 검색 조건을 받습니다<br>2. AladinApiService를 호출하여 외부 데이터를 가져옵니다<br>3. 검색 결과 검증 및 정제, 외부 DTO를 내부 Entity로 변환하는 최종 로직을 수행합니다<br>4. 최종적으로 ExternalBook Entity 리스트를 Controller로 반환합니다 |

#### 4.2. 🔑 분리의 핵심 이점

##### A. 단일 책임 원칙 준수 (SRP)

- **AladinApiService**는 오직 '알라딘 API와의 연동'이라는 통합 책임만 가집니다.
- **BookSearchService**는 '책 검색 결과 처리'라는 비즈니스 책임만 가집니다.

만약 알라딘 API가 아닌 다른 서점 API(예: 교보문고)를 추가하게 된다면, `KyoboApiService`만 추가하면 되고 기존의 `BookSearchService`는 그대로 유지할 수 있습니다.

##### B. 쉬운 테스트

`BookSearchService`를 테스트할 때, 실제로 알라딘 API 서버와 통신할 필요 없이 `AladinApiService`를 Mocking하여 예상 데이터를 주입하고 비즈니스 로직(검증 등)만 테스트할 수 있습니다.

##### C. 응답 반환 책임

변환된 Entity 리스트는 최종적으로 **비즈니스 흐름을 제어하는 BookSearchService**가 Controller로 반환하는 것이 맞습니다. `AladinApiService`는 외부 데이터를 성공적으로 가져왔다는 것까지만 책임지고, 이를 내부 모델로 변환하여 활용하는 책임은 `BookSearchService`에 있습니다.

#### 4.3. 📋 서비스 분리 구현 가이드

**AladinApiService의 책임:**
```java
// AladinApiService는 외부 API 통신만 담당
public AladinBookResponseDTO searchBooks(String query, BookSearchFilter queryType, ...) {
    // 1. HTTP 요청 생성 및 전송
    // 2. JSON 응답 수신
    // 3. AladinBookResponseDTO (외부 DTO)로 파싱
    // 4. 외부 DTO 반환 (변환 로직 없음)
}
```

**BookSearchService의 책임:**
```java
// BookSearchService는 비즈니스 로직 및 흐름 제어 담당
public List<ExternalBook> searchBooks(String query, BookSearchFilter queryType, ...) {
    // 1. Controller로부터 검색 조건 수신
    // 2. AladinApiService 호출하여 외부 DTO 획득
    AladinBookResponseDTO externalDto = aladinApiService.searchBooks(query, queryType, ...);
    
    // 3. Mapper를 통해 외부 DTO → 내부 Entity 변환
    List<ExternalBook> entities = aladinBookMapper.toExternalBookList(externalDto);
    
    // 4. 검색 결과 검증 및 정제 (비즈니스 로직)
    // 5. Entity 리스트 반환
    return entities;
}
```

### 5. 🎯 핵심 원칙 요약

1. **외부 API 응답은 항상 DTO → Entity로 변환**
   - 외부 API의 데이터 구조 변경에 대한 의존성을 최소화합니다.
   - 서버의 핵심 비즈니스 로직을 외부 변화로부터 보호합니다.

2. **Entity는 서비스 계층의 표준 모델**
   - Service 계층은 Entity만 사용하여 비즈니스 로직을 수행합니다.
   - 외부 DTO와 내부 DTO 모두 Entity를 거쳐 변환됩니다.

3. **Controller는 Entity → ResponseDTO 변환만 담당**
   - Controller는 Entity를 클라이언트가 이해할 수 있는 ResponseDTO로 변환합니다.
   - 변환 로직은 Mapper를 통해 수행됩니다.

4. **데이터 정제 및 유효성 검증은 Mapper에서 수행**
   - 외부 API 데이터의 필드명 통일, 데이터 타입 변환, 필수 필드 검증 등을 Mapper에서 처리합니다.

### 6. 📌 참고 사항

- 이 가이드는 **외부 API와의 통신**에 대한 DTO → Entity 변환 원칙입니다.
- **내부 클라이언트 요청**에 대한 DTO → Entity 변환은 `ARCHITECTURE.md`의 "DTO → Entity 변환의 필요성" 섹션을 참고하세요.
- 검색 요청(Read Operation)의 경우, Controller에서 Service로 전달하는 파라미터는 개별 파라미터로 전달하는 것이 권장됩니다. (자세한 내용은 `ARCHITECTURE.md` 참고)

