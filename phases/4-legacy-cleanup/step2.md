# Step 2: legacy-restaurant-removal

## 읽을 파일

먼저 아래 파일과 완료된 step summary를 읽는다:

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/API_SPEC.md`
- `/src/main/kotlin/com/ridervoice/api/restaurant/**`
- `/src/test/kotlin/com/ridervoice/api/restaurant/**`
- `/src/main/kotlin/com/ridervoice/api/common/security/SecurityConfig.kt`

## 작업

목표 모델과 호환되지 않는 기존 restaurant module을 제거한다.

- 단일 `Restaurant.kakaoPlaceId` Entity와 기존 domain 테스트를 제거한다.
- 기존 restaurant application model, port, service를 제거한다.
- `KakaoLocalAdapter`, properties, persistence adapter를 제거한다.
- 기존 `RestaurantController`, request/response DTO, mapper와 전용 API 테스트를 제거한다.
- `/api/v1/restaurants/search` USER matcher와 독립 `POST /api/v1/restaurants` matcher를 제거한다.
- application 설정의 기존 `kakao.local` 항목은 새 adapter phase에서 재정의하므로 제거한다.
- persistence foundation 테스트에서 legacy restaurant schema 기대값을 제거한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. `src/main`과 `src/test`에 단일 Restaurant 구현 참조가 남지 않았는지 확인한다.
2. auth와 common 테스트가 통과하는지 확인한다.
3. 성공 시 step 2를 `completed`로 바꾸고 한 줄 `summary`를 기록한다.
4. 3회 실패 시 `error`, 사용자 입력 필요 시 `blocked`로 기록한다.

## 하지 말 것

- 완료된 Harness phase 0~3 기록을 삭제하지 말 것. 이유: 감사 이력이다.
- 새 픽업 장소·브랜드 구현을 추가하지 말 것. 이유: 다음 phase 범위다.
- 문서에서 목표 restaurant API를 삭제하지 말 것.
- legacy 구현 제거와 무관한 테스트를 삭제하지 말 것.
