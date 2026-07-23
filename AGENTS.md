# 프로젝트: Rider Voice

## 현재 MVP 목표

- 카카오 소셜 로그인 사용자가 음식점을 검색하고 선택할 수 있게 한다.
- 선택된 카카오 장소를 내부 음식점으로 중복 없이 등록한다.
- 로그인 사용자가 음식점별 비공개 리뷰를 작성·조회·수정·삭제할 수 있게 한다.
- 리뷰는 6개 구조화 평가와 최대 200자의 선택 의견으로 구성한다.
- 방문 인증, 공개 리포트와 운영 기능은 현재 구현하지 않는다.

## 기술 스택

- Spring Boot, Kotlin, Gradle Kotlin DSL
- Spring MVC, Spring Security, Bean Validation
- Spring Data JPA, Hibernate, MySQL 9.3
- OpenAPI, RFC 7807 `ProblemDetail`
- JUnit 5, MockK
- 카카오 REST OAuth, 카카오 로컬 REST API

## 현재 실행 경계

- API 서버와 MySQL은 로컬 프로세스로 실행한다.
- 로컬 MySQL `rider` 데이터베이스를 사용한다.
- CRITICAL: 사용자가 별도로 요청하기 전에는 Docker, Docker Compose 또는 Testcontainers를 실행하지 않는다.
- CRITICAL: 사용자가 별도로 요청하기 전에는 AWS 리소스 생성, 배포 또는 production readiness 작업을 수행하지 않는다.
- CRITICAL: 방문 증빙, OCR, `WriteGrant`, 공개 리포트, 검수, 신고, 정정과 클라이언트 앱을 미리 구현하지 않는다.

## 구현 전 필수 확인

- 구현 전에 `docs/PRD.md`, `docs/ARCHITECTURE.md`, `docs/ADR.md`를 읽는다.
- 제품 범위나 기술 결정이 바뀌면 코드보다 관련 문서를 먼저 업데이트한다.
- 현재 작업과 무관한 사용자 변경, 삭제 파일과 `.codex` 설정을 되돌리지 않는다.

## 아키텍처 규칙

- CRITICAL: Controller에 비즈니스 로직이나 JPA query를 작성하지 않는다.
- CRITICAL: JPA Entity를 API request 또는 response로 직접 사용하지 않는다.
- CRITICAL: application package가 presentation DTO나 infrastructure 구현 package를 import하지 않게 한다.
- CRITICAL: Controller는 application input port에 의존하고 repository나 외부 adapter를 직접 호출하지 않는다.
- CRITICAL: 외부 API는 infrastructure adapter에서만 호출한다.
- CRITICAL: 클라이언트가 DB나 카카오 API를 직접 호출하게 하지 않는다.
- CRITICAL: 모든 Entity 기본 키는 `BaseEntity`의 `Long IDENTITY` 전략을 사용한다.
- CRITICAL: 로컬과 통합 테스트에서는 Hibernate `ddl-auto=update`로 Entity mapping을 schema에 반영한다.
- CRITICAL: 운영 환경에서 Hibernate schema auto-generation을 활성화하지 않는다.
- CRITICAL: 외부 provider 오류, secret과 stack trace를 클라이언트에 노출하지 않는다.
- Controller는 HTTP validation, principal 추출, input port 호출과 response 변환만 담당한다.
- request/response DTO는 Controller 파일과 분리해 기능별 `presentation/dto`에 둔다.
- request DTO는 application command로, application result는 response DTO로 명시적으로 변환한다.
- request DTO를 application service에 그대로 전달하거나 application result를 API 응답으로 직접 반환하지 않는다.
- 트랜잭션 경계와 소유권 검사는 application service에 둔다.
- 기능 간에는 entity나 presentation DTO 대신 ID, application model 또는 공개 input port를 전달한다.
- input port는 `application/port/in`, repository와 외부 provider port는 `application/port/out`에 둔다.
- infrastructure adapter는 output port를 구현하며 application은 adapter 구현을 알지 못한다.
- 모든 class에 interface를 만들지 말고 inbound use case와 outbound dependency에만 port를 둔다.
- 성공 응답은 기능별 DTO, 오류 응답은 RFC 7807 `ProblemDetail`을 사용한다.
- 모든 시각은 UTC로 저장하고 API에서는 RFC 3339로 반환한다.
- Entity 연관관계는 필요한 자식→부모 단방향 `LAZY` 관계만 사용하고 편의를 위한 양방향 컬렉션을 추가하지 않는다.

## 패키지 규칙

```text
com.ridervoice.api
├── common
├── auth
├── restaurant
└── review
```

- 기능 안에서 `presentation`, `application`, `domain`, `infrastructure` 경계를 사용한다.
- `presentation`에는 Controller, `dto`, HTTP mapper를 둔다.
- `application`에는 input/output port, command/result와 service를 둔다.
- `domain`에는 entity, value object와 policy를 둔다.
- `infrastructure`에는 JPA repository 구현과 외부 provider adapter를 둔다.
- 비즈니스 규칙을 편의상 `common`에 두지 않는다.
- provider별 request/response 타입을 infrastructure adapter 밖으로 노출하지 않는다.
- 작은 DTO는 class마다 파일을 만들지 않고 `FeatureRequests.kt`, `FeatureResponses.kt`처럼 역할별로 묶는다.
- 기존 인증 코드의 헥사고날 경계 정리는 별도 refactor로 수행하며 음식점·리뷰 기능 변경에 섞지 않는다.

## 음식점 규칙

- 카카오 장소 ID를 외부 기준 식별자로 사용한다.
- 등록 요청에는 원래 검색어와 선택한 카카오 장소 ID를 받고, 서버가 같은 키워드 검색을 반복해 일치하는 provider 결과로만 등록한다.
- 사용자 입력의 음식점명, 주소, 좌표와 카카오 장소 ID를 그대로 신뢰하지 않는다.
- 카카오 로컬 API는 장소 ID 단건 조회를 제공하지 않으므로 클라이언트가 보낸 장소 ID만으로 등록하지 않는다.
- 제거된 지역 파일럿 필드를 다시 추가하거나 다른 의미로 재사용하지 않는다.
- `kakaoPlaceId` unique 제약과 application 정책으로 중복 등록을 방지한다.
- 최초 선택 사용자에게 음식점 소유권이나 수정 권한을 부여하지 않는다.

## 리뷰 규칙

- CRITICAL: 현재 리뷰는 비공개·미인증 기록이며 공개하거나 집계하지 않는다.
- CRITICAL: 사용자는 본인의 리뷰만 조회·수정·삭제할 수 있다.
- CRITICAL: 사용자와 음식점 조합당 리뷰를 하나만 허용하고 DB unique 제약으로도 보장한다.
- 6개 평가 항목은 모두 `VERY_GOOD`, `GOOD`, `NEEDS_IMPROVEMENT`, `MAJOR_IMPROVEMENT`, `NOT_OBSERVED` 중 하나여야 한다.
- 의견은 선택 사항이며 최대 200자다.
- 중복 생성은 기존 리뷰를 덮어쓰지 않고 `409 Conflict`로 반환한다.
- 타인의 리뷰 ID를 사용한 조회·수정·삭제는 리소스 존재를 노출하지 않도록 `404 Not Found`로 처리한다.
- 삭제는 현재 MVP에서 hard delete로 처리한다.

## API 규칙

- API prefix는 `/api/v1`을 사용한다.
- OpenAPI를 API 계약의 기준으로 유지한다.
- endpoint 또는 DTO 변경 시 OpenAPI annotation과 schema를 같은 변경에 포함한다.
- 공개 DTO가 `/v3/api-docs`에 정확히 노출되는지 검증한다.
- request DTO는 Bean Validation으로 검증한다.
- 목록 API는 cursor pagination을 기본으로 한다.
- 인증 필요 여부와 role을 endpoint test로 검증한다.

## 테스트 및 개발 프로세스

- CRITICAL: 새 기능은 실패하는 테스트를 먼저 작성하고 테스트가 통과하는 최소 구현을 작성한다.
- domain 정책은 단위 테스트로 검증한다.
- JPA schema 생성, 연관관계와 transaction 검증은 실행 중인 로컬 MySQL을 기준으로 수행한다.
- Docker/Testcontainers 기반 통합 테스트를 기본 검증 절차에 포함하지 않는다.
- 카카오 로컬 adapter는 stub server로 성공, timeout, rate limit과 잘못된 응답을 검증한다.
- 음식점과 리뷰의 unique 제약은 동시 요청을 포함해 검증한다.
- 기존 테스트를 삭제하거나 약화해 빌드를 통과시키지 않는다.
- 커밋 메시지는 conventional commits 형식을 따른다.

## 기본 명령어

```bash
./gradlew bootRun
./gradlew test
./gradlew check
./gradlew build
./gradlew integrationTest  # 실행 중인 로컬 MySQL 필요
```

## 현재 구현 순서

1. Spring Boot, MySQL, 공통 오류·보안·OpenAPI 기반
2. 카카오 로그인, 약관 동의와 서비스 세션
3. 음식점 검색과 카카오 장소 기반 지연 등록
4. 로그인 사용자의 비공개 리뷰 CRUD
5. MVP API 계약과 회귀 검증

이후 기능은 `docs/PRD.md`의 후속 로드맵을 검토하고 문서를 먼저 변경한 뒤 착수한다.
