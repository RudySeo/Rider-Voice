# Rider Voice MVP 아키텍처

## 1. 범위

현재 서버는 카카오 인증, 음식점 검색·등록과 비공개 리뷰 CRUD만 담당한다. 방문 증빙, OCR, 공개 리포트, 검수와 정정은 현재 아키텍처 범위가 아니다.

서버는 인증·권한, 카카오 장소 검증, 음식점 중복 방지와 리뷰 소유권 규칙의 유일한 기준이다. 클라이언트는 데이터베이스나 카카오 API를 직접 호출하지 않는다.

## 2. 기술 스택과 실행 경계

- Spring Boot, Kotlin, Gradle Kotlin DSL
- Spring MVC, Spring Security, Bean Validation
- Spring Data JPA, Hibernate, MySQL 9.3
- springdoc-openapi, RFC 7807 `ProblemDetail`
- JUnit 5, MockK, Spring MVC 테스트
- 카카오 REST OAuth, 카카오 로컬 REST API

API 서버와 `rider` MySQL 데이터베이스는 로컬 프로세스로 실행한다. 사용자가 별도로 요청하기 전에는 Docker, Docker Compose, Testcontainers, AWS 리소스와 배포 작업을 사용하지 않는다.

## 3. 프로젝트 구조

```text
com.ridervoice.api
├── common
├── auth
├── restaurant
└── review
```

각 기능은 가벼운 헥사고날 구조를 사용한다.

```text
feature
├── presentation
│   ├── FeatureController.kt
│   ├── dto
│   │   ├── FeatureRequests.kt
│   │   └── FeatureResponses.kt
│   └── FeatureHttpMapper.kt
├── application
│   ├── port
│   │   ├── in
│   │   │   └── FeatureUseCase.kt
│   │   └── out
│   │       ├── FeatureRepository.kt
│   │       └── ExternalProviderPort.kt
│   ├── model
│   │   ├── FeatureCommands.kt
│   │   └── FeatureResults.kt
│   └── FeatureService.kt
├── domain
│   ├── Feature.kt
│   └── FeaturePolicy.kt
└── infrastructure
    ├── persistence
    │   └── JpaFeatureRepository.kt
    └── external
        └── ExternalProviderAdapter.kt
```

### 의존 방향

```text
HTTP request
  -> presentation DTO
  -> application input port (command)
  -> domain
  -> application output port
  -> infrastructure adapter

application result
  -> presentation response DTO
  -> HTTP response
```

- Controller는 HTTP validation, principal 추출, input port 호출과 response 변환만 담당한다.
- Controller는 request DTO를 application에 그대로 전달하지 않고 command로 변환한다.
- application은 HTTP request/response DTO, Spring MVC, Swagger, Jackson과 infrastructure 구현 타입을 알지 못한다.
- input port는 application use case의 공개 계약이며 Controller는 이 interface에 의존한다.
- repository와 외부 provider interface는 `application/port/out`에 두고 infrastructure adapter가 구현한다.
- application result를 API 응답으로 직접 반환하지 않고 presentation mapper가 response DTO로 변환한다.
- JPA Entity를 request, response, command 또는 result로 직접 사용하지 않는다.
- 트랜잭션 경계와 소유권 검사는 application service에 둔다.
- 모든 내부 class에 interface를 만들지 않는다. Controller가 호출하는 use case와 application이 사용하는 외부 경계에만 port를 둔다.

### DTO와 모델 규칙

- request/response DTO는 `presentation/dto`에 두며 Bean Validation과 OpenAPI schema annotation을 가질 수 있다.
- 작은 DTO는 class마다 파일을 만들지 않고 `FeatureRequests.kt`, `FeatureResponses.kt`처럼 역할별로 묶는다.
- command/result는 `application/model`에 두며 HTTP annotation이나 provider 타입을 포함하지 않는다.
- HTTP mapping은 `presentation`의 명시적 mapper 또는 extension function에서 수행한다.
- provider request/response 타입과 JPA 전용 구현 타입은 `infrastructure` 밖으로 노출하지 않는다.
- 현재 MVP에서는 domain entity에 JPA mapping annotation을 허용하되 persistence 세부사항이 domain behavior나 공개 계약으로 새지 않게 한다.

### 식별자와 연관관계

- 모든 Entity는 `BaseEntity`가 제공하는 `Long` PK와 `GenerationType.IDENTITY`를 사용한다.
- 공개 API의 `userId`, `restaurantId`와 이후 `reviewId`도 JSON 정수와 OpenAPI `int64`로 표현한다.
- `OAuthAccount`, `OnboardingToken`, `UserSession`은 `User`를 단방향 `LAZY @ManyToOne`으로 참조한다.
- refresh session 회전은 이전 `UserSession`이 다음 `UserSession`을 단방향 `LAZY @OneToOne`으로 참조한다.
- 부모 Entity에는 편의를 위한 역방향 컬렉션을 두지 않고 연관관계에 cascade remove를 사용하지 않는다.
- 로컬과 통합 테스트 profile은 Hibernate `ddl-auto=update`, 운영 profile은 `ddl-auto=none`을 사용한다.
- 현재 로컬 MVP에서는 Flyway 또는 별도 DB 형상관리 도구를 사용하지 않는다.

### 기존 인증 코드 정리 경계

기존 인증 코드는 Controller가 application result를 직접 반환하고 application service가 infrastructure repository를 직접 참조하는 과도기 구조다. 음식점과 리뷰 신규 구현은 위 경계를 먼저 적용한다. 인증 구조 정리는 별도 refactor 작업으로 수행하며 음식점·리뷰 기능 작업에 섞지 않는다.

## 4. 핵심 흐름

### 4.1 카카오 로그인

```text
GET /api/v1/auth/kakao/authorize
  -> 카카오 로그인과 authorization code
GET /api/v1/auth/kakao/callback
  -> 카카오 token/user adapter
  -> User 및 OAuthAccount 확인
  -> 신규 사용자는 onboarding token 발급
POST /api/v1/auth/consents
  -> ACTIVE 전환
  -> access token과 rotating refresh token 발급
```

- onboarding token은 약관 동의 API에만 사용할 수 있다.
- 카카오 access token은 계정 확인 후 장기 보관하지 않는다.
- refresh token은 원문이 아니라 해시로 저장하고 갱신할 때 회전시킨다.

### 4.2 음식점 검색과 지연 등록

```text
GET /api/v1/restaurants/search?query=...
  -> 내부 음식점 검색
  -> KakaoLocalPort를 통한 카카오 장소 검색
  -> 내부 등록 여부를 포함한 장소 후보 DTO 반환

POST /api/v1/restaurants
  -> 원래 query와 선택한 kakaoPlaceId 입력
  -> 서버가 같은 카카오 키워드 검색을 다시 수행
  -> 결과에 일치하는 장소가 있는지 검증
  -> kakaoPlaceId unique key로 Restaurant upsert
  -> 내부 restaurantId 반환
```

- 외부 API 타입은 infrastructure adapter 밖으로 노출하지 않는다.
- 클라이언트가 보낸 이름, 주소, 좌표를 음식점 기준 정보로 신뢰하지 않는다.
- 카카오 로컬 API는 장소 ID 단건 조회를 제공하지 않으므로 클라이언트가 보낸 장소 ID만으로 등록하지 않는다.
- 등록 요청의 원래 검색어로 카카오 키워드 검색을 반복하고, 결과에서 같은 장소 ID를 찾은 경우에만 provider 응답의 이름·주소·좌표를 저장한다.
- 동시 최초 등록 요청도 하나의 Restaurant만 생성되도록 DB unique 제약과 충돌 후 재조회로 처리한다.
- 음식점을 최초 선택한 사용자에게 소유권이나 수정 권한을 부여하지 않는다.

### 4.3 비공개 리뷰 CRUD

```text
ROLE_USER
  + restaurantId
  + 6개 ReviewRating
  + 선택 comment
  -> Review 생성
  -> 본인 리뷰 목록·상세 조회
  -> 본인 리뷰 수정 또는 삭제
```

- 사용자와 음식점 조합당 리뷰는 하나다.
- 중복 생성은 `409 Conflict`로 반환하고 기존 리뷰를 변경하지 않는다.
- 타인의 리뷰 ID는 상세·수정·삭제 요청에서 `404 Not Found`로 처리한다.
- 리뷰는 비공개이며 방문 인증, 공개 조회와 집계 상태를 갖지 않는다.
- 삭제는 현재 MVP에서 hard delete다.

## 5. 도메인과 데이터 모델

### 인증

- `users`: 내부 사용자 ID, 상태, 약관 동의 정보
- `oauth_accounts`: `User`와 provider 외부 subject의 연결
- `onboarding_tokens`: `User`와 연결된 일회용 token hash 및 만료·소비 정보
- `user_sessions`: `User`와 연결된 refresh token hash 및 다음 session 회전·폐기 정보

### 음식점

- `restaurants`: `kakao_place_id`, 이름, 주소, 좌표, 마지막 동기화 시각
- `kakao_place_id`에 unique 제약을 둔다.

### 리뷰

- `reviews`: 작성자, 음식점, 6개 평가 값, 선택 의견, 생성·수정 시각
- `(author_user_id, restaurant_id)`에 unique 제약을 둔다.
- `ReviewRating`은 `VERY_GOOD`, `GOOD`, `NEEDS_IMPROVEMENT`, `MAJOR_IMPROVEMENT`, `NOT_OBSERVED`다.
- 의견은 nullable이며 최대 200자다.

모든 기본 키는 `BIGINT AUTO_INCREMENT`를 사용한다. 시각은 UTC로 저장하고 API에서는 RFC 3339로 반환한다. Entity annotation이 로컬 schema의 FK, unique key와 index 기준이다.

## 6. API 계약

```text
# 구현됨
GET    /api/v1/auth/kakao/authorize
GET    /api/v1/auth/kakao/callback
POST   /api/v1/auth/consents
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/users/me

# MVP 다음 구현 대상
GET    /api/v1/restaurants/search
POST   /api/v1/restaurants
POST   /api/v1/reviews
GET    /api/v1/reviews
GET    /api/v1/reviews/{reviewId}
PATCH  /api/v1/reviews/{reviewId}
DELETE /api/v1/reviews/{reviewId}
```

- 인증과 현재 사용자 API를 제외한 MVP 신규 API는 `ROLE_USER`를 요구한다.
- 목록 API는 cursor pagination을 사용한다.
- 성공 응답은 기능별 DTO, 오류 응답은 안정적인 `code`를 포함한 `ProblemDetail`을 사용한다.
- request DTO는 Bean Validation으로 검증한다.
- endpoint와 DTO 변경은 같은 변경에서 OpenAPI annotation과 schema에 반영한다.

공개 request 필드:

```text
CreateRestaurantRequest
- query: String
- kakaoPlaceId: String

CreateReviewRequest
- restaurantId: Long
- pickupSpaceCleanliness: ReviewRating
- packagingStability: ReviewRating
- orderReadiness: ReviewRating
- handoffAccuracy: ReviewRating
- staffInteraction: ReviewRating
- riderRespect: ReviewRating
- comment: String?  # 최대 200자

UpdateReviewRequest
- 위 6개 ReviewRating: 각각 optional, 전달된 항목만 변경
- comment: optional, 빈 문자열은 의견 삭제
```

리뷰 수정 요청은 평가나 의견 중 하나 이상을 포함해야 한다. 의견의 앞뒤 공백은 제거하고 빈 문자열은 `null`로 정규화한다.

## 7. 테스트 전략

### 단위 테스트

- 리뷰 평가 값과 의견 길이 검증
- 리뷰 수정 정책
- 음식점과 리뷰 도메인 규칙

### 통합 테스트

- 로컬 MySQL 기준 Hibernate schema 생성과 JPA mapping
- `kakao_place_id`와 `(author_user_id, restaurant_id)` 동시 중복 생성 방지
- application service의 리뷰 소유권과 트랜잭션 경계
- 카카오 로컬 adapter의 성공, timeout, rate limit과 잘못된 응답

### API 계약 테스트

- 로그인 사용자 리뷰 CRUD 성공
- 미인증 요청 거부
- 타인 리뷰 접근 비노출
- 잘못된 enum, 누락된 평가와 200자 초과 의견 거부
- 중복 리뷰 생성 시 `409 Conflict`
- `/v3/api-docs`의 인증 요구사항과 DTO schema 검증

## 8. 후속 확장 경계

방문 인증을 구현하기 전까지 현재 리뷰를 인증 리뷰로 표현하거나 공개하지 않는다. 후속 단계에서는 별도 ADR을 먼저 작성해 증빙 방식, 인증 리뷰 전환, 공개 기준과 보관 정책을 결정한다. 현재 MVP에 미래 상태 enum, OCR port, `WriteGrant`, 리포트 또는 관리자 모델을 미리 추가하지 않는다.
