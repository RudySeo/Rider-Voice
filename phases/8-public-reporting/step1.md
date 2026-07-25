# Step 1: aggregate-policy-service

## 읽을 파일

- `/AGENTS.md`
- `/docs/API_SPEC.md`
- step 0 aggregate query contract
- ReviewRating domain

## 작업

- `NO_REVIEWS`, `COLLECTING`, `PUBLISHED` 상태를 유효 distinct author 0/1~4/5+로 계산한다.
- 브랜드 3개와 장소 3개 metric을 독립 계산한다.
- `NOT_OBSERVED`는 notObservedCount에 포함하고 distribution denominator에서 제외한다.
- 관찰값 0인 metric은 빈 distribution/관찰 없음 상태를 명시적으로 표현한다.
- distribution은 합계 100이 되도록 API 계약과 일관된 정밀도를 사용한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. 0/1/4/5명, duplicate author, NOT_OBSERVED-only와 삭제/제외 입력을 단위 테스트한다.
2. 성공 시 step 1을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 제품 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- 종합 점수, 평균 별점 또는 ranking을 계산하지 말 것.
- 5 review count를 5 distinct author로 오해하지 말 것.
- 브랜드 metric과 장소 metric을 섞지 말 것.
