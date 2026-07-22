# Step 1: kakao-oauth-adapter

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/1-kakao-auth/step0.md`

## 작업
KakaoOAuthPort와 infrastructure adapter를 구현한다. authorization URL 생성, authorization code 교환, 사용자 조회를 담당한다. provider DTO는 adapter 내부에만 둔다. client id, secret, redirect URI, timeout은 설정으로 주입한다. timeout, rate limit, 4xx/5xx, 잘못된 JSON을 내부 예외로 정규화한다. 실제 네트워크 대신 HTTP stub 테스트를 작성한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- 카카오 access token을 DB에 저장하지 말 것. 이유: provider credential과 서비스 session을 분리해야 한다.
- Controller에서 카카오 API를 호출하지 말 것. 이유: 외부 provider 경계를 보존해야 한다.
