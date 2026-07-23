# Rider Voice API 명세 및 테스트 결과

> 기준일: 2026-07-23
> 구현 기준: `feature/long-identity-relations`
> Base URL: `http://localhost:8080`
> API prefix: `/api/v1`

## 1. 현재 구현 범위

현재 구현된 사용자 흐름은 다음과 같다.

```text
카카오 로그인 URL 생성
  -> 카카오 로그인 callback
  -> 신규 사용자는 필수 약관 동의
  -> 서비스 access/refresh token 발급
  -> 음식점 검색
  -> 카카오 장소 선택
  -> 내부 음식점 멱등 등록
```

별도의 회원가입 endpoint는 없다. 카카오 callback에서 외부 계정을 확인하고 신규 사용자를 생성하며, 신규 사용자가 필수 약관에 동의하면 `ACTIVE` 상태로 전환한다.

비공개 리뷰 CRUD는 다음 구현 대상이며 이 문서의 구현 완료 API에 포함하지 않는다.

## 2. 인증 방식

| 구분 | Header | 사용 범위 |
| --- | --- | --- |
| 공개 | 없음 | 로그인 URL, callback, token 갱신 |
| 온보딩 | `Authorization: Bearer {onboardingToken}` | 신규 사용자의 필수 약관 동의 |
| 사용자 | `Authorization: Bearer {accessToken}` | 로그아웃, 내 정보, 음식점 검색·등록 |

- onboarding token은 callback에서 신규 사용자에게 발급되며 5분 동안 유효하다.
- access token은 15분, refresh token은 30일 동안 유효하다.
- refresh token은 서버에 원문으로 저장하지 않고 해시로 저장하며 갱신 시 회전한다.
- 카카오 access token과 카카오 계정 정보는 공개 API 응답에 포함하지 않는다.

## 3. API 목록

| Method | Endpoint | 인증 | 설명 | 성공 상태 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/auth/kakao/authorize` | 공개 | 카카오 로그인 URL 생성 | `200` |
| `GET` | `/api/v1/auth/kakao/callback` | 공개 | 카카오 로그인 callback 처리 | `200` |
| `POST` | `/api/v1/auth/consents` | 온보딩 | 필수 약관 동의 및 정식 token 발급 | `200` |
| `POST` | `/api/v1/auth/refresh` | 공개 | access/refresh token 갱신 | `200` |
| `POST` | `/api/v1/auth/logout` | 사용자 | 사용자 session 폐기 | `204` |
| `GET` | `/api/v1/users/me` | 사용자 | 현재 사용자 조회 | `200` |
| `GET` | `/api/v1/restaurants/search` | 사용자 | 내부·카카오 음식점 후보 검색 | `200` |
| `POST` | `/api/v1/restaurants` | 사용자 | 선택한 카카오 장소의 내부 음식점 등록 | `200` |

## 4. 인증 및 사용자 API

### 4.1 카카오 로그인 URL 생성

```http
GET /api/v1/auth/kakao/authorize
```

로그인 위조 방지용 `state`를 생성·저장하고 해당 값이 포함된 카카오 OAuth 인증 URL을 반환한다.

응답 예시:

```json
{
  "authorizationUrl": "https://kauth.kakao.com/oauth/authorize?..."
}
```

### 4.2 카카오 로그인 callback

```http
GET /api/v1/auth/kakao/callback?code={authorizationCode}&state={state}
```

Query parameter:

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `code` | `String` | 예 | 카카오가 발급한 authorization code |
| `state` | `String` | 예 | 로그인 요청 위조 방지를 위한 state |

신규 사용자 또는 약관 미동의 사용자 응답 예시:

```json
{
  "user": {
    "id": 1,
    "status": "PENDING_TERMS",
    "termsVersion": null
  },
  "termsAgreed": false,
  "tokens": null,
  "onboardingToken": "onboarding-token"
}
```

이미 활성화된 사용자 응답 예시:

```json
{
  "user": {
    "id": 1,
    "status": "ACTIVE",
    "termsVersion": "2026-07-01"
  },
  "termsAgreed": true,
  "tokens": {
    "accessToken": "access-token",
    "refreshToken": "refresh-token",
    "user": {
      "id": 1,
      "status": "ACTIVE",
      "termsVersion": "2026-07-01"
    }
  },
  "onboardingToken": null
}
```

### 4.3 필수 약관 동의

```http
POST /api/v1/auth/consents
Authorization: Bearer {onboardingToken}
Content-Type: application/json
```

요청:

```json
{
  "termsVersion": "2026-07-01"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `termsVersion` | `String` | 예 | 공백일 수 없음 |

약관 동의가 완료되면 사용자는 `ACTIVE` 상태가 되고 정식 token을 반환한다.

응답 예시:

```json
{
  "accessToken": "access-token",
  "refreshToken": "refresh-token",
  "user": {
    "id": 1,
    "status": "ACTIVE",
    "termsVersion": "2026-07-01"
  }
}
```

### 4.4 서비스 token 갱신

```http
POST /api/v1/auth/refresh
Content-Type: application/json
```

요청:

```json
{
  "refreshToken": "current-refresh-token"
}
```

유효한 refresh token을 소비하고 새로운 access token과 refresh token을 발급한다. 응답 형식은 약관 동의의 token 응답과 같다.

### 4.5 로그아웃

```http
POST /api/v1/auth/logout
Authorization: Bearer {accessToken}
Content-Type: application/json
```

요청:

```json
{
  "refreshToken": "current-refresh-token"
}
```

요청 사용자에게 속한 session을 폐기하고 본문 없이 `204 No Content`를 반환한다.

### 4.6 현재 사용자 조회

```http
GET /api/v1/users/me
Authorization: Bearer {accessToken}
```

응답 예시:

```json
{
  "id": 1,
  "status": "ACTIVE",
  "termsVersion": "2026-07-01"
}
```

## 5. 음식점 API

### 5.1 음식점 검색

```http
GET /api/v1/restaurants/search?query={검색어}
Authorization: Bearer {accessToken}
```

Query parameter:

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `query` | `String` | 예 | 공백일 수 없음 |

서버는 내부 DB의 음식점과 카카오 로컬 API의 `FD6` 음식점 후보를 합친다. 같은 `kakaoPlaceId`는 하나의 후보로 병합한다.

응답 예시:

```json
{
  "candidates": [
    {
      "restaurantId": null,
      "kakaoPlaceId": "1234567890",
      "name": "라이더보이스 강남점",
      "address": "서울 강남구 테헤란로 1",
      "latitude": 37.4987654,
      "longitude": 127.0276543
    },
    {
      "restaurantId": 10,
      "kakaoPlaceId": "9876543210",
      "name": "라이더보이스 역삼점",
      "address": "서울 강남구 역삼로 1",
      "latitude": 37.5000000,
      "longitude": 127.0300000
    }
  ]
}
```

- `restaurantId == null`: 카카오에는 있지만 내부 DB에는 아직 등록되지 않은 장소다.
- `restaurantId != null`: 이미 내부 음식점으로 등록된 장소다.

### 5.2 선택한 카카오 장소 등록

```http
POST /api/v1/restaurants
Authorization: Bearer {accessToken}
Content-Type: application/json
```

요청:

```json
{
  "query": "강남 분식",
  "kakaoPlaceId": "1234567890"
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `query` | `String` | 예 | 장소 선택에 사용한 원 검색어, 공백일 수 없음 |
| `kakaoPlaceId` | `String` | 예 | 선택한 카카오 장소 ID, 공백일 수 없음 |

처리 규칙:

1. 서버가 전달받은 원 검색어로 카카오 키워드 검색을 다시 수행한다.
2. 결과에 같은 `kakaoPlaceId`가 있는 경우에만 등록을 허용한다.
3. 이름, 주소와 좌표는 클라이언트 입력이 아닌 카카오 응답을 사용한다.
4. 이미 등록된 장소면 기존 내부 음식점을 반환한다.
5. 동시 요청에서도 `kakao_place_id` unique 제약과 충돌 후 재조회로 하나의 음식점만 생성한다.
6. 최초 등록자에게 음식점 소유권이나 수정 권한을 부여하지 않는다.

응답 예시:

```json
{
  "restaurantId": 10,
  "kakaoPlaceId": "1234567890",
  "name": "라이더보이스 강남점",
  "address": "서울 강남구 테헤란로 1",
  "latitude": 37.4987654,
  "longitude": 127.0276543
}
```

원 검색 결과에서 선택한 장소를 확인할 수 없으면 `404 Not Found`를 반환한다.

## 6. 공통 오류 응답

오류 응답은 `Content-Type: application/problem+json`인 RFC 7807 `ProblemDetail`을 사용한다. 외부 provider 오류, secret과 stack trace는 응답에 노출하지 않는다.

예시:

```json
{
  "type": "urn:ridervoice:error:validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request validation failed.",
  "instance": "/api/v1/restaurants/search",
  "code": "VALIDATION_FAILED"
}
```

| HTTP 상태 | 대표 code | 의미 |
| --- | --- | --- |
| `400` | `VALIDATION_FAILED`, `BAD_REQUEST` | Bean Validation 실패 또는 잘못된 요청 |
| `401` | `AUTHENTICATION_REQUIRED` | token 누락·만료·오류 |
| `403` | `ACCESS_DENIED` | endpoint에 필요한 role 없음 |
| `404` | `RESOURCE_NOT_FOUND` | 요청한 리소스나 재검증 장소를 찾지 못함 |
| `409` | `STATE_CONFLICT` | 현재 상태와 충돌하는 요청 |
| `500` | `INTERNAL_ERROR` | 클라이언트에 세부 원인을 노출하지 않는 서버 오류 |

## 7. Swagger와 운영 확인 API

서버 실행 후 다음 주소에서 실제 OpenAPI 계약을 확인할 수 있다.

| 용도 | URL | 인증 |
| --- | --- | --- |
| Swagger UI | `http://localhost:8080/swagger-ui.html` | 공개 |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` | 공개 |
| OpenAPI YAML | `http://localhost:8080/v3/api-docs.yaml` | 공개 |
| Health | `http://localhost:8080/actuator/health` | 공개 |

## 8. 테스트 결과

### 8.1 기본 테스트 강제 재실행

실행 명령:

```bash
./gradlew test --rerun-tasks --no-daemon
```

최신 결과:

| 항목 | 결과 |
| --- | ---: |
| 전체 테스트 | 87 |
| 성공 | 87 |
| 실패 | 0 |
| 건너뜀 | 0 |
| 성공률 | 100% |
| 테스트 suite 실행 시간 | 8.314초 |
| Gradle 결과 | `BUILD SUCCESSFUL` |

전체 프로젝트 검증 명령도 성공했다.

```bash
./gradlew check build
```

### 8.2 검증 범위

- 인증 domain 정책: 사용자 상태, OAuth state, onboarding token과 session 정책
- 인증 application: 신규·기존 사용자 callback, 약관 동의, token 회전과 로그아웃
- 인증 API 계약: 공개 endpoint, onboarding/user role, 요청 검증과 응답 계약
- 공통 기반: 보안 정책, `ProblemDetail`, OpenAPI 설정
- 음식점 domain: 장소 ID, 이름·주소와 좌표 불변식
- 음식점 application: 내부·카카오 결과 병합, 원 검색어 재검증과 멱등 등록
- 카카오 로컬 adapter: 성공, 빈 결과, timeout, rate limit, 4xx/5xx와 손상 응답
- 음식점 API 계약: `ROLE_USER`, Bean Validation, 오류 응답과 공개 DTO/OpenAPI schema

### 8.3 로컬 MySQL 통합 테스트

기본 `test` 결과에는 로컬 MySQL을 사용하는 별도 통합 테스트가 포함되지 않는다. Hibernate schema 생성, JPA 연관관계와 DB unique 제약은 로컬 `rider` 데이터베이스가 실행 중일 때 다음 명령으로 검증한다.

```bash
./gradlew integrationTest
```

최신 통합 테스트 결과는 9개 성공, 실패·오류·건너뜀 0개이며 테스트 suite 실행 시간은 12.739초다. 실제 schema에서도 6개 Entity의 `id`가 `BIGINT AUTO_INCREMENT`이고 `oauth_accounts.user_id`, `onboarding_tokens.user_id`, `user_sessions.user_id`, `user_sessions.rotated_to_session_id` 외래 키가 생성된 것을 확인했다.

Flyway나 별도 migration 파일은 사용하지 않는다. 이 문서의 기본 테스트 87개에는 `integrationTest` 9개를 합산하지 않으며 Docker와 Testcontainers도 사용하지 않는다.

## 9. 다음 구현 대상

다음 endpoint는 MVP 계약에 계획되어 있지만 아직 구현되지 않았다.

```text
POST   /api/v1/reviews
GET    /api/v1/reviews
GET    /api/v1/reviews/{reviewId}
PATCH  /api/v1/reviews/{reviewId}
DELETE /api/v1/reviews/{reviewId}
```

리뷰는 6개의 필수 `ReviewRating`과 최대 200자의 선택 의견으로 구성하고 작성자 본인에게만 공개할 예정이다. 구현이 완료되기 전에는 위 endpoint를 사용할 수 없다.
