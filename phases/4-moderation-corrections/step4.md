# Step 4: correction-api

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/4-moderation-corrections/index.json`
- `/src/main/kotlin/com/ridervoice/api/correction/`

## 작업

`POST /api/v1/restaurants/{id}/corrections`와 `GET /api/v1/corrections/{publicToken}`을 구현한다. request validation, rate limit application interface, 공개 상태 response DTO, ProblemDetail과 Swagger 계약을 추가한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 정상 접수, validation, 존재하지 않는 음식점/token과 공개 필드 제한을 테스트한다.
2. index step 4를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 내부 correction ID나 요청자 연락처를 public token 조회에 노출하지 말 것. 이유: 내부 연결과 개인정보가 노출된다.
- Controller에 ownership 검증 로직을 넣지 말 것. 이유: application 정책이어야 한다.
- 기존 test를 깨뜨리지 말 것.
