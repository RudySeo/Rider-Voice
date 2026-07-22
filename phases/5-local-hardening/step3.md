# Step 3: local-performance-check

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/`
- `/src/main/resources/db/migration/`

## 작업

로컬 MySQL에서 음식점 검색, 내 리뷰 cursor 조회, report snapshot 조회의 query 수와 주요 인덱스를 점검한다. N+1과 전체 table scan을 줄이고 실행 방법·관찰 결과를 문서화한다. 배포 용량 산정이나 AWS 설정은 포함하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. query regression과 cursor 안정성을 테스트한다.
2. Architecture checklist를 확인하고 index step 3을 `completed`로 바꾼다.

## 하지 말 것

- AWS/ECS/RDS 설정을 추가하지 말 것. 이유: 배포는 현재 범위 밖이다.
- 성능을 이유로 domain invariant나 authorization을 우회하지 말 것. 이유: 데이터 무결성과 보안이 우선이다.
- 기존 test를 깨뜨리지 말 것.
