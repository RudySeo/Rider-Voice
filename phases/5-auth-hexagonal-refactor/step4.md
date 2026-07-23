# Step 4: auth-refactor-regression

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/5-auth-hexagonal-refactor/index.json`
- `/src/main/kotlin/com/ridervoice/api/auth/`
- `/src/main/kotlin/com/ridervoice/api/common/security/`
- `/src/test/kotlin/com/ridervoice/api/auth/`

## 작업

인증 헥사고날 리팩터링의 회귀 테스트를 완성한다. application의 역방향 import, Controller DTO 분리, repository adapter, token scope·만료·회전·로그아웃, OpenAPI schema와 기존 JSON 계약을 검증한다. 테스트 이름과 fixture는 새 command/result 경계를 반영하되 기존 보안 시나리오를 삭제하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. Architecture checklist와 전체 인증 회귀 테스트를 확인한다.
2. `phases/5-auth-hexagonal-refactor/index.json`의 step 4와 상위 phase를 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- 테스트 통과를 위해 인증 규칙이나 기존 assertion을 약화하지 말 것. 이유: 리팩터링 회귀를 숨기면 안 된다.
- 음식점이나 리뷰 기능을 수정하지 말 것. 이유: 이 phase는 auth module만 담당한다.
- 기존 test를 깨뜨리지 말 것.
