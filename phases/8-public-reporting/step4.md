# Step 4: search-cache-rate-limit

## 읽을 파일

- `/AGENTS.md`
- `/docs/ADR.md`
- phase 6 public search service/controller
- common configuration/security code

## 작업

- Spring Cache와 Caffeine을 추가해 성공한 Kakao search 결과를 normalized query 기준 5분 저장한다.
- provider failure는 cache에 저장하지 않는다.
- 공개 검색 호출자 기준 메모리 token bucket으로 분당 30회를 적용한다.
- single-instance 한계를 configuration/comment에 명시하고 Redis를 추가하지 않는다.
- limit 초과는 stable ProblemDetail과 429로 반환한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. clock을 주입해 cache hit/expiry, failure no-cache와 29/30/31 request 경계를 test한다.
2. 성공 시 step 4를 `completed`로 기록한다.
3. 실패 3회 시 `error`, 인프라 결정이 필요하면 `blocked`로 기록한다.

## 하지 말 것

- Redis, distributed lock 또는 external cache를 추가하지 말 것.
- provider registration revalidation을 search cache 결과만으로 승인하지 말 것.
- client IP header를 무조건 신뢰하지 말 것.
