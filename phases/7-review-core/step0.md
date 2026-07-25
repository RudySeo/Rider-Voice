# Step 0: review-domain

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/API_SPEC.md`
- phase 6 restaurant public contracts

## 작업

- `ReviewRating` 5개 값과 6개 필수 평가를 모델링한다.
- `Review`, comment moderation state, review visibility state와 visit month value/policy를 추가한다.
- 방문 연월은 Asia/Seoul 기준 현재 또는 직전 달만 허용한다.
- comment는 trim 후 nullable, 최대 200자이며 입력 시 PENDING이다.
- 작성 제한 정책은 마지막 제출 Instant + 90일을 사용한다.
- history/current 구분과 ACTIVE/EXCLUDED 상태 전이를 domain test로 정의한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. rating, month boundary, comment와 90일 경계를 단위 테스트한다.
2. 성공 시 step 0을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 제품 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- 종합 점수나 평균 별점 field를 추가하지 말 것.
- 방문 인증, WriteGrant 또는 OCR 상태를 추가하지 말 것.
- API annotation을 domain에 넣지 말 것.
