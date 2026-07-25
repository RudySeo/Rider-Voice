# Step 3: public-review-list

## 읽을 파일

- `/docs/API_SPEC.md`
- `/AGENTS.md`
- phase 7 review history/state
- common cursor patterns

## 작업

- public `GET /api/v1/restaurants/{restaurantId}/reviews`를 구현한다.
- ACTIVE non-deleted history를 createdAt+ID 역순 cursor로 반환한다.
- current flag는 state current pointer와 비교한다.
- comment는 PUBLISHED일 때만 반환하고 다른 상태는 null로 처리한다.
- author는 공개 ID/닉네임 없이 활동 개월과 공개 review count만 제공한다.
- response item에 UNVERIFIED를 포함한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. history/current, comment visibility, excluded/deleted, cursor와 anonymous author activity를 검증한다.
2. 성공 시 step 3을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 개인정보 정책 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- stable public author ID를 추가하지 말 것.
- PENDING/REJECTED/HIDDEN comment를 노출하지 말 것.
- history review를 aggregate current로 취급하지 말 것.
