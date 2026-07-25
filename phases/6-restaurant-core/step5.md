# Step 5: restaurant-search-api

## 읽을 파일

- `/docs/API_SPEC.md`
- `/AGENTS.md`
- step 1 input ports/results
- step 4 service
- common security/OpenAPI patterns

## 작업

- 공개 `GET /api/v1/restaurants/search`를 추가한다.
- USER `GET /api/v1/addresses/search`를 추가한다.
- request/response DTO와 HTTP mapper를 presentation 하위 별도 파일로 둔다.
- query 2~100자, 결과 최대 20개 계약과 external search status를 노출한다.
- security와 OpenAPI에 public/USER 요구사항을 반영한다.
- `POST /api/v1/restaurants`는 추가하지 않는다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. MockMvc와 `/v3/api-docs` schema를 검증한다.
2. 성공 시 step 5를 `completed`로 기록한다.
3. 실패 3회 시 `error`, 계약 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- Controller에서 provider adapter/repository를 호출하지 말 것.
- same-location sibling brand 목록을 response에 넣지 말 것.
- 아직 review aggregate를 구현하지 말 것.
