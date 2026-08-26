# Rider Voice MVP 기술 결정 기록

이 문서는 Rider Voice MVP를 만들면서 선택한 중요한 제품·기술 결정과 그 이유를 기록한다. 세부 구현 구조는 `ARCHITECTURE.md`, API 형식은 실행 중인 OpenAPI, 테이블 관계는 `ERD.md`에서 확인한다.

각 결정은 다음 형식으로 정리한다.

- **선택**: 무엇을 하기로 했는가
- **선택한 이유**: 왜 이 방법을 골랐는가
- **감수할 점**: 이 선택으로 생기는 불편이나 한계는 무엇인가

## ADR-001: Spring Boot API를 먼저 만든다

**선택**: Kotlin과 JDK 25 기반 Spring Boot 서버를 만들고 `/api/v1` REST API를 제공한다. 클라이언트가 따라야 할 최종 계약은 실행 중인 OpenAPI 문서로 관리한다. 오류는 RFC 7807 `ProblemDetail` 형식으로 반환한다.

**선택한 이유**: 인증, 음식점 구분, 리뷰 공개와 집계 규칙을 서버에서 한 가지 기준으로 적용하기 위해서다.

**감수할 점**: 실제 화면이 없어도 먼저 API와 Swagger UI, 자동 테스트로 사용자 흐름을 확인해야 한다.

## ADR-002: MySQL과 JPA를 사용한다

**선택**: MySQL 8.4.10, Spring Data JPA와 Hibernate를 사용한다. 모든 테이블의 기본 키는 `BaseEntity`의 `Long IDENTITY` 방식을 따른다. 로컬과 일반 통합 테스트에서는 `ddl-auto=update`를 사용한다. 운영과 migration 전용 검증에서는 Flyway가 schema를 반영하고 Hibernate `ddl-auto=validate`가 Entity mapping과 일치하는지 확인한다.

**선택한 이유**: 음식점 관계, 리뷰 이력과 중복 작성 방지 규칙을 애플리케이션뿐 아니라 데이터베이스에서도 지켜야 하기 때문이다.

**감수할 점**: 로컬 `ddl-auto=update` schema와 versioned migration 사이의 불일치를 별도 migration 검증으로 막아야 한다. 운영 schema 변경은 Entity 수정만으로 끝나지 않고 Flyway migration을 함께 작성해야 한다.

## ADR-003: 기능별로 책임을 나눈다

**선택**: 각 기능을 HTTP 처리, 업무 흐름, 핵심 규칙, DB·외부 API 연결 영역으로 나눈다. 실제 패키지명은 `presentation`, `application`, `domain`, `infrastructure`다. 외부에서 들어오는 기능과 외부로 나가는 의존성에만 port 인터페이스를 둔다.

**선택한 이유**: 화면, Spring MVC, JPA와 카카오 API 형식이 핵심 정책에 직접 섞이지 않게 하기 위해서다.

**감수할 점**: 요청·응답 DTO, command, result와 변환 코드가 늘어난다.

## ADR-004: 카카오 로그인은 Spring Security가 처리한다

**선택**: 카카오 로그인 시작, 요청 위조 방지 값(`state`), 인증 코드 교환과 사용자 정보 조회는 Spring Security OAuth2 Client로 처리한다. 카카오 사용자 정보에서는 `id`만 계정 식별값으로 사용한다.

모든 카카오 인가 요청에는 `prompt=login`을 사용해 기존 카카오 브라우저 세션과 관계없이 계정을 다시 인증한다. 로그인 과정에서만 임시 HTTP session을 사용하고 완료되면 폐기한다. 성공 시 2분 유효 일회용 코드만 `ridervoice://auth/callback`에 전달한다. 서비스 token과 provider 오류는 URL에 넣지 않는다.

**선택한 이유**: OAuth 보안 흐름을 직접 구현하는 범위를 줄이고, 로그아웃한 사용자가 이전 카카오 계정으로 자동 로그인되지 않고 원하는 계정으로 다시 인증할 수 있게 하기 위해서다.

**감수할 점**: REST API는 로그인용 임시 session을 로그인 상태로 받아들이지 않도록 분리해야 한다. 카카오 세션이 남아 있어도 로그인할 때마다 재인증 단계가 추가된다. custom scheme callback은 Expo Go가 아닌 개발 빌드에서 검증해야 한다.

## ADR-005: Rider Voice 전용 토큰을 사용한다

**선택**: OAuth callback을 정상 완료하면 신규 사용자를 `ACTIVE` 상태로 만들고 ADR-024의 모바일 일회용 교환 코드를 발급한다. 애플리케이션에서 생성하는 사용자의 권한은 항상 `USER`이며 `ADMIN`은 운영자가 DB에서 직접 부여한다.

access token은 15분 동안 유효하고 앱 메모리에만 둔다. refresh token은 30일 동안 유효하며 backend에는 원문 대신 hash로 저장하고 앱에서는 SecureStore에 보관하며 사용할 때마다 교체한다. 두 token 모두 URL과 일반 Web Storage에 넣지 않는다.

**선택한 이유**: 카카오 로그인과 Rider Voice의 로그인 상태를 분리하고, 서비스가 refresh session을 직접 폐기하거나 교체할 수 있게 하기 위해서다.

**감수할 점**: 인증할 때 서비스 session과 현재 사용자 권한을 DB에서 확인해야 한다.

## ADR-006: 픽업 장소와 배달 브랜드를 분리한다

**선택**: 실제로 음식을 받는 장소는 `PickupLocation`, 소비자와 배달 앱에 보이는 브랜드는 `Restaurant`으로 나눈다. 한 픽업 장소에는 여러 브랜드를 연결할 수 있다. 초기 MVP의 장소 검색 provider는 카카오 하나이므로 카카오 장소 ID는 `Restaurant`의 nullable unique 컬럼으로 직접 관리한다. 브랜드명은 저장 전에 NFKC와 공백을 정리하며, 별도 정규화 이름 컬럼 없이 MySQL `utf8mb4_0900_ai_ci`의 대소문자 비구분 비교로 같은 장소의 중복을 막는다.

**선택한 이유**: 하나의 주방에서 여러 브랜드를 운영하거나 카카오 지도에 없는 배달 브랜드가 존재할 수 있기 때문이다.

**감수할 점**: 한 음식점에는 카카오 장소 ID 하나만 연결할 수 있으며 다른 장소 provider를 추가하려면 모델을 다시 검토해야 한다. 주소와 이름 정규화만으로 의미상 중복을 완전히 막을 수 없지만 초기 MVP에서는 사후 병합 기능을 제공하지 않는다. 같은 장소의 다른 브랜드 목록은 운영 관계를 오해하게 할 수 있어 소비자에게 공개하지 않는다.

## ADR-007: 첫 리뷰를 작성할 때 음식점을 등록한다

**선택**: 검색 결과는 내부 음식점과 카카오 장소를 함께 보여준다. 카카오에 없는 브랜드는 확인된 주소를 이용해 수동 등록할 수 있다. 음식점 등록과 첫 리뷰 작성은 함께 성공하거나 실패한다.

클라이언트가 보낸 장소 ID나 주소를 그대로 믿지 않고, 서버가 같은 검색을 다시 실행해 선택한 결과가 맞는지 확인한다.

**선택한 이유**: 아직 사용하지 않는 음식점을 미리 저장하지 않으면서 카카오에 없는 브랜드도 리뷰할 수 있게 하기 위해서다.

**감수할 점**: 첫 리뷰 작성 중 외부 API를 다시 호출해야 한다. 주소와 이름 표기가 다르면 의미상 같은 음식점이 서로 다른 기본 키로 등록될 수 있다.

## ADR-008: 미인증 리뷰도 첫 작성부터 공개한다

**선택**: 카카오 로그인 사용자는 별도의 라이더·방문 인증 없이 리뷰를 작성한다. 조건에 맞는 개별 평가는 첫 리뷰부터 공개하며 모든 공개 정보에 `verificationStatus=UNVERIFIED`와 미인증 안내를 넣는다.

**선택한 이유**: 초기 사용자의 작성 부담을 낮추고 실제 정보 공유 수요부터 확인하기 위해서다.

**감수할 점**: 카카오 로그인은 라이더 신분이나 방문을 증명하지 않는다. 리뷰를 인증 정보나 사실 보증처럼 표현해서는 안 된다.

## ADR-009: 작성자 5명부터 평가 결과를 공개한다

**선택**: 배달 브랜드와 픽업 장소 결과를 따로 계산하고 각각 서로 다른 유효 작성자 5명부터 공개한다. 공개 항목은 네 단계 응답을 동일 간격으로 1.0~5.0에 환산해 소수점 첫째 자리의 `score`를 함께 제공한다. `NOT_OBSERVED`는 점수 분모에서 제외하고 관찰 응답이 없으면 `score=null`이다. 이는 6개 항목 각각의 표현이며 종합 점수, 평균 별점과 순위는 만들지 않는다.

장소 결과에서는 같은 작성자가 여러 브랜드에 리뷰해도 가장 최근의 유효한 리뷰 하나만 사용한다. `NOT_OBSERVED`는 개수에는 포함하지만 평가 비율에서는 제외한다.

**선택한 이유**: 한 작성자의 반복 리뷰가 결과에 미치는 영향을 줄이고 항목별 관찰 정보를 그대로 보여주기 위해서다.

**감수할 점**: 5개 계정은 방문 인증이나 조작 방지를 보장하지 않는다. 초기에는 조회할 때 계산하므로 데이터가 크게 늘면 집계 방식을 다시 검토해야 한다.

## ADR-010: 음식점마다 활성 리뷰는 하나만 둔다

**선택**: 한 사용자는 같은 음식점에 활성 리뷰를 하나만 작성할 수 있다. 활성 리뷰가 있으면 새 리뷰 대신 기존 리뷰를 수정한다. 삭제되거나 전체 제외된 경우에는 최초 작성 시각부터 90일이 지나야 다시 작성할 수 있다.

삭제와 제외 기록은 지우지 않고 `reviews`에 남긴다. 활성 리뷰만 `current_slot=1`을 사용하고 `(작성자, 음식점, current_slot)` unique 제약으로 동시 중복 작성을 막는다.

**선택한 이유**: 반복 작성이 집계에 미치는 영향을 제한하고 삭제 후 즉시 다시 작성하는 우회를 막기 위해서다.

**감수할 점**: 운영 환경이 달라져도 활성 리뷰가 있는 동안에는 새 이력을 추가하지 않고 기존 리뷰를 수정해야 한다. 동시 요청의 unique 충돌도 처리해야 한다.

## ADR-011: 평가와 의견은 바로 공개하고 신고 후 조치한다

**선택**: 6개 정해진 평가와 최대 200자의 자유 의견은 작성·수정 즉시 공개한다. 신고가 접수되면 의견만 먼저 숨기고, 허위·도배로 판단되면 리뷰 전체를 공개와 집계에서 제외한다. 신고로 숨겨진 의견은 작성자가 수정해도 처리 전까지 공개하지 않는다.

**선택한 이유**: 초기 MVP에서 사전 검수 운영 부담과 공개 지연을 없애고, 실제 문제가 발생한 의견에 집중해 대응하기 위해서다.

**감수할 점**: 신고 전까지 부적절한 의견이 공개될 수 있다. 신고와 관리자 사후 조치를 유지하고 모든 관리자 결정을 기록으로 남긴다. 자동 필터는 실제 필요성이 확인된 뒤 별도 결정한다.

## ADR-012: 초기에는 단일 서버용 캐시와 호출 제한을 사용한다

**선택**: 카카오 검색 성공 결과는 Caffeine에 5분간 저장한다. 검색 호출 제한도 서버 메모리에서 처리하고, 리뷰·신고 작성 횟수는 DB 기록으로 확인한다.

**선택한 이유**: 초기 단일 서버와 MAU 1,000 목표에서는 별도 인프라 없이도 외부 API 호출과 기본적인 도배를 줄일 수 있기 때문이다.

**감수할 점**: 메모리에 저장된 캐시와 제한 횟수는 여러 서버가 공유할 수 없다. 서버를 늘릴 때 Redis 같은 분산 저장소를 검토해야 한다.

## ADR-013: 로컬 설정은 프로젝트의 `.env`로 관리한다

**선택**: Git에서 제외한 프로젝트 루트 `.env`를 `local` profile에서만 선택적으로 읽는다. OS와 IDE가 제공한 환경 변수는 `.env`보다 우선한다.

**선택한 이유**: 터미널과 IDE에서 같은 변수 이름을 사용하면서 비밀값을 저장소에 올리지 않기 위해서다.

**감수할 점**: 실행 위치가 프로젝트 루트가 아니라면 `.env`를 찾지 못할 수 있어 환경 변수를 직접 제공해야 한다.

## ADR-014: 이미지와 OCR로 방문을 인증하지 않는다

**선택**: 배달 내역 캡처, 이미지 업로드, OCR와 배달 앱 화면 분석을 만들지 않는다. 현재 MVP에는 라이더 또는 방문 인증 자체를 포함하지 않는다.

**선택한 이유**: 개인정보 노출 위험, 화면 변경 의존성, 저장 비용과 작성 부담이 크기 때문이다.

**감수할 점**: 리뷰는 미인증 정보로 유지된다. 실제 사용 데이터에서 필요성과 가능한 방식이 확인된 뒤에만 새 결정을 추가한다.

## ADR-015: 음식점 정정과 신고 처리를 함께 완료한다

**선택**: 음식점 정보 신고를 승인할 때는 이름 변경, 픽업 장소 재연결 또는 폐업 중 실제 정정도 함께 수행한다. 확인된 데이터 저장, 음식점 변경, 신고 종결과 감사 기록은 하나의 DB 트랜잭션으로 처리한다.

주소 같은 외부 정보 확인은 DB 트랜잭션 전에 끝낸다. 리뷰 전체 제외로 대상이 사라지면 같은 대상의 나머지 신고도 원인을 기록하고 종결한다.

**선택한 이유**: 신고만 처리되고 잘못된 음식점 정보는 그대로 남는 상황을 막기 위해서다.

**감수할 점**: 관리자 요청과 트랜잭션 범위가 커진다. 외부 API 호출을 트랜잭션 안에서 하지 않도록 흐름을 나눠야 한다.

## ADR-016: 로컬 React 화면으로 사용자 흐름을 확인한다

> **상태: 폐기됨.** ADR-025에 따라 `/frontend`와 브라우저 refresh cookie 계약을 제거하고 모바일 앱만 유지한다. 아래 내용은 당시 선택의 기록이다.

**선택**: 기존 Spring Boot 프로젝트는 그대로 두고 `/frontend`에 React, Vite와 TypeScript 기반 SPA를 둔다. TanStack Query, React Router, React Hook Form, Zod, CSS Modules, Vitest와 Testing Library를 사용한다. 실행 중인 OpenAPI에서 API 타입을 생성한다.

access token은 JavaScript 메모리에 보관하고 refresh token은 backend가 설정한 `HttpOnly` cookie에 보관한다. Web Storage에는 서비스 토큰을 저장하지 않는다.

**선택한 이유**: 서버 계약을 유지하면서 공개 조회, 로그인 고지, 음식점 등록과 리뷰 관리 흐름을 실제 브라우저에서 확인하기 위해서다.

**감수할 점**: 앱을 열 때마다 refresh API로 로그인 상태를 복구해야 하고 frontend와 backend를 함께 실행해야 한다. `HttpOnly` cookie는 JavaScript token 탈취 위험을 줄이지만 cookie 설정과 만료를 backend가 책임져야 한다. 이 화면은 로컬 prototype이며 운영 배포, 관리자 UI와 실제 카카오 브라우저 E2E는 다루지 않는다.

## ADR-017: 검증된 백엔드 이미지를 Docker Hub에 전달한다

**선택**: 백엔드 API를 JDK 25 멀티 스테이지 Docker 이미지로 패키징한다. `feat/**`와 `feature/**` push는 master 대상 Draft PR만 자동 생성한다. master 대상 PR workflow는 항상 시작하되 변경 경로를 먼저 판별하고, 백엔드 영향 변경에는 backend build, MySQL 8.4.10 통합 테스트와 컨테이너 health check를, `/mobile` 변경에는 pnpm 기반 typecheck·lint·test·Expo 의존성 및 export 검증을 각각 수행한다. 문서만 바뀐 PR은 애플리케이션 검증 job을 건너뛰되 하나의 최종 gate 상태를 남긴다. master push의 이미지 게시와 EC2 배포는 백엔드 영향 경로가 바뀐 경우에만 실행하며, 전체 검증을 반복하지 않고 `linux/amd64` 이미지를 공개 Docker Hub 저장소에 `latest`와 commit SHA 태그로 게시한다. mobile은 백엔드 이미지에 포함하지 않고 이번 자동화에서 배포하지 않는다.

Docker Hub PAT는 GitHub Environment secret으로 관리한다. 자동 생성된 PR의 CI를 별도 승인 없이 시작하기 위한 fine-grained GitHub PAT는 Contents Read와 Pull requests Read/Write만 허용한 Repository secret으로 관리하고, 값이 없으면 기본 `GITHUB_TOKEN`을 사용한다. 실제 DB·카카오 값은 build argument, Dockerfile, 이미지와 Docker Hub에 저장하지 않는다. CI의 통합·기동 검증은 일회용 MySQL 계정과 dummy provider 값을 사용한다.

**선택한 이유**: PR의 merge ref로 master와 결합된 결과를 변경 영역별로 검증해 테스트하지 않은 백엔드 이미지가 registry에 게시되는 것을 막고, 서로 독립적인 backend·mobile 검증의 실행 시간과 실패 범위를 줄이기 위해서다. PR workflow 자체는 경로 필터로 생략하지 않아 branch protection의 필수 상태가 대기 상태로 남지 않게 하고, master에서는 백엔드 영향 변경만 게시·배포해 모바일 또는 문서 변경이 불필요한 서버 배포를 일으키지 않게 한다. commit별 불변 태그로 어떤 코드가 이미지가 되었는지 추적하면서 비밀값과 빌드 산출물도 분리한다.

**감수할 점**: master 직접 push를 차단하고 변경 영역별 job을 모으는 최종 `PR CI gate` 상태 검사를 필수화해야 이 정책이 안전하다. 경로 분류 규칙에 백엔드 영향 파일이 누락되면 검증이나 배포가 생략될 수 있으므로 workflow와 분류기 변경은 모든 영역 검증을 실행하고 계약 테스트로 경계를 고정한다. PR은 최신 master 기준 검증을 통과해야 하며, 보호 규칙 우회가 발생하면 게시 전에 전체 검증을 반복하지 않는 위험이 생긴다. 고정한 MySQL 8.4.10의 minor 업그레이드는 별도 결정과 전체 회귀가 필요하다. mobile app store build·배포는 별도 운영 결정으로 남긴다.

## ADR-018: 운영 DB 기준을 RDS MySQL 8.4 LTS로 맞춘다

**선택**: 운영 데이터베이스 목표를 RDS MySQL 8.4.10으로 정하고 로컬 개발 기준과 CI 통합 테스트도 같은 버전을 사용한다. 이번 결정은 버전 호환성 기준만 정하며 RDS 리소스 생성, 네트워크와 배포 구성은 별도 작업으로 남긴다.

**선택한 이유**: RDS에서 정식 지원되는 LTS 버전으로 schema, collation, unique 제약과 트랜잭션 동작을 미리 검증하고, RDS Preview에서 유지되지 않는 MySQL 9.3과 운영 환경의 차이를 제거하기 위해서다.

**감수할 점**: 기존 MySQL 9.3 데이터 디렉터리를 8.4로 직접 낮출 수 없으므로 로컬 데이터 이전이 필요하면 논리 dump와 import를 별도로 수행해야 한다. RDS의 TLS, parameter group, 보안 그룹과 실제 네트워크 동작은 비운영 RDS 환경에서 추가로 검증해야 한다.

## ADR-019: Flyway로 운영 schema 변경 이력을 관리한다

**선택**: 운영 DB schema는 `V1__...sql`부터 시작하는 Flyway versioned migration으로 변경한다. 애플리케이션 시작 시 별도 migration 계정으로 아직 적용되지 않은 migration을 실행하고, 완료 후 runtime 계정으로 Hibernate `ddl-auto=validate`를 수행한다. migration 전용 CI 검증은 빈 MySQL 8.4.10에서 최초 적용과 재실행을 확인한다. `clean`, 자동 baseline과 out-of-order 적용은 허용하지 않으며 적용된 migration은 수정·삭제하지 않는다.

**선택한 이유**: 빈 RDS를 재현 가능하게 초기화하고 코드와 운영 schema의 불일치를 배포 전에 발견하며, API runtime 계정이 DDL 권한을 계속 갖지 않게 하기 위해서다.

**감수할 점**: Entity 또는 제약 변경마다 SQL migration을 함께 작성해야 한다. 애플리케이션 시작 프로세스가 migration 자격 증명도 받으므로 완전한 자격 증명 격리가 필요해지면 별도 일회성 migration 실행기로 분리해야 한다. 적용된 DDL을 자동 rollback하지 않으며 후속 호환 migration 또는 RDS 복구 절차를 사용한다.

## ADR-020: 기존 EC2 한 대에 OIDC와 SSM으로 백엔드를 자동 배포한다

**선택**: Docker Hub에 commit SHA 이미지가 게시되면 GitHub Actions가 `production` environment로 제한된 AWS OIDC role을 얻고 SSM Run Command로 기존 Ubuntu EC2의 배포 script를 실행한다. EC2에는 Nginx와 Docker를 두고 API container는 `127.0.0.1:8080`에만 노출한다. Elastic IP를 포함한 `sslip.io` 임시 주소와 Let's Encrypt 인증서를 사용한다. 운영 DB는 같은 VPC의 private Single-AZ RDS MySQL 8.4.10을 사용하며 runtime과 Flyway 계정을 분리하고 TLS host 검증을 강제한다. RDS CA truststore는 JDBC URL 속성으로 MySQL Connector/J에만 적용하고 JVM 기본 공개 CA truststore는 외부 HTTPS provider 연결에 유지한다. 운영 secret은 SSM Parameter Store Standard SecureString으로 관리한다.

새 image는 `sha-<12자리>` 불변 태그로 교체하고 health check에 실패하면 직전 image를 다시 실행한다. 운영 migration은 자동 rollback하지 않으며 이전 application과 호환되는 추가형 변경을 기본으로 한다. 초기에는 frontend, ALB, ECS, Route 53, NAT Gateway와 Multi-AZ를 사용하지 않는다.

**선택한 이유**: 이미 만든 EC2와 공개 Docker Hub image를 재사용하면서 장기 AWS access key와 SSH 기반 자동 배포를 피하고, 초기 트래픽에서 load balancer 비용과 운영 구성 수를 줄이기 위해서다. Nginx가 TLS와 신뢰할 client IP 경계를 담당하고 SSM이 배포와 운영 접속의 감사 가능한 통로를 제공한다.

**감수할 점**: 배포 중 한 container를 교체하므로 짧은 중단이 있고 EC2와 Single-AZ RDS가 단일 장애 지점이다. Elastic IP와 RDS는 사용량이 적어도 비용이 발생하며 AWS Budget은 알림만 제공하고 지출을 차단하지 않는다. `sslip.io`는 구매한 서비스 도메인이 아니므로 frontend와 실제 OAuth browser E2E를 배포할 때 정식 도메인과 cookie·redirect 설정을 다시 결정해야 한다. ALB나 CloudFront를 추가하면 forwarded header 신뢰 정책도 다시 설계해야 한다.

## ADR-021: 기존 EC2에 Prometheus와 Grafana를 함께 실행한다

**선택**: Spring Boot Actuator와 Micrometer Prometheus registry로 `/actuator/prometheus`를 제공하고, 기존 단일 EC2의 전용 Docker network에서 Prometheus가 15초마다 API를 수집한다. Prometheus와 Grafana는 운영 전용 Docker Compose가 선언적으로 관리하고 API container의 health rollback 배포는 기존 배포 script가 담당한다. Grafana는 Prometheus datasource와 Rider Voice 기본 dashboard를 provisioning한다. 두 UI는 EC2 localhost에만 bind하고 SSM port forwarding으로 접근한다. 로컬에서는 Spring과 MySQL을 기존 프로세스로 유지하고 Prometheus와 Grafana만 Docker Compose로 실행한다. Prometheus 보관 한도는 7일과 2GB이며 두 서비스의 데이터는 Docker volume에 유지한다.

**선택한 이유**: 현재 단일 API 인스턴스의 HTTP 오류율과 지연, JVM, process와 DB connection pool 상태를 기존 EC2와 SSM 경계 안에서 낮은 구성 비용으로 확인하기 위해서다. 공개 domain이나 monitoring ingress를 추가하지 않고 로컬과 운영에서 같은 dashboard를 검증할 수 있다.

**감수할 점**: EC2가 중단되면 API와 관측 stack이 함께 중단되어 외부 장애 감지나 고가용성 알림을 제공하지 못한다. 초기 범위에는 Alertmanager, 로그·trace, node exporter와 cAdvisor를 포함하지 않는다. 외부 감지 또는 장기 보관이 필요해지면 별도 host나 관리형 서비스를 새 ADR로 결정한다.

## ADR-022: Grafana만 기존 HTTPS 도메인에서 로그인 접근을 허용한다

**선택**: ADR-021의 UI 접근 결정 중 Grafana 부분을 변경한다. Grafana container의 `3000` 포트는 계속 EC2 localhost에만 bind하고 security group ingress를 추가하지 않는다. 대신 기존 Nginx와 TLS 도메인의 `/grafana/` 하위 경로를 Grafana로 reverse proxy한다. Grafana는 하위 경로를 canonical root URL로 사용하고 secure cookie, 익명 접속·회원가입 차단과 로그인 시도 제한을 적용한다. Prometheus `9090`과 `/actuator/prometheus`는 계속 비공개로 유지한다.

**선택한 이유**: 별도 관리자 PC 설정 없이 외부에서 운영 dashboard를 확인하되, 평문 `3000` 포트를 직접 공개하거나 Prometheus query UI와 원본 metric을 노출하지 않기 위해서다. 기존 443/TCP와 인증서를 재사용하므로 새 load balancer, DNS와 security group 규칙이 필요하지 않다.

**감수할 점**: Grafana 로그인 화면이 인터넷에 노출되어 자동 스캔과 로그인 시도의 대상이 된다. 강한 관리자 비밀번호와 Grafana의 로그인 보호를 유지하고 보안 업데이트를 적용해야 한다. 외부 접근이 더 이상 필요하지 않으면 Nginx `/grafana/` 경로와 Grafana 하위 경로 설정을 제거해 SSM 전용 접근으로 되돌린다.

## ADR-023: Expo 기반 React Native 모바일 앱을 별도로 개발한다

**선택**: 기존 Spring Boot API를 유지하고 `/mobile`에 Expo SDK 57, React Native 0.86과 TypeScript 기반 iOS·Android 앱을 둔다. Expo Router로 화면을 구성하고 TanStack Query로 서버 상태를 관리한다. 앱은 Spring Boot `/api/v1`만 호출하며 카카오와 DB를 직접 호출하지 않는다. 현재 검색 계약에는 위치 좌표가 없으므로 사용자 위치 권한, 거리 표시와 가까운 순 정렬은 포함하지 않는다.

현재 브라우저용 OAuth의 refresh cookie를 모바일에 그대로 의존하지 않는다. 모바일 인증은 ADR-024의 일회용 교환 코드와 앱 딥링크 계약을 사용한다.

**선택한 이유**: 확정한 모바일 UI를 iOS와 Android의 안전 영역, 키보드와 접근성 규칙에 맞게 실제 앱 구조로 구현하면서 기존 서버 정책과 API 계약을 재사용하기 위해서다. 위치 기능을 초기 범위에서 제외하면 불필요한 권한 요청 없이 음식점명과 주소 중심의 핵심 검색·리뷰 흐름부터 검증할 수 있다.

**감수할 점**: 모바일용 서버 계약과 보안 테스트가 추가로 필요하다. 화면과 native 기능은 iOS Simulator와 Android Emulator에서 각각 검증해야 한다.

## ADR-024: 네이티브 OAuth에는 일회용 교환 코드와 SecureStore를 사용한다

**선택**: iOS bundle identifier와 Android application id를 `com.ridervoice.app`, callback을 `ridervoice://auth/callback`으로 고정한다. 네이티브 개발 빌드는 시스템 브라우저에서 기존 Spring Security OAuth2 Client 흐름을 시작한다. 성공 callback은 256-bit 무작위 코드를 생성하고 backend에는 SHA-256 hash, 사용자, 2분 만료 시각과 사용 시각만 저장한다. 딥링크에는 이 일회용 코드만 전달하며 `/api/v1/auth/mobile/exchange`가 아직 사용되지 않은 코드를 한 번 소비하면서 15분 access token과 30일 refresh token을 발급한다. provider 실패에는 고정된 `error=oauth_failed`만 전달한다.

앱은 access token을 메모리에만 두고 refresh token을 SecureStore에 저장한다. refresh는 매번 token을 회전하며 로그아웃은 server session을 폐기하고 로컬 token을 항상 제거한다. Expo Go는 고정 custom scheme을 신뢰할 수 있는 실제 인증 환경으로 취급하지 않고 공개 mock 조회만 제공한다.

**선택한 이유**: 서비스 token과 provider 오류를 URL·브라우저 기록에 노출하지 않으면서 네이티브 앱이 브라우저와 같은 계정 식별 흐름을 재사용하고, 탈취된 단기 코드의 재사용 가능성을 제한하기 위해서다.

**감수할 점**: `mobile_login_grants` 정리와 만료·재사용 검증이 필요하고, SecureStore 동작과 custom scheme callback은 Expo Go가 아닌 iOS·Android 개발 빌드에서 검증해야 한다.

## ADR-025: React frontend와 브라우저 인증 계약을 제거한다

**선택**: `/frontend` React prototype을 삭제하고 모바일 앱을 유일한 사용자 클라이언트로 유지한다. 브라우저용 OAuth 시작 endpoint, refresh cookie 기반 refresh·logout API, `FRONTEND_BASE_URL`, `AUTH_COOKIE_SECURE`와 `refreshCookie` OpenAPI scheme을 제거한다. 카카오 provider callback URI는 유지하지만 성공과 실패는 항상 `ridervoice://auth/callback`으로 전달한다.

**선택한 이유**: 같은 사용자 흐름을 웹과 모바일에서 중복 관리하지 않고 실제 제품 클라이언트인 iOS·Android 앱에 구현과 검증을 집중하기 위해서다.

**감수할 점**: 기존 브라우저 클라이언트와 cookie 인증 API의 하위 호환성은 제공하지 않는다. custom scheme OAuth는 Expo Go나 일반 웹 브라우저가 아니라 네이티브 개발 빌드에서 검증해야 한다.

## ADR-026: 모바일 클라이언트는 iOS와 Android만 지원한다

**선택**: `/mobile`은 iOS와 Android만 지원하고 Expo Web target은 제거한다. `react-dom`, `react-native-web`, 웹 실행 script, 웹 전용 app 설정과 layout 분기를 유지하지 않는다. CI는 iOS와 Android export를 각각 검증한다. `expo-web-browser`는 웹 클라이언트용 의존성이 아니라 네이티브 카카오 로그인 창을 여는 데 사용하므로 유지한다.

**선택한 이유**: 실제 제품 대상인 iOS·Android에서 안전 영역, 키보드, SecureStore와 custom scheme OAuth를 검증하는 데 집중하고 별도의 브라우저 호환 계층을 다시 만들지 않기 위해서다.

**감수할 점**: 브라우저에서 모바일 화면을 미리 볼 수 없으며 Xcode iOS Simulator, Android Emulator 또는 실제 기기가 필요하다. Expo Go에서는 화면 확인만 가능하고 실제 카카오 로그인은 네이티브 개발 빌드에서 확인한다.
