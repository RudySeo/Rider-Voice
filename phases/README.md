# Harness phase 기록 안내

`phases/`는 각 기능을 구현하던 시점의 계획, 실행 결과와 완료 상태를 보존하는 역사적 기록이다. 현재 제품 요구사항이나 구현 계약의 기준으로 사용하지 않는다.

- 현재 요구사항과 경계는 루트 `AGENTS.md`와 `docs/`의 PRD, Architecture, ADR, API Specification을 따른다.
- 현재 동작은 실행 중인 OpenAPI와 코드·테스트를 기준으로 확인한다.
- 완료된 phase의 step, output과 summary는 당시 구현 사실을 보존하기 위해 다시 작성하거나 재실행하지 않는다.
- onboarding token, `ROLE_ONBOARDING`과 `POST /api/v1/auth/consents`를 설명하는 기록은 `f76679c`에서 폐기된 이전 인증 설계다. 현재는 로그인 교환 시 access/refresh token을 직접 발급한다.
