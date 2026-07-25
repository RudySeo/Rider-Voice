# Rider Voice 공개 리뷰 MVP 아키텍처

## 1. 문서 상태와 범위

이 문서는 Rider Voice 공개 리뷰 MVP의 목표 아키텍처다. 현재 코드에는 카카오 OAuth 직접 구현과 단일 카카오 장소 기반 `Restaurant`가 남아 있으며, 구현 작업에서 이 문서의 구조로 교체한다.

서버는 다음 책임을 갖는다.

- Spring Security OAuth2 Client 기반 카카오 로그인
- Rider Voice service token과 권한 처리
- 음식점·주소 검색과 provider 결과 검증
- 픽업 장소와 배달 브랜드의 중복 방지
- 리뷰 이력, 90일 작성 제한과 소유권 정책
- 공개 리뷰, 작성자 5명 집계, 의견 검수와 신고
- 관리자 정정, 제외와 중복 병합

라이더 신분과 실제 방문은 인증하지 않는다. 배달내역 캡처, 이미지 업로드, OCR와 배달 앱 화면 파싱은 구현하지 않는다.

## 2. 기술 스택과 실행 경계

- Kotlin, JDK 21, Gradle Kotlin DSL
- Spring Boot, Spring MVC, Spring Security OAuth2 Client
- Bean Validation, Spring Data JPA, Hibernate, MySQL 9.3
- Spring `RestClient`, Spring Cache, Caffeine
- springdoc-openapi, RFC 7807 `ProblemDetail`
- JUnit 5, MockK, MockMvc와 HTTP stub server

API 서버와 `rider` MySQL 데이터베이스는 로컬 프로세스로 실행한다. 초기에는 단일 API 인스턴스를 전제로 하며 Redis, Kafka, Elasticsearch, Docker, Testcontainers, AWS와 배포 작업을 추가하지 않는다.

로컬 설정은 Git에서 제외한 프로젝트 루트 `.env` 하나로 관리한다. `local` profile만 이를 선택적으로 읽고, IntelliJ EnvFile 플러그인은 개인 실행 설정으로 사용할 수 있다. OS 또는 IDE 환경 변수가 `.env`보다 우선하며 `test`와 `prod` profile은 `.env`를 자동으로 읽지 않는다.

로컬과 통합 테스트는 Hibernate `ddl-auto=update`, 운영 profile은 `ddl-auto=none`을 사용한다. 현재 로컬 MVP에서는 Flyway를 사용하지 않으며 목표 모델 적용 시 로컬 `rider` schema를 한 번 초기화한다.

## 3. 아키텍처 경계

```text
com.ridervoice.api
├── common
├── auth
├── restaurant
├── review
└── moderation
```

기능은 가벼운 헥사고날 경계를 따른다.

```text
HTTP / Security adapter
  -> presentation DTO
  -> application input port(command)
  -> domain policy
  -> application output port
  -> JPA / external provider adapter

application result
  -> presentation mapper
  -> response DTO
```

- Controller와 OAuth handler는 validation, principal 추출, command 변환, input port 호출과 응답 변환만 담당한다.
- application은 Spring MVC, Swagger, Jackson, presentation DTO와 infrastructure 구현 타입을 import하지 않는다.
- JPA Entity를 API request/response, command 또는 result로 사용하지 않는다.
- repository와 외부 provider port는 `application/port/out`에 둔다.
- 트랜잭션, 소유권, 작성 제한과 상태 전이는 application service에 둔다.
- 외부 API와 provider별 DTO는 infrastructure adapter 안에서만 사용한다.
- Entity 연관관계는 필요한 자식→부모 단방향 `LAZY` 관계만 사용한다.
- 모든 Entity는 `BaseEntity`의 `Long` ID와 `GenerationType.IDENTITY`를 사용한다.
- 시각은 UTC로 저장하고 API에서는 RFC 3339로 반환한다. 방문 연월 검증에만 `Asia/Seoul` 기준을 사용한다.

## 4. 인증과 권한

### 4.1 OAuth 로그인 체인

`spring-boot-starter-oauth2-client`에 카카오 provider를 사용자 정의 등록한다.

```text
GET /api/v1/auth/oauth2/authorization/kakao
  -> 카카오 authorization endpoint
GET /api/v1/auth/oauth2/callback/kakao
  -> authorization code 교환
  -> Kakao user info 조회
  -> OAuthAccount 확인 또는 생성
  -> 약관/활성 상태에 따른 Rider Voice token 응답
```

- 사용자 식별에는 카카오 user info의 `id`만 사용한다.
- OAuth handshake 동안 Spring Security의 임시 HTTP session으로 `state`를 관리한다.
- 성공 또는 실패 후 임시 session을 폐기한다.
- `KAKAO_CLIENT_SECRET`이 없으면 client authentication `none`, 있으면 `client_secret_post`를 사용한다.
- 카카오 access token은 user info 확인 뒤 저장하지 않는다.
- 성공 handler는 provider 타입을 application에 넘기지 않고 `provider`와 `subject`로 command를 만든다.

### 4.2 REST API 체인

- `/api/v1/**` API는 stateless chain으로 처리한다.
- OAuth 임시 session의 `SecurityContext`로 REST API에 접근할 수 없다.
- 약관 미동의 사용자는 5분짜리 onboarding token만 발급받는다.
- 활성 사용자는 15분 access token과 30일 rotating refresh token을 사용한다.
- refresh token은 원문이 아니라 hash로 저장하고 갱신 시 회전시킨다.
- 로그아웃은 Rider Voice session을 폐기하며 카카오 로그아웃을 호출하지 않는다.

`UserRole`은 `USER`, `ADMIN`을 가진다. access token 인증 시 사용자의 현재 DB role을 읽어 수동 관리자 승격이 기존 access token에도 반영되게 한다.

## 5. 음식점 도메인

### 5.1 데이터 모델

```text
PickupLocation 1 <- N Restaurant 1 <- N RestaurantExternalReference
                           |
                           + <- N RestaurantPlatform
```

`PickupLocation`

- 표준 주소, 정규화 주소, 선택 상세 위치
- `location_key` unique
- 위도, 경도, 등록 출처

`Restaurant`

- 배달 브랜드명과 정규화 브랜드명
- `pickup_location_id` 단방향 `LAZY` FK
- 상태: `ACTIVE`, `MERGED`
- nullable `canonical_restaurant_id`
- `(pickup_location_id, normalized_name)` unique

`RestaurantExternalReference`

- provider와 external place ID
- 외부 응답의 마지막 확인 정보
- `(provider, external_place_id)` unique

`RestaurantPlatform`

- `BAEMIN`, `COUPANG_EATS`, `YOGIYO`, `OTHER`
- 플랫폼은 선택 메타데이터이며 공개 동일성 증거로 사용하지 않는다.

### 5.2 검색과 지연 등록

공개 검색은 내부 브랜드를 먼저 조회하고 카카오 키워드 검색 결과와 외부 참조 ID로 병합한다. 미등록 카카오 후보는 내부 ID 없이 반환한다.

첫 리뷰의 음식점 target은 다음 중 하나다.

- `EXISTING`: canonical 내부 음식점
- `KAKAO`: 원 검색어와 선택한 카카오 장소 ID
- `MANUAL_EXISTING_LOCATION`: 내부 픽업 장소와 새 브랜드명
- `MANUAL_ADDRESS`: 원 주소 검색어, 선택 표준 주소, 상세 위치와 브랜드명

외부 검증은 DB 트랜잭션 전에 수행한다. 트랜잭션 안에서는 장소, 브랜드와 외부 참조를 다시 조회하고 생성한다. unique 충돌은 동시 요청의 승자 데이터를 재조회해 처리한다. 음식점 생성과 첫 리뷰 저장은 같은 트랜잭션에서 완료하거나 함께 실패한다.

카카오 장소 ID는 클라이언트가 보낸 값만 신뢰하지 않고 같은 키워드 검색 결과에 포함됐을 때만 연결한다. 수동 주소도 같은 원 검색어로 주소 검색을 반복해 선택 표준 주소를 검증한다.

## 6. 리뷰 도메인

### 6.1 데이터 모델

`Review`

- 작성자와 음식점 FK
- 방문 연월
- 6개 `ReviewRating`
- nullable 의견과 의견 검수 상태
- 공개 상태: `ACTIVE`, `EXCLUDED`
- 작성자·음식점 기준 제출 순번
- 생성·수정 시각

`AuthorRestaurantReviewState`

- `(author_user_id, restaurant_id)` unique
- `last_submitted_at`
- `last_sequence`
- nullable `current_review_id`

리뷰 반복 작성 때문에 `(author_user_id, restaurant_id)`를 `reviews`의 unique로 두지 않는다. 대신 상태 row를 잠그고 마지막 제출 시각과 순번으로 90일 정책과 현재 리뷰를 직렬화한다.

### 6.2 작성·수정·삭제

- 방문 연월은 한국 시간 기준 현재 또는 직전 달만 허용한다.
- 같은 음식점은 `lastSubmittedAt + 90일` 이후 다시 작성할 수 있다.
- 새 리뷰가 등록되면 이전 리뷰는 과거 이력이 되고 현재 리뷰만 수정할 수 있다.
- 방문 연월은 수정할 수 없다.
- 최신 리뷰를 hard delete하면 `currentReviewId`만 비우고 마지막 제출 시각과 순번은 유지한다.
- 삭제나 전체 제외 후 과거 리뷰를 현재 상태로 복원하지 않는다.
- 타인 리뷰와 수정 불가능한 과거 리뷰는 `404 Not Found`로 처리한다.

## 7. 공개 조회와 집계

개별 리뷰는 `ACTIVE`이면 첫 작성부터 공개한다. 의견은 `PUBLISHED` 상태일 때만 포함한다. 공개 작성자 정보는 고정 공개 ID나 닉네임 없이 활동 기간과 공개 리뷰 수만 제공한다.

집계 상태:

- `NO_REVIEWS`: 유효 현재 작성자 0명
- `COLLECTING`: 1~4명
- `PUBLISHED`: 5명 이상

브랜드 집계는 작성자별 해당 브랜드의 현재 리뷰를 사용한다. 장소 집계는 동일 작성자의 여러 브랜드 현재 리뷰 중 생성 시각과 ID가 가장 최신인 하나만 사용한다.

`NOT_OBSERVED`는 작성자 표본과 값별 개수에는 포함하지만 평가 비율 분모에서는 제외한다. 한 항목의 관찰값이 0개라면 비율 대신 관찰값 없음 상태를 반환한다.

초기 MAU 1,000 단계에서는 별도 aggregate entity나 batch를 만들지 않고 조회 쿼리와 application 집계로 계산한다. 상태 FK, 음식점 FK, 작성자 FK, 공개 상태와 생성 시각에 필요한 인덱스를 둔다.

## 8. 의견 검수와 신고

의견 상태는 `NONE`, `PENDING`, `PUBLISHED`, `REJECTED`, `HIDDEN_REPORTED`다.

- 의견 입력·수정은 `PENDING`으로 전환한다.
- 승인 전에는 구조화 평가만 공개한다.
- 공개 의견 신고 접수 시 의견을 `HIDDEN_REPORTED`로 전환한다.
- 신고 기각 시 이전 의견 상태를 복원한다.
- 의견 위반이면 의견만 비공개 처리한다.
- 허위·도배이면 리뷰를 `EXCLUDED`로 전환하고 현재 리뷰 포인터를 제거한다.
- 전체 제외 후에도 90일 작성 제한 상태를 유지한다.

`ReviewReport`, `RestaurantInfoReport`, `ModerationAudit`을 두고 신고자·대상·사유·처리 상태와 관리자 결정을 저장한다. 한 사용자는 같은 대상에 한 번만 신고할 수 있다.

중복 음식점 병합은 duplicate를 `MERGED`로 표시하고 canonical ID를 남긴다. 검색은 canonical 음식점만 반환하고 기존 ID 조회와 작성 요청은 canonical 음식점으로 해석한다.

## 9. API 계약

```text
# OAuth와 service token
GET    /api/v1/auth/oauth2/authorization/kakao
GET    /api/v1/auth/oauth2/callback/kakao
POST   /api/v1/auth/consents
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/users/me

# 공개 조회
GET    /api/v1/restaurants/search
GET    /api/v1/restaurants/{restaurantId}
GET    /api/v1/restaurants/{restaurantId}/reviews

# 작성자
GET    /api/v1/addresses/search
POST   /api/v1/reviews
GET    /api/v1/users/me/reviews
PATCH  /api/v1/reviews/{reviewId}
DELETE /api/v1/reviews/{reviewId}
POST   /api/v1/reviews/{reviewId}/reports
POST   /api/v1/restaurants/{restaurantId}/reports

# 관리자
GET/PATCH /api/v1/admin/review-comments/**
GET/PATCH /api/v1/admin/review-reports/**
GET/PATCH /api/v1/admin/restaurant-reports/**
POST      /api/v1/admin/restaurants/{restaurantId}/merge
PATCH     /api/v1/admin/restaurants/{restaurantId}/pickup-location
```

목록은 생성 시각과 ID 기반 cursor pagination을 사용한다. 성공 응답은 기능별 DTO, 오류는 안정적인 `code`를 포함한 `ProblemDetail`을 사용한다.

## 10. 캐시·호출 제한·장애 처리

- 검색어는 정규화 후 2~100자로 제한한다.
- 검색 결과는 최대 20개다.
- 카카오 성공 검색 결과는 Caffeine에 5분간 저장한다.
- 검색은 호출자 기준 분당 30회로 제한한다.
- 리뷰는 계정당 최근 24시간 최대 10개다.
- 신고는 계정당 하루 최대 20개다.
- 카카오 장애 시 공개 검색은 내부 결과와 `externalSearchStatus=UNAVAILABLE`을 반환한다.
- 카카오·주소 기반 신규 등록은 provider 검증이 불가능하면 `503`으로 실패한다.

메모리 캐시와 rate limit은 단일 인스턴스에만 유효하다. 다중 인스턴스가 필요해질 때 Redis 등 분산 저장소를 별도 결정한다.

## 11. 테스트 전략

- OAuth redirect, state, code 교환, user info와 임시 session 폐기
- OAuth session으로 stateless API 접근 불가
- onboarding, opaque token 회전, logout과 USER/ADMIN 권한
- 카카오·주소 adapter의 성공, timeout, rate limit과 손상 응답
- 같은 장소 여러 브랜드, 같은 브랜드 다른 주소와 수동/카카오 참조 연결
- 장소·브랜드·외부 참조의 동시 unique 충돌
- 90일 전후 작성, 최신/과거 수정, 삭제 후 재작성 차단
- 브랜드·장소 각각 작성자 4명/5명 경계
- 장소 집계의 작성자 중복 제거와 `NOT_OBSERVED` 처리
- 의견 승인·수정·신고·기각·전체 제외
- 삭제·제외로 5명 미만이 될 때 집계 비공개 전환
- canonical 음식점 처리
- 공개·USER·ADMIN endpoint와 OpenAPI DTO 계약
- 실행 중인 로컬 MySQL에서 schema, FK, index, unique와 동시 요청 검증
