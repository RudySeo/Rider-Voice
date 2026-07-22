# Step 0: review-domain

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-local-visit-verification/index.json`

## 작업
`review/domain`에 Review, 6개 구조화 항목, 5개 응답 값과 optional 자유 의견을 구현한다. 하나의 방문/WriteGrant당 하나의 리뷰, 6개 답변 필수성, 최대 200자, 자유 의견 초기 검수 상태와 집계 포함 상태를 domain method로 검증한다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- WriteGrant 없는 리뷰 생성 경로를 테스트 fixture로 허용하지 말 것. 이유: 핵심 불변식이다.
- 자유 의견 승인 상태를 Controller에서 변경하지 말 것. 이유: 검수 규칙을 우회한다.
- 기존 test를 깨뜨리지 말 것.
