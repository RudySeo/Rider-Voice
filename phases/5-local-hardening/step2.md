# Step 2: security-hardening

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/src/main/kotlin/com/ridervoice/api/common/security/`
- `/src/main/kotlin/com/ridervoice/api/auth/`

## 작업

onboarding token scope·만료·재사용, access/refresh token 폐기·rotation replay, endpoint role, 입력 크기, rate limit interface, 민감정보 로그와 오류 노출을 점검하고 보강한다. local upload ticket, object key, place ID, reviewDraftId와 role 같은 사용자 입력을 신뢰하지 않는지 테스트한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 인증 우회, token 재사용과 관리자 권한 실패 시나리오를 테스트한다.
2. index step 2를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- secret, token, stack trace를 로그나 응답에 기록하지 말 것. 이유: 보안 정보가 노출된다.
- 테스트 편의를 위해 endpoint를 permitAll로 바꾸지 말 것. 이유: 운영 보안 계약이 약화된다.
- 기존 test를 깨뜨리지 말 것.
