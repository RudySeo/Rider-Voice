# Step 3: restaurant-search-service

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-restaurant-search/index.json`
- `/src/main/kotlin/com/ridervoice/api/restaurant/application/`
- `/src/main/kotlin/com/ridervoice/api/restaurant/domain/Restaurant.kt`
- `/src/main/kotlin/com/ridervoice/api/restaurant/infrastructure/persistence/RestaurantRepository.kt`

## 작업

검색어·중심 좌표·반경을 받아 카카오 candidate를 조회하고 파일럿 반경 3km 정책을 적용하는 application service를 구현한다. 허용된 장소만 카카오 장소 ID로 upsert하고 entity가 아닌 공개 application result를 반환한다. 트랜잭션 경계는 service에 둔다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 경계 안/밖, 중복 place ID와 빈 결과를 단위 테스트로 검증한다.
2. index의 step 3을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- Controller에서 거리 계산이나 JPA query를 수행하지 말 것. 이유: 비즈니스 규칙은 application/domain 소유다.
- 클라이언트가 보낸 place ID를 검증 없이 저장하지 말 것. 이유: 외부 식별자를 신뢰할 수 없다.
- 기존 test를 깨뜨리지 말 것.
