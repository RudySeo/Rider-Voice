# Step 5: report-api

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-identity-restaurants/step5.md`
- `/phases/3-reviews-reports/step4.md`

## 작업
로그인 없이 사용할 수 있는 `GET /api/v1/restaurants/{id}/report`와 methodology endpoint를 구현한다. 공개 표본 기준, 항목별 분포·긍정 비율·추이·마지막 갱신일만 반환하고 작성자 식별 정보는 제거한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- 종합 별점·순위·작성자 목록을 추가하지 말 것. 이유: PRD의 운영환경 데이터 원칙에 위배된다.
