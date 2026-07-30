# Step 1: moderation-persistence

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- step 0 domain
- review/restaurant persistence adapters

## 작업

- `ReviewReport`, `RestaurantInfoReport`, `ModerationAudit` Entity와 repository adapter를 구현한다.
- 한 reporter가 같은 target을 한 번만 신고하도록 DB unique를 둔다.
- pending queue cursor 조회와 admin decision write를 지원한다.
- audit는 actor, action, target type/ID, reason, before/after와 UTC timestamp를 보존한다.
- 필요한 report status/createdAt index를 추가한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. repository contract와 integration-tag unique/FK test를 작성한다.
2. 성공 시 step 1을 `completed`로 기록한다.
3. 기본 test는 MySQL 없이 통과해야 한다.

## 하지 말 것

- report/audit를 cascade delete하지 말 것.
- presentation DTO를 persistence에 저장하지 말 것.
- Flyway나 DB trigger를 추가하지 말 것.
