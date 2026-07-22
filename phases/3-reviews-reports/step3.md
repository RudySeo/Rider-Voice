# Step 3: review-api

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/3-reviews-reports/step2.md`

## 작업
`GET /api/v1/write-grants/{id}`, `POST /api/v1/write-grants/{id}/review`, `GET /api/v1/users/me/reviews`를 구현한다. Bean Validation, idempotency header, Bearer 인증, cursor pagination, 기능별 response DTO, ProblemDetail과 Swagger schema를 포함한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- JPA Entity를 request/response로 사용하지 말 것. 이유: persistence 구조를 API에 노출한다.
- Controller에서 grant 상태를 변경하지 말 것. 이유: transaction invariant가 깨진다.
- 기존 test를 깨뜨리지 말 것.
