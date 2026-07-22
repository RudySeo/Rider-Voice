# Step 1: kakao-oauth-adapter

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-identity-restaurants/step0.md`

## 작업
카카오 authorization code, token, user 정보 호출을 infrastructure adapter로 구현한다. state 검증에 필요한 port를 제공하고 provider 오류를 내부 오류로 정규화한다. secret과 redirect URI는 환경 설정으로 주입한다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- 카카오 DTO를 controller 응답으로 반환하지 말 것. 이유: provider 경계를 유지해야 한다.
