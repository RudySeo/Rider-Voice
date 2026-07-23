# Step 2: review-draft-service

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-review-drafts/index.json`
- `/src/main/kotlin/com/ridervoice/api/restaurant/`
- `/src/main/kotlin/com/ridervoice/api/review/`

## 작업

ACTIVE 사용자가 파일럿 대상 내부 restaurant ID로 초안을 생성·조회·수정하고 자신의 초안 목록을 cursor로 조회하는 application service를 구현한다. 사용자·음식점당 활성 초안 하나만 허용하며 중복 생성 요청은 기존 활성 초안 결과를 반환한다. PATCH는 version을 요구해 stale update를 충돌로 반환한다. 모든 읽기와 변경에서 소유자를 확인하고 entity 대신 application command/result를 사용한다. 초안 데이터는 공개 조회나 리포트 application interface에 제공하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 본인·타인 접근, 존재하지 않거나 파일럿 밖 음식점, 부분 수정과 cursor 조회를 단위 테스트한다.
2. 중복 생성, stale version, 동시 수정과 90일 미사용 정리 정책을 테스트한다.
3. phase index step 2를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 클라이언트가 전달한 사용자 ID나 카카오 place ID를 그대로 신뢰하지 말 것.
- 초안 생성 시 WriteGrant 또는 Review를 함께 만들지 말 것. 이유: 방문 인증 경계를 유지해야 한다.
- 기존 test를 깨뜨리지 말 것.
