# Step 0: project-scaffold

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`

## 작업

Spring Boot 4.1.x, Kotlin, Java 21, Gradle Kotlin DSL 기반의 API 서버 scaffold를 만든다.

- `settings.gradle.kts`, `build.gradle.kts`, Gradle Wrapper를 구성한다.
- 애플리케이션 패키지 root는 `com.ridervoice.api`로 고정한다.
- Spring MVC, Actuator, Validation, Security, JPA, PostgreSQL, Flyway, OpenAPI 의존성 기반을 추가한다.
- `RiderVoiceApplication`과 `/actuator/health`의 최소 실행 경로를 만든다.
- 테스트가 실행되는 최소 `contextLoads` 테스트를 만든다.
- 환경변수와 secret 값은 코드에 하드코딩하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew build
```

## 하지 말 것

- React Native 또는 화면 코드를 만들지 말 것. 이유: 서버 API 우선 개발 원칙 때문이다.
- 도메인 entity나 외부 API 연동을 만들지 말 것. 이유: 이 step은 실행 가능한 기반만 담당한다.
- `ddl-auto=create`를 설정하지 말 것. 이유: 운영 스키마는 Flyway가 관리해야 한다.
