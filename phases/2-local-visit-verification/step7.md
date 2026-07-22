# Step 7: write-grant-domain

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-local-visit-verification/index.json`
- `/src/main/kotlin/com/ridervoice/api/visit/domain/`
- `/src/main/resources/db/migration/`

## 작업

`WriteGrant` domain, repository와 새 MySQL migration을 구현한다. grant는 사용자·방문·음식점에 귀속되고 승인 시점부터 24시간 유효하며 AVAILABLE→CONSUMED/EXPIRED/REVOKED 전이만 허용한다. 하나의 방문에 하나의 grant만 생성되도록 unique constraint를 둔다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 만료, 소진, 재사용과 중복 방문 grant를 테스트한다.
2. index step 7을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- grant 소비를 Controller에서 처리하지 말 것. 이유: 리뷰 생성 transaction과 원자적으로 묶어야 한다.
- 유효기간이 지난 grant를 AVAILABLE로 되돌리지 말 것. 이유: 일회성 보장이 깨진다.
- 기존 test를 깨뜨리지 말 것.
