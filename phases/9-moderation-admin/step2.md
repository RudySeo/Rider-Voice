# Step 2: comment-moderation-service

## 읽을 파일

- `/AGENTS.md`
- `/docs/API_SPEC.md`
- step 0~1 moderation model
- review comment states and repository ports

## 작업

- ADMIN comment pending queue와 approve/reject use case를 구현한다.
- approve는 PENDING→PUBLISHED, reject는 PENDING→REJECTED로 전환한다.
- review author가 published comment를 수정한 경우 새 PENDING 상태만 처리한다.
- 모든 decision을 같은 transaction에서 audit로 기록한다.
- 이미 처리됐거나 target이 사라진 요청은 stable conflict/not-found로 처리한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. approve/reject/idempotency/concurrency와 audit를 service test로 검증한다.
2. 성공 시 step 2를 `completed`로 기록한다.
3. 실패 3회 시 `error`, 정책 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- comment를 자동 승인하지 말 것.
- admin role 검증을 Controller만 믿지 말 것.
- struct rating을 comment rejection과 함께 제외하지 말 것.
