# Step 3: api-session-layer

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/API_SPEC.md`
- phase 11 step 1의 OAuth exchange endpoint와 DTO
- phase 11 step 2의 `/frontend` 전체 구조와 package scripts
- backend `/v3/api-docs` 계약

## 작업

- 먼저 API client와 session 동작의 실패 테스트를 작성한다.
- `openapi-typescript`를 사용해 실행 중인 backend `http://localhost:8080/v3/api-docs`에서 `/frontend/src/shared/api/generated.ts`를 생성하는 `npm run api:generate` script를 제공하고 생성 파일을 커밋한다. backend가 없으면 타입을 추측해 손으로 대체하지 말고 `blocked` 처리한다.
- native fetch 기반 typed client를 작성한다. 성공 JSON/204, query parameter, bearer header와 RFC 7807 `ProblemDetail`을 일관되게 처리한다.
- access token은 module memory, refresh token과 onboarding token은 `sessionStorage`에 저장한다. localStorage, cookie 또는 URL에 token을 저장하지 않는다.
- page reload 시 refresh token이 있으면 한 번 갱신해 access token과 회전된 refresh token을 복구한다.
- 여러 요청이 동시에 401을 받으면 하나의 refresh promise만 공유하고 원 요청을 한 번만 재시도한다. exchange, refresh, consent와 logout 요청에는 무한 refresh 재시도를 적용하지 않는다.
- refresh/logout 실패나 명시적 logout 시 모든 session state를 비우고 auth 상태를 anonymous로 전환한다.
- UI 문구가 필요한 오류에는 provider detail을 그대로 표시하지 않고 안정적인 `code`와 HTTP status를 기반으로 안전한 사용자 메시지를 만들 수 있는 error model을 제공한다.

## 인수 기준

```bash
cd frontend && npm run api:generate
cd frontend && npm run lint
cd frontend && npm test
cd frontend && npm run build
git diff --check
```

## 검증

1. token storage 위치, 204, ProblemDetail, 단일 refresh, 회전 token, 재시도 1회와 실패 정리를 테스트한다.
2. generated.ts가 실제 OpenAPI에서 생성됐고 손으로 정의한 중복 transport DTO가 없는지 확인한다.
3. 성공 시 step 3을 `completed`로 바꾸고 generated types, client와 session 정책을 summary에 기록한다.
4. backend/OpenAPI 접근 불가면 `blocked`, 코드 결함은 3회 수정 후 `error`로 기록한다.

## 하지 말 것

- JPA Entity나 Kotlin domain class를 기준으로 frontend type을 만들지 말 것. 이유: OpenAPI가 공개 계약의 기준이다.
- access/refresh/onboarding token을 console 또는 error message에 기록하지 말 것. 이유: 민감정보 노출이다.
- 모든 401에 무제한 refresh loop를 만들지 말 것. 이유: 만료 session에서 요청 폭주가 생긴다.
- 기존 테스트를 삭제하거나 약화하지 말 것.
