# Step 1: review-persistence

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/3-reviews-reports/step0.md`

## 작업
Review, ReviewAnswer, ReviewComment 테이블과 repository를 Flyway/JPA로 구현한다. 사용자·음식점·방문 unique 제약, 최근 기간 조회 인덱스, 자유 의견 검수 상태를 추가한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- 조회용 리포트에서 entity 전체 graph를 무조건 로딩하지 말 것. 이유: projection 기반 조회가 필요하다.
