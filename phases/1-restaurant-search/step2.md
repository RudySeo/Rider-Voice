# Step 2: kakao-local-adapter

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/restaurant/application/`
- `/src/main/resources/application-local.yml`

## 작업
`restaurant/infrastructure`에 카카오 로컬 REST adapter를 구현한다. API key와 base URL을 configuration properties로 주입하고 성공, 빈 결과, timeout, rate limit과 잘못된 응답을 Docker 없는 HTTP stub 테스트로 검증한다. provider request/response 타입은 adapter 내부에 둔다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- provider 원문이나 API key를 application/domain에 노출하지 말 것. 이유: 외부 연동 경계를 유지해야 한다.
- 실제 카카오 API를 자동 테스트에서 호출하지 말 것. 이유: 로컬 테스트가 secret과 네트워크에 의존하면 안 된다.
- 기존 test를 깨뜨리지 말 것.
