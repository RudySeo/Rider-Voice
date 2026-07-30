# Step 3: reporting-service

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- step 0~2 output
- review current state service/repository

## 작업

- USER review/restaurant report create use case와 하루 20개 제한을 구현한다.
- review report 접수 시 PUBLISHED comment만 HIDDEN_REPORTED로 임시 숨기고 이전 state를 복원 가능하게 보존한다.
- ADMIN `DISMISS`, `HIDE_COMMENT`, `EXCLUDE_REVIEW` 결정을 구현한다.
- EXCLUDE_REVIEW는 ACTIVE→EXCLUDED, current pointer null, no history fallback과 cooldown retention을 원자적으로 처리한다.
- restaurant info report decision과 audit를 구현한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. duplicate report, daily limit, hide/restore/exclude와 aggregate impact를 검증한다.
2. 성공 시 step 3을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 제품 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- report 접수 즉시 review 전체를 숨기지 말 것.
- exclusion 후 이전 history를 current로 복원하지 말 것.
- cooldown state를 삭제하지 말 것.
