# Step 3: oauth2-security-flow

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/API_SPEC.md`
- step 0~2 output
- `/src/main/kotlin/com/ridervoice/api/common/security/**`

## 작업

- 우선순위가 높은 OAuth SecurityFilterChain과 stateless API chain을 분리한다.
- authorization base URI를 `/api/v1/auth/oauth2/authorization`, callback base URI를 `/api/v1/auth/oauth2/callback/*`로 구성한다.
- OAuth handshake 동안만 IF_REQUIRED session과 state 검증을 사용한다.
- success handler가 provider subject를 application command로 변환해 login use case를 호출하고 계약 응답을 쓴다.
- success/failure 후 OAuth 임시 session을 폐기한다.
- API chain은 session SecurityContext를 사용하지 않고 opaque bearer filter만 신뢰한다.
- failure handler는 안전한 ProblemDetail을 반환한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. OAuth redirect/state, callback 성공·실패, session 폐기와 session 기반 API 접근 거부를 MockMvc로 검증한다.
2. 성공 시 step 3을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 외부 credential 요구 없이 test 가능해야 한다.

## 하지 말 것

- OAuth session을 service login session으로 재사용하지 말 것.
- success/failure handler에 repository 호출이나 account 생성 로직을 넣지 말 것.
- CSRF를 OAuth state 대체 수단으로 오해해 state 검증을 제거하지 말 것.
