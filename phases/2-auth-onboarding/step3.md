# Step 3: onboarding-consent-flow

> **역사적 기록:** 이 phase의 onboarding token 설계는 `f76679c`에서 폐기됐다. 현재 계약은 루트 `AGENTS.md`와 `docs/API_SPEC.md`를 따른다.

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/2-auth-onboarding/index.json`
- `/src/main/kotlin/com/ridervoice/api/auth/domain/OnboardingToken.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/infrastructure/persistence/OnboardingTokenRepository.kt`
- `/src/main/kotlin/com/ridervoice/api/common/security/`

## 작업

AuthService와 AuthController의 신규 사용자 흐름을 구현한다. 카카오 callback은 `PENDING_TERMS` 사용자에게 5분 유효 random onboarding token을 발급하고 원문은 response에 한 번만 반환한다. `POST /api/v1/auth/consents`는 `OnboardingPrincipal`을 받아 locked token 조회·사용자 일치·만료를 검증하고 token 소비, `User.agreeToTerms`, 정식 access/refresh token 발급을 하나의 transaction에서 처리한다. callback response는 ACTIVE 사용자에게 정식 tokens, PENDING_TERMS 사용자에게 onboardingToken 중 정확히 하나만 포함한다. OpenAPI에 일반 Bearer와 별도 onboarding Bearer scheme을 정의한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 신규·기존 사용자 callback, 정상 동의, 만료·소비된 token, 다른 사용자와 동시 중복 동의를 검증한다.
2. callback과 consent request/response schema 및 두 Bearer scheme이 `/v3/api-docs`에 노출되는지 검증한다.
3. phase index step 3을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- `PENDING_TERMS` 사용자에게 일반 access token을 발급하지 말 것. 이유: 약관 동의 전 보호 API 접근을 막아야 한다.
- Controller에서 token을 hash·조회하거나 사용자 상태를 전이하지 말 것. 이유: 보안과 트랜잭션 경계는 application service 책임이다.
- onboarding token이나 카카오 subject를 공개 응답 외 로그에 남기지 말 것.
- 기존 test를 깨뜨리지 말 것.
