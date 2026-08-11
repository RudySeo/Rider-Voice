# Step 0: onboarding-token-domain

> **역사적 기록:** 이 phase의 onboarding token 설계는 `f76679c`에서 폐기됐다. 현재 계약은 루트 `AGENTS.md`와 `docs/API_SPEC.md`를 따른다.

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/src/main/kotlin/com/ridervoice/api/auth/`

## 작업

`auth/domain`에 약관 미동의 신규 사용자를 위한 `OnboardingToken` JPA entity와 상태 전이를 구현한다. 생성 시 사용자 ID, SHA-256 token hash, 발급 시각, 5분 만료 시각을 받고 `consume(at)`만으로 미사용 token을 소비한다. `isUsableAt(at)`는 미소비이며 `at < expiresAt`일 때만 true여야 한다. token 원문과 일반 access 권한은 domain에 저장하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 경계 시각 만료, 일회성 소비, 재사용과 blank hash를 단위 테스트한다.
2. phase index step 0을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- onboarding token 원문을 DB나 로그에 저장하지 말 것. 이유: 탈취 시 약관 동의 흐름을 위조할 수 있다.
- repository, schema 설정, Controller 또는 security filter를 이 step에 추가하지 말 것. 이유: domain step의 범위를 유지해야 한다.
- 기존 test를 깨뜨리지 말 것.
