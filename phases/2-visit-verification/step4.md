# Step 4: baemin-evidence-parser

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-visit-verification/step3.md`

## 작업
배달의민족 완료 화면 OCR result에서 앱 종류, 완료 상태, 음식점명, 완료 시각, 주문 식별자를 추출하는 parser를 구현한다. fixture 기반 단위 테스트로 정상·누락·변형 레이아웃을 검증한다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- OCR 텍스트가 없는데 추측값으로 방문을 승인하지 말 것. 이유: 리뷰 신뢰를 훼손한다.
