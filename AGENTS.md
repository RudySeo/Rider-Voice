# 프로젝트: Rider Voice

## 현재 MVP 목표

- 소비자가 로그인 없이 음식점을 검색하고 공개 리뷰와 리포트를 확인할 수 있게 한다.
- 카카오 로그인 활성 사용자가 별도 라이더·방문 인증 없이 리뷰를 작성할 수 있게 한다.
- 실제 픽업 장소와 소비자에게 보이는 배달 브랜드를 분리한다.
- 카카오에 없는 배달 브랜드를 검증된 주소 기반으로 수동 등록할 수 있게 한다.
- 개별 구조화 평가는 즉시 공개하고 브랜드·장소 집계는 각각 서로 다른 작성자 5명부터 공개한다.
- 자유 의견은 관리자 검수 후 공개하고 신고·정정·중복 병합을 지원한다.
- 카카오 로그인과 필수 약관 동의를 완료한 활성 사용자(이하 라이더)는 음식점별 활성 리뷰를 하나만 작성할 수 있게 한다.
- 활성 리뷰가 삭제되거나 전체 제외된 경우 최초 제출 시각부터 90일 후 다시 작성할 수 있게 한다.

## 제품 신뢰 경계

- CRITICAL: 카카오 로그인은 계정 식별 수단이며 라이더 신분이나 음식점 방문을 증명하지 않는다.
- CRITICAL: 리뷰와 집계를 인증된 라이더 리뷰, 방문 인증 정보 또는 사실 보증으로 표현하지 않는다.
- CRITICAL: 모든 공개 리뷰·리포트 응답에 `verificationStatus=UNVERIFIED`와 미인증 안내를 포함한다.
- CRITICAL: 서로 다른 작성자 5명 기준은 공개 집계 기준일 뿐 방문 인증이나 조작 방지 보장이 아니다.
- CRITICAL: 배달내역 캡처·이미지 업로드, OCR와 배달 앱 화면 파싱을 구현하지 않는다.
- 종합 점수, 평균 별점, 음식점 순위와 인증 배지를 만들지 않는다.
- 같은 픽업 장소에 연결된 다른 브랜드 목록을 소비자에게 공개하지 않는다.

## 기술 스택

- Kotlin, JDK 25, Gradle Kotlin DSL
- Spring Boot, Spring MVC, Spring Security OAuth2 Client
- Bean Validation, Spring Data JPA, Hibernate, MySQL 9.3
- Spring `RestClient`, Spring Cache, Caffeine
- springdoc-openapi, RFC 7807 `ProblemDetail`
- JUnit 5, MockK, MockMvc와 HTTP stub server
- 카카오 REST OAuth, 카카오 로컬 REST API

## 현재 실행 경계

- API 서버와 MySQL은 로컬 프로세스로 실행한다.
- 로컬 MySQL `rider` 데이터베이스를 사용한다.
- 초기 MVP는 단일 API 인스턴스를 전제로 한다.
- CRITICAL: 사용자가 별도로 요청하기 전에는 Docker, Docker Compose 또는 Testcontainers를 실행하지 않는다.
- CRITICAL: 사용자가 별도로 요청하기 전에는 AWS 리소스 생성, 배포 또는 production readiness 작업을 수행하지 않는다.
- Redis, Kafka, Elasticsearch, WebFlux와 비동기 메시지 큐를 미리 추가하지 않는다.

## 구현 전 필수 확인

- 구현 전에 `docs/PRD.md`, `docs/ARCHITECTURE.md`, `docs/ADR.md`, `docs/API_SPEC.md`를 읽는다.
- 제품 범위나 기술 결정이 바뀌면 코드보다 관련 문서를 먼저 업데이트한다.
- 목표 문서와 현재 구현 상태를 혼동하지 않는다.
- 현재 작업과 무관한 사용자 변경, 삭제 파일과 `.codex` 설정을 되돌리지 않는다.

## 아키텍처 규칙

- CRITICAL: Controller와 OAuth handler에 비즈니스 로직이나 JPA query를 작성하지 않는다.
- CRITICAL: JPA Entity를 API request 또는 response로 직접 사용하지 않는다.
- CRITICAL: application package가 presentation DTO나 infrastructure 구현 package를 import하지 않게 한다.
- CRITICAL: Controller와 security success/failure handler는 application input port에 의존하고 repository나 외부 adapter를 직접 호출하지 않는다.
- CRITICAL: 외부 API는 infrastructure adapter에서만 호출한다.
- CRITICAL: 클라이언트가 DB나 카카오 API를 직접 호출하게 하지 않는다.
- CRITICAL: 모든 Entity 기본 키는 `BaseEntity`의 `Long IDENTITY` 전략을 사용한다.
- CRITICAL: 로컬과 통합 테스트에서는 Hibernate `ddl-auto=update`로 Entity mapping을 schema에 반영한다.
- CRITICAL: 운영 환경에서 Hibernate schema auto-generation을 활성화하지 않는다.
- CRITICAL: 외부 provider 오류, token, secret과 stack trace를 클라이언트에 노출하지 않는다.
- request/response DTO는 Controller 파일과 분리해 기능별 `presentation/dto`에 둔다.
- request DTO는 application command로, application result는 response DTO로 명시적으로 변환한다.
- 트랜잭션, 소유권, 작성 제한과 상태 전이는 application service에 둔다.
- 기능 간에는 Entity나 presentation DTO 대신 ID, application model 또는 공개 input port를 전달한다.
- input port는 `application/port/in`, repository와 외부 provider port는 `application/port/out`에 둔다.
- infrastructure adapter는 output port를 구현하며 application은 adapter 구현을 알지 못한다.
- 모든 class에 interface를 만들지 말고 inbound use case와 outbound dependency에만 port를 둔다.
- 성공 응답은 기능별 DTO, 오류 응답은 RFC 7807 `ProblemDetail`을 사용한다.
- 모든 시각은 UTC로 저장하고 API에서는 RFC 3339로 반환한다. 방문 연월 검증만 `Asia/Seoul`을 사용한다.
- Entity 연관관계는 필요한 자식→부모 단방향 `LAZY` 관계만 사용하고 편의를 위한 양방향 컬렉션을 추가하지 않는다.

## 패키지 규칙

```text
com.ridervoice.api
├── common
├── auth
├── restaurant
├── review
└── moderation
```

- 기능 안에서 `presentation`, `application`, `domain`, `infrastructure` 경계를 사용한다.
- `presentation`에는 Controller, security HTTP adapter, `dto`와 mapper를 둔다.
- `application`에는 input/output port, command/result와 service를 둔다.
- `domain`에는 entity, value object와 policy를 둔다.
- `infrastructure`에는 JPA adapter와 외부 provider adapter를 둔다.
- 비즈니스 규칙을 편의상 `common`에 두지 않는다.
- provider별 request/response 타입을 infrastructure adapter 밖으로 노출하지 않는다.
- 작은 DTO는 `FeatureRequests.kt`, `FeatureResponses.kt`처럼 역할별로 묶는다.

## 인증 규칙

- 카카오 authorization, `state`, code 교환과 user info는 Spring Security OAuth2 Client로 처리한다.
- 카카오를 사용자 정의 OAuth provider로 등록하고 user info의 `id`만 외부 subject로 사용한다.
- OAuth handshake에만 임시 HTTP session을 허용하고 성공·실패 후 폐기한다.
- REST API security chain은 stateless이며 OAuth session을 API 인증으로 받아들이지 않는다.
- 로그인 화면에서 현재 필수 약관 동의를 고지하고 OAuth 교환 시 신규·약관 미동의 사용자를 활성화한 뒤 opaque access/refresh token을 발급한다.
- 카카오 access token은 사용자 확인 뒤 저장하지 않는다.
- refresh token은 hash로 저장하고 갱신할 때 회전시킨다.
- `UserRole`은 `USER`, `ADMIN`이며 access token 인증 시 현재 DB role을 확인한다.

## 음식점 규칙

- `PickupLocation`은 실제 픽업 장소, `Restaurant`은 소비자에게 보이는 배달 브랜드다.
- 하나의 픽업 장소에 여러 배달 브랜드를 연결할 수 있다.
- 카카오 장소 ID는 `RestaurantExternalReference`에서 `(provider, externalPlaceId)` unique로 관리한다.
- 같은 장소와 정규화 브랜드명 조합은 DB unique 제약으로 중복을 막는다.
- 카카오와 주소 등록 요청에는 원 검색어와 선택 결과 식별 정보를 받고 서버가 같은 검색을 반복해 검증한다.
- 사용자 입력의 주소, 이름, 좌표와 카카오 장소 ID를 그대로 신뢰하지 않는다.
- 음식점·장소 등록과 첫 리뷰 생성은 같은 application use case와 트랜잭션에서 완료한다.
- 최초 등록 사용자에게 음식점 소유권이나 수정 권한을 부여하지 않는다.
- 중복 음식점은 hard delete하지 않고 canonical 음식점 연결과 감사 기록을 남긴다.

## 리뷰 규칙

- CRITICAL: 활성 사용자는 별도 라이더·방문 인증 없이 작성할 수 있다.
- CRITICAL: 유효한 개별 구조화 평가는 첫 리뷰부터 공개한다.
- CRITICAL: 브랜드와 픽업 장소 집계는 각각 서로 다른 유효 작성자 5명부터 공개한다.
- 라이더는 같은 음식점에 활성 리뷰를 하나만 가질 수 있으며 활성 리뷰가 있으면 새 리뷰를 작성할 수 없다.
- 활성 리뷰가 삭제되거나 전체 제외된 경우 해당 리뷰의 최초 제출 시각부터 90일 후 새 리뷰를 작성할 수 있다.
- 활성 리뷰만 수정·삭제할 수 있고 방문 연월은 수정할 수 없다.
- 리뷰 삭제는 soft delete이며 삭제·전체 제외 리뷰도 작성 제한과 24시간 작성 횟수 계산에 포함한다.
- 6개 평가는 모두 `VERY_GOOD`, `GOOD`, `NEEDS_IMPROVEMENT`, `MAJOR_IMPROVEMENT`, `NOT_OBSERVED` 중 하나여야 한다.
- 의견은 선택 사항이며 trim 후 최대 200자다.
- `NOT_OBSERVED`는 개수로 표시하고 평가 비율 분모에서는 제외한다.
- 타인의 리뷰와 비활성 리뷰는 `404 Not Found`로 처리한다.

## 검수와 신고 규칙

- 구조화 평가는 즉시 공개하고 자유 의견은 승인 후 공개한다.
- 공개 의견 수정은 다시 검수 대기로 전환한다.
- 신고 접수 시 공개 의견만 임시 숨기고 구조화 평가는 유지한다.
- 신고 기각은 의견 상태를 복원한다.
- 의견 위반은 의견만 비공개 처리한다.
- 허위·도배로 인정된 리뷰는 전체를 공개와 집계에서 제외한다.
- 관리자 결정, 음식점 정정과 병합은 `ModerationAudit`에 기록한다.
- 한 사용자는 같은 신고 대상에 한 번만 신고할 수 있다.

## API 규칙

- API prefix는 `/api/v1`을 사용한다.
- OpenAPI를 API 계약의 기준으로 유지한다.
- endpoint 또는 DTO 변경 시 OpenAPI annotation과 schema를 같은 변경에 포함한다.
- 공개 DTO가 `/v3/api-docs`에 정확히 노출되는지 검증한다.
- request DTO는 Bean Validation으로 검증한다.
- 목록 API는 생성 시각과 ID 기반 cursor pagination을 기본으로 한다.
- 공개·온보딩·USER·ADMIN 인증 요구사항을 endpoint test로 검증한다.
- 공개 리뷰와 리포트 DTO에 미인증 상태와 안내를 포함한다.

## 캐시와 호출 제한

- 검색어는 정규화 후 2~100자, 결과는 최대 20개다.
- 카카오 성공 검색 결과는 Caffeine에 5분간 저장한다.
- 검색은 호출자 기준 분당 30회로 제한한다.
- 리뷰는 계정당 최근 24시간 최대 10개, 신고는 계정당 하루 최대 20개다.
- 카카오 장애 시 공개 검색은 내부 결과와 외부 검색 불가 상태를 반환한다.
- 카카오·주소 기반 신규 등록은 provider 검증이 불가능하면 실패시킨다.

## 테스트 및 개발 프로세스

- CRITICAL: 새 기능은 실패하는 테스트를 먼저 작성하고 테스트가 통과하는 최소 구현을 작성한다.
- domain 정책은 단위 테스트로 검증한다.
- JPA schema, 연관관계, transaction과 unique 제약은 실행 중인 로컬 MySQL을 기준으로 검증한다.
- Docker/Testcontainers 기반 통합 테스트를 기본 검증 절차에 포함하지 않는다.
- 카카오 adapter는 stub server로 성공, timeout, rate limit과 잘못된 응답을 검증한다.
- 장소·브랜드·리뷰 상태의 unique 제약은 동시 요청을 포함해 검증한다.
- 4명/5명 집계 경계, 장소 작성자 중복 제거와 `NOT_OBSERVED`를 검증한다.
- 활성 리뷰 중복 작성, 삭제·전체 제외 후 90일 우회, 의견 검수와 canonical 병합을 검증한다.
- 기존 테스트를 삭제하거나 약화해 빌드를 통과시키지 않는다.
- 커밋 메시지는 Conventional Commits 형식을 따른다.

## Code Review Rules

### 제품 신뢰와 공개 데이터

- 공개 API·UI에서 `UNVERIFIED` 안내를 누락하거나 카카오 로그인을 라이더·방문 인증으로 표현하거나 같은 픽업 장소의 다른 브랜드를 노출하는 변경을 지적한다. 기존 미인증 안내와 공개 범위를 유지하는 방향을 제안한다.

### 애플리케이션 경계

- Controller·OAuth handler의 비즈니스 로직이나 JPA query, API의 Entity 직접 사용, application의 presentation·infrastructure 구현 의존, infrastructure 밖의 provider 호출을 지적한다. command/result와 input/output port 경계를 유지하는 방향을 제안한다.

### 리뷰 생명주기와 집계 무결성

- 음식점별 활성 리뷰 하나, 삭제·전체 제외 후 최초 제출 기준 90일, 삭제·제외 기록을 포함한 24시간 제한, 의견 사전 검수, 작성자 5명 집계와 `NOT_OBSERVED` 분모 규칙을 우회하는 변경을 지적하고 해당 경계 테스트를 요구한다.

## 기본 명령어

```bash
./gradlew bootRun
./gradlew test
./gradlew check
./gradlew build
./gradlew integrationTest  # 실행 중인 로컬 MySQL 필요
```

## 현재 구현 순서

1. 기획·ADR·아키텍처·API 계약 갱신
2. Spring Security OAuth2 Client 기반 카카오 로그인 전환
3. 픽업 장소·배달 브랜드·외부 참조 모델
4. 음식점별 활성 리뷰 1개, 삭제·전체 제외 후 90일 제한과 의견 검수
5. 공개 검색·상세·리뷰와 작성자 5명 집계
6. 신고, 관리자 처리와 음식점 병합
7. OpenAPI, 보안, 동시성과 로컬 MySQL 회귀 검증

클라이언트 앱, 방문 인증과 배포 인프라는 실제 운영 필요성을 확인한 뒤 문서를 먼저 변경하고 착수한다.
