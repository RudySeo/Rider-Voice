# Step 3: kakao-local-adapter

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/3-restaurant-search/index.json`
- `/src/main/kotlin/com/ridervoice/api/restaurant/application/port/out/KakaoLocalPort.kt`
- `/src/main/resources/application.yml`
- `/.env.example`

## 작업

`restaurant/infrastructure/external`에 카카오 키워드 장소 검색 adapter를 구현한다. REST API key, base URL과 timeout을 configuration properties로 주입하고 provider 응답을 application candidate로 변환한다. 음식점 카테고리 결과만 반환하고 provider request/response 타입은 adapter 안에 둔다. 성공, 빈 결과, timeout, rate limit, 4xx/5xx와 잘못된 JSON을 Docker 없는 HTTP stub 테스트로 검증한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 실제 네트워크나 secret 없이 adapter 계약 테스트가 통과하는지 확인한다.
2. `phases/3-restaurant-search/index.json`의 step 3을 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- 실제 카카오 API를 자동 테스트에서 호출하지 말 것. 이유: 테스트가 secret과 네트워크에 의존하면 안 된다.
- provider 오류 본문이나 API key를 외부로 노출하지 말 것. 이유: 내부 정보와 secret을 보호해야 한다.
- 기존 test를 깨뜨리지 말 것.
