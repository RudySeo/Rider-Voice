# Step 5: public-discovery

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/API_SPEC.md`
- phase 11 step 2~4의 frontend app, API와 auth 기반
- restaurant search/detail/public review OpenAPI generated types

## 작업

- 공개 검색과 상세 사용자 흐름의 실패하는 Testing Library 테스트를 먼저 작성한다.
- `/`에서 trim/정규화 기준 2~100자 검색, loading/empty/error 상태, 최대 20개 후보를 제공한다. INTERNAL 후보는 상세 링크, KAKAO 후보는 인증 후 리뷰 작성 시작 action으로 연결한다.
- `externalSearchStatus=UNAVAILABLE`이면 내부 결과는 유지하고 외부 검색만 현재 불가능하다는 안내를 표시한다.
- `/restaurants/:restaurantId`에서 브랜드명, ACTIVE/CLOSED 상태, 단일 픽업 장소 주소, 브랜드와 픽업 장소의 독립 집계 상태를 표시한다. 같은 장소의 다른 브랜드 목록은 만들지 않는다.
- `NO_REVIEWS`, `COLLECTING`, `PUBLISHED`를 점수나 별점으로 변환하지 않고 contributor count와 항목별 분포를 그대로 표현한다. `NOT_OBSERVED` 개수를 별도로 표시한다.
- 공개 리뷰는 cursor 기반 더 보기로 연결하며 승인된 comment만 렌더링한다.
- 상세와 각 공개 리뷰 응답의 `verificationStatus=UNVERIFIED` 및 안내를 눈에 보이게 표시하고 인증 배지처럼 표현하지 않는다.
- 모바일 우선 반응형, keyboard focus, form label과 의미 있는 heading 구조를 유지한다.

## 인수 기준

```bash
cd frontend && npm run lint
cd frontend && npm test
cd frontend && npm run build
git diff --check
```

## 검증

1. 검색 validation/장애/빈 결과, CLOSED 상세, 집계 0/1~4/5+, NOT_OBSERVED와 cursor 추가 로드를 테스트한다.
2. UNVERIFIED 안내와 같은 장소 다른 브랜드 비노출을 DOM assertion으로 검증한다.
3. 성공 시 step 5를 `completed`로 바꾸고 routes, components와 tests를 summary에 기록한다.
4. 3회 수정 후에도 실패하면 `error`, 계약 불일치는 `blocked`로 기록한다.

## 하지 말 것

- 종합 점수, 평균 별점, 순위 또는 인증 배지를 만들지 말 것. 이유: 제품 신뢰 경계를 위반한다.
- 같은 픽업 장소의 다른 브랜드를 표시하지 말 것. 이유: 소비자 공개 금지 정보다.
- frontend에서 카카오 API나 DB를 직접 호출하지 말 것. 이유: 서버 검증과 아키텍처 경계를 우회한다.
- 기존 테스트를 삭제하거나 약화하지 말 것.
