# Step 2: public-restaurant-detail

## 읽을 파일

- `/docs/API_SPEC.md`
- `/AGENTS.md`
- phase 6 restaurant search/result
- step 1 aggregate result

## 작업

- public `GET /api/v1/restaurants/{restaurantId}` input port/service/controller를 구현한다.
- canonical restaurant, nested pickup location, brand report와 location report를 반환한다.
- 모든 응답에 `verificationStatus=UNVERIFIED`와 고정 notice를 포함한다.
- MERGED ID 요청은 canonical data와 canonical ID를 반환한다.
- 같은 pickup location의 다른 brand 목록은 조회하거나 반환하지 않는다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. public 권한, 404, canonical, collecting/published DTO와 OpenAPI를 검증한다.
2. 성공 시 step 2를 `completed`로 기록한다.
3. 실패 3회 시 `error`, 계약 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- login을 요구하지 말 것.
- same-location sibling brand를 응답하지 말 것.
- verification notice를 조건부로 생략하지 말 것.
