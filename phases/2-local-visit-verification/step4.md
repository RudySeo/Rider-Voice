# Step 4: clova-ocr-adapter

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-local-visit-verification/index.json`
- `/src/main/kotlin/com/ridervoice/api/visit/application/`

## 작업

OCR port와 NAVER CLOVA OCR infrastructure adapter를 구현한다. endpoint와 secret은 환경변수로 주입하고 provider DTO를 adapter 내부에 둔다. 성공, timeout, rate limit, 5xx와 잘못된 JSON을 내부 결과로 매핑하며 secret과 provider 원문 오류를 노출하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. Docker 없는 HTTP stub으로 provider 시나리오를 검증한다.
2. index step 4를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 실제 CLOVA secret을 코드·테스트·로그에 넣지 말 것. 이유: 자격 증명이 노출된다.
- domain/application에 provider 타입을 노출하지 말 것. 이유: adapter 교체가 어려워진다.
- 기존 test를 깨뜨리지 말 것.
