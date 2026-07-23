# Step 6: restaurant-regression-tests

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/3-restaurant-search/index.json`
- `/src/main/kotlin/com/ridervoice/api/restaurant/`
- `/src/test/kotlin/com/ridervoice/api/restaurant/`
- `/README.md`

## 작업

음식점 검색과 지연 등록의 계층별 회귀 테스트를 완성한다. application의 presentation/infrastructure 역방향 의존이 없는지, adapter stub 시나리오, 등록 멱등성, 권한, 공개 DTO와 OpenAPI 계약을 검증한다. README의 카카오 로컬 설정과 구현 API 목록을 실제 동작에 맞춘다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. Architecture checklist와 전체 기본 검증 명령을 확인한다.
2. `phases/3-restaurant-search/index.json`의 step 6과 상위 phase를 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- 실제 카카오 API key를 테스트나 문서에 기록하지 말 것. 이유: secret 유출을 막아야 한다.
- 방문 인증이나 지역 제한을 추가하지 말 것. 이유: 현재 phase 범위를 벗어난다.
- 기존 test를 깨뜨리지 말 것.
