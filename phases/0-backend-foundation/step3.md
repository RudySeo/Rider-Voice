# Step 3: security-skeleton

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/0-backend-foundation/step2.md`

## 작업

카카오 OAuth 구현 전 사용할 Spring Security 경계를 구성한다.

- security filter chain을 구성한다.
- `/actuator/health`, OpenAPI 문서 등 공개 endpoint와 `/api/v1/**` 기본 보호 정책을 분리한다.
- 인증 principal을 feature가 직접 provider token에 의존하지 않도록 서비스 사용자 principal interface를 정의한다.
- 관리자 role 검사를 위한 authority 이름과 annotation/policy 확장 지점을 정의한다.
- 아직 인증 provider가 없으므로 테스트용 인증을 운영 설정에 노출하지 않는다.
- 인증 실패와 권한 부족을 ProblemDetail로 반환한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

보호된 endpoint는 인증 없이 401, 권한 부족은 403을 반환하고 health endpoint는 200을 반환해야 한다.

## 하지 말 것

- 임시 user ID나 항상 허용되는 bypass filter를 만들지 말 것. 이유: 이후 카카오 인증 경계를 우회하게 된다.
- 관리자 권한을 request parameter로 받지 말 것. 이유: 권한 상승 취약점이 된다.
