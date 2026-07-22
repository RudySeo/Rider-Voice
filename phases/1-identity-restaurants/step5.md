# Step 5: restaurant-api

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-identity-restaurants/step3.md`
- `/phases/1-identity-restaurants/step4.md`

## 작업
`GET /api/v1/restaurants/search`를 구현한다. 검색어·좌표·반경을 검증하고 카카오 결과를 내부 Restaurant로 upsert/cache한다. 파일럿 반경 밖 장소는 결과와 인증 대상으로 노출하지 않는다.

## 인수 기준
```bash
./gradlew test
./gradlew build
```

## 하지 말 것
- 인증되지 않은 provider 원문을 API 응답에 그대로 반환하지 말 것. 이유: 내부 응답 계약을 안정적으로 유지해야 한다.
