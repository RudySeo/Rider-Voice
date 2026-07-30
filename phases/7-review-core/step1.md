# Step 1: review-application-contracts

## 읽을 파일

- `/AGENTS.md`
- `/docs/API_SPEC.md`
- step 0 review domain
- phase 6 `ResolveRestaurantTargetUseCase`

## 작업

- create, update, delete와 my review list input port를 정의한다.
- create command는 restaurant target, visit month, 6 ratings와 nullable comment를 포함한다.
- presentation discriminator DTO를 application command로 명시적으로 변환할 수 있는 sealed target model을 둔다.
- result는 Entity가 아닌 ID, restaurant summary, ratings, states와 timestamps를 사용한다.
- review repository와 author-restaurant state repository output port를 정의한다.
- cursor는 createdAt+ID 기반 application model로 정의한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. application dependency contract test를 추가한다.
2. 성공 시 step 1을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 계약 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- presentation DTO나 JPA Entity를 input port에 사용하지 말 것.
- restaurant Entity를 review module로 전달하지 말 것.
- 공개 aggregate model을 이 phase에 추가하지 말 것.
