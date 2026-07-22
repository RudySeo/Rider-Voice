# Step 1: comment-moderation

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/4-moderation-corrections/index.json`
- `/src/main/kotlin/com/ridervoice/api/moderation/domain/`
- `/src/main/kotlin/com/ridervoice/api/review/domain/`

## 작업

자유 의견 제출 후 규칙 탐지, ModerationCase 생성, 관리자 승인·redact·수정 요구·거절을 처리하는 application service와 persistence를 구현한다. 관리자 결정은 처리자 ID, UTC 시각과 사유를 필수로 기록한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 개인정보 탐지와 각 관리자 결정 결과를 테스트한다.
2. index step 1을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 검수 전 원문을 공개 projection에 포함하지 말 것. 이유: 미검수 개인정보가 노출된다.
- 관리자 사유 없이 상태를 변경하지 말 것. 이유: 감사 가능성이 사라진다.
- 기존 test를 깨뜨리지 말 것.
