# Rider Voice MVP Architecture Decision Records

현재 적용되는 결정만 기록한다. 후속 기능은 구현 직전에 별도 ADR로 결정하며, 현재 코드에 미리 반영하지 않는다.

## ADR-001: 서버 API 우선 개발

**결정**: Spring Boot 서버 API와 OpenAPI 계약을 먼저 구현한다.

**이유**: 인증, 음식점 식별, 리뷰 소유권과 데이터 제약을 서버에서 일관되게 검증하기 위해서다.

**트레이드오프**: 실제 사용자 화면 검증은 늦어진다. 현재는 Swagger UI와 API 테스트로 흐름을 검증한다.

## ADR-002: Spring Boot와 Kotlin

**결정**: API 서버는 Spring Boot와 Kotlin으로 구현한다.

**이유**: Spring Security, Validation, Transaction과 JPA를 사용해 인증된 CRUD API를 일관된 구조로 구현할 수 있다.

**트레이드오프**: Kotlin JPA plugin과 entity proxy 제약을 고려해야 한다.

## ADR-003: MySQL, JPA와 Flyway

**결정**: MySQL 9.3을 사용하고 Spring Data JPA/Hibernate로 접근하며 모든 스키마 변경은 Flyway로 관리한다.

**이유**: 사용자, 음식점과 리뷰 사이의 관계 및 unique 제약을 데이터베이스에서도 보장해야 한다.

**트레이드오프**: 로컬 MySQL이 필요한 통합 테스트는 기본 단위 테스트와 분리해 실행한다. UUID는 `BINARY(16)`, 시각은 UTC `DATETIME(6)`으로 저장한다.

## ADR-004: REST와 OpenAPI

**결정**: `/api/v1` JSON REST API를 제공하고 OpenAPI를 클라이언트 계약의 기준으로 사용한다. 오류는 안정적인 `code`를 포함한 RFC 7807 `ProblemDetail`로 반환한다.

**이유**: 서버 구현과 이후 클라이언트가 동일한 요청·응답 및 인증 계약을 사용할 수 있다.

**트레이드오프**: endpoint 또는 DTO 변경 시 코드, 테스트와 OpenAPI schema를 함께 관리해야 한다.

## ADR-005: 카카오 로그인과 서비스 세션

**결정**: 회원 식별에는 카카오 REST OAuth를 사용한다. 신규 사용자는 짧은 수명의 일회용 onboarding token으로 약관에 동의한 뒤 서비스 access token과 rotating refresh token을 발급받는다.

**이유**: 별도 비밀번호를 저장하지 않으면서 약관 동의 전후 권한을 분리할 수 있다.

**트레이드오프**: 카카오 장애와 OAuth 설정에 영향을 받는다. 카카오 access token은 계정 확인 뒤 장기 보관하지 않으며 refresh token은 해시로 저장한다.

카카오 로그인은 라이더 신분이나 음식점 방문을 증명하지 않는다.

## ADR-006: 카카오 장소 기반 음식점 지연 등록

**결정**: 카카오 로컬 API의 장소 ID를 음식점 외부 식별자로 사용한다. 사용자가 원래 검색어와 선택한 장소 ID를 제출하면 서버가 같은 키워드 검색을 반복하고 일치하는 provider 결과로만 Restaurant를 멱등 등록한다.

**이유**: 사용자가 음식점 정보를 자유 입력할 때 생기는 중복과 오등록을 줄이면서, 사용되지 않는 장소를 미리 모두 저장하지 않을 수 있다.

**트레이드오프**: 최초 등록 시 카카오 API를 한 번 더 호출하고 검색 결과 변동의 영향을 받을 수 있다. `kakaoPlaceId` unique 제약으로 동시 중복 등록을 막는다.

최초 선택 사용자는 음식점의 소유자나 관리자가 아니다.

## ADR-007: 비공개 리뷰 CRUD

**결정**: 로그인 사용자는 음식점당 하나의 비공개 리뷰를 생성하고 자신의 리뷰만 조회·수정·삭제할 수 있다. 리뷰는 고정된 6개 `ReviewRating`과 최대 200자의 선택 의견으로 구성한다.

**이유**: 방문 인증과 공개 운영을 도입하기 전에 핵심 리뷰 작성 흐름과 서버의 소유권·유효성·중복 방지 규칙을 검증하기 위해서다.

**트레이드오프**: 현재 리뷰는 방문 근거가 없으므로 인증 리뷰로 표현하거나 공개·집계할 수 없다. 삭제는 MVP에서 hard delete로 처리한다.

## ADR-008: 가벼운 헥사고날 경계와 DTO 분리

**결정**: 기능 패키지 안에서 presentation, application, domain, infrastructure를 나누고 application의 input/output port를 의존 경계로 사용한다. HTTP request/response DTO는 `presentation/dto`, command/result는 `application/model`에 분리한다.

Controller는 request DTO를 command로 변환해 input port를 호출하고, application result를 response DTO로 변환한다. repository와 외부 provider port는 application에 두며 infrastructure adapter가 구현한다.

**이유**: HTTP, JPA와 외부 provider 형식이 비즈니스 로직으로 전파되는 것을 막고 Controller가 커지는 문제를 방지한다. API 계약과 use case 계약을 분리하면 각 계층을 독립적으로 테스트하기 쉽다.

**트레이드오프**: command, result, DTO와 mapper가 추가되어 파일 수와 변환 코드가 늘어난다. 모든 class에 interface를 만들지 않고 inbound use case와 outbound dependency에만 port를 두며, 작은 DTO는 역할별 파일로 묶는다.

현재 domain entity에는 JPA annotation을 허용한다. persistence model 완전 분리는 MVP 복잡도에 비해 이점이 확인될 때 별도 결정한다. 기존 인증 코드의 경계 정리는 음식점·리뷰 구현과 섞지 않고 별도 refactor로 수행한다.

## 후속 결정

다음 항목은 현재 결정하지 않는다.

- 배달 완료 증빙과 방문 인증 방식
- 기존 비공개 리뷰의 인증 리뷰 전환 정책
- 이미지 보관 기간과 OCR provider
- 공개 리포트의 최소 표본과 집계 방식
- 자유 의견 검수, 신고와 정정 절차
- 사용자용 웹 또는 React Native 클라이언트
- AWS 배포와 운영 인프라

후속 기능을 시작할 때 제품 필요성과 운영 비용을 다시 검토하고 관련 문서와 ADR을 먼저 갱신한다.
