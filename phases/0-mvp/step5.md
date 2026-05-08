# Step 5: dashboard-ui

## 읽을 파일

먼저 아래 파일을 읽고 architecture와 design intent를 이해한다:

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/0-mvp/index.json`
- `/src/app/page.tsx`
- `/src/components/ChannelInput.tsx`
- `/src/components/ProgressPanel.tsx`
- `/src/components/ErrorPanel.tsx`
- `/src/lib/metrics.ts`
- `/src/types/youtube.ts`
- `/src/types/analysis.ts`

수정하기 전에 이전 step에서 작성된 code와 test를 주의 깊게 읽는다.

## 작업

분석 완료 후 보여줄 대시보드 UI를 구현한다. 목표는 사용자가 다음 영상 제작 결정을 바로 내릴 수 있게 하는 것이다.

생성/수정할 핵심 파일:

- `src/components/dashboard/OverviewCards.tsx`
- `src/components/dashboard/PerformanceCharts.tsx`
- `src/components/dashboard/RecommendedVideos.tsx`
- `src/components/dashboard/ActionChecklist.tsx`
- 필요 시 `src/components/dashboard/Dashboard.tsx`
- `src/app/page.tsx`
- 관련 test 파일

대시보드 필수 섹션:

1. 한눈에 보는 진단
   - 전체 점수.
   - 3줄 요약.
   - 가장 강한 신호 1개.
   - 가장 위험한 병목 1개.
   - 이번 주 바로 할 일 1개.
2. 성과 패턴
   - 상위 영상 랭킹.
   - 조회수/좋아요/댓글 성과 카드.
   - 최근 영상 성과 추이 chart.
   - 업로드 리듬 chart.
   - 평균 대비 튄 영상 표시.
3. 다음에 만들 콘텐츠 추천
   - 추천 영상 아이디어 5개.
   - 각 카드에 제목 방향, 추천 포맷, 맞는 이유, 근거, 기대 효과, 난이도를 표시.
4. 실행 체크리스트
   - 우선순위 7개 이하.
   - 각 항목에 해야 할 일, 이유, 예상 효과, 우선순위를 표시.

UI 제약:

- 카드 안에 또 다른 카드가 중첩되지 않게 한다.
- chart container는 stable height를 가져 layout shift를 방지한다.
- 모바일과 데스크톱에서 텍스트가 버튼/카드 밖으로 넘치지 않게 한다.
- 과도한 hero/marketing layout을 만들지 않는다.
- lucide icon을 적절히 사용한다.
- Recharts는 client component 안에서 사용한다.

Test 요구사항:

- dashboard가 score, summary, recommended videos, checklist를 렌더링한다.
- chart component가 empty/short video list에서도 crash하지 않는다.
- nullable metric field가 있어도 UI가 crash하지 않는다.
- 모바일 수준의 좁은 layout에서도 핵심 텍스트가 DOM에 남아 있다.

## 인수 기준

```bash
npm run lint
npm run test
npm run build
```

## 검증

1. Dashboard component test가 주요 섹션을 검증하는지 확인한다.
2. `npm run build`가 Recharts client boundary 문제 없이 통과하는지 확인한다.
3. Architecture checklist를 확인한다:
   - 대시보드가 긴 text report보다 chart/card/checklist 중심인가?
   - UI가 `src/components/dashboard/` 구조를 따르는가?
   - provider 호출이 dashboard component에 들어가지 않았는가?
4. 이 step에 대해 `phases/0-mvp/index.json`을 업데이트한다:
   - 성공: `"status": "completed"`로 설정하고 `"summary": "Dashboard UI implemented with overview cards, performance charts, recommendations, checklist, and component tests."`를 추가한다.
   - 3회 수정 시도 후에도 실패: `"status": "error"`로 설정하고 `"error_message": "specific error"`를 추가한다.
   - 사용자 입력 필요: `"status": "blocked"`로 설정하고 `"blocked_reason": "specific reason"`을 추가한 뒤 중단한다.

## 하지 말 것

- 결과 화면을 긴 Markdown 리포트 중심으로 만들지 말 것. 이유: PRD는 대시보드와 실행 체크리스트를 우선한다.
- chart를 server component에서 렌더링하지 말 것. 이유: Recharts는 client component boundary가 필요하다.
- decorative gradient/orb 중심의 landing page를 만들지 말 것. 이유: 이 앱은 분석 업무 도구다.
