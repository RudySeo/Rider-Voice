# Step 3: ocr-job-pipeline

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-local-visit-verification/index.json`
- `/src/main/kotlin/com/ridervoice/api/visit/application/`
- `/src/main/kotlin/com/ridervoice/api/visit/domain/`

## 작업

OCR 작업 발행 port와 로컬 in-process adapter를 구현한다. Visit 생성 transaction 이후 작업을 받아 제한된 executor에서 처리하고 중복 job ID를 멱등하게 무시한다. 처리 성공·재시도 가능 실패·수동 검수 전환 결과를 application service interface로 반환한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 중복 발행, 성공, timeout과 재시도 한도를 단위 테스트로 검증한다.
2. index step 3을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- SQS, DLQ 또는 Docker queue를 추가하지 말 것. 이유: 현재 로컬 실행 경계를 벗어난다.
- request thread에서 OCR 전체 처리를 기다리지 말 것. 이유: 외부 지연이 API에 전파된다.
- 기존 test를 깨뜨리지 말 것.
