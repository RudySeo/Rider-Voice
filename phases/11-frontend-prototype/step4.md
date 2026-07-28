# Step 4: auth-user-flow

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/API_SPEC.md`
- phase 11 step 1의 OAuth redirect/exchange 계약
- phase 11 step 2의 router/provider/shell
- phase 11 step 3의 generated API, client와 session layer

## 작업

- 인증 사용자 흐름의 실패하는 Testing Library 테스트를 먼저 작성한다.
- 카카오 로그인 버튼은 현재 경로를 sessionStorage에 저장한 뒤 동일 origin `/api/v1/auth/oauth2/authorization/kakao`로 full-page navigation한다.
- `/auth/callback` page는 `code`를 한 번 교환한다. `termsAgreed=false`면 onboarding token을 보관하고 `/consent`로, true면 service token을 적용하고 저장한 안전한 내부 경로로 이동한다.
- callback의 `error=oauth_failed`, 누락/재사용/만료 code와 네트워크 실패에 generic 재시도 UI를 제공한다. 외부 error detail과 token을 표시하지 않는다.
- `/consent`는 문서에 정한 현재 약관 버전을 명시적으로 동의받아 API를 호출하고 성공 시 service session으로 전환한다. onboarding token이 없으면 로그인으로 유도한다.
- header에 anonymous/login, authenticated/my reviews/logout 상태를 표현하고 protected route는 로그인 후 돌아올 내부 경로를 보존한다.
- 초기 session 복구 동안 보호 화면을 먼저 렌더링하지 않고 loading state를 표시한다.

## 인수 기준

```bash
cd frontend && npm run lint
cd frontend && npm test
cd frontend && npm run build
git diff --check
```

## 검증

1. 신규/기존 사용자 callback, 약관 동의, 복구 loading, 보호 route, logout, 실패 재시도를 mock API로 검증한다.
2. redirect 대상이 `/`로 시작하는 내부 경로만 허용되고 외부 URL을 열지 않는지 검증한다.
3. 성공 시 step 4를 `completed`로 바꾸고 auth routes, UI와 tests를 summary에 기록한다.
4. 3회 수정 후에도 실패하면 `error`, 제품 결정이 필요하면 `blocked`로 기록한다.

## 하지 말 것

- OAuth callback을 popup DOM parsing으로 구현하지 말 것. 이유: 승인된 일회용 exchange code 계약을 우회한다.
- token을 React component state, DOM, URL 또는 log에 표시하지 말 것. 이유: 민감정보 노출이다.
- 실제 카카오 계정으로 자동 E2E를 만들지 말 것. 이유: 이번 local integration test 범위 밖이다.
- 기존 테스트를 삭제하거나 약화하지 말 것.
