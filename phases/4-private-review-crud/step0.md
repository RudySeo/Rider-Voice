# Step 0: review-domain

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/common/persistence/BaseEntity.kt`
- `/src/main/kotlin/com/ridervoice/api/restaurant/domain/Restaurant.kt`

## 작업

`review/domain`에 `Review`, `ReviewRating`과 6개 평가 값을 표현하는 domain model을 구현한다. Review는 작성자 ID와 음식점 ID를 불변으로 유지하고 평가·의견만 domain method로 수정한다. 6개 평가는 모두 필수이며 `ReviewRating`은 `VERY_GOOD`, `GOOD`, `NEEDS_IMPROVEMENT`, `MAJOR_IMPROVEMENT`, `NOT_OBSERVED`만 허용한다. 의견은 trim하고 빈 문자열은 `null`, 최대 200자로 처리한다. 현재 리뷰에는 인증·공개·검수 상태를 추가하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 생성과 수정, 200자 경계, 빈 의견 정규화와 작성자·음식점 불변식을 단위 테스트한다.
2. `phases/4-private-review-crud/index.json`의 step 0을 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- `ReviewDraft`, 방문 인증, `WriteGrant` 또는 공개 상태를 추가하지 말 것. 이유: 현재 MVP 범위를 벗어난다.
- Controller, repository 또는 migration을 이 step에 추가하지 말 것. 이유: domain 범위를 유지한다.
- 기존 test를 깨뜨리지 말 것.
