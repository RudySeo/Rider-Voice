# Step 2: review-persistence

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- step 0 domain
- step 1 output ports
- phase 6 restaurant persistence IDs/relations

## 작업

- `Review`와 `AuthorRestaurantReviewState` Entity mapping 및 adapters를 구현한다.
- state에 `(author_user_id, restaurant_id)` unique, lastSubmittedAt, lastSequence와 nullable currentReview FK를 둔다.
- review에 author, restaurant 단방향 LAZY FK, sequence, states와 timestamps를 둔다.
- state row를 write 시 비관적 잠금으로 조회하는 repository method를 제공한다.
- current review ownership 조회와 cursor list query를 구현한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. adapter contract와 integration-tag schema test를 작성한다.
2. 성공 시 step 2를 `completed`로 기록한다.
3. 기본 test가 MySQL 없이 통과해야 한다.

## 하지 말 것

- `(author_user_id, restaurant_id)`를 reviews table unique로 두지 말 것. 이유: 90일 이력을 허용한다.
- 양방향 relation이나 cascade delete를 추가하지 말 것.
- 삭제 시 state row를 cascade 삭제하지 말 것.
