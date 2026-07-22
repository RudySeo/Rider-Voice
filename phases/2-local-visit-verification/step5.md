# Step 5: baemin-parser

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-local-visit-verification/index.json`
- `/src/main/kotlin/com/ridervoice/api/visit/application/`

## 작업

CLOVA의 provider 비종속 OCR text 결과에서 배달의민족 완료 화면의 완료 상태, 음식점명, 완료 시각과 주문 식별 후보를 추출하는 parser를 구현한다. 필수 필드 누락과 불확실한 결과는 자동 승인하지 않고 수동 검수 후보로 반환한다.

## 인수 기준

```bash
./gradlew test
```

## 검증

1. 정상, 포맷 변형, 필드 누락과 지원하지 않는 앱 fixture를 단위 테스트한다.
2. index step 5를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- OCR 문자열만으로 자동 승인하지 말 것. 이유: 장소와 방문 정책 검증이 추가로 필요하다.
- 주문 식별 원문을 저장하지 말 것. 이유: 개인정보 최소화 원칙을 위반한다.
- 기존 test를 깨뜨리지 말 것.
