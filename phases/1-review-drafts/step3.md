# Step 3: review-draft-api-tests

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-review-drafts/index.json`
- `/src/main/kotlin/com/ridervoice/api/review/`
- `/src/main/kotlin/com/ridervoice/api/common/config/OpenApiConfiguration.kt`

## 작업

`POST /api/v1/review-drafts`, `GET /api/v1/review-drafts/{id}`, `PATCH /api/v1/review-drafts/{id}`와 `GET /api/v1/users/me/review-drafts`를 구현한다. POST는 사용자·음식점당 활성 초안을 get-or-create하고, PATCH는 request version을 검증한다. Bearer 인증, Bean Validation, cursor pagination, 기능별 DTO, ProblemDetail과 Swagger schema를 포함하고 공개·집계 API에 초안이 나타나지 않는 회귀 테스트를 추가한다. 90일 이상 갱신되지 않은 초안과 탈퇴 사용자 초안의 삭제 경계를 명시한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. 인증, 소유권, validation, OpenAPI path와 공개·집계 제외를 검증한다.
2. get-or-create 중복 요청, version conflict와 stale access를 검증한다.
3. phase index step 3과 상위 phase 상태를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- JPA Entity를 request/response로 사용하지 말 것.
- 초안 API를 공개 endpoint로 허용하지 말 것. 이유: 작성자 전용 비공개 데이터다.
- 기존 test를 깨뜨리지 말 것.
