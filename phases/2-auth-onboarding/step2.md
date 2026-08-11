# Step 2: bearer-auth-boundary

> **역사적 기록:** 이 phase의 onboarding token 설계는 `f76679c`에서 폐기됐다. 현재 계약은 루트 `AGENTS.md`와 `docs/API_SPEC.md`를 따른다.

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/2-auth-onboarding/index.json`
- `/src/main/kotlin/com/ridervoice/api/common/security/`
- `/src/main/kotlin/com/ridervoice/api/auth/domain/OnboardingToken.kt`

## 작업

Bearer 인증 결과가 token scope를 보존하도록 `common/security` 경계를 확장한다. 일반 access token은 `AuthenticatedUserPrincipal`과 `ROLE_USER`, onboarding token은 별도 `OnboardingPrincipal`과 `ROLE_ONBOARDING`을 반환해야 한다. 인증 filter는 scope별 principal과 authority를 그대로 SecurityContext에 설정한다. `POST /api/v1/auth/consents`는 `ROLE_ONBOARDING`만, 로그아웃·현재 사용자와 향후 리뷰 초안 API는 `ROLE_USER`만 허용한다. token 종류를 판별하는 interface는 provider/domain 타입을 노출하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. onboarding token으로 consent만 접근 가능하고 일반 access token으로 consent에 접근할 수 없는지 MockMvc로 검증한다.
2. onboarding token으로 logout, users/me와 임의 API 접근 시 403인지 검증한다.
3. phase index step 2를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- onboarding principal을 `AuthenticatedUserPrincipal` subtype 또는 `ROLE_USER`로 만들지 말 것. 이유: 약관 동의 전 일반 API 접근을 차단해야 한다.
- consent를 단순 `.authenticated()`로 두지 말 것. 이유: token scope가 강제되지 않는다.
- 기존 test를 깨뜨리지 말 것.
