# Step 2: auth-application-service

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/5-auth-hexagonal-refactor/index.json`
- `/src/main/kotlin/com/ridervoice/api/auth/application/`
- `/src/main/kotlin/com/ridervoice/api/auth/domain/`
- `/src/main/kotlin/com/ridervoice/api/auth/infrastructure/persistence/`

## 작업

`AuthService`가 application input port를 구현하고 output port에만 의존하도록 리팩터링한다. Controller request나 Spring Security principal 대신 application command를 받고 application result를 반환한다. access token 인증도 별도 input port 결과로 반환해 security adapter가 principal로 변환할 수 있게 한다. 기존 onboarding 5분, access 15분, refresh 30일, 회전·잠금·로그아웃과 메모리 access token 동작을 그대로 유지한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. `auth/application`에 presentation, common security principal과 infrastructure 구현 import가 남지 않았는지 확인한다.
2. 기존 AuthService 단위·동시성 테스트를 application command/result 기준으로 갱신한다.
3. `phases/5-auth-hexagonal-refactor/index.json`의 step 2를 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- 인증 오류 의미나 공개 API wire shape을 변경하지 말 것. 이유: 구조만 리팩터링하는 phase다.
- token 저장 방식이나 암호화 방식을 교체하지 말 것. 이유: 별도 기술 결정이 필요하다.
- 기존 test를 깨뜨리지 말 것.
