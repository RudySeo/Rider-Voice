# Step 4: client-flow

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
- `/src/types/api.ts`
- `/src/types/youtube.ts`
- `/src/types/analysis.ts`
- `/src/app/api/channel/collect/route.ts`
- `/src/app/api/channel/analyze/route.ts`

수정하기 전에 이전 step에서 작성된 code와 test를 주의 깊게 읽는다.

## 작업

사용자가 채널 URL을 입력하고 `collect -> analyze`를 실행하는 client flow를 TDD로 구현한다. 이 step은 대시보드 상세 chart 구현 전까지의 shell, 입력, 진행 상태, 오류 상태, API orchestration을 담당한다.

생성/수정할 핵심 파일:

- `src/app/page.tsx`
- `src/components/ChannelInput.tsx`
- `src/components/ProgressPanel.tsx`
- `src/components/ErrorPanel.tsx`
- 필요 시 `src/components/AnalysisShell.tsx`
- 관련 test 파일

상태 모델:

- `idle`
- `validating`
- `collecting`
- `analyzing`
- `complete`
- `validation_failed`
- `collection_failed`
- `analysis_failed`

Flow 요구사항:

1. 첫 화면은 실제 입력 폼이어야 한다.
2. 사용자는 채널 URL 또는 `@handle`을 입력한다.
3. submit 시 client-side parser로 먼저 형식을 검증한다.
4. validation 실패 시 API를 호출하지 않는다.
5. validation 성공 시 `/api/channel/collect`를 호출한다.
6. collect 성공 시 `/api/channel/analyze`를 호출한다.
7. collect 실패 시 분석을 시도하지 않는다.
8. analyze 실패 시 수집 결과는 유지하고 분석 실패 error를 보여준다.
9. complete 상태에서는 이후 step에서 dashboard가 들어갈 영역에 분석 결과 placeholder를 보여준다.

UI 요구사항:

- 입력 영역은 앱 목적이 바로 보이게 한다.
- 지원 URL 예시는 짧게 보여준다.
- progress label은 단계별로 다르게 표시한다:
  - 채널 찾는 중
  - 최근 업로드 읽는 중
  - 영상 성과 정리 중
  - 성과 패턴 찾는 중
  - 다음 액션 만드는 중
- error panel은 실패 원인과 다음 조치를 함께 보여준다.

Test 요구사항:

- initial form render.
- invalid URL submit 시 fetch 호출 없음.
- valid URL submit 시 collect와 analyze가 순서대로 호출됨.
- collect 실패 시 analyze 호출 없음.
- analyze 실패 시 collected data 유지 메시지 표시.
- complete 상태 placeholder 표시.

## 인수 기준

```bash
npm run lint
npm run test
npm run build
```

## 검증

1. React Testing Library test가 입력, progress, error, complete 상태를 검증하는지 확인한다.
2. Architecture checklist를 확인한다:
   - Client Component가 YouTube/OpenAI provider를 직접 호출하지 않는가?
   - Client Component가 앱 API route만 호출하는가?
   - long-running AI 분석 중 progress가 보이는가?
3. 이 step에 대해 `phases/0-mvp/index.json`을 업데이트한다:
   - 성공: `"status": "completed"`로 설정하고 `"summary": "Client flow implemented with URL validation, collect/analyze orchestration, progress labels, and error states."`를 추가한다.
   - 3회 수정 시도 후에도 실패: `"status": "error"`로 설정하고 `"error_message": "specific error"`를 추가한다.
   - 사용자 입력 필요: `"status": "blocked"`로 설정하고 `"blocked_reason": "specific reason"`을 추가한 뒤 중단한다.

## 하지 말 것

- Client Component에서 YouTube 또는 OpenAI endpoint를 직접 호출하지 말 것. 이유: API key와 provider boundary를 보호해야 한다.
- 긴 landing page를 만들지 말 것. 이유: 첫 화면은 실제 분석 입력 화면이어야 한다.
- chart와 dashboard 상세 구현을 이 step에서 끝까지 만들지 말 것. 이유: dashboard는 다음 step에서 분리한다.
