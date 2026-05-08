# 프로젝트: YouTube Channel Insight

## 기술 스택
- Next.js App Router
- TypeScript strict mode
- Tailwind CSS
- Vitest + React Testing Library
- YouTube Data API v3
- OpenAI Responses API + Structured Outputs

## 제품 목표
- 사용자가 YouTube 채널 URL만 입력하면 공개 채널 정보와 최근 업로드 데이터를 수집한다.
- 수집된 데이터를 ChatGPT API로 분석해 채널 운영자가 다음에 만들 콘텐츠를 결정하도록 돕는다.
- MVP는 긴 보고서보다 대시보드, 추천 카드, 실행 체크리스트를 우선한다.

## 아키텍처 규칙
- CRITICAL: 외부 API 호출은 `src/app/api/**/route.ts`와 server-only service에서만 처리한다.
- CRITICAL: Client Component에서 YouTube API 또는 OpenAI API를 직접 호출하지 않는다.
- CRITICAL: API key, provider raw error, 내부 stack trace를 클라이언트에 노출하지 않는다.
- CRITICAL: 데이터 파이프라인은 `collect -> analyze` 경계를 유지한다.
  - `collect`는 YouTube 데이터 수집만 담당한다.
  - `analyze`는 수집 결과를 받아 OpenAI 분석만 담당한다.
- CRITICAL: MVP는 공개 YouTube Data API만 사용한다. YouTube OAuth와 YouTube Analytics API는 사용하지 않는다.
- 컴포넌트는 `src/components/`에 둔다.
- 공통 타입은 `src/types/`에 둔다.
- URL 파싱, metric 계산, validation helper는 `src/lib/`에 둔다.
- YouTube/OpenAI wrapper는 `src/services/`에 두고 server-only boundary를 유지한다.

## 사용자 플로우 원칙
- 사용자는 채널 운영자이며, 앱의 핵심 질문은 "다음에 무엇을 만들까?"다.
- 첫 화면은 랜딩 페이지가 아니라 실제 채널 URL 입력 화면이어야 한다.
- 지원 URL은 `youtube.com/@handle`, `@handle`, `youtube.com/channel/UC...`, `UC...`로 제한한다.
- `/c/...`, `/user/...`, 개별 영상 URL, Shorts URL은 MVP에서 명확한 validation error로 안내한다.
- 분석 결과는 다음 질문에 답해야 한다:
  - 지금 채널에서 잘 되는 것은 무엇인가?
  - 왜 잘 되는가?
  - 무엇을 반복해야 하는가?
  - 무엇을 줄이거나 그만두어야 하는가?
  - 다음 영상은 무엇을 만들면 좋은가?
- AI 분석은 추상적인 조언보다 데이터 근거가 있는 실행 항목을 우선한다.

## 개발 프로세스
- CRITICAL: 새 기능 구현 시 반드시 테스트를 먼저 작성하고, 테스트가 통과하는 구현을 작성할 것 (TDD).
- URL parser, metric helper, API route, provider error mapping, 주요 UI 상태는 테스트 대상이다.
- 구현 전 `docs/PRD.md`, `docs/ARCHITECTURE.md`, `docs/ADR.md`를 읽고 현재 범위를 확인한다.
- 제품 범위가 바뀌면 관련 docs를 먼저 업데이트한다.
- 커밋 메시지는 conventional commits 형식을 따른다: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.

## 환경 변수
- `YOUTUBE_API_KEY`: YouTube Data API v3 key.
- `OPENAI_API_KEY`: OpenAI API key.
- `OPENAI_MODEL`: 선택값. 설정하지 않으면 앱의 기본 비용형 모델을 사용한다.

## 명령어
```bash
npm run dev      # 개발 서버
npm run build    # 프로덕션 빌드
npm run lint     # ESLint
npm run test     # 테스트
```
