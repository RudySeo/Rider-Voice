# Step 5: admin-audit-api

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/4-moderation-corrections/index.json`
- `/src/main/kotlin/com/ridervoice/api/moderation/`
- `/src/main/kotlin/com/ridervoice/api/correction/`

## 작업

관리자 검수·신고·정정 처리 API와 AuditLog domain/persistence를 `/api/v1/admin` 아래 구현한다. ADMIN role을 요구하고 모든 상태 변경에 행위자, 대상, UTC 시각, 사유, 변경 전후 metadata를 같은 transaction에서 기록한다. 관리자만 작성자와 리뷰 내부 연결을 조회할 수 있다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. anonymous/user/admin role별 접근과 감사 로그 원자성을 테스트한다.
2. index step 5를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 관리자 상태 변경을 감사 로그 없이 commit하지 말 것. 이유: 운영 추적성이 사라진다.
- public API에서 작성자 내부 연결을 노출하지 말 것. 이유: 익명성 정책을 위반한다.
- 기존 test를 깨뜨리지 말 것.
