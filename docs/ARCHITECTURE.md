# 아키텍처

## 개요
YouTube Channel Insight는 Next.js App Router 기반 단일 웹 앱이다. UI와 API route를 같은 프로젝트에서 관리하지만, 외부 provider 호출은 서버 영역으로만 제한한다. 핵심 데이터 흐름은 `collect -> analyze` 두 단계로 분리한다.

## 기술 스택
- Next.js App Router
- TypeScript strict mode
- Tailwind CSS
- Vitest + React Testing Library
- Zod
- Recharts
- Lucide React
- YouTube Data API v3
- OpenAI Responses API + Structured Outputs

## 디렉토리 구조
```text
src/
├── app/
│   ├── page.tsx                     # 메인 사용자 플로우
│   └── api/
│       └── channel/
│           ├── collect/route.ts      # YouTube 데이터 수집
│           └── analyze/route.ts      # OpenAI 분석
├── components/
│   ├── AnalysisShell.tsx             # 입력 -> 수집 -> 분석 -> 결과 상태 orchestration
│   ├── ChannelInput.tsx
│   ├── ProgressPanel.tsx
│   ├── ErrorPanel.tsx
│   └── dashboard/
│       ├── Dashboard.tsx             # 결과 대시보드 composition
│       ├── OverviewCards.tsx
│       ├── PerformanceCharts.tsx
│       ├── RecommendedVideos.tsx
│       └── ActionChecklist.tsx
├── lib/
│   ├── channel-url.ts                # 지원 URL 파싱
│   ├── metrics.ts                    # 평균, engagement, ranking 계산
│   ├── app-errors.ts                 # 앱 에러 코드/메시지
│   └── schemas.ts                    # Zod request/response schema
├── services/
│   ├── youtube.ts                    # server-only YouTube wrapper
│   └── openai.ts                     # server-only OpenAI wrapper
└── types/
    ├── youtube.ts
    ├── analysis.ts
    └── api.ts
```

## 주요 패턴
- Server Components를 기본으로 하되, 입력/진행 상태/결과 전환이 필요한 메인 플로우는 Client Component로 구현한다.
- Route Handler는 request validation, service 호출, 앱 에러 매핑을 담당한다.
- Provider wrapper는 raw provider response를 앱 내부 타입으로 정규화한다.
- Client Component는 앱 API만 호출하고 provider SDK, provider endpoint, API key에 접근하지 않는다.
- API 응답은 성공과 실패 모두 예측 가능한 JSON 형태를 유지한다.

## 데이터 흐름
```text
사용자 채널 URL 입력
  -> client-side URL 형식 검증
  -> POST /api/channel/collect
  -> YouTube service
  -> normalized collect result
  -> POST /api/channel/analyze
  -> OpenAI service
  -> structured analysis result
  -> dashboard render
```

## API Route 책임
### `POST /api/channel/collect`
- 입력: `{ "channelUrl": string }`
- 책임:
  - URL 형식 검증.
  - handle 또는 channel ID 추출.
  - YouTube Data API로 채널 기본정보 조회.
  - uploads playlist에서 최근 공개 영상 최대 50개 조회.
  - 영상 통계와 메타데이터 보강.
  - 앱 내부 collect result 형태로 반환.
- 하지 않는 것:
  - OpenAI 호출.
  - GPT prompt 생성.
  - 분석 문장 생성.

### `POST /api/channel/analyze`
- 입력: collect success payload.
- 책임:
  - 수집 결과 schema 검증.
  - 분석용 payload 정규화.
  - OpenAI Responses API 호출.
  - Structured Outputs schema로 dashboard JSON 반환.
- 하지 않는 것:
  - YouTube API 호출.
  - 채널 URL 재해석.
  - 데이터를 DB에 저장.

## YouTube 수집 전략
- `@handle` 입력은 `channels.list`의 handle 기반 조회를 사용한다.
- `UC...` 입력은 channel ID 기반 조회를 사용한다.
- 채널 응답에서 uploads playlist ID를 얻는다.
- `playlistItems.list`로 최근 업로드 video ID를 최대 50개 수집한다.
- `videos.list`로 `snippet`, `statistics`, `contentDetails`를 보강한다.
- 숫자 필드가 누락된 경우 0으로 단정하지 않고 nullable로 정규화한다.
- 공개 영상이 없는 경우 `NO_PUBLIC_VIDEOS`를 반환한다.

## OpenAI 분석 전략
- OpenAI provider 호출은 `src/services/openai.ts`에서만 수행한다.
- Responses API를 사용한다.
- Structured Outputs로 다음 UI 필드를 안정적으로 생성한다:
  - `overallScore`
  - `executiveSummary`
  - `strongSignals`
  - `growthBottlenecks`
  - `contentPatterns`
  - `recommendedNextVideos`
  - `actionChecklist`
  - `confidence`
- prompt는 데이터 기반 근거와 AI 추론을 구분하도록 작성한다.
- 분석 언어는 한국어로 고정한다.
- 모델은 `OPENAI_MODEL` 환경변수를 우선하고, 없으면 앱 기본 비용형 모델을 사용한다.

## 상태 관리
메인 클라이언트 플로우는 다음 상태만 사용한다.

```text
idle
validating
collecting
analyzing
complete
validation_failed
collection_failed
analysis_failed
```

- `collection_failed`: 수집 결과가 없으므로 분석을 시도하지 않는다.
- `analysis_failed`: 수집 결과는 유지하고, AI 분석 실패 메시지를 보여준다.
- 새로고침 시 결과는 사라져도 된다. MVP에서는 persistence를 제공하지 않는다.

## API 계약
### Collect request
```json
{
  "channelUrl": "https://www.youtube.com/@handle"
}
```

### Collect success
```json
{
  "channel": {
    "id": "UC...",
    "title": "Channel title",
    "description": "Channel description",
    "thumbnailUrl": "https://...",
    "subscriberCount": 1000,
    "viewCount": 100000,
    "videoCount": 50
  },
  "videos": [
    {
      "id": "video-id",
      "title": "Video title",
      "description": "Video description",
      "publishedAt": "2026-05-08T00:00:00Z",
      "thumbnailUrl": "https://...",
      "viewCount": 1000,
      "likeCount": 50,
      "commentCount": 10,
      "duration": "PT10M"
    }
  ],
  "sampleSize": 50,
  "collectedAt": "2026-05-08T00:00:00Z"
}
```

### Analyze success
```json
{
  "overallScore": 82,
  "executiveSummary": ["요약 1", "요약 2", "요약 3"],
  "strongSignals": ["강한 성과 신호"],
  "growthBottlenecks": ["성장 병목"],
  "contentPatterns": ["반복 가능한 콘텐츠 패턴"],
  "recommendedNextVideos": [
    {
      "titleDirection": "추천 제목 방향",
      "format": "추천 포맷",
      "whyItFits": "이 채널에 맞는 이유",
      "evidence": "근거가 된 기존 영상 패턴",
      "expectedImpact": "기대 효과",
      "difficulty": "low"
    }
  ],
  "actionChecklist": [
    {
      "priority": "high",
      "task": "해야 할 일",
      "reason": "이유",
      "expectedImpact": "예상 효과"
    }
  ],
  "confidence": {
    "level": "medium",
    "reason": "최근 공개 영상 37개 기준"
  }
}
```

### Error response
```json
{
  "error": {
    "code": "INVALID_CHANNEL_URL",
    "message": "지원하지 않는 YouTube URL 형식입니다."
  }
}
```

## 에러 taxonomy
- `INVALID_CHANNEL_URL`: 지원하지 않는 URL 형식.
- `MISSING_YOUTUBE_API_KEY`: 서버에 `YOUTUBE_API_KEY`가 없음.
- `MISSING_OPENAI_API_KEY`: 서버에 `OPENAI_API_KEY`가 없음.
- `CHANNEL_NOT_FOUND`: handle/channel ID로 공개 채널을 찾지 못함.
- `NO_PUBLIC_VIDEOS`: 최근 공개 업로드가 없음.
- `YOUTUBE_RATE_LIMITED`: YouTube quota 또는 rate limit 실패.
- `YOUTUBE_PROVIDER_ERROR`: 기타 YouTube provider 실패.
- `OPENAI_REFUSAL`: OpenAI가 structured analysis를 제공하지 않음.
- `OPENAI_PROVIDER_ERROR`: 기타 OpenAI provider 실패.

## 테스트 전략
- URL parser와 validation은 unit test를 먼저 작성한다.
- metric 계산 helper는 unit test로 검증한다.
- YouTube service는 mocked fetch로 provider 응답과 에러 매핑을 검증한다.
- OpenAI service는 mocked SDK로 structured output parsing과 실패 처리를 검증한다.
- API route는 missing env, invalid request, provider failure, success path를 검증한다.
- UI는 입력, 진행 상태, 오류 상태, 대시보드 렌더링을 React Testing Library로 검증한다.

## 배포 및 런타임
- 로컬 개발과 Vercel 배포를 기준으로 설계한다.
- API route runtime은 Node.js로 고정한다.
- 환경변수는 `.env.local` 또는 배포 환경변수로 주입한다.
- 파일 시스템 persistence에 의존하지 않는다.
