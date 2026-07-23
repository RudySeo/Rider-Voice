# Step 0: review-draft-domain

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/1-restaurant-search/index.json`

## 작업

`review/domain`에 정식 Review와 분리된 `ReviewDraft`를 구현한다. 초안은 사용자 ID와 내부 restaurant ID에 귀속되고 6개 구조화 항목의 부분 응답과 최대 200자 선택 자유 의견을 저장할 수 있다. 제공된 응답 값과 자유 의견 형식은 검증하되 빈 항목은 허용한다. 초안에는 ReviewStatus를 부여하지 않으며 `ACTIVE → CONVERTING` domain method만 허용하고 전환 transaction에서 소비 후 삭제한다. 활성 초안은 사용자·음식점 조합당 하나다.

## 인수 기준

```bash
./gradlew test
```

## 검증

1. 부분 저장, 응답 값, 자유 의견 길이와 소유자 불변식을 단위 테스트한다.
2. 중복 활성 초안과 `CONVERTING` 재사용을 거부하는 domain test를 추가한다.
3. phase index step 0을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- ReviewDraft를 Review subtype이나 집계 대상으로 만들지 말 것. 이유: 방문 인증 전 데이터가 공개 신뢰 경계를 넘는다.
- WriteGrant나 방문 인증 상태를 초안 생성 조건으로 요구하지 말 것. 이유: 초안의 목적은 인증 전 작성이다.
- 기존 test를 깨뜨리지 말 것.
