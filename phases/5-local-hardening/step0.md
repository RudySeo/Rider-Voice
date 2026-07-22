# Step 0: openapi-contract-tests

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/`

## 작업

전체 `/api/v1` endpoint가 OpenAPI에 포함되고 request/response schema, Bearer 요구사항, 공개 endpoint, RFC 7807 오류 계약이 실제 Controller와 일치하는지 Docker 없는 contract test로 검증한다. 생성된 schema를 React Native 계약 기준 파일로 내보낼 수 있는 Gradle task를 추가한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 의도하지 않은 endpoint 누락과 schema 변경을 테스트 실패로 감지한다.
2. phase index step 0을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- DB entity를 OpenAPI schema로 노출하지 말 것. 이유: persistence 구조가 클라이언트 계약이 된다.
- 실제 secret/token을 example에 넣지 말 것. 이유: 자격 증명이 노출된다.
- 기존 test를 깨뜨리지 말 것.
