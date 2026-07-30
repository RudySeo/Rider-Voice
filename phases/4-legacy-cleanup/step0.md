# Step 0: legacy-contract-removal

## 읽을 파일

먼저 아래 파일을 모두 읽는다:

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/API_SPEC.md`
- `/src/main/kotlin/com/ridervoice/api/common/config/OpenApiConfiguration.kt`
- `/src/main/kotlin/com/ridervoice/api/common/security/SecurityConfig.kt`
- 관련 common 테스트

## 작업

새 공개 리뷰 MVP와 충돌하는 공통 계약만 제거한다.

- OpenAPI 설명에서 비공개 리뷰 또는 방문 인증 리뷰 표현을 제거하고 미인증 공개 리뷰 서비스로 정렬한다.
- 존재하지 않는 `/api/v1/review-drafts/**`와 `/api/v1/users/me/review-drafts` security matcher를 제거한다.
- 직접 카카오 OAuth와 단일 Restaurant endpoint matcher는 다음 step에서 구현과 함께 제거하므로 이 step에서 건드리지 않는다.
- 변경된 OpenAPI metadata와 security deny-by-default 동작을 테스트한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. 인수 기준 command를 실행한다.
2. Controller/JPA 경계와 공개·미인증 제품 문구를 확인한다.
3. 성공 시 phase index의 step 0을 `completed`로 바꾸고 변경 파일과 제거 계약을 한 줄 `summary`로 기록한다.
4. 3회 실패 시 `error`, 사용자 입력이 필요하면 `blocked`로 기록하고 중단한다.

## 하지 말 것

- auth 또는 restaurant 구현을 이 step에서 삭제하지 말 것. 이유: 다음 step의 독립 범위다.
- 새 endpoint를 구현하지 말 것. 이유: 이 step은 폐기 계약 정리만 담당한다.
- retained token·onboarding 테스트를 삭제하지 말 것.
- Docker, Testcontainers 또는 외부 리소스를 실행하지 말 것.
