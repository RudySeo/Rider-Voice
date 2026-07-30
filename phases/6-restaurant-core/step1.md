# Step 1: restaurant-application-contracts

## 읽을 파일

- `/AGENTS.md`
- `/docs/API_SPEC.md`
- step 0 domain
- auth principal/application model

## 작업

- 공개 검색과 USER 주소 검색 input port를 정의한다.
- 내부·카카오 후보, external search status와 aggregation placeholder를 provider 비종속 application result로 표현한다.
- 음식점 target 해석을 위한 `ResolveRestaurantTargetUseCase`와 `EXISTING`, `KAKAO`, `MANUAL_EXISTING_LOCATION`, `MANUAL_ADDRESS` command를 정의한다.
- review module은 향후 이 input port를 호출하고 Entity를 직접 전달받지 않도록 restaurant ID/result만 반환한다.
- repository, Kakao keyword/address provider output port를 정의한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. application package가 presentation/infrastructure를 import하지 않는 contract test를 추가한다.
2. 성공 시 step 1을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 계약 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- HTTP DTO나 Kakao response type을 command/result에 사용하지 말 것.
- 독립 `RegisterRestaurantUseCase` 또는 POST restaurant 계약을 만들지 말 것.
- review domain type에 의존하지 말 것.
