# 프로젝트: Rider Voice

## 제품 목표

- 배달 완료 화면으로 음식점 방문이 확인된 리뷰만 수집한다.
- 픽업 과정에서 직접 관찰 가능한 운영 경험을 구조화된 데이터로 제공한다.
- 소비자에게 작성자의 신원을 노출하지 않고 음식점에는 공식 정정 절차를 제공한다.
- 서버 API와 데이터 무결성을 먼저 완성한 뒤 React Native 앱을 개발한다.

## 기술 스택

### 현재 구현 대상: API 서버

- Spring Boot
- Kotlin
- Gradle Kotlin DSL
- Spring MVC / Spring Security
- Spring Data JPA / Hibernate
- MySQL 9.3
- Flyway
- Bean Validation
- OpenAPI
- JUnit 5, MockK

### 외부 서비스

- 카카오 REST OAuth
- 카카오 로컬 REST API
- 방문 증빙 추출 provider는 OCR 단계 착수 시 NAVER Cloud CLOVA OCR과 멀티모달 모델을 비교해 결정한다.
- LangChain 계열 framework의 실행 형태도 OCR 단계의 ADR에서 결정한다.

### 후속 운영 인프라

- AWS RDS for MySQL, S3, SQS, ECS, KMS, Secrets Manager, CloudWatch
- 현재 로컬 개발 단계에서는 구성하거나 배포하지 않는다.

### 현재 실행 환경

- API 서버와 MySQL은 로컬 프로세스로만 실행한다.
- 로컬 MySQL `rider` 데이터베이스를 사용한다.
- CRITICAL: 사용자가 별도로 요청하기 전에는 Docker, Docker Compose, Testcontainers를 실행하지 않는다.
- CRITICAL: 사용자가 별도로 요청하기 전에는 AWS 리소스 생성, 배포 또는 production readiness 작업을 수행하지 않는다.

### 후속 구현 대상

- React Native 크로스 플랫폼 앱
- 서버 API와 OpenAPI 계약이 완료되기 전에는 화면 구현을 시작하지 않는다.

## 구현 전 필수 확인

- 구현 전에 `docs/PRD.md`, `docs/ARCHITECTURE.md`, `docs/ADR.md`를 읽는다.
- 제품 범위나 기술 결정이 바뀌면 코드보다 관련 문서를 먼저 업데이트한다.
- 현재 작업과 무관한 사용자 변경, 삭제 파일, `.codex` 설정을 되돌리지 않는다.

## 아키텍처 규칙

- CRITICAL: Controller에 비즈니스 로직이나 JPA query를 작성하지 않는다.
- CRITICAL: JPA Entity를 API request 또는 response로 직접 사용하지 않는다.
- CRITICAL: 외부 API, S3와 SQS는 infrastructure adapter에서만 호출한다.
- CRITICAL: React Native 또는 다른 클라이언트가 DB, 증빙 저장소, 증빙 추출 provider 또는 카카오 로컬 API를 직접 호출하게 하지 않는다.
- CRITICAL: 유효한 `WriteGrant` 없이 리뷰를 생성하지 않는다.
- CRITICAL: 방문 인증 전 `ReviewDraft`는 정식 리뷰가 아니며 공개, 리포트 집계 또는 관리자 검수 대상에 포함하지 않는다.
- CRITICAL: `WriteGrant` 확인과 소진, 리뷰 생성은 하나의 트랜잭션에서 원자적으로 처리한다.
- CRITICAL: 하나의 방문 증빙으로 두 개 이상의 리뷰를 생성하지 않는다.
- CRITICAL: OCR 증빙 원본이나 카카오 계정 정보를 공개 API 응답에 포함하지 않는다.
- CRITICAL: OCR 성공 원본은 즉시 삭제하고 수동 검수 원본은 72시간 이상 보관하지 않는다.
- CRITICAL: Flyway migration 없이 DB schema를 변경하지 않는다.
- CRITICAL: 운영 환경에서 Hibernate schema auto-generation을 활성화하지 않는다.
- CRITICAL: 외부 provider 오류, secret, stack trace를 클라이언트에 노출하지 않는다.
- Controller는 HTTP validation과 response 변환만 담당한다.
- 트랜잭션 경계는 application service에 둔다.
- 상태 전이는 domain method를 사용하며 enum field를 임의로 덮어쓰지 않는다.
- 기능 간에는 entity 대신 ID, DTO 또는 공개 application interface를 전달한다.
- 성공 응답은 기능별 DTO, 오류 응답은 RFC 7807 `ProblemDetail`을 사용한다.
- 모든 시각은 UTC로 저장하고 API는 RFC 3339로 반환한다.

## 패키지 규칙

```text
com.ridervoice.api
├── common
├── auth
├── restaurant
├── visit
├── review
├── report
├── moderation
└── correction
```

- 기능 패키지 안에서 `presentation`, `application`, `domain`, `infrastructure` 경계를 사용한다.
- 공통 기능이라는 이유만으로 비즈니스 규칙을 `common`에 두지 않는다.
- provider별 request/response 타입은 해당 infrastructure adapter 밖으로 노출하지 않는다.

## 데이터 및 개인정보 규칙

- 주문 식별 정보는 원문으로 저장하지 않고 HMAC으로 저장한다.
- 증빙 S3 bucket은 public access를 차단한다.
- presigned URL에는 짧은 만료 시간과 파일 형식·크기 제한을 적용한다.
- 카카오 외부 subject와 공개 프로필 데이터를 분리한다.
- 관리자만 작성자와 리뷰의 내부 연결을 조회할 수 있다.
- 관리자 상태 변경은 사유와 함께 감사 로그에 남긴다.
- 사용자 입력으로 전달된 object key, 장소 ID, role을 그대로 신뢰하지 않는다.

## API 규칙

- API prefix는 `/api/v1`을 사용한다.
- OpenAPI를 React Native 클라이언트 계약의 기준으로 유지한다.
- 새 endpoint를 추가하거나 request/response를 변경할 때 Swagger/OpenAPI 명세를 같은 변경에 포함한다.
- Controller에는 endpoint 목적과 인증 요구사항을 OpenAPI annotation으로 기록한다.
- 공개 request/response DTO가 Swagger schema에 정확히 노출되는지 `/v3/api-docs`로 검증한다.
- 로컬 Swagger UI는 `/swagger-ui.html`, OpenAPI JSON은 `/v3/api-docs`에서 제공한다.
- request DTO는 Bean Validation으로 검증한다.
- 목록 API는 cursor pagination을 기본으로 한다.
- 리뷰 제출과 같이 중복 요청 위험이 있는 API는 idempotency를 고려한다.
- 인증 필요 여부와 role을 endpoint test로 검증한다.

## 테스트 및 개발 프로세스

- CRITICAL: 새 기능은 실패하는 테스트를 먼저 작성하고 테스트가 통과하는 최소 구현을 작성한다.
- domain 정책은 단위 테스트로 검증한다.
- 현재 단계의 JPA, Flyway와 transaction 검증은 실행 중인 로컬 MySQL을 기준으로 수행한다.
- Docker/Testcontainers 기반 `integrationTest`는 현재 기본 검증 절차에 포함하지 않는다.
- 외부 API adapter는 stub server를 사용해 성공, timeout, rate limit, 잘못된 응답을 검증한다.
- 인증부터 방문, WriteGrant, 리뷰, 리포트까지 핵심 흐름은 통합 테스트를 유지한다.
- 동시 리뷰 제출에서 하나의 WriteGrant가 한 번만 소진되는지 검증한다.
- 기존 테스트를 삭제하거나 약화해 빌드를 통과시키지 않는다.
- 커밋 메시지는 conventional commits 형식을 따른다: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.

## 기본 명령어

```bash
./gradlew bootRun   # 로컬 API 서버
./gradlew test      # 테스트
./gradlew check     # 정적 검사와 테스트
./gradlew build     # 전체 빌드
```

프로젝트 초기 scaffold가 생성되기 전에는 명령이 아직 존재하지 않을 수 있다. scaffold 작업에서 Gradle Wrapper와 위 명령을 사용할 수 있도록 구성한다.

## 구현 순서

1. Spring Boot/Kotlin 프로젝트 기반
2. MySQL, JPA, Flyway
3. 공통 오류, 보안, OpenAPI
4. 카카오 로그인, 온보딩 토큰과 세션
5. 음식점과 지역 제한
6. 로그인 사용자의 리뷰 초안
7. 증빙 업로드와 OCR provider 선정
8. WriteGrant와 정식 리뷰 전환
9. 리포트 집계
10. 관리자 검수, 신고, 정정
11. 서버 보안·통합·성능 검증
12. React Native 앱
