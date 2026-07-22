# Step 3: correction-domain

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/4-moderation-corrections/index.json`
- `/src/main/kotlin/com/ridervoice/api/restaurant/domain/Restaurant.kt`

## 작업

`correction/domain`에 CorrectionRequest, 요청 사유, 공개 token과 RECEIVED→VERIFYING_OWNER→REVIEWING→RESOLVED/REJECTED 상태 전이를 구현한다. 요청자 연락 수단은 공개 응답과 분리하고 처리 결과·사유를 기록한다. repository와 새 MySQL migration을 추가한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 상태 전이, 공개 token uniqueness와 사유 필수성을 테스트한다.
2. index step 3을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 음식점 요청자가 점수나 리뷰 상태를 직접 변경하게 하지 말 것. 이유: 정정 절차는 관리자 판단을 거쳐야 한다.
- 연락처를 공개 response에 포함하지 말 것. 이유: 개인정보가 노출된다.
- 기존 test를 깨뜨리지 말 것.
