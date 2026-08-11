# Step 1: legacy-auth-removal

> **역사적 기록:** 이 step이 보존한 onboarding 흐름은 `f76679c`에서 폐기됐다. 현재 계약은 루트 `AGENTS.md`와 `docs/API_SPEC.md`를 따른다.

## 읽을 파일

먼저 아래 파일과 step 0 변경을 모두 읽는다:

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/API_SPEC.md`
- `/src/main/kotlin/com/ridervoice/api/auth/**`
- `/src/test/kotlin/com/ridervoice/api/auth/**`
- `/src/main/kotlin/com/ridervoice/api/common/security/**`

## 작업

직접 구현된 카카오 OAuth 부분만 제거하고 Rider Voice token 기반은 보존한다.

- `KakaoOAuthPort`, `KakaoOAuthAdapter`, `KakaoOAuthProperties`와 전용 테스트를 제거한다.
- `OAuthLoginState`, repository와 전용 테스트를 제거한다.
- `AuthController`의 `/kakao/authorize`, `/kakao/callback`을 제거한다.
- `AuthService`에서 authorization URI, state와 직접 code 교환 흐름 및 `CallbackResult`를 제거한다.
- `OAuthAccount`, `User`, onboarding token, opaque access/refresh token, refresh rotation, logout과 `/users/me`는 유지한다.
- security matcher와 application 설정에서 제거된 OAuth endpoint/property만 정리한다.
- persistence foundation과 auth 테스트는 남은 Entity·기능 기준으로 갱신한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. 제거된 class·property·endpoint 참조가 `rg`에 남지 않았는지 확인한다.
2. retained onboarding/session 테스트가 통과하는지 확인한다.
3. 성공 시 step 1을 `completed`로 바꾸고 한 줄 `summary`를 기록한다.
4. 3회 실패 시 `error`, 사용자 입력 필요 시 `blocked`로 기록한다.

## 하지 말 것

- `OAuthAccount`를 삭제하지 말 것. 이유: Spring OAuth2 로그인도 provider subject 연결에 사용한다.
- opaque token/session 구현을 JWT나 HTTP session으로 교체하지 말 것.
- 폐기 구현과 직접 결합되지 않은 auth 테스트를 삭제하거나 약화하지 말 것.
- Spring OAuth2 Client 새 구현을 추가하지 말 것. 이유: 다음 phase 범위다.
