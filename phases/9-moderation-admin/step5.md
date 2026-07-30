# Step 5: restaurant-canonical-merge

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/API_SPEC.md`
- restaurant domain/persistence/service
- review state/history와 moderation audit

## 작업

- ADMIN restaurant merge와 pickup-location relink input port/service/API를 구현한다.
- duplicate restaurant는 hard delete하지 않고 MERGED와 canonicalRestaurantId를 저장한다.
- 검색은 duplicate를 제외하고 detail/review create는 canonical ID로 해석한다.
- merge 시 review FK/state 충돌을 해결해 author별 가장 최근 valid current만 유지하고 나머지는 history로 남긴다.
- lastSubmittedAt은 합쳐진 상태 중 최댓값을 유지한다.
- external references/platforms를 canonical에 이전하고 모든 변경을 audit한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. old ID resolution, duplicate search exclusion, state collision, cooldown과 audit를 검증한다.
2. 성공 시 step 5를 `completed`로 기록한다.
3. 실패 3회 시 `error`, merge 정책 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- duplicate restaurant row를 hard delete하지 말 것.
- merge를 DB FK update만으로 처리해 state conflict를 무시하지 말 것.
- sibling brand 목록을 public response에 추가하지 말 것.
