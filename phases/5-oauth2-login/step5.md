# Step 5: oauth2-regression

> **역사적 기록:** 이 step의 onboarding 회귀 범위는 `f76679c`에서 폐기됐다. 현재 계약은 루트 `AGENTS.md`와 `docs/API_SPEC.md`를 따른다.

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- phase 5 전체 변경
- auth/common 전체 테스트

## 작업

- OAuth2 login부터 onboarding, refresh rotation, logout과 current DB role까지 회귀 검증한다.
- 직접 OAuth adapter/state/endpoint 참조가 다시 생기지 않았는지 검사한다.
- OpenAPI metadata를 공개 미인증 리뷰 목표와 일치시킨다.
- 이 phase 범위를 벗어난 restaurant/review 코드는 구현하지 않는다.

## 인수 기준

```bash
./gradlew test --no-daemon
./gradlew check build --no-daemon
```

## 검증

1. 두 command와 `git diff --check`를 실행한다.
2. 성공 시 step 5를 `completed`로 기록한다.
3. 실패 3회 시 `error`, 환경 설정이 필요하면 구체적 `blocked_reason`을 기록한다.

## 하지 말 것

- 실제 카카오 계정 로그인을 acceptance criterion으로 요구하지 말 것.
- Docker, Testcontainers 또는 AWS를 실행하지 말 것.
- 테스트를 삭제해 regression을 통과시키지 말 것.
