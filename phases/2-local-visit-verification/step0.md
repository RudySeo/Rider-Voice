# Step 0: evidence-domain

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/auth/domain/`
- `/src/main/kotlin/com/ridervoice/api/restaurant/domain/Restaurant.kt`

## 작업

`visit/domain`에 `VisitEvidence`, 배달 앱 종류, OCR/검수 상태와 상태 전이 method를 구현한다. 사용자·음식점은 ID로 참조하고 주문 HMAC, 원본 hash, perceptual hash, 완료 시각, 원본 삭제 시각을 표현한다. 완료 시각 7일, 중복 보류와 잘못된 상태 전이를 domain policy로 검증한다.

## 인수 기준

```bash
./gradlew test
```

## 검증

1. 상태 전이와 시간 경계 단위 테스트를 실행한다.
2. phase index step 0을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 원본 이미지 bytes를 Entity에 저장하지 말 것. 이유: 개인정보와 DB 용량 경계를 분리해야 한다.
- 상태 enum을 service에서 임의로 덮어쓰지 말 것. 이유: domain invariant가 우회된다.
- 기존 test를 깨뜨리지 말 것.
