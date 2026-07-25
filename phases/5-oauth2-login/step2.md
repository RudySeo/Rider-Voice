# Step 2: oauth2-provider-adapter

## 읽을 파일

- `/AGENTS.md`
- `/docs/ADR.md`
- step 0 ClientRegistration configuration
- step 1 social login input port와 model

## 작업

- infrastructure oauth package에 카카오 `OAuth2UserService` adapter를 구현한다.
- user-info 응답의 top-level `id`만 provider subject로 사용한다.
- provider response 구조와 Spring OAuth2 타입은 adapter 밖으로 노출하지 않는다.
- 잘못된 id, 빈 응답, provider 4xx/5xx를 안전한 내부 failure로 변환한다.
- 카카오 access token과 전체 account profile을 저장하지 않는다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. local HTTP stub으로 정상·손상·4xx/5xx를 검증한다.
2. 성공 시 step 2를 `completed`로 기록한다.
3. 실패 3회 시 `error`, 외부 credential이 필요하면 설계를 고쳐 stub test로 대체하고 계속한다.

## 하지 말 것

- 실제 카카오 API를 test에서 호출하지 말 것.
- nickname, email 등 불필요한 개인정보를 저장하지 말 것.
- provider exception body를 API 응답으로 전달하지 말 것.
