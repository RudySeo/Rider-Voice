# Step 2: review-persistence-adapter

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/4-private-review-crud/index.json`
- `/src/main/kotlin/com/ridervoice/api/review/application/port/out/ReviewRepository.kt`
- `/src/main/kotlin/com/ridervoice/api/review/domain/`
- `/src/main/resources/db/migration/`

## 작업

`review/infrastructure/persistence`에 Spring Data 내부 repository와 application output port adapter를 구현한다. 새 Flyway migration으로 `reviews` 테이블을 만들고 작성자·음식점 foreign key, 6개 평가 열, nullable `VARCHAR(200)` 의견, UTC audit 시각을 저장한다. `(author_user_id, restaurant_id)` unique 제약과 내 리뷰 cursor 조회를 위한 `(author_user_id, created_at, id)` 인덱스를 둔다. 삭제는 hard delete로 구현한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. adapter mapping, 소유자 조건 조회, cursor 정렬과 unique 충돌을 테스트한다.
2. 로컬 MySQL 환경이 준비된 경우 `./gradlew integrationTest`로 migration, FK, enum 문자열과 unique 제약을 확인한다.
3. `phases/4-private-review-crud/index.json`의 step 2를 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- 기존 migration을 수정하지 말 것. 이유: 적용된 Flyway checksum을 보존해야 한다.
- application output port가 `JpaRepository`를 상속하게 하지 말 것. 이유: persistence 세부사항을 application에 노출하면 안 된다.
- Docker나 Testcontainers를 실행하지 말 것. 이유: 현재 실행 경계를 벗어난다.
- 기존 test를 깨뜨리지 말 것.
