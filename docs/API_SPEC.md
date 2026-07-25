# Rider Voice 공개 리뷰 MVP API 계약

> 목표 계약 문서. 현재 구현 완료 여부와 관계없이 새 MVP의 공개 인터페이스를 정의한다. 구현 시 OpenAPI `/v3/api-docs`가 최종 실행 계약이 된다.

## 1. 공통 규칙

- Base URL: `http://localhost:8080`
- API prefix: `/api/v1`
- JSON 필드는 `camelCase`를 사용한다.
- Entity ID는 JSON 정수와 OpenAPI `int64`다.
- 시각은 UTC 기준 RFC 3339, 방문 연월은 `YYYY-MM`이다.
- 목록은 `cursor`와 `size`를 사용하며 기본 20개, 최대 50개다.
- 오류는 `application/problem+json`인 RFC 7807 `ProblemDetail`을 사용한다.
- 외부 provider 오류, token, secret과 stack trace를 응답에 노출하지 않는다.

공개 리뷰 관련 응답에는 다음 필드를 포함한다.

```json
{
  "verificationStatus": "UNVERIFIED",
  "verificationNotice": "라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다."
}
```

## 2. 인증 구분

| 구분 | 인증 | 용도 |
| --- | --- | --- |
| 공개 | 없음 | OAuth 시작·callback, 음식점 검색·상세·리뷰 조회, token 갱신 |
| 온보딩 | onboarding bearer token | 필수 약관 동의 |
| 사용자 | opaque access bearer token, `ROLE_USER` | 작성, 내 리뷰, 신고 |
| 관리자 | opaque access bearer token, `ROLE_ADMIN` | 검수, 신고 처리, 병합·정정 |

OAuth 임시 HTTP session은 authorization과 callback에서만 사용하며 REST API 인증으로 사용하지 않는다.

## 3. 인증 API

```text
GET  /api/v1/auth/oauth2/authorization/kakao
GET  /api/v1/auth/oauth2/callback/kakao
POST /api/v1/auth/consents
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/users/me
```

카카오 OAuth 성공 응답은 약관 상태에 따라 구분한다.

```json
{
  "termsAgreed": false,
  "onboardingToken": "one-time-token",
  "tokens": null
}
```

```json
{
  "termsAgreed": true,
  "onboardingToken": null,
  "tokens": {
    "accessToken": "access-token",
    "refreshToken": "refresh-token"
  }
}
```

## 4. 공개 음식점 API

### 검색

```http
GET /api/v1/restaurants/search?query=강남 분식
```

- `query`: 공백 제거 후 2~100자
- 결과 최대 20개
- 내부 브랜드와 카카오 후보를 병합
- 카카오 장애 시 내부 결과만 반환

```json
{
  "externalSearchStatus": "AVAILABLE",
  "candidates": [
    {
      "candidateType": "INTERNAL",
      "restaurantId": 10,
      "kakaoPlaceId": "1234567890",
      "name": "라이더보이스 강남점",
      "address": "서울 강남구 테헤란로 1",
      "aggregationStatus": "COLLECTING",
      "contributorCount": 3
    },
    {
      "candidateType": "KAKAO",
      "restaurantId": null,
      "kakaoPlaceId": "9876543210",
      "name": "등록 전 음식점",
      "address": "서울 강남구 역삼로 1",
      "aggregationStatus": "NO_REVIEWS",
      "contributorCount": 0
    }
  ]
}
```

`ExternalSearchStatus`: `AVAILABLE`, `UNAVAILABLE`

### 상세

```http
GET /api/v1/restaurants/{restaurantId}
```

응답은 브랜드, 중첩된 픽업 장소, 브랜드 리포트와 장소 리포트를 포함한다. 같은 픽업 장소의 다른 브랜드는 포함하지 않는다.

각 리포트는 다음 형식이다.

```json
{
  "status": "PUBLISHED",
  "contributorCount": 5,
  "metrics": {
    "packagingStability": {
      "observedCount": 4,
      "notObservedCount": 1,
      "distribution": {
        "VERY_GOOD": 50.0,
        "GOOD": 25.0,
        "NEEDS_IMPROVEMENT": 25.0,
        "MAJOR_IMPROVEMENT": 0.0
      }
    }
  }
}
```

`AggregationStatus`: `NO_REVIEWS`, `COLLECTING`, `PUBLISHED`

### 리뷰 목록

```http
GET /api/v1/restaurants/{restaurantId}/reviews?cursor={cursor}&size=20
```

```json
{
  "items": [
    {
      "reviewId": 100,
      "visitMonth": "2026-07",
      "current": true,
      "ratings": {
        "pickupSpaceCleanliness": "GOOD",
        "packagingStability": "VERY_GOOD",
        "orderReadiness": "GOOD",
        "handoffAccuracy": "GOOD",
        "staffInteraction": "NOT_OBSERVED",
        "riderRespect": "GOOD"
      },
      "comment": null,
      "authorActivity": {
        "activityMonths": 3,
        "publicReviewCount": 8
      },
      "createdAt": "2026-07-25T03:00:00Z",
      "verificationStatus": "UNVERIFIED"
    }
  ],
  "nextCursor": null
}
```

의견은 승인된 경우에만 `comment`에 포함한다.

## 5. 주소 검색 API

```http
GET /api/v1/addresses/search?query=서울 강남구 테헤란로 1
Authorization: Bearer {accessToken}
```

원 주소 검색어, 표준 주소, 지번 주소, 위·경도와 기존 픽업 장소 후보를 반환한다. 주소 선택 후 리뷰 작성 요청에서 원 검색어와 선택 표준 주소를 다시 전송한다.

## 6. 리뷰 작성 API

```http
POST /api/v1/reviews
Authorization: Bearer {accessToken}
Content-Type: application/json
```

공통 필드:

```json
{
  "restaurantTarget": {},
  "visitMonth": "2026-07",
  "pickupSpaceCleanliness": "GOOD",
  "packagingStability": "VERY_GOOD",
  "orderReadiness": "GOOD",
  "handoffAccuracy": "GOOD",
  "staffInteraction": "NOT_OBSERVED",
  "riderRespect": "GOOD",
  "comment": "픽업 동선이 잘 구분되어 있었습니다."
}
```

`comment`는 선택이며 trim 후 최대 200자다. 6개 평가는 모두 `VERY_GOOD`, `GOOD`, `NEEDS_IMPROVEMENT`, `MAJOR_IMPROVEMENT`, `NOT_OBSERVED` 중 하나여야 한다.

`restaurantTarget`은 OpenAPI discriminator `type`을 사용한다.

기존 음식점:

```json
{ "type": "EXISTING", "restaurantId": 10 }
```

카카오 후보:

```json
{
  "type": "KAKAO",
  "query": "강남 분식",
  "kakaoPlaceId": "1234567890"
}
```

기존 픽업 장소의 새 브랜드:

```json
{
  "type": "MANUAL_EXISTING_LOCATION",
  "pickupLocationId": 20,
  "name": "새 배달 브랜드",
  "platforms": ["BAEMIN"]
}
```

새 주소와 브랜드:

```json
{
  "type": "MANUAL_ADDRESS",
  "addressQuery": "서울 강남구 테헤란로 1",
  "selectedStandardAddress": "서울 강남구 테헤란로 1",
  "detailAddress": "지하 1층 픽업대",
  "name": "새 배달 브랜드",
  "platforms": ["COUPANG_EATS"]
}
```

음식점 등록과 리뷰 생성은 같은 use case에서 완료한다. provider 검증 실패는 `503`, 90일 이전 재작성은 `409 Conflict`다.

## 7. 내 리뷰 API

```text
GET    /api/v1/users/me/reviews?cursor={cursor}&size=20
PATCH  /api/v1/reviews/{reviewId}
DELETE /api/v1/reviews/{reviewId}
```

- 현재 리뷰만 수정·삭제할 수 있다.
- 방문 연월은 수정할 수 없다.
- 의견 수정은 다시 `PENDING`으로 전환한다.
- 타인 리뷰와 과거 리뷰는 `404 Not Found`다.
- 삭제 후에도 90일 작성 제한은 유지한다.

## 8. 신고 API

```text
POST /api/v1/reviews/{reviewId}/reports
POST /api/v1/restaurants/{restaurantId}/reports
```

한 계정은 같은 대상에 한 번만 신고할 수 있다. 신고 요청은 reason enum과 선택 상세 내용을 받는다. 리뷰 신고 접수 시 공개 의견만 임시 숨긴다.

## 9. 관리자 API

```text
GET   /api/v1/admin/review-comments
PATCH /api/v1/admin/review-comments/{reviewId}
GET   /api/v1/admin/review-reports
PATCH /api/v1/admin/review-reports/{reportId}
GET   /api/v1/admin/restaurant-reports
PATCH /api/v1/admin/restaurant-reports/{reportId}
POST  /api/v1/admin/restaurants/{restaurantId}/merge
PATCH /api/v1/admin/restaurants/{restaurantId}/pickup-location
```

리뷰 신고 결정:

- `DISMISS`: 신고 기각과 의견 상태 복원
- `HIDE_COMMENT`: 의견만 비공개
- `EXCLUDE_REVIEW`: 리뷰 전체 공개·집계 제외

음식점 병합 시 duplicate는 `MERGED` 상태와 canonical ID를 유지한다. 기존 ID 요청은 canonical 음식점으로 해석한다.

## 10. 현재 구현과 교체 대상

현재 코드에는 다음 기능이 구현돼 있다.

- 직접 구현한 카카오 OAuth와 opaque service token
- 카카오 장소 검색과 단일 `Restaurant.kakaoPlaceId` 기반 등록

새 목표 계약 구현에서는 다음을 교체한다.

- 직접 OAuth 흐름 → Spring Security OAuth2 Client
- 단일 음식점 → 픽업 장소·배달 브랜드·외부 참조
- 독립 `POST /restaurants` → 첫 리뷰 작성 시 지연 등록
- 인증 필요 음식점 검색 → 공개 검색

리뷰 공개·집계·검수·신고와 관리자 API는 신규 구현 대상이다.
