# Step 2: api-conventions

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/0-backend-foundation/step0.md`
- `/phases/0-backend-foundation/step1.md`

## 작업

모든 feature가 사용할 REST API 공통 규칙을 구현한다.

- `/api/v1` 경로 정책을 적용한다.
- Bean Validation 오류를 RFC 7807 `ProblemDetail`로 변환한다.
- provider 오류, stack trace, secret이 응답에 나오지 않도록 공통 exception handler를 만든다.
- request/response DTO 규칙과 Jackson Kotlin 설정을 고정한다.
- springdoc-openapi와 기본 API 문서 endpoint를 구성한다.
- API clock은 UTC를 사용하고 응답 날짜는 RFC 3339로 직렬화한다.
- 공통 테스트로 validation 오류와 unknown exception 응답을 검증한다.

## 인수 기준

```bash
./gradlew test
./gradlew build
```

## 하지 말 것

- feature별 비즈니스 예외를 공통 패키지에 넣지 말 것. 이유: 도메인 책임이 흐려진다.
- API 응답에 JPA lazy proxy를 노출하지 말 것. 이유: 직렬화와 개인정보 누출 위험이 있다.
