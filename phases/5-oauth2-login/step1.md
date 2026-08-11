# Step 1: auth-application-boundary

> **역사적 기록:** 이 step의 onboarding 응답 분기는 `f76679c`에서 폐기됐다. 현재 계약은 루트 `AGENTS.md`와 `docs/API_SPEC.md`를 따른다.

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/API_SPEC.md`
- phase 4에서 보존한 auth domain/application/persistence
- step 0 configuration

## 작업

- `CompleteSocialLoginUseCase` input port를 정의한다.
- command는 provider와 provider subject만 포함하고 Spring `OAuth2User`나 카카오 DTO를 포함하지 않는다.
- result는 사용자 summary, 약관 동의 여부, nullable onboarding token과 nullable service tokens를 표현한다.
- 기존 `OAuthAccount`로 계정을 조회하고 신규 `User`와 account를 원자적으로 생성한다.
- ACTIVE 사용자는 opaque tokens, PENDING_TERMS 사용자는 onboarding token을 발급한다.
- `UserRole(USER, ADMIN)`을 `User`에 추가하고 기존 사용자의 기본값은 USER로 유지한다.
- AccessTokenAuthenticator가 DB의 현재 role을 principal authority에 반영하게 한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. 신규·기존·정지 상태, USER/ADMIN role을 application/domain test로 검증한다.
2. 성공 시 step 1을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 사용자 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- application package에 Spring Security, OAuth2 또는 provider DTO를 import하지 말 것.
- refresh token 원문을 저장하지 말 것.
- 기존 onboarding/session 회전 정책을 약화하지 말 것.
