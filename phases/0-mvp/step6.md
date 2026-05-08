# Step 6: final-hardening

## 읽을 파일

먼저 아래 파일을 읽고 architecture와 design intent를 이해한다:

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/0-mvp/index.json`
- 전체 `src/` 구현
- `package.json`

수정하기 전에 이전 step에서 작성된 code와 test를 주의 깊게 읽는다.

## 작업

MVP 전체를 점검하고 통합 품질을 높인다. 이 step은 새로운 큰 기능을 추가하지 않고, 문서 계약과 실제 구현이 맞는지 검증하고 작은 결함을 수정한다.

필수 작업:

1. 전체 command 실행:
   - `npm run lint`
   - `npm run test`
   - `npm run build`
2. 문서 계약 점검:
   - `AGENTS.md` CRITICAL rule 위반이 없는지 확인한다.
   - `docs/ARCHITECTURE.md`의 directory structure와 실제 파일 구조가 맞는지 확인한다.
   - `docs/PRD.md`의 MVP 제외 범위를 침범하지 않았는지 확인한다.
3. UX 점검:
   - API key 누락 메시지가 명확한지 확인한다.
   - invalid URL 메시지가 명확한지 확인한다.
   - collect 실패와 analyze 실패가 구분되는지 확인한다.
   - 분석 실패 시 collected data가 사라지지 않는지 확인한다.
4. 코드 점검:
   - client component에서 provider endpoint/API key를 직접 사용하지 않는지 검색한다.
   - provider raw error가 response로 노출되지 않는지 확인한다.
   - nullable metric field가 UI crash를 일으키지 않는지 확인한다.
5. 필요하면 `README.md`를 추가해 로컬 실행 방법과 `.env.local` 변수명을 짧게 문서화한다.

검증 검색 예시:

```bash
rg -n "YOUTUBE_API_KEY|OPENAI_API_KEY|api.openai.com|youtube.googleapis.com" src
```

`YOUTUBE_API_KEY`, `OPENAI_API_KEY`, provider endpoint 문자열은 server-only service 또는 API route 쪽에서만 발견되어야 한다.

## 인수 기준

```bash
npm run lint
npm run test
npm run build
```

## 검증

1. 인수 기준 command를 실행한다.
2. 위 provider boundary 검색을 실행하고 결과를 확인한다.
3. Architecture checklist를 확인한다:
   - `collect -> analyze` 경계가 유지되는가?
   - DB, OAuth, Analytics API, 경쟁 분석이 추가되지 않았는가?
   - dashboard-first UX가 유지되는가?
4. 이 step에 대해 `phases/0-mvp/index.json`을 업데이트한다:
   - 성공: `"status": "completed"`로 설정하고 `"summary": "MVP hardened with full lint/test/build verification, provider boundary checks, and run documentation."`를 추가한다.
   - 3회 수정 시도 후에도 실패: `"status": "error"`로 설정하고 `"error_message": "specific error"`를 추가한다.
   - 사용자 입력 필요: `"status": "blocked"`로 설정하고 `"blocked_reason": "specific reason"`을 추가한 뒤 중단한다.

## 하지 말 것

- 새 major feature를 추가하지 말 것. 이유: 이 step은 hardening과 검증 step이다.
- DB, OAuth, Analytics API, 경쟁 채널 분석을 추가하지 말 것. 이유: MVP 제외 사항이다.
- 실패 command를 무시하고 completed로 표시하지 말 것. 이유: Harness index는 다음 작업의 source of truth다.
