# Step 5: report-api

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-restaurant-search/index.json`
- `/phases/3-reviews-reports/step4.md`

## 작업
로그인 없이 사용할 수 있는 `GET /api/v1/restaurants/{id}/report`와 methodology API를 구현한다. `PUBLISHED`, `COLLECTING`, `NO_DATA` 상태, 표본 수, UTC 갱신 시각, 항목별 분포와 허용된 변화만 response DTO로 반환하고 Swagger 계약을 추가한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- 종합 별점·순위·작성자 목록을 추가하지 말 것. 이유: PRD의 운영환경 데이터 원칙에 위배된다.
- 자유 의견 검수 전 원문을 공개하지 말 것. 이유: 개인정보와 명예훼손 위험이 있다.
- 기존 test를 깨뜨리지 말 것.
