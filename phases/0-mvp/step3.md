# Step 3: openai-analyze

## 읽을 파일

먼저 아래 파일을 읽고 architecture와 design intent를 이해한다:

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/0-mvp/index.json`
- `/src/lib/app-errors.ts`
- `/src/lib/metrics.ts`
- `/src/lib/schemas.ts`
- `/src/types/youtube.ts`
- `/src/types/analysis.ts`
- `/src/types/api.ts`
- `/src/app/api/channel/collect/route.ts`

수정하기 전에 이전 step에서 작성된 code와 test를 주의 깊게 읽는다.

## 작업

OpenAI Responses API로 collect result를 분석하는 server-only service와 `POST /api/channel/analyze` route를 TDD로 구현한다.

생성/수정할 핵심 파일:

- `src/services/openai.ts`
- `src/app/api/channel/analyze/route.ts`
- 관련 test 파일

필수 service interface:

- `analyzeChannelData(input: CollectChannelDataResult, options?: { apiKey?: string; model?: string; fetchImpl?: typeof fetch }): Promise<ChannelAnalysisResult>`
- `apiKey` 기본값은 `process.env.OPENAI_API_KEY`다.
- `model` 기본값은 `process.env.OPENAI_MODEL || "gpt-5.4-mini"`다.
- `fetchImpl`는 test에서 mock 주입 가능해야 한다.

OpenAI 호출 요구사항:

- Responses API를 사용한다.
- Structured Outputs JSON schema를 사용한다.
- SDK type 불확실성으로 빌드가 흔들릴 수 있으면 REST `fetch` 호출로 구현한다.
- request body에 API key를 포함하지 말고 Authorization header만 사용한다.
- prompt는 한국어 분석을 요구한다.
- prompt는 다음을 명확히 요구한다:
  - 데이터 기반 근거와 AI 추론을 구분한다.
  - 다음 영상 제작 결정을 돕는다.
  - 추상 조언보다 실행 체크리스트를 우선한다.
  - 최근 공개 영상 sample size가 작으면 confidence를 낮춘다.

Analyze success output 필드:

- `overallScore`
- `executiveSummary`
- `strongSignals`
- `growthBottlenecks`
- `contentPatterns`
- `recommendedNextVideos`
- `actionChecklist`
- `confidence`

Route 요구사항:

- `export const runtime = "nodejs"`를 선언한다.
- request body는 analyze request schema로 검증한다.
- 성공 시 analyze success JSON을 반환한다.
- 실패 시 `{ error: { code, message } }` JSON을 반환한다.
- YouTube API를 호출하지 않는다.

에러 매핑:

- missing key: `MISSING_OPENAI_API_KEY`, HTTP 500.
- refusal 또는 structured output 없음: `OPENAI_REFUSAL`, HTTP 502.
- 기타 provider 실패: `OPENAI_PROVIDER_ERROR`, HTTP 502.

## 인수 기준

```bash
npm run lint
npm run test
npm run build
```

## 검증

1. mocked OpenAI fetch test가 structured output 성공, missing key, provider error, missing output/refusal을 검증하는지 확인한다.
2. analyze route test가 request schema 검증과 error mapping을 검증하는지 확인한다.
3. Architecture checklist를 확인한다:
   - OpenAI 호출이 `src/services/openai.ts`와 analyze route 밖으로 새지 않는가?
   - analyze route가 YouTube를 호출하지 않는가?
   - client-facing error가 app error taxonomy를 따르는가?
4. 이 step에 대해 `phases/0-mvp/index.json`을 업데이트한다:
   - 성공: `"status": "completed"`로 설정하고 `"summary": "OpenAI analyze service and API route implemented with structured output schema and mocked provider tests."`를 추가한다.
   - 3회 수정 시도 후에도 실패: `"status": "error"`로 설정하고 `"error_message": "specific error"`를 추가한다.
   - 사용자 입력 필요: `"status": "blocked"`로 설정하고 `"blocked_reason": "specific reason"`을 추가한 뒤 중단한다.

## 하지 말 것

- YouTube provider 호출을 이 step에서 추가하지 말 것. 이유: analyze는 collect result만 입력으로 받는다.
- 자유 형식 Markdown 리포트만 반환하지 말 것. 이유: UI가 안정적으로 렌더링할 structured JSON이 필요하다.
- API key 또는 provider raw error를 클라이언트 응답에 노출하지 말 것. 이유: 보안과 사용자 경험을 지킨다.
