# Step 5: restaurant-search-tests

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-restaurant-search/index.json`
- `/src/main/kotlin/com/ridervoice/api/restaurant/`

## 작업

restaurant 검색 흐름의 Docker 없는 회귀 테스트를 완성한다. adapter stub, application service 정책, Controller validation, 공개/인증 정책, ProblemDetail과 OpenAPI 계약을 계층별로 검증한다. README에 로컬 카카오 Local API 환경변수를 추가한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. Architecture checklist와 API 계약을 확인한다.
2. index의 step 5를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- Docker/Testcontainers를 실행하지 말 것. 이유: 현재 검증 경로는 로컬 전용이다.
- 실제 카카오 API key를 테스트나 문서에 기록하지 말 것. 이유: secret 유출 위험이 있다.
- 기존 test를 깨뜨리지 말 것.
