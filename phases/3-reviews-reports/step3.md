# Step 3: moderation-domain

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/phases/3-reviews-reports/step1.md`

## 작업
자유 의견의 규칙 탐지 결과와 `PENDING`, `APPROVED`, `REDACTED`, `REVISION_REQUIRED`, `REJECTED` 상태를 구현한다. 개인정보·연락처·주문번호·금지 표현을 탐지하는 순수 policy와 관리자 결정 사유를 저장한다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- 규칙 탐지만으로 자유 의견을 자동 공개하지 말 것. 이유: 최종 공개는 관리자 승인이다.
