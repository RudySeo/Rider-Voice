# Step 2: restaurant-persistence-adapter

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/3-restaurant-search/index.json`
- `/src/main/kotlin/com/ridervoice/api/restaurant/application/port/out/RestaurantRepository.kt`
- `/src/main/kotlin/com/ridervoice/api/restaurant/domain/Restaurant.kt`
- `/src/main/kotlin/com/ridervoice/api/restaurant/infrastructure/persistence/RestaurantRepository.kt`
- `/src/main/resources/db/migration/V3__restaurant_domain.sql`

## 작업

기존 Spring Data repository를 내부 구현으로 바꾸고 application output port를 구현하는 persistence adapter를 작성한다. 기존 migration을 수정하지 말고 새 Flyway migration으로 `restaurants.included_in_pilot`을 제거한다. `kakao_place_id` unique 제약은 유지한다. 내부 검색, ID 조회, 카카오 장소 ID 조회와 저장에 필요한 최소 method만 제공하고 JPA 타입을 application에 노출하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. repository port와 adapter mapping, unique 제약 충돌 동작을 테스트한다.
2. 로컬 MySQL 환경이 준비된 경우 `./gradlew integrationTest`로 Flyway와 JPA mapping을 확인한다.
3. `phases/3-restaurant-search/index.json`의 step 2를 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- `V3__restaurant_domain.sql`을 수정하지 말 것. 이유: 적용된 Flyway checksum을 보존해야 한다.
- application package에서 `JpaRepository`를 상속하지 말 것. 이유: persistence adapter 방향을 역전시키면 안 된다.
- Docker나 Testcontainers를 실행하지 말 것. 이유: 현재 로컬 실행 경계를 벗어난다.
- 기존 test를 깨뜨리지 말 것.
