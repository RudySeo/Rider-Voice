# Step 0: restaurant-domain

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/API_SPEC.md`
- phase 4 cleanup summary
- `/src/main/kotlin/com/ridervoice/api/common/persistence/BaseEntity.kt`

## 작업

- restaurant domain에 `PickupLocation`, `Restaurant`, `RestaurantExternalReference`, `RestaurantPlatform`을 추가한다.
- 모든 Entity는 Long IDENTITY와 필요한 자식→부모 단방향 LAZY 관계를 사용한다.
- `PickupLocation`은 표준/정규화 주소, 상세 위치, location key, 좌표와 source를 가진다.
- `Restaurant`는 brand/normalized name, pickup location, ACTIVE/MERGED와 nullable canonical restaurant를 가진다.
- external reference는 provider/external ID, platform은 정해진 enum을 가진다.
- 주소·상호 정규화는 결정적이고 trim/공백/Unicode 차이를 줄이되 주소를 임의 추론하지 않는다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. domain 불변식과 normalization을 단위 테스트한다.
2. 성공 시 step 0을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 사용자 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- `kakaoPlaceId`를 PickupLocation이나 Restaurant의 필수 단일 키로 두지 말 것.
- 부모에 양방향 collection이나 cascade remove를 추가하지 말 것.
- 같은 장소의 다른 브랜드 공개 모델을 만들지 말 것.
