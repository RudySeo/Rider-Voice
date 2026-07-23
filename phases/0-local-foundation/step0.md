# Step 0: baseline-alignment

## 읽을 파일

먼저 아래 파일과 현재 worktree 변경을 읽고 architecture와 design intent를 이해한다:

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/build.gradle.kts`
- `/src/main/resources/application.yml`
- `/src/main/resources/application-local.yml`
- `/src/main/resources/db/migration/`

## 작업

현재 worktree의 PostgreSQL→MySQL 9.3 전환, 로컬 전용 실행 정책과 Swagger/OpenAPI 변경을 검토해 일관된 baseline으로 완성한다. PostgreSQL 및 Testcontainers 의존성·테스트 지원 코드를 제거하고 기본 `test`, `check`, `build`가 Docker 없이 실행되게 한다. 로컬 datasource는 `rider` DB와 환경변수 override를 유지하며 Flyway V1~V3와 Hibernate `ddl-auto=validate`가 호환되어야 한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. 인수 기준 command를 실행한다.
2. MySQL, 로컬 실행, OpenAPI 문서와 설정이 서로 일치하는지 확인한다.
3. `phases/0-local-foundation/index.json`의 step 0을 `completed`로 바꾸고 산출물 summary를 추가한다.

## 하지 말 것

- Docker, Docker Compose 또는 Testcontainers를 실행하거나 유지하지 말 것. 이유: 현재 실행 경계는 로컬 프로세스뿐이다.
- 기존 MySQL `rider` 데이터를 삭제하지 말 것. 이유: 사용자 로컬 데이터는 보존해야 한다.
- 기존 test를 깨뜨리지 말 것.
