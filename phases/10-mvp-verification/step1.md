# Step 1: mysql-schema-concurrency

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- 모든 Entity와 persistence adapter
- `/src/test/kotlin/com/ridervoice/api/support/MySqlIntegrationTest.kt`
- phases 6, 7, 9 integration-tag tests

## 작업

- 실행 중인 로컬 MySQL `rider`를 대상으로 Hibernate `ddl-auto=update` mapping을 검증한다.
- 모든 Entity Long IDENTITY, FK, unique, index와 LAZY 자식→부모 관계를 확인한다.
- 장소·브랜드·external reference race, author state locking, report unique와 merge state conflict를 동시 요청으로 검증한다.
- legacy table이 남아 있어도 자동 DROP하지 않고 목표 Entity mapping과 제약만 검증한다.
- MySQL이 실행 중이 아니면 Docker를 시작하지 말고 구체적 이유로 `blocked` 처리한다.

## 인수 기준

```bash
./gradlew integrationTest --no-daemon
```

## 검증

1. command 결과와 schema metadata assertion을 확인한다.
2. 성공 시 step 1을 `completed`로 기록한다.
3. DB 연결 불가 시 `blocked`, mapping/concurrency 결함은 3회 수정 후 `error`로 기록한다.

## 하지 말 것

- `rider` 데이터베이스를 DROP하거나 truncate하지 말 것.
- Docker, Docker Compose 또는 Testcontainers를 실행하지 말 것.
- concurrency test를 단순 순차 test로 대체하지 말 것.
