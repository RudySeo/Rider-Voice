# Step 2: restaurant-api

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/1-restaurant-search/step0.md`
- `/phases/1-restaurant-search/step1.md`

## 작업
`GET /api/v1/restaurants/search`를 구현한다. 검색어·좌표·반경을 검증하고 카카오 결과를 내부 Restaurant로 upsert/cache한다. 파일럿 반경 밖 장소는 노출하지 않는다. OpenAPI와 DTO 경계를 추가한다.

## 인수 기준
```bash
./gradlew test
./gradlew build
```

## 하지 말 것
- provider 원문을 API 응답으로 반환하지 말 것. 이유: 공개 계약과 외부 API를 분리해야 한다.
