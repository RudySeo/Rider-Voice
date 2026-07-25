# Step 0: oauth2-dependencies-config

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/API_SPEC.md`
- `/build.gradle.kts`
- `/src/main/resources/application.yml`
- `/src/main/resources/application-local.yml`
- phase 4에서 변경된 auth/common code

## 작업

- Spring Security OAuth2 Client starter를 추가한다.
- 카카오 authorization, token, user-info URI와 redirect URI를 type-safe configuration으로 정의한다.
- `KAKAO_CLIENT_ID`, 선택 `KAKAO_CLIENT_SECRET`, 목표 callback URI를 사용한다.
- secret이 비어 있으면 client authentication `none`, 있으면 `client_secret_post`인 ClientRegistration을 만들 수 있는 configuration 경계를 둔다.
- OAuth HTTP 흐름이나 application service는 아직 구현하지 않는다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. secret 설정·미설정 configuration test를 작성해 실행한다.
2. 성공 시 step 0을 `completed`로 바꾸고 한 줄 summary를 기록한다.
3. 실패 3회 시 `error`, 필수 설정이 없어 정적 검증조차 불가능하면 `blocked`로 기록한다.

## 하지 말 것

- 실제 카카오 네트워크를 호출하지 말 것. 이유: configuration step은 로컬 test로 검증한다.
- 카카오 secret 또는 token을 로그·테스트 fixture에 기록하지 말 것.
- API security chain을 이 step에서 변경하지 말 것.
