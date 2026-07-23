# Step 1: review-draft-persistence

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-review-drafts/index.json`
- `/src/main/kotlin/com/ridervoice/api/review/domain/`
- `/src/main/resources/db/migration/`

## 작업

ReviewDraft repository와 새 MySQL Flyway migration을 추가한다. UUID는 `BINARY(16)`, 시각은 UTC `DATETIME(6)`로 저장하고 작성자·음식점·갱신 시각 조회에 필요한 인덱스를 둔다. 사용자·음식점·활성 상태 unique constraint로 활성 초안 하나를 보장하고 `version` 낙관적 잠금을 추가한다. 정식 reviews 및 report snapshot 테이블과 집계 query로 연결하지 않는다. 정식 전환 시 초안을 삭제할 수 있도록 transaction 경계를 repository interface에 반영한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. repository interface와 migration 정합성을 로컬 MySQL 기준으로 확인한다.
2. 활성 초안 unique constraint, version mapping과 삭제 동작을 검증한다.
3. phase index step 1을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 기존 migration을 수정하지 말 것. 이유: 적용된 checksum과 충돌한다.
- Hibernate schema auto-generation을 활성화하지 말 것. 이유: Flyway가 schema source of truth다.
- 기존 test를 깨뜨리지 말 것.
