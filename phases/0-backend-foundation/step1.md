# Step 1: persistence-foundation

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/0-backend-foundation/step0.md`
- 이전 step에서 생성된 Gradle 및 Spring Boot 파일

## 작업

PostgreSQL·JPA·Flyway를 실행하고 테스트할 수 있는 persistence 기반을 구성한다.

- PostgreSQL datasource 설정을 profile별로 분리한다.
- Hibernate `ddl-auto`를 `validate` 또는 비활성으로 설정한다.
- Flyway migration 위치와 baseline 정책을 구성한다.
- 공통 `BaseEntity` 또는 auditing 전략을 결정하고 UTC 기준 created/updated 시각을 제공한다.
- Testcontainers PostgreSQL 통합 테스트 기반을 추가한다.
- 운영 도메인 테이블은 아직 만들지 말고 migration 실행 경로만 검증한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

PostgreSQL 컨테이너에서 Flyway migration이 성공하고 JPA context가 부팅되어야 한다.

## 하지 말 것

- JPA entity를 API 응답으로 반환하지 말 것. 이유: DTO 경계를 다음 step부터 강제해야 한다.
- 운영 도메인 테이블을 임의로 만들지 말 것. 이유: 각 feature phase가 소유해야 한다.
