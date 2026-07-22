# Step 3: clova-ocr-adapter

## 읽을 파일
- `/AGENTS.md`
- `/docs/ADR.md`
- `/phases/2-visit-verification/step2.md`

## 작업
CLOVA OCR client adapter를 구현하고 텍스트·bounding box를 내부 OCR result로 정규화한다. timeout, rate limit, malformed response를 retryable/non-retryable 오류로 구분한다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- CLOVA response 타입을 domain entity로 저장하지 말 것. 이유: provider 변경 가능성을 격리해야 한다.
