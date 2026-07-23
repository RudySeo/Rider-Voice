# Step 1: restaurant-application-ports

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/3-restaurant-search/index.json`
- `/src/main/kotlin/com/ridervoice/api/restaurant/domain/Restaurant.kt`

## 작업

`restaurant/application`에 헥사고날 계약만 정의한다. `port/in`에는 음식점 검색과 선택 등록을 제공하는 `RestaurantUseCase`, `port/out`에는 내부 음식점 저장·검색을 위한 `RestaurantRepository`와 provider 비종속 `KakaoLocalPort`를 둔다. `application/model`에는 검색 query, 등록 command, 장소 candidate와 결과 모델을 둔다. 등록 command는 원래 검색어와 선택한 카카오 장소 ID만 받는다. HTTP, Swagger, Jackson, Spring Data와 카카오 provider DTO를 import하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. application package가 presentation 및 infrastructure 구현 package를 import하지 않는지 확인한다.
2. `phases/3-restaurant-search/index.json`의 step 1을 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- Controller나 adapter를 구현하지 말 것. 이유: 이 step은 application interface와 model만 고정한다.
- JPA Entity나 provider DTO를 command/result로 사용하지 말 것. 이유: 경계 모델을 분리해야 한다.
- 기존 test를 깨뜨리지 말 것.
