# Step 6: write-grant-domain

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-visit-verification/step5.md`

## 작업
방문 승인 시 사용자·음식점·방문에 귀속된 24시간 `WriteGrant`를 발급한다. AVAILABLE→CONSUMED/EXPIRED/REVOKED 상태 전이, atomic consume, 중복 발급 방지를 구현한다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- 음식점 ID를 client request에서 다시 받아 grant 대상과 비교 없이 신뢰하지 말 것. 이유: 다른 음식점 리뷰로 권한을 전용할 수 있다.
