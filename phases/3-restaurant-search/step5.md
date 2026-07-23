# Step 5: restaurant-presentation-api

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/3-restaurant-search/index.json`
- `/src/main/kotlin/com/ridervoice/api/restaurant/application/port/in/RestaurantUseCase.kt`
- `/src/main/kotlin/com/ridervoice/api/restaurant/application/model/`
- `/src/main/kotlin/com/ridervoice/api/common/config/OpenApiConfiguration.kt`

## 작업

`GET /api/v1/restaurants/search`와 `POST /api/v1/restaurants`를 구현한다. Controller, `presentation/dto/RestaurantRequests.kt`, `RestaurantResponses.kt`와 HTTP mapper를 분리한다. Controller는 `RestaurantUseCase`에만 의존하며 request를 command로, result를 response로 변환한다. 두 endpoint는 `ROLE_USER`를 요구한다. query와 kakaoPlaceId를 Bean Validation으로 검증하고 OpenAPI 목적, 인증과 schema를 기록한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 인증, validation, DTO mapping, ProblemDetail과 `/v3/api-docs` 계약을 MockMvc로 검증한다.
2. `phases/3-restaurant-search/index.json`의 step 5를 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- Controller 파일 안에 공개 request/response DTO를 선언하지 말 것. 이유: HTTP 계약과 endpoint 코드를 분리한다.
- application result나 JPA Entity를 API response로 직접 반환하지 말 것. 이유: 계층별 모델을 분리해야 한다.
- 기존 test를 깨뜨리지 말 것.
