# Step 0: restaurant-domain-alignment

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/restaurant/domain/Restaurant.kt`
- `/src/test/kotlin/com/ridervoice/api/restaurant/`

## 작업

현재 `Restaurant` domain을 MVP 규칙에 맞춘다. `includedInPilot`을 제거하고 카카오 장소 ID, 이름, 주소, 위도와 경도 불변식만 유지한다. 공통 Long IDENTITY 식별자와 JPA mapping을 사용한다. 장소 정보는 provider 결과로 생성되며 사용자에게 소유권을 부여하지 않는다. domain 단위 테스트를 새 생성자와 불변식에 맞춘다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 빈 장소 ID·이름·주소와 좌표 범위를 단위 테스트한다.
2. `phases/3-restaurant-search/index.json`의 step 0을 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- repository를 수정하지 말 것. 이유: 이 step은 domain 정렬만 담당한다.
- 방문 인증이나 파일럿 반경 정책을 추가하지 말 것. 이유: 현재 MVP 범위가 아니다.
- 기존 test를 깨뜨리지 말 것.
