# Step 2: security-baseline

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/0-local-foundation/index.json`
- `/src/main/kotlin/com/ridervoice/api/common/security/SecurityConfig.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/`

## 작업

Spring Security의 endpoint 정책을 명시한다. health, Swagger/OpenAPI와 카카오 로그인 시작·callback·refresh는 공개하고 약관 동의, 로그아웃, `/api/v1/users/me`는 Bearer access token을 요구한다. 인증 principal이 application service에 전달되게 하며 Controller가 문자열 token을 직접 해석하지 않도록 security component interface를 둔다. 현재 로컬 opaque access token 방식은 유지한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. endpoint별 2xx/401/403 동작을 Docker 없는 MockMvc 테스트로 검증한다.
2. `phases/0-local-foundation/index.json`의 step 2를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 모든 `/api/v1/**`를 `permitAll`로 두지 말 것. 이유: 인증이 필요한 사용자 API가 보호되지 않는다.
- access token이나 refresh token 원문을 로그에 남기지 말 것. 이유: 세션 탈취로 이어질 수 있다.
- 기존 test를 깨뜨리지 말 것.
