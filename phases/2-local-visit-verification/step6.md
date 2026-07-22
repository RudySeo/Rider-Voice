# Step 6: verification-policy

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-local-visit-verification/index.json`
- `/src/main/kotlin/com/ridervoice/api/visit/`
- `/src/main/kotlin/com/ridervoice/api/restaurant/application/`

## 작업

완료 시각 7일, 주문 HMAC·이미지 중복, 파일럿 반경, OCR 음식점과 카카오 장소 일치 신뢰도, 24시간 제출량을 조합하는 검증 policy와 application service를 구현한다. 승인·거절·수동 검수 결과를 명시적으로 반환하고 성공 시 원본 삭제를 요청한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 각 정책 경계와 복합 판정 우선순위를 단위 테스트한다.
2. index step 6을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 하나라도 불확실한 증빙을 자동 승인하지 말 것. 이유: 방문 인증 신뢰가 훼손된다.
- 음식점 entity를 visit 기능에 전달하지 말 것. 이유: 기능 간 결합을 ID/interface로 제한해야 한다.
- 기존 test를 깨뜨리지 말 것.
