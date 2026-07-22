# Step 4: restaurant-search-api

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-restaurant-search/index.json`
- `/src/main/kotlin/com/ridervoice/api/restaurant/application/`
- `/src/main/kotlin/com/ridervoice/api/common/config/OpenApiConfiguration.kt`

## 작업

`GET /api/v1/restaurants/search` Controller와 request/response DTO를 구현한다. query, latitude, longitude와 radius를 Bean Validation으로 검증하고 application result를 공개 DTO로 변환한다. Swagger summary, parameter 설명, 성공/오류 schema를 추가한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. DTO validation과 OpenAPI path 생성을 검증한다.
2. index의 step 4를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- JPA Entity나 카카오 provider DTO를 응답으로 사용하지 말 것. 이유: 공개 API 계약을 분리해야 한다.
- Controller에서 카카오 API를 호출하지 말 것. 이유: infrastructure 경계를 우회한다.
- 기존 test를 깨뜨리지 말 것.
