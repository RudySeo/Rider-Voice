# Step 6: restaurant-integration

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- phase 6 전체 변경
- `/src/test/kotlin/com/ridervoice/api/support/MySqlIntegrationTest.kt`

## 작업

- 같은 장소 여러 브랜드, 같은 브랜드 다른 주소, manual 후 Kakao reference 연결을 검증한다.
- location/brand/external reference 동시 unique 충돌을 검증한다.
- public search, USER address search와 deny-by-default를 회귀 검증한다.
- MySQL integration test는 별도 tag로 유지하고 기본 test가 DB 없이 통과하게 한다.

## 인수 기준

```bash
./gradlew test --no-daemon
./gradlew check build --no-daemon
```

## 검증

1. 두 command와 `git diff --check`를 실행한다.
2. 성공 시 step 6을 `completed`로 기록한다.
3. MySQL 통합 실행은 phase 10에 맡기며 이 단계에서 DB가 없다고 막지 않는다.

## 하지 말 것

- 로컬 DB를 DROP하거나 Docker를 시작하지 말 것.
- unique 테스트를 삭제하거나 application check만으로 대체하지 말 것.
- review, aggregate 또는 moderation 기능을 미리 구현하지 말 것.
