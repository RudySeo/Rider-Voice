# Step 2: full-regression-doc-status

## 읽을 파일

- `/AGENTS.md`
- `/README.md`
- `/docs/*.md`
- phase 10 step 0~1 결과
- 전체 source/test tree

## 작업

- 전체 기본·통합·build 검증 결과를 확인한다.
- README의 현재 구현 상태를 실제 완료 기능과 endpoint로 갱신한다.
- docs 목표 계약과 구현/OpenAPI가 다른 항목이 없는지 `rg`와 contract test로 확인한다.
- 폐기된 직접 OAuth, single Restaurant, standalone restaurant registration, private review/review-drafts 참조가 남지 않았는지 확인한다.
- 코드 변경 없이 문서 상태 정리만 수행한다.

## 인수 기준

```bash
./gradlew test --no-daemon
./gradlew integrationTest --no-daemon
./gradlew check build --no-daemon
git diff --check
```

## 검증

1. 네 command와 architecture checklist를 확인한다.
2. 성공 시 step 2를 `completed`로 기록하고 구현 완료 요약을 남긴다.
3. integration DB가 중간에 사라지면 `blocked`, 코드 결함은 3회 수정 후 `error`로 기록한다.

## 하지 말 것

- 테스트 개수나 실행 시간을 수동 API 명세에 고정하지 말 것.
- 실패한 test를 disable/delete해 완료 처리하지 말 것.
- push, 배포 또는 AWS 작업을 수행하지 말 것.
