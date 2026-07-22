# Step 5: visit-verification-policy

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/phases/1-identity-restaurants/step5.md`
- `/phases/2-visit-verification/step0.md`
- `/phases/2-visit-verification/step4.md`

## 작업
최근 7일, 반경 3km, 장소 후보 신뢰도, 주문 HMAC, image hash 중복, 비정상 제출량을 결합한 방문 승인 policy를 구현한다. 승인·거절·수동 검수 사유를 내부 code로 남긴다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- 사람이 확인하지 않은 범죄·위생 판단을 자동 생성하지 말 것. 이유: OCR은 방문 증빙만 확인한다.
