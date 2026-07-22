# Step 2: review-api

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/3-reviews-reports/step0.md`
- `/phases/3-reviews-reports/step1.md`

## 작업
`GET /api/v1/write-grants/{id}`, `POST /api/v1/write-grants/{id}/review`, `GET /api/v1/users/me/reviews`를 구현한다. grant consume과 Review 저장을 하나의 transaction으로 묶고 idempotency를 지원한다.

## 인수 기준
```bash
./gradlew test
./gradlew build
```

## 하지 말 것
- 작성자 카카오 subject나 내부 user ID를 public review 응답에 포함하지 말 것. 이유: 익명성 정책이다.
