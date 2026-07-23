# Step 2: review-service

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/3-reviews-reports/step0.md`
- `/phases/3-reviews-reports/step1.md`
- `/phases/1-review-drafts/index.json`

## 작업
본인 소유 ReviewDraft의 완성도를 검증하고 WriteGrant 확인·조건부 소진, 초안 전환과 Review 생성을 하나의 transaction에서 수행하는 application service를 구현한다. idempotency key 재요청은 기존 결과를 반환하고 만료·소진·다른 사용자 grant 또는 다른 음식점 초안은 거부한다. 내 리뷰 cursor 조회 interface도 제공한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- grant 소진과 review 저장을 별도 transaction으로 나누지 말 것. 이유: 핵심 원자성이 깨진다.
- 초안을 먼저 공개 상태로 바꾸거나 transaction 밖에서 소비하지 말 것. 이유: 실패 시 초안과 정식 리뷰 상태가 어긋난다.
- Controller request 타입을 application service에 전달하지 말 것. 이유: presentation 결합을 피해야 한다.
- 기존 test를 깨뜨리지 말 것.
