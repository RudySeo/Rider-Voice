# Step 1: review-persistence

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/3-reviews-reports/step0.md`

## 작업
Review, ReviewAnswer, ReviewComment repository와 새 MySQL Flyway migration을 구현한다. UUID `BINARY(16)`, UTC `DATETIME(6)`, 방문당 리뷰 unique, 답변 항목 unique와 음식점·작성자·제출 시각 조회 인덱스를 추가한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- 조회용 리포트에서 entity 전체 graph를 무조건 로딩하지 말 것. 이유: projection 기반 조회가 필요하다.
- 기존 migration을 수정하지 말 것. 이유: 적용된 checksum과 충돌한다.
- 기존 test를 깨뜨리지 말 것.
