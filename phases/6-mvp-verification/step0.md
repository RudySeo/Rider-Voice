# Step 0: api-security-contracts

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/3-restaurant-search/index.json`
- `/phases/4-private-review-crud/index.json`
- `/src/main/kotlin/com/ridervoice/api/`
- `/src/test/kotlin/com/ridervoice/api/`

## 작업

현재 MVP의 OpenAPI와 보안 계약 회귀 테스트를 완성한다. 인증 endpoint, 음식점 검색·등록, 내 리뷰 CRUD의 request/response schema, Bearer 요구사항, status code와 ProblemDetail code가 실제 Controller와 일치하는지 검증한다. presentation DTO만 OpenAPI schema로 노출되고 application result, domain/JPA entity와 provider DTO가 노출되지 않는지 확인한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. `/v3/api-docs`의 MVP endpoint, schema와 security requirement를 검증한다.
2. `phases/6-mvp-verification/index.json`의 step 0을 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- 새 제품 기능을 추가하지 말 것. 이유: 이 phase는 기존 MVP 계약 검증만 담당한다.
- 테스트 편의를 위해 보호 endpoint를 `permitAll`로 바꾸지 말 것. 이유: 인증 경계를 약화하면 안 된다.
- 기존 test를 깨뜨리지 말 것.
