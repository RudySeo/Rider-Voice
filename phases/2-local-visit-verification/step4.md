# Step 4: evidence-extraction-adapter

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/2-local-visit-verification/index.json`
- `/src/main/kotlin/com/ridervoice/api/visit/application/`

## 작업

NAVER Cloud CLOVA OCR과 후보 멀티모달 모델을 정확도, 비용, 응답 시간, 개인정보 처리와 로컬 운영 복잡도 기준으로 비교한다. LangChain4j를 Spring Boot 내부에 둘지 Python LangChain 서비스를 분리할지도 함께 검토하고 선택 결과를 새 ADR로 먼저 기록한다. 이후 provider-neutral OCR port와 선택한 infrastructure adapter를 구현한다. endpoint와 secret은 환경변수로 주입하고 provider DTO는 adapter 내부에 둔다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 고정 fixture로 후보별 구조화 추출 품질과 실패 동작을 비교한 기록을 남긴다.
2. Docker 없는 stub으로 성공, timeout, rate limit, 잘못된 응답과 provider 장애를 검증한다.
3. 새 ADR과 구현이 일치하는지 확인한 뒤 index step 4를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 비교 전에 provider나 LangChain 실행 형태를 확정하지 말 것. 이유: ADR-017의 재검토 결정을 우회한다.
- 실제 provider secret을 코드·테스트·로그에 넣지 말 것. 이유: 자격 증명이 노출된다.
- domain/application에 provider 타입을 노출하지 말 것. 이유: adapter 교체가 어려워진다.
- 기존 test를 깨뜨리지 말 것.
