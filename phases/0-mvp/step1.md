# Step 1: core-domain

## 읽을 파일

먼저 아래 파일을 읽고 architecture와 design intent를 이해한다:

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/0-mvp/index.json`
- `/package.json`
- `/tsconfig.json`
- `/src/app/page.tsx`

수정하기 전에 이전 step에서 작성된 code를 주의 깊게 읽는다.

## 작업

MVP 전체에서 공유할 domain type, schema, URL parser, metric helper를 TDD로 구현한다.

생성/수정할 핵심 파일:

- `src/types/youtube.ts`
- `src/types/analysis.ts`
- `src/types/api.ts`
- `src/lib/app-errors.ts`
- `src/lib/channel-url.ts`
- `src/lib/metrics.ts`
- `src/lib/schemas.ts`
- 관련 test 파일

필수 interface:

- `src/lib/channel-url.ts`
  - `parseChannelInput(input: string): ParseChannelInputResult`
  - 결과는 성공 시 `{ ok: true, identifier: { type: "handle" | "channelId", value: string } }`
  - 실패 시 `{ ok: false, error: { code: "INVALID_CHANNEL_URL", message: string } }`
- `src/lib/metrics.ts`
  - `getAverageViewCount(videos)`
  - `getEngagementRate(video)`
  - `getTopVideos(videos, limit)`
  - `getUploadCadenceDays(videos)`
  - `getOutlierVideos(videos)`
- `src/lib/app-errors.ts`
  - `AppErrorCode` union:
    - `INVALID_CHANNEL_URL`
    - `MISSING_YOUTUBE_API_KEY`
    - `MISSING_OPENAI_API_KEY`
    - `CHANNEL_NOT_FOUND`
    - `NO_PUBLIC_VIDEOS`
    - `YOUTUBE_RATE_LIMITED`
    - `YOUTUBE_PROVIDER_ERROR`
    - `OPENAI_REFUSAL`
    - `OPENAI_PROVIDER_ERROR`
  - `toErrorResponse(code, message?, status?)`

URL parser 규칙:

- 지원:
  - `@handle`
  - `https://www.youtube.com/@handle`
  - `https://youtube.com/@handle`
  - `UC...` channel ID
  - `https://www.youtube.com/channel/UC...`
  - `https://youtube.com/channel/UC...`
- 미지원:
  - `/c/...`
  - `/user/...`
  - `/watch?v=...`
  - `/shorts/...`
  - 빈 문자열
  - YouTube가 아닌 domain

Schema 요구사항:

- collect request schema.
- collect success schema.
- analyze request schema.
- analyze success schema.
- API error schema.
- 숫자 provider field는 누락 가능성을 고려해 nullable을 허용한다.

## 인수 기준

```bash
npm run lint
npm run test
npm run build
```

## 검증

1. URL parser test가 지원/미지원 입력을 모두 검증하는지 확인한다.
2. metric helper test가 empty list, nullable count, normal list를 검증하는지 확인한다.
3. Architecture checklist를 확인한다:
   - shared type은 `src/types/`에 있는가?
   - parser/helper/schema는 `src/lib/`에 있는가?
   - client에서 provider API를 직접 호출하지 않는가?
4. 이 step에 대해 `phases/0-mvp/index.json`을 업데이트한다:
   - 성공: `"status": "completed"`로 설정하고 `"summary": "Core domain types, schemas, URL parser, app errors, and metric helpers implemented with tests."`를 추가한다.
   - 3회 수정 시도 후에도 실패: `"status": "error"`로 설정하고 `"error_message": "specific error"`를 추가한다.
   - 사용자 입력 필요: `"status": "blocked"`로 설정하고 `"blocked_reason": "specific reason"`을 추가한 뒤 중단한다.

## 하지 말 것

- YouTube 또는 OpenAI provider 호출을 이 step에서 구현하지 말 것. 이유: service/API route step과 경계를 유지한다.
- `/c/...` 또는 `/user/...`를 검색 fallback으로 지원하지 말 것. 이유: MVP 정책상 지원하지 않는다.
- nullable provider field를 임의로 0으로 단정하지 말 것. 이유: 누락과 실제 0을 구분해야 한다.
