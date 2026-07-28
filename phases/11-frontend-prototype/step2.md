# Step 2: frontend-foundation

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/API_SPEC.md`
- `/.gitignore`
- `/postcss.config.mjs`
- phase 11 step 0~1 결과

## 작업

- Node 24 LTS와 npm을 기준으로 `/frontend` 독립 프로젝트를 만든다. `/frontend/.nvmrc`와 `package.json#engines`를 24.x로 고정하고 package-lock.json을 커밋한다.
- React 19, Vite 8, TypeScript의 SPA로 구성한다. router, TanStack Query provider, React Hook Form, Zod를 의존성에 포함하고 Vitest, Testing Library, jsdom, ESLint를 개발 의존성으로 둔다.
- `/frontend/src`에 `app`, `pages`, `features`, `shared`, `test` 구조를 만들고 path alias `@/`를 설정한다. 아직 기능별 화면을 미리 구현하지 않는다.
- `vite.config.ts`에서 `/api`를 `http://localhost:8080`으로 proxy하고 OAuth redirect의 cookie가 local frontend origin을 통해 왕복하도록 한다.
- CSS Modules와 공통 CSS custom property 기반의 반응형 토큰, 접근 가능한 기본 typography/focus/reset을 추가한다. 외부 이미지나 새 로고 asset은 만들지 않는다.
- 최소 shell, navigation, 404 route와 smoke test를 테스트 우선으로 구현한다.
- `dist`, `coverage` 등 frontend 산출물이 Git에 포함되지 않도록 `.gitignore`를 보완한다. 기존 루트 Spring Boot와 불필요한 `postcss.config.mjs`는 이 step에서 이동·삭제하지 않는다.

## 인수 기준

```bash
cd frontend && npm ci
cd frontend && npm run lint
cd frontend && npm test
cd frontend && npm run build
git diff --check
```

## 검증

1. Node와 npm engine, Vite proxy, alias, test environment와 production build를 확인한다.
2. architecture 문서의 `app/pages/features/shared` 구조와 일치하는지 확인한다.
3. 성공 시 step 2를 `completed`로 바꾸고 scaffold, stack과 commands를 summary에 기록한다.
4. Node 24 또는 registry 접근이 없으면 원인을 구체적으로 `blocked`, 코드 결함은 3회 수정 후 `error`로 기록한다.

## 하지 말 것

- 기존 backend를 `/backend`로 이동하지 말 것. 이유: Gradle, 문서와 harness 경로를 불필요하게 깨뜨린다.
- Next.js, Tailwind, MUI, Redux를 추가하지 말 것. 이유: 승인된 local SPA 범위와 선택을 넘는다.
- npm audit 자동 수정으로 의존성 major를 임의 변경하지 말 것. 이유: lockfile의 재현성을 깨뜨릴 수 있다.
- 기존 테스트를 삭제하거나 약화하지 말 것.
