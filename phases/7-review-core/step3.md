# Step 3: review-create-service

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- step 0~2 output
- phase 6 restaurant target use case
- auth USER principal model

## 작업

- 외부 target validation을 먼저 수행한 뒤 transaction에서 restaurant resolve와 review create를 처리한다.
- state row를 잠그고 90일, 24시간 계정당 10개와 sequence를 검증한다.
- 새 리뷰가 생기면 이전 current는 history로 남기고 state current pointer를 새 review로 교체한다.
- comment 입력 시 PENDING, 미입력 시 NONE으로 저장한다.
- restaurant resolve와 review 저장이 함께 성공하거나 롤백되게 한다.
- concurrent first state creation과 unique race를 winner 재조회로 처리한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. 90일 전/경계/후, 24시간 limit, atomic rollback과 concurrent create를 service test로 검증한다.
2. 성공 시 step 3을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 사용자 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- 외부 Kakao/address 호출을 DB transaction 안에서 수행하지 말 것.
- 삭제된 review 부재만 보고 cooldown을 허용하지 말 것.
- Controller에 transaction이나 정책을 두지 말 것.
