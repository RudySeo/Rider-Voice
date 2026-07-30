# Step 0: aggregate-query-contracts

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- phase 6 restaurant model
- phase 7 review/state model

## 작업

- aggregate 입력용 application model과 output port를 정의한다.
- 브랜드는 author별 current ACTIVE review 하나, 장소는 여러 브랜드 current 중 author별 최신 하나를 조회할 수 있게 한다.
- EXCLUDED, deleted와 null current state를 결과에서 제외한다.
- query result는 필요한 IDs, ratings, timestamps만 포함하고 Entity를 노출하지 않는다.
- 별도 aggregate Entity나 materialized table은 만들지 않는다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. query port contract와 author de-dup ordering을 test로 정의한다.
2. 성공 시 step 0을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 쿼리 계약 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- JPA projection을 application 공개 type으로 노출하지 말 것.
- 과거 history를 current aggregate에 포함하지 말 것.
- same-location sibling brand 목록을 공개 result에 넣지 말 것.
