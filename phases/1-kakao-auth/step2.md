# Step 2: auth-api

> **역사적 기록:** 이 step의 onboarding 동의 API는 `f76679c`에서 폐기됐다. 현재 계약은 루트 `AGENTS.md`와 `docs/API_SPEC.md`를 따른다.

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/1-kakao-auth/step0.md`
- `/phases/1-kakao-auth/step1.md`

## 작업
서버 callback 방식의 인증 API를 구현한다. `GET /api/v1/auth/kakao/authorize`, `GET /api/v1/auth/kakao/callback`, `POST /api/v1/auth/consents`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `GET /api/v1/users/me`를 제공한다. callback은 JSON으로 access/refresh token과 사용자 상태를 반환한다. OAuth state는 일회성·만료 검증한다. 약관 미동의 신규 사용자는 PENDING_TERMS로 유지하고 consent 후 ACTIVE로 전환한다. JWT access token과 해시 저장·회전 refresh token을 사용한다. OpenAPI와 ProblemDetail을 반영한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
./gradlew build
```

## 하지 말 것
- 카카오 subject나 provider 원문을 응답하지 말 것. 이유: 익명성과 provider 경계를 보장해야 한다.
- 약관 미동의 사용자를 ACTIVE로 만들지 말 것. 이유: PRD의 필수 약관 정책을 지켜야 한다.
