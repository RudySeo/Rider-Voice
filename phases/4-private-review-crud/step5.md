# Step 5: review-regression-tests

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/4-private-review-crud/index.json`
- `/src/main/kotlin/com/ridervoice/api/review/`
- `/src/test/kotlin/com/ridervoice/api/review/`
- `/README.md`

## 작업

비공개 리뷰 CRUD의 계층별 회귀 테스트를 완성한다. application의 presentation/infrastructure 역방향 의존이 없는지, 사용자·음식점 unique 정책, 타인 리소스 비노출, cursor, validation, DTO와 OpenAPI 계약을 검증한다. README의 구현 API와 현재 범위를 실제 동작에 맞춘다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. Architecture checklist와 전체 기본 검증 명령을 확인한다.
2. `phases/4-private-review-crud/index.json`의 step 5와 상위 phase를 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- 테스트 통과를 위해 endpoint를 공개하거나 소유권 검사를 약화하지 말 것. 이유: 비공개 리뷰 경계가 무너진다.
- 방문 인증, 공개 리포트나 관리자 기능을 추가하지 말 것. 이유: 현재 phase 범위를 벗어난다.
- 기존 test를 깨뜨리지 말 것.
