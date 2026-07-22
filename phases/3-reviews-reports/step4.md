# Step 4: report-aggregation

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/3-reviews-reports/step1.md`
- `/phases/3-reviews-reports/step2.md`

## 작업
최근 90일 서로 다른 작성자 5명, 항목별 `NOT_OBSERVED` 제외, 동일 작성자 하루 1건·90일 3건, 현재/직전 30일 각각 5명인 변화 조건을 계산하는 aggregation service와 ReportSnapshot persistence를 구현한다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- 표본 부족을 0점으로 계산하지 말 것. 이유: 데이터 수집 중 상태와 낮은 평가를 혼동하면 안 된다.
- 원본 review를 공개 조회마다 전체 재계산하지 말 것. 이유: snapshot 제공 결정과 성능 요구를 위반한다.
- 기존 test를 깨뜨리지 말 것.
