# Step 8: visit-api

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-local-visit-verification/index.json`
- `/src/main/kotlin/com/ridervoice/api/visit/`
- `/src/main/kotlin/com/ridervoice/api/common/config/OpenApiConfiguration.kt`

## 작업

로컬 증빙 업로드 ticket, Visit 생성, 상태 조회와 음식점 확인 API를 `/api/v1/visits` 아래 구현한다. 인증 사용자 ID는 principal에서 받고 request DTO를 검증한다. 원본 key, 주문 식별자, OCR 원문과 내부 사용자 연결을 응답하지 않는다. Swagger 계약과 ProblemDetail을 포함한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. 인증, validation, 상태별 응답과 OpenAPI path를 검증한다.
2. index step 8을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 클라이언트가 전달한 object key나 user ID를 신뢰하지 말 것. 이유: 다른 사용자의 증빙 접근 위험이 있다.
- OCR 원문이나 로컬 파일 경로를 공개 응답에 포함하지 말 것. 이유: 개인정보와 내부 구조가 노출된다.
- 기존 test를 깨뜨리지 말 것.
