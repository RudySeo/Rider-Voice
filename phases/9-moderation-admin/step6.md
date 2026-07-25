# Step 6: moderation-regression

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- phase 9 전체 변경
- public aggregate/list tests

## 작업

- comment approval/edit/re-review, report hide/dismiss/full exclusion을 회귀 검증한다.
- full exclusion으로 distinct author가 5 미만이면 aggregate가 COLLECTING으로 전환되는지 검증한다.
- USER/ADMIN 권한, duplicate report unique, audit와 canonical merge를 검증한다.
- API/OpenAPI 전체 moderation contract를 검증한다.

## 인수 기준

```bash
./gradlew test --no-daemon
./gradlew check build --no-daemon
```

## 검증

1. 두 command와 `git diff --check`를 실행한다.
2. 성공 시 step 6을 `completed`로 기록한다.
3. MySQL integration 실행은 phase 10에 맡긴다.

## 하지 말 것

- DB 부재 때문에 Docker/Testcontainers를 시작하지 말 것.
- audit/report 테스트를 생략하지 말 것.
- 배포/운영 인프라를 추가하지 말 것.
