# Step 0: moderation-domain

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/review/domain/`

## 작업

`moderation/domain`에 ModerationCase, 대상 종류, 탐지 규칙, 상태 전이와 관리자 판단을 구현한다. 개인정보·연락처·주문번호·혐오 표현·단정적 범죄/위생 주장 탐지 결과를 모델링하되 자동 규칙은 최종 공개/삭제를 결정하지 않는다.

## 인수 기준

```bash
./gradlew test
```

## 검증

1. 허용/금지 상태 전이와 판단 사유 필수성을 단위 테스트한다.
2. phase index step 0을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 규칙 탐지만으로 자유 의견을 자동 공개하거나 삭제하지 말 것. 이유: 관리자 최종 판단이 필요하다.
- enum field를 service에서 직접 덮어쓰지 말 것. 이유: domain 상태 전이가 우회된다.
- 기존 test를 깨뜨리지 말 것.
