# Step 7: review-management

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/API_SPEC.md`
- phase 11 step 3~6의 frontend API, auth와 review form
- my review/update/delete OpenAPI generated types

## 작업

- 내 리뷰 목록, 수정과 삭제의 실패하는 Testing Library 테스트를 먼저 작성한다.
- `/me/reviews`는 보호 route로 만들고 cursor 기반 더 보기, empty/loading/error 상태를 제공한다.
- 각 review에서 restaurant, visit month, 6개 rating, comment moderation/visibility/history 상태와 생성·수정 시각을 읽기 쉽게 표시한다.
- `historyStatus`가 현재 수정 가능한 상태인 review에만 수정·삭제 action을 표시한다. 서버가 404를 반환하면 소유하지 않았거나 더 이상 수정 가능한 최신 review가 아니라는 일반 안내를 한다.
- `/reviews/:reviewId/edit`은 기존 작성 form의 rating/comment field를 재사용하되 visit month와 restaurant는 read-only로 표시하고 update request에 포함하지 않는다.
- comment 수정 시 기존 공개 의견이 숨겨지고 다시 검수 대기가 된다는 안내를 제출 전에 표시한다.
- 삭제는 명시적 확인 후 실행하고 90일 제한 상태는 유지된다는 안내를 표시한다. 성공 후 관련 my-review와 restaurant query cache를 무효화한다.

## 인수 기준

```bash
cd frontend && npm run lint
cd frontend && npm test
cd frontend && npm run build
git diff --check
```

## 검증

1. cursor 추가 로드, 현재/과거 action 차이, visit month read-only, comment 재검수 안내, 삭제 확인과 cache 무효화를 테스트한다.
2. 404, 409, 401과 일반 ProblemDetail이 token/provider detail 없이 안전하게 표시되는지 확인한다.
3. 성공 시 step 7을 `completed`로 바꾸고 my-review routes, shared form과 tests를 summary에 기록한다.
4. 3회 수정 후에도 실패하면 `error`, 계약 결정이 필요하면 `blocked`로 기록한다.

## 하지 말 것

- 과거 review나 타인 review의 수정·삭제 action을 추측해 제공하지 말 것. 이유: 서버 소유권·이력 정책을 오해시킨다.
- visit month 수정 field를 전송하지 말 것. 이유: API와 domain 정책상 불변이다.
- 삭제 후 새 review를 즉시 작성할 수 있다고 안내하지 말 것. 이유: 마지막 제출 후 90일 제한이 유지된다.
- 기존 테스트를 삭제하거나 약화하지 말 것.
