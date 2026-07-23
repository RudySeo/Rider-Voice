# Step 0: auth-application-contracts

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/2-auth-onboarding/index.json`
- `/src/main/kotlin/com/ridervoice/api/auth/application/AuthService.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/application/KakaoOAuthPort.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/infrastructure/persistence/`
- `/src/main/kotlin/com/ridervoice/api/common/security/AccessTokenAuthenticator.kt`

## 작업

기존 인증 동작을 바꾸지 않고 `auth/application`의 헥사고날 계약을 추가한다. `port/in`에는 로그인·동의·세션·현재 사용자 use case와 token 인증 use case를 정의하고, `port/out`에는 카카오 OAuth와 User, OAuthAccount, OAuthLoginState, OnboardingToken, UserSession 저장소 port를 둔다. `application/model`에는 HTTP 및 Spring Security principal과 독립적인 command/result를 둔다. 기존 service와 adapter가 다음 step까지 컴파일되도록 호환 경계를 유지한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 새 application 계약이 presentation, Spring Data와 infrastructure 구현 package를 import하지 않는지 확인한다.
2. `phases/5-auth-hexagonal-refactor/index.json`의 step 0을 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- 인증 동작이나 token 수명을 변경하지 말 것. 이유: 구조 리팩터링과 정책 변경을 섞으면 안 된다.
- 기존 repository나 Controller를 아직 삭제하지 말 것. 이유: 독립 step 사이의 컴파일 가능성을 유지해야 한다.
- 기존 test를 깨뜨리지 말 것.
