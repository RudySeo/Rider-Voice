# Step 2: restaurant-persistence

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- step 0 domain
- step 1 repository ports
- 기존 common persistence patterns

## 작업

- Spring Data repository는 infrastructure 안에 숨기고 application repository port adapter를 구현한다.
- `location_key`, `(pickup_location_id, normalized_name)`, `(provider, external_place_id)` unique를 Entity mapping으로 정의한다.
- 검색, canonical 조회, 정확한 location key와 external reference 조회를 제공한다.
- 동시 unique 충돌 후 application이 기존 winner를 재조회할 수 있게 한다.
- 필요한 FK·상태·검색 index를 mapping에 정의한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. adapter contract를 단위 test/fake로 검증하고 integration test는 `@Tag("integration")`으로 분리한다.
2. 성공 시 step 2를 `completed`로 기록한다.
3. MySQL이 없으면 기본 test는 계속 통과해야 한다.

## 하지 말 것

- application service에서 JpaRepository를 직접 import하지 말 것.
- Flyway나 SQL migration을 추가하지 말 것.
- 로컬 DB를 DROP하지 말 것.
