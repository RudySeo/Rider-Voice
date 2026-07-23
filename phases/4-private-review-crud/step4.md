# Step 4: review-presentation-api

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/4-private-review-crud/index.json`
- `/src/main/kotlin/com/ridervoice/api/review/application/port/in/ReviewUseCase.kt`
- `/src/main/kotlin/com/ridervoice/api/review/application/model/`
- `/src/main/kotlin/com/ridervoice/api/common/config/OpenApiConfiguration.kt`
- `/src/main/kotlin/com/ridervoice/api/common/security/`

## 작업

`POST /api/v1/reviews`, `GET /api/v1/reviews`, `GET/PATCH/DELETE /api/v1/reviews/{reviewId}`를 구현한다. Controller, `presentation/dto/ReviewRequests.kt`, `ReviewResponses.kt`와 HTTP mapper를 분리한다. Controller는 `ReviewUseCase`와 인증 principal만 사용한다. 6개 생성 평가의 필수값, PATCH의 최소 한 필드, enum, 의견 200자와 cursor를 Bean Validation으로 검증한다. 모든 endpoint에 `ROLE_USER`, 기능별 DTO, ProblemDetail과 OpenAPI 계약을 적용한다. DELETE는 `204 No Content`를 반환한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 인증, validation, DTO mapping, CRUD status code와 `/v3/api-docs` schema를 MockMvc로 검증한다.
2. `phases/4-private-review-crud/index.json`의 step 4를 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- Controller 파일 안에 공개 request/response DTO를 선언하지 말 것. 이유: endpoint와 HTTP 계약을 분리한다.
- application result나 JPA Entity를 API response로 직접 반환하지 말 것. 이유: presentation 경계를 유지해야 한다.
- 공개 음식점별 리뷰 endpoint를 추가하지 말 것. 이유: 현재 리뷰는 작성자 전용이다.
- 기존 test를 깨뜨리지 말 것.
