# Step 4: report-aggregation

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/3-reviews-reports/step1.md`
- `/phases/3-reviews-reports/step3.md`

## 작업
최근 90일, 서로 다른 작성자 5명, 항목별 관찰자 5명, 최근·직전 30일 추이, 반복 작성자 제한을 적용하는 report snapshot 집계를 구현한다. `관찰하지 못함`을 분모에서 제외하고 긍정 비율을 계산한다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- 표본 부족을 0점으로 계산하지 말 것. 이유: 데이터 수집 중 상태와 낮은 평가를 혼동하면 안 된다.
