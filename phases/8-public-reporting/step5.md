# Step 5: public-reporting-regression

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- phase 8 전체 변경
- phase 6/7 integration-tag tests

## 작업

- brand/location 각각 4명/5명 경계와 같은 장소 여러 브랜드 author de-dup을 검증한다.
- NOT_OBSERVED denominator, all-unobserved metric과 distribution을 검증한다.
- current 삭제·제외로 5명 미만이 될 때 COLLECTING 전환을 검증한다.
- public detail/list/search와 OpenAPI/security regression을 수행한다.

## 인수 기준

```bash
./gradlew test --no-daemon
./gradlew check build --no-daemon
```

## 검증

1. 두 command와 `git diff --check`를 실행한다.
2. 성공 시 step 5를 `completed`로 기록한다.
3. MySQL integration 실행은 phase 10에 맡긴다.

## 하지 말 것

- threshold test를 review count만으로 작성하지 말 것.
- aggregate snapshot/table을 추가하지 말 것.
- moderation/admin 기능을 미리 구현하지 말 것.
