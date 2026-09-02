# Step 6: public-contract-cleanup

## 읽을 파일

- `/src/main/kotlin/com/ridervoice/api/restaurant`
- `/src/main/kotlin/com/ridervoice/api/review`
- `/mobile/src/shared/api`
- `/phases/12-rider-role-review-access/step5.md`

## 작업

공개 음식점 상세와 공개 리뷰 application model·response DTO·mapper에서 `verificationStatus`와 `verificationNotice`를 제거하고 OpenAPI·회귀 기대값을 갱신한다. 모바일 생성 타입은 OpenAPI에서 다시 생성한다.

## 인수 기준

```bash
./gradlew test --tests '*Public*' --tests '*ApiContract*'
```

## 검증

공개 응답에 두 필드가 존재하지 않는지 확인하고 phase index를 갱신한다.

## 하지 말 것

- 생성된 TypeScript 타입을 계약과 다르게 수동 유지하지 말 것. 이유: OpenAPI가 기준이다.
- 공개 집계 5명 경계를 변경하지 말 것.
