# Step 0: project-setup

## 읽을 파일

먼저 아래 파일을 읽고 architecture와 design intent를 이해한다:

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`

이 repo에는 아직 Next.js 앱 코드가 없다. 기존 `AGENTS.md`, `docs/`, `scripts/`, `.agents/`, `.codex/`, `.githooks/`, `.gitignore`, `.env`는 보존한다.

## 작업

Next.js App Router 기반 MVP를 구현할 수 있는 프로젝트 골격을 repo root에 만든다.

생성/수정할 핵심 파일:

- `package.json`
- `tsconfig.json`
- `next.config.ts`
- `eslint.config.mjs`
- `postcss.config.mjs`
- `vitest.config.ts`
- `vitest.setup.ts`
- `src/app/layout.tsx`
- `src/app/page.tsx`
- `src/app/globals.css`

`package.json` 요구사항:

- scripts:
  - `"dev": "next dev"`
  - `"build": "next build"`
  - `"lint": "next lint"` 또는 현재 Next.js/ESLint flat config에서 동작하는 equivalent lint command
  - `"test": "vitest run"`
- dependencies:
  - `next`
  - `react`
  - `react-dom`
  - `zod`
  - `recharts`
  - `lucide-react`
  - `server-only`
- devDependencies:
  - `typescript`
  - `eslint`
  - `eslint-config-next`
  - `@eslint/eslintrc`
  - `vitest`
  - `@vitejs/plugin-react`
  - `vite-tsconfig-paths`
  - `jsdom`
  - `@testing-library/react`
  - `@testing-library/jest-dom`
  - `@testing-library/user-event`
  - `@types/node`
  - `@types/react`
  - `@types/react-dom`
  - Tailwind CSS와 Next.js에서 필요한 PostCSS package

구현 제약:

- TypeScript strict mode를 켠다.
- import alias `@/*`가 `src/*`를 가리키게 한다.
- Tailwind CSS를 `src/app/globals.css`에서 활성화한다.
- `src/app/page.tsx`는 임시 placeholder 화면만 둔다. 실제 client flow는 이후 step에서 구현한다.
- `npm install`을 실행해 lockfile을 만든다.

## 인수 기준

```bash
npm run lint
npm run test
npm run build
```

## 검증

1. 인수 기준 command를 실행한다.
2. `npm run build`가 API key 없이도 통과하는지 확인한다.
3. 이 step에 대해 `phases/0-mvp/index.json`을 업데이트한다:
   - 성공: `"status": "completed"`로 설정하고 `"summary": "Next.js/Tailwind/Vitest project scaffold created with package scripts and placeholder app."`를 추가한다.
   - 3회 수정 시도 후에도 실패: `"status": "error"`로 설정하고 `"error_message": "specific error"`를 추가한다.
   - 사용자 입력 필요: `"status": "blocked"`로 설정하고 `"blocked_reason": "specific reason"`을 추가한 뒤 중단한다.

## 하지 말 것

- 기존 `AGENTS.md`, `docs/`, `scripts/`, `.agents/`, `.codex/`, `.githooks/`, `.env`를 삭제하거나 덮어쓰지 말 것. 이유: 이미 프로젝트 계약과 Harness 실행 파일이 들어 있다.
- 환경변수 값을 코드에 하드코딩하지 말 것. 이유: API key는 로컬/배포 환경에서 주입되어야 한다.
- 앱 기능 전체를 이 step에서 구현하지 말 것. 이유: Harness step 경계를 작게 유지한다.
