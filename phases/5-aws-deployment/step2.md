# Step 2: deployment-pipeline

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/5-aws-deployment/step1.md`

## 작업
Gradle test/check/build를 통과한 artifact만 API와 worker 이미지로 빌드·배포하는 CI pipeline을 구성한다. migration 실행 순서와 rollback 절차를 문서화한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
./gradlew build
```

## 하지 말 것
- 테스트 실패 상태로 배포하는 우회 경로를 만들지 말 것. 이유: API 계약과 DB 무결성을 보장해야 한다.
