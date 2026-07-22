# Step 3: auth-security-tests

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/1-kakao-auth/step0.md`
- `/phases/1-kakao-auth/step1.md`
- `/phases/1-kakao-auth/step2.md`

## 작업
인증 filter와 endpoint 권한, token 만료·위조·폐기, refresh replay 방지를 검증하는 테스트를 보강한다. 공개 auth endpoint와 인증 필요 users/me endpoint를 MockMvc로 검증한다. Docker가 없어도 전체 기본 테스트가 실행되도록 통합 테스트 실행 방식을 정리한다. 로컬 실행 문서와 환경변수 예시를 추가한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- 테스트 통과를 위해 기존 테스트를 삭제하거나 약화하지 말 것. 이유: 인증 회귀를 방지해야 한다.
