# Step 4: review-owner-service

## 읽을 파일

- `/AGENTS.md`
- `/docs/API_SPEC.md`
- step 2 persistence
- step 3 create service

## 작업

- 본인의 current review만 update/delete하는 application service를 구현한다.
- visit month는 update command에 포함하지 않는다.
- rating/comment 일부 변경 후 전체 domain validation을 다시 적용한다.
- published comment 변경은 PENDING으로 전환하고 이전 공개 comment를 더 이상 노출하지 않는다.
- hard delete 후 state current pointer만 null로 만들고 lastSubmittedAt/lastSequence를 유지한다.
- 이전 history를 current로 복원하지 않는다.
- my review cursor list에 current/history와 moderation state를 제공한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. owner/current 성공, 타인/history 404, delete cooldown 유지와 no fallback을 검증한다.
2. 성공 시 step 4를 `completed`로 기록한다.
3. 실패 3회 시 `error`, 정책 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- 타인/history 요청을 403으로 반환하지 말 것. 이유: 존재 비노출 계약은 404다.
- delete 시 state를 제거하거나 이전 review를 current로 만들지 말 것.
- comment 수정 상태를 자동 승인하지 말 것.
