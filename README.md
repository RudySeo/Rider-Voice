# YouTube Channel Insight

YouTube Channel Insight는 채널 URL 하나로 공개 채널 정보와 최근 업로드 데이터를 수집하고, OpenAI 분석 결과를 대시보드와 실행 체크리스트로 보여주는 MVP입니다.

## 로컬 실행

```bash
npm install
npm run dev
```

개발 서버는 기본적으로 `http://localhost:3000`에서 실행됩니다.

## 환경 변수

`.env.local`에 아래 값을 설정합니다.

```bash
YOUTUBE_API_KEY=your_youtube_data_api_key
OPENAI_API_KEY=your_openai_api_key
OPENAI_MODEL=gpt-5.4-mini
```

`OPENAI_MODEL`은 선택값입니다. 설정하지 않으면 앱의 기본 비용형 모델을 사용합니다.

## 검증 명령

```bash
npm run lint
npm run test
npm run build
```

## MVP 범위

- 지원 입력: `youtube.com/@handle`, `@handle`, `youtube.com/channel/UC...`, `UC...`
- 수집: YouTube Data API v3 공개 채널 정보와 최근 공개 업로드 최대 50개
- 분석: OpenAI Responses API Structured Outputs 기반 한국어 분석
- 제외: 로그인, DB 저장, YouTube OAuth, YouTube Analytics API, 경쟁 채널 비교, 내보내기
