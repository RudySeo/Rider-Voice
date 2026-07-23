# Step 5: auth-regression-tests

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/2-auth-onboarding/index.json`
- `/src/main/kotlin/com/ridervoice/api/auth/`
- `/src/main/kotlin/com/ridervoice/api/common/security/`
- `/README.md`

## 작업

로그인부터 onboarding 동의, 정식 token 발급·갱신·로그아웃까지 Docker 없는 회귀 테스트와 OpenAPI 계약 테스트를 완성한다. onboarding token은 consent에만 성공하고 일반 access token은 ACTIVE 사용자 API에만 성공해야 한다. 안정적인 ProblemDetail code를 사용하고 token, provider 오류와 stack trace가 응답·로그에 노출되지 않게 한다. README의 현재 인증 동작과 5분 onboarding/15분 access/30일 refresh 수명을 실제 구현에 맞춘다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. 공개 endpoint, `ROLE_ONBOARDING`, `ROLE_USER`, 만료·재사용·동시 refresh와 로그아웃 시나리오를 검증한다.
2. Architecture와 ADR checklist 및 `/v3/api-docs`의 schema/security requirement를 확인한다.
3. phase index step 5와 상위 phase 상태를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- 테스트 통과를 위해 endpoint를 `permitAll`로 바꾸거나 기존 테스트를 약화하지 말 것. 이유: 인증 회귀가 숨겨진다.
- 실제 카카오 API나 Docker/Testcontainers에 테스트를 의존시키지 말 것. 이유: 현재 기본 검증 경계와 충돌한다.
- 기존 test를 깨뜨리지 말 것.
