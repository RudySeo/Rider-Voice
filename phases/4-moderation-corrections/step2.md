# Step 2: abuse-report-api

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/4-moderation-corrections/index.json`
- `/src/main/kotlin/com/ridervoice/api/moderation/`

## 작업

공개 자유 의견 신고 domain/application service와 `POST /api/v1/comments/{id}/reports`를 구현한다. 신고 사유를 검증하고 동일 신고자의 반복 요청을 멱등하게 처리하며 신고 접수만으로 원문을 자동 삭제하지 않는다. ProblemDetail과 Swagger 계약을 추가한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 존재하지 않거나 비공개 comment, 중복 신고와 정상 접수를 테스트한다.
2. index step 2를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 신고자에게 작성자 내부 ID를 반환하지 말 것. 이유: 익명성 정책을 위반한다.
- 신고 수만으로 comment를 자동 삭제하지 말 것. 이유: 관리자 검수 절차가 필요하다.
- 기존 test를 깨뜨리지 말 것.
