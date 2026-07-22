# Step 0: review-domain

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-visit-verification/step6.md`

## 작업
6개 평가 항목, 5개 응답 enum, `관찰하지 못함`, 200자 자유 의견과 Review 상태를 domain으로 구현한다. 유효한 WriteGrant를 받은 방문만 Review를 생성할 수 있도록 application port를 정의한다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- WriteGrant 없는 리뷰 생성 경로를 테스트 fixture로 허용하지 말 것. 이유: 핵심 불변식이다.
