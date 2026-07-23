# Step 4: restaurant-application-service

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/3-restaurant-search/index.json`
- `/src/main/kotlin/com/ridervoice/api/restaurant/application/`
- `/src/main/kotlin/com/ridervoice/api/restaurant/domain/Restaurant.kt`

## 작업

`RestaurantUseCase`를 구현하는 application service를 작성한다. 검색은 내부 음식점과 카카오 후보를 `kakaoPlaceId`로 중복 제거하고 내부 `restaurantId` 등록 여부를 포함한 result를 반환한다. 등록은 command의 원래 검색어로 카카오 검색을 다시 수행해 선택한 장소 ID가 결과에 있을 때만 provider 정보로 Restaurant를 생성한다. 이미 등록된 장소와 동시 unique 충돌은 기존 Restaurant를 반환해 멱등성을 보장한다. application service가 presentation DTO나 infrastructure 구현 class를 참조하지 않게 한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 내부·외부 결과 병합, 빈 결과, 검증되지 않은 장소 ID, 기존 장소와 동시 등록 경합을 단위 테스트한다.
2. `phases/3-restaurant-search/index.json`의 step 4를 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- 클라이언트가 전달한 이름·주소·좌표를 저장하지 말 것. 이유: provider 검색 결과만 음식점 기준 정보로 사용한다.
- Controller나 provider DTO를 application service에 전달하지 말 것. 이유: 헥사고날 경계를 유지해야 한다.
- 기존 test를 깨뜨리지 말 것.
