# Step 5: review-api

## 읽을 파일

- `/docs/API_SPEC.md`
- `/AGENTS.md`
- step 1 input ports/results
- step 3~4 services
- common validation/security/OpenAPI patterns

## 작업

- USER `POST /api/v1/reviews`, `GET /api/v1/users/me/reviews`, `PATCH/DELETE /api/v1/reviews/{reviewId}`를 구현한다.
- target request는 OpenAPI discriminator `type`과 4개 subtype DTO를 사용한다.
- Bean Validation으로 필수 ratings, month format, comment 200자와 target fields를 검증한다.
- request→command, result→response mapping을 presentation에 둔다.
- 90일 conflict는 stable ProblemDetail code와 409로 반환한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. MockMvc 권한·validation·404/409와 `/v3/api-docs` discriminator를 검증한다.
2. 성공 시 step 5를 `completed`로 기록한다.
3. 실패 3회 시 `error`, API 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- JPA Entity나 application result를 response로 직접 반환하지 말 것.
- 라이더/방문 인증 필드를 request에 추가하지 말 것.
- 개별 review 공개 list를 이 phase에 구현하지 말 것.
