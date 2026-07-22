# Step 1: evidence-persistence

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-local-visit-verification/index.json`
- `/src/main/kotlin/com/ridervoice/api/visit/domain/`
- `/src/main/resources/db/migration/`

## 작업

VisitEvidence의 Spring Data repository와 MySQL Flyway migration을 추가한다. UUID는 `BINARY(16)`, 시각은 UTC `DATETIME(6)`로 맞추고 주문 HMAC·이미지 hash 중복 제약, 사용자·상태·완료 시각 조회 인덱스를 정의한다. schema 변경은 새 migration으로만 추가한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. repository interface와 migration 정합성을 로컬 MySQL 기준으로 확인한다.
2. index step 1을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 기존 Flyway migration을 다시 수정하지 말 것. 이유: 이미 적용된 checksum과 충돌한다.
- Hibernate schema auto-generation을 활성화하지 말 것. 이유: Flyway가 schema source of truth다.
- 기존 test를 깨뜨리지 말 것.
