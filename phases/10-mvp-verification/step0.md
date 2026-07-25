# Step 0: api-security-contracts

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/API_SPEC.md`
- phases 5~9 전체 endpoint/security/OpenAPI test

## 작업

- 목표 API의 공개·온보딩·USER·ADMIN 권한 matrix를 하나의 회귀 suite로 검증한다.
- 모든 request/response DTO, enum, nullable, cursor와 restaurant target discriminator가 `/v3/api-docs`에 정확히 노출되는지 검증한다.
- 공개 review/report response의 UNVERIFIED와 notice를 검증한다.
- deny-by-default와 provider/secret/stack trace 비노출을 검증한다.
- 중복되거나 의미가 충돌하는 contract test만 정리하고 coverage를 약화하지 않는다.

## 인수 기준

```bash
./gradlew test --no-daemon
./gradlew check --no-daemon
```

## 검증

1. 두 command와 `git diff --check`를 실행한다.
2. 성공 시 step 0을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 계약 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- security test를 permitAll로 완화해 통과시키지 말 것.
- OpenAPI annotation만 보고 runtime security 검증을 생략하지 말 것.
- 클라이언트 앱을 구현하지 말 것.
