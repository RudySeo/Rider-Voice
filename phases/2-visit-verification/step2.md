# Step 2: ocr-job-pipeline

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-visit-verification/step0.md`
- `/phases/2-visit-verification/step1.md`

## 작업
Visit 생성 후 SQS 작업을 발행하고 worker가 상태를 갱신하는 pipeline을 구현한다. retry, visibility timeout, DLQ, idempotency key를 정의하고 OCR 처리 중 상태 조회 API를 위한 application port를 둔다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- OCR provider 호출을 HTTP request thread에서 동기 처리하지 말 것. 이유: 외부 지연이 API timeout으로 전파된다.
