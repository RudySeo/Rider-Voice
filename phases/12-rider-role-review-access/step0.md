# Step 0: docs-contract

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/ERD.md`
- `/docs/design.md`

## 작업

공용 6자리 인증번호로 `USER`를 `RIDER`로 승격하고 `RIDER`와 `ADMIN`만 리뷰를 작성·수정하는 제품 계약을 문서에 먼저 반영한다. 방문 인증과 공개 `verificationStatus`·`verificationNotice` 계약은 제거하되 이미지·OCR 금지와 나머지 리뷰 생명주기 규칙은 유지한다.

## 인수 기준

```bash
rg -n "RIDER|인증번호" AGENTS.md README.md docs
```

## 검증

문서 간 역할, 공개 계약, 데이터 모델과 모바일 흐름이 일치하는지 확인하고 phase index를 갱신한다.

## 하지 말 것

- 기존 모니터링 관련 사용자 변경을 되돌리지 말 것. 이유: 현재 작업과 무관한 미완료 변경이다.
- 기존 test를 깨뜨리지 말 것.
