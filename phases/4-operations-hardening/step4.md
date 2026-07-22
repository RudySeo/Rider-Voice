# Step 4: contract-e2e-tests

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/3-reviews-reports/index.json`
- `/phases/4-operations-hardening/step0.md`

## 작업
OpenAPI schema 검증과 Testcontainers 기반 전체 backend 흐름을 작성한다. 카카오·CLOVA·카카오 로컬·S3·SQS는 stub으로 대체하고 로그인부터 리포트 공개까지 정상·실패 시나리오를 검증한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
./gradlew build
```

## 하지 말 것
- 외부 provider 실계정과 운영 데이터를 테스트에 사용하지 말 것. 이유: 재현성과 개인정보 보호 때문이다.
