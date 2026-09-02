# Step 8: full-regression

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/12-rider-role-review-access/index.json`
- `/phases/12-rider-role-review-access/step0.md`부터 `/step7.md`

## 작업

전체 backend·mobile·OpenAPI·Flyway·MySQL 회귀를 실행하고 실패 원인을 수정한다. 관련 없는 사용자 변경을 보존한 채 phase 상태와 한 줄 summary를 완료 처리한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
./gradlew integrationTest
./gradlew migrationTest
cd mobile && pnpm run api:generate && pnpm run typecheck && pnpm run lint && pnpm run test
```

## 검증

Architecture와 CRITICAL 규칙, migration 적용·재실행, API role matrix와 모바일 role 가시성을 최종 확인한다.

## 하지 말 것

- Docker, Testcontainers 또는 AWS 리소스를 실행하지 말 것. 이유: 사용자가 별도로 승인하지 않았다.
- 기존 테스트를 삭제하거나 약화해 통과시키지 말 것.
