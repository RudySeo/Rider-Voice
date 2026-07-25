# Step 6: review-integration

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- phase 7 전체 변경
- phase 6 restaurant contract/integration tests

## 작업

- existing/Kakao/manual target review create를 회귀 검증한다.
- concurrent state creation, sequence와 90일 lock을 integration-tag test로 검증한다.
- latest update/delete, history immutability와 state retention을 검증한다.
- API/OpenAPI/security 전체 review core 계약을 검증한다.

## 인수 기준

```bash
./gradlew test --no-daemon
./gradlew check build --no-daemon
```

## 검증

1. 두 command와 `git diff --check`를 실행한다.
2. 성공 시 step 6을 `completed`로 기록한다.
3. MySQL integration 실행은 phase 10에 맡긴다.

## 하지 말 것

- DB 부재 때문에 Docker/Testcontainers를 시작하지 말 것.
- aggregate, public list, report/admin 기능을 미리 구현하지 말 것.
- 기존 테스트를 약화하거나 disable하지 말 것.
