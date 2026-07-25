# Step 4: admin-security-api

## 읽을 파일

- `/docs/API_SPEC.md`
- `/AGENTS.md`
- step 2~3 use cases/results
- phase 5 USER/ADMIN principal and security

## 작업

- USER review/restaurant report endpoint를 추가한다.
- ADMIN comment queue, review report queue와 restaurant report queue endpoint를 추가한다.
- request/response DTO와 mapper를 presentation에 분리한다.
- security chain에 정확한 USER/ADMIN matcher를 추가하고 deny-by-default를 유지한다.
- cursor, reason/decision enum과 ProblemDetail을 OpenAPI에 반영한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. public/USER/ADMIN 권한 matrix와 OpenAPI schema를 MockMvc로 검증한다.
2. 성공 시 step 4를 `completed`로 기록한다.
3. 실패 3회 시 `error`, 계약 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- ADMIN endpoint를 ROLE_USER에 허용하지 말 것.
- Controller에서 state transition이나 repository query를 수행하지 말 것.
- provider/error 내부 정보를 response에 포함하지 말 것.
