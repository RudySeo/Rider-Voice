# Step 2: rider-access-persistence

## 읽을 파일

- `/docs/ERD.md`
- `/src/main/resources/db/migration/V1__create_initial_schema.sql`
- `/src/main/resources/db/migration/V2__create_mobile_login_grants.sql`
- `/src/main/kotlin/com/ridervoice/api/auth/domain`
- `/phases/12-rider-role-review-access/step1.md`

## 작업

활성 인증번호 이력과 사용자별 실패 상태를 저장하는 auth output port와 JPA adapter를 추가한다. 새 Flyway migration은 새 테이블·제약을 만들고 승인된 초기화 순서로 리뷰 대상 감사 기록, 리뷰 신고, 리뷰만 삭제한다.

## 인수 기준

```bash
./gradlew test --tests '*Persistence*'
```

## 검증

빈 schema와 기존 데이터 schema 모두에서 FK·unique·Hibernate mapping이 일치하는지 확인하고 phase index를 갱신한다.

## 하지 말 것

- 적용된 V1/V2 migration을 수정하지 말 것. 이유: 운영 Flyway checksum 계약을 깨뜨린다.
- 사용자·음식점·음식점 신고 데이터를 삭제하지 말 것. 이유: 승인된 초기화 범위를 벗어난다.
