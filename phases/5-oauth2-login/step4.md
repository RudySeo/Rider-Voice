# Step 4: oauth2-api-contract

> **역사적 기록:** 이 step의 onboarding API 계약은 `f76679c`에서 폐기됐다. 현재 계약은 루트 `AGENTS.md`와 `docs/API_SPEC.md`를 따른다.

## 읽을 파일

- `/docs/API_SPEC.md`
- `/docs/ARCHITECTURE.md`
- step 1 application result
- step 3 security handlers
- 기존 auth presentation DTO와 OpenAPI test

## 작업

- 목표 authorization/callback endpoint와 응답 DTO를 OpenAPI에 노출한다.
- 약관 미동의와 ACTIVE 사용자 응답을 nullable contract에 맞춘다.
- consents, refresh, logout, `/users/me` 기존 계약을 새 DTO mapper 경계에 맞춘다.
- public/onboarding/USER/ADMIN security scheme과 응답 schema를 검증한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. `/v3/api-docs`와 MockMvc 계약을 검증한다.
2. 성공 시 step 4를 `completed`로 기록한다.
3. 실패 3회 시 `error`, 계약 결정이 필요하면 `blocked`로 기록한다.

## 하지 말 것

- application result를 HTTP response로 직접 반환하지 말 것.
- deprecated `/kakao/authorize`와 `/kakao/callback`을 다시 추가하지 말 것.
- provider 오류나 token을 문서 예시 외 실제 로그에 남기지 말 것.
