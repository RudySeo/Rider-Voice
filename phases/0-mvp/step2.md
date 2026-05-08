# Step 2: youtube-collect

## 읽을 파일

먼저 아래 파일을 읽고 architecture와 design intent를 이해한다:

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/0-mvp/index.json`
- `/src/lib/channel-url.ts`
- `/src/lib/app-errors.ts`
- `/src/lib/schemas.ts`
- `/src/types/youtube.ts`
- `/src/types/api.ts`

수정하기 전에 이전 step에서 작성된 code와 test를 주의 깊게 읽는다.

## 작업

YouTube 공개 데이터를 수집하는 server-only service와 `POST /api/channel/collect` route를 TDD로 구현한다.

생성/수정할 핵심 파일:

- `src/services/youtube.ts`
- `src/app/api/channel/collect/route.ts`
- 관련 test 파일

필수 service interface:

- `collectChannelData(input: string, options?: { apiKey?: string; fetchImpl?: typeof fetch }): Promise<CollectChannelDataResult>`
- `input`은 사용자가 입력한 채널 URL 또는 handle/channel ID다.
- `apiKey` 기본값은 `process.env.YOUTUBE_API_KEY`다.
- `fetchImpl`는 test에서 mock 주입 가능해야 한다.

YouTube API 흐름:

1. `parseChannelInput(input)`으로 handle 또는 channel ID를 얻는다.
2. `YOUTUBE_API_KEY`가 없으면 `MISSING_YOUTUBE_API_KEY`를 반환한다.
3. handle이면 `channels.list` handle 기반 조회를 사용한다.
4. channel ID면 `channels.list` id 기반 조회를 사용한다.
5. channel 응답에서 uploads playlist ID를 얻는다.
6. `playlistItems.list`로 최근 공개 업로드 video ID를 최대 50개 얻는다.
7. `videos.list`로 `snippet`, `statistics`, `contentDetails`를 보강한다.
8. 앱의 collect success payload로 정규화한다.

Route 요구사항:

- `export const runtime = "nodejs"`를 선언한다.
- request body는 collect request schema로 검증한다.
- 성공 시 collect success JSON을 반환한다.
- 실패 시 `{ error: { code, message } }` JSON을 반환한다.
- provider raw error, API key, stack trace를 응답에 포함하지 않는다.

에러 매핑:

- invalid input: `INVALID_CHANNEL_URL`, HTTP 400.
- missing key: `MISSING_YOUTUBE_API_KEY`, HTTP 500.
- channel empty result: `CHANNEL_NOT_FOUND`, HTTP 404.
- uploads playlist는 있으나 공개 영상 없음: `NO_PUBLIC_VIDEOS`, HTTP 404.
- quota/rate limit 계열: `YOUTUBE_RATE_LIMITED`, HTTP 429.
- 기타 provider 실패: `YOUTUBE_PROVIDER_ERROR`, HTTP 502.

## 인수 기준

```bash
npm run lint
npm run test
npm run build
```

## 검증

1. mocked fetch test가 channel lookup, playlistItems lookup, videos lookup을 검증하는지 확인한다.
2. missing env, channel not found, no public videos, quota error test가 있는지 확인한다.
3. Architecture checklist를 확인한다:
   - YouTube 호출이 `src/services/youtube.ts`와 collect route 밖으로 새지 않는가?
   - collect route가 OpenAI를 호출하지 않는가?
   - client-facing error가 app error taxonomy를 따르는가?
4. 이 step에 대해 `phases/0-mvp/index.json`을 업데이트한다:
   - 성공: `"status": "completed"`로 설정하고 `"summary": "YouTube collect service and API route implemented with mocked provider tests and app error mapping."`를 추가한다.
   - 3회 수정 시도 후에도 실패: `"status": "error"`로 설정하고 `"error_message": "specific error"`를 추가한다.
   - 사용자 입력 필요: `"status": "blocked"`로 설정하고 `"blocked_reason": "specific reason"`을 추가한 뒤 중단한다.

## 하지 말 것

- OpenAI 분석을 이 step에서 구현하지 말 것. 이유: `collect -> analyze` 경계를 유지한다.
- YouTube OAuth 또는 YouTube Analytics API를 사용하지 말 것. 이유: MVP는 공개 YouTube Data API만 사용한다.
- API key 또는 provider raw error를 클라이언트 응답에 노출하지 말 것. 이유: 보안과 사용자 경험을 지킨다.
