# Step 1: oauth-login-exchange

> **역사적 기록:** 이 step의 onboarding 응답 분기는 `f76679c`에서 폐기됐다. 현재 frontend는 access/refresh token을 직접 교환한다.

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/API_SPEC.md`
- `/src/main/kotlin/com/ridervoice/api/auth/application/AuthService.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/application/port/in/CompleteSocialLoginUseCase.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/application/port/out/AuthPersistencePorts.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/presentation/OAuth2LoginHandlers.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/presentation/OAuth2SecurityConfig.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/presentation/AuthController.kt`
- `/src/test/kotlin/com/ridervoice/api/auth`
- phase 11 step 0에서 변경한 문서

## 작업

- 실패하는 application 및 MockMvc 테스트를 먼저 추가한 뒤 최소 구현을 작성한다.
- provider 로그인 완료와 service token 발급을 두 단계로 분리한다. callback 단계는 OAuth 계정을 확인·생성하고 암호학적으로 안전한 raw exchange code를 한 번만 반환한다. exchange 단계는 code를 원자적으로 소비한 뒤 기존 `OAuth2LoginResponse`와 동일한 약관/서비스 token 결과를 만든다.
- inbound use case는 provider login 시작 결과와 code 교환을 명확히 분리하고, 일회용 grant 저장은 `application/port/out`의 output port와 `infrastructure`의 Caffeine adapter로 구현한다. application이 infrastructure 구현 package를 import하지 않게 한다.
- grant에는 최소 user ID와 만료 시각만 저장하고 raw code 대신 SHA-256 hash를 key로 사용한다. 유효 시간은 60초, 단일 API 인스턴스 메모리 범위이며 consume은 동시 요청에서도 한 번만 성공해야 한다.
- `POST /api/v1/auth/oauth2/exchange`에 `{ "code": "..." }` request DTO를 추가하고 기존 `OAuth2LoginResponse`를 반환한다. request/response DTO는 Controller 파일과 분리하고 OpenAPI schema를 갱신한다.
- OAuth success handler는 임시 session과 SecurityContext를 폐기한 뒤 설정된 `FRONTEND_BASE_URL`의 `/auth/callback?code=...`으로 redirect한다. 사용자 request의 redirect URL을 받지 않는다.
- OAuth failure handler도 session을 폐기하고 `/auth/callback?error=oauth_failed`로만 redirect한다. provider error, token, stack trace를 노출하지 않는다.
- 기존 refresh, consent, logout, access-token 현재 role 확인을 그대로 유지한다.

## 인수 기준

```bash
./gradlew test --tests '*auth*' --no-daemon
./gradlew test --tests '*SecurityPolicyMockMvcTest' --tests '*ApiContractRegressionMockMvcTest' --no-daemon
git diff --check
```

## 검증

1. 정상 교환, 빈 code 400, 잘못됨·만료·재사용 401, 동시 consume 한 번 성공, 고정 redirect, session 폐기와 URL token 비노출을 검증한다.
2. OpenAPI에 exchange endpoint와 DTO가 노출되고 callback 계약이 redirect로 반영되는지 확인한다.
3. 성공 시 step 1을 `completed`로 바꾸고 port, adapter, endpoint와 테스트를 summary에 기록한다.
4. 3회 수정 후에도 실패하면 `error`, 외부 설정이 필요하면 `blocked`로 기록한다.

## 하지 말 것

- service access/refresh/onboarding token을 redirect URL에 넣지 말 것. 이유: URL 기록에 민감정보가 남는다.
- OAuth handler에 JPA query나 사용자 상태 결정을 작성하지 말 것. 이유: application 경계를 위반한다.
- exchange grant를 새 DB table로 만들지 말 것. 이유: 단일 인스턴스 local prototype 범위를 넘는다.
- CORS를 전체 origin에 개방하지 말 것. 이유: frontend는 동일 origin 개발 프록시를 사용한다.
- 기존 테스트를 삭제하거나 약화하지 말 것.
