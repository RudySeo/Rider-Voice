# Step 0: docs-client-contract

## 읽을 파일

- `/AGENTS.md`
- `/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/API_SPEC.md`
- `/docs/GIT_FLOW.md`
- `/src/main/kotlin/com/ridervoice/api/auth/presentation/OAuth2LoginHandlers.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/presentation/AuthController.kt`

## 작업

- 코드보다 먼저 제품과 기술 문서를 임시 웹 클라이언트 목표에 맞게 갱신한다.
- PRD 포함 범위에 공개 음식점 검색·상세·리뷰, 카카오 로그인·약관 동의, 네 가지 음식점 target 리뷰 작성, 내 리뷰 수정·삭제 웹 흐름을 추가한다. 관리자·신고 UI와 실제 카카오 브라우저 E2E는 제외로 명시한다.
- ARCHITECTURE에 루트 Spring Boot 프로젝트를 이동하지 않고 `/frontend`에 React SPA를 두는 구조, `/api` 개발 프록시, 기능 중심 `app/pages/features/shared` 경계, CSS Modules, OpenAPI 타입 생성과 token 보관 정책을 기록한다.
- ADR에 Node 24 LTS, React 19, Vite 8, TypeScript, npm, TanStack Query, React Router, React Hook Form, Zod, Vitest/Testing Library 선택과 local prototype이라는 트레이드오프를 기록한다.
- OAuth 성공 callback은 service token을 URL에 넣지 않고 고정된 frontend callback URL에 60초 단일 사용 교환 코드만 전달하도록 계약을 바꾼다. `POST /api/v1/auth/oauth2/exchange`의 request/response, 빈 코드 400, 잘못됨·만료·재사용 401과 일반화된 OAuth 실패 redirect를 API_SPEC에 명시한다.
- OpenAPI가 실행 계약이라는 원칙과 모든 공개 리뷰·리포트의 `UNVERIFIED` 안내를 유지한다.

## 인수 기준

```bash
git diff --check
rg -n "frontend|oauth2/exchange|UNVERIFIED|Node 24" docs README.md
```

## 검증

1. 인수 기준 command를 실행한다.
2. 문서가 현재 목표와 구현 예정 상태를 혼동하지 않는지 확인한다.
3. 성공 시 step 0을 `completed`로 바꾸고 변경 문서와 핵심 계약을 summary에 기록한다.
4. 3회 수정 후에도 실패하면 `error`, 제품 결정이 필요하면 `blocked`로 기록한다.

## 하지 말 것

- 코드나 frontend 파일을 수정하지 말 것. 이유: 이 step은 제품·계약 결정만 고정한다.
- 관리자·신고 UI를 포함하지 말 것. 이유: 승인된 임시 frontend 범위 밖이다.
- token을 query string 또는 fragment로 전달한다고 문서화하지 말 것. 이유: 브라우저 기록과 로그에 노출될 수 있다.
- 기존 테스트를 삭제하거나 약화하지 말 것.
