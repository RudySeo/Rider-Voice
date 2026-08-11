# Step 3: cleanup-regression

> **역사적 기록:** 이 step이 보존한 onboarding 흐름은 `f76679c`에서 폐기됐다. 현재 계약은 루트 `AGENTS.md`와 `docs/API_SPEC.md`를 따른다.

## 읽을 파일

먼저 아래 파일과 step 0~2 변경을 모두 읽는다:

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/**`
- `/src/test/kotlin/com/ridervoice/api/**`
- `/build.gradle.kts`
- `/src/main/resources/application.yml`

## 작업

legacy 제거 뒤 남은 공통 기반을 검증하고 참조 찌꺼기만 정리한다.

- 직접 카카오 OAuth, OAuth login state, 단일 Restaurant, review-drafts 참조를 `rg`로 검사한다.
- 사용하지 않는 import, bean, config와 schema expectation을 제거한다.
- onboarding, token refresh/rotation, logout, ProblemDetail, OpenAPI와 deny-by-default security를 유지한다.
- 목표 기능을 미리 구현하지 않고 auth/common 기반만 빌드 가능하게 만든다.

## 인수 기준

```bash
./gradlew test --no-daemon
./gradlew check build --no-daemon
```

## 검증

1. 두 command와 `git diff --check`를 실행한다.
2. AGENTS.md의 architecture checklist를 확인한다.
3. 성공 시 step 3을 `completed`로 바꾸고 남은 기반을 한 줄 `summary`로 기록한다.
4. 3회 실패 시 `error`, 사용자 입력 필요 시 `blocked`로 기록한다.

## 하지 말 것

- 로컬 `rider` 데이터베이스를 DROP하지 말 것. 이유: 이 phase는 code cleanup이다.
- OAuth2, restaurant 또는 review 신규 구현을 시작하지 말 것.
- Docker, Testcontainers, AWS를 사용하지 말 것.
