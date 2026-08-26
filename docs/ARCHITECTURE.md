# Rider Voice 공개 리뷰 MVP 아키텍처

## 1. 이 문서의 목적

이 문서는 Rider Voice MVP가 어떤 구조로 동작하고 각 영역이 어떤 책임을 가지는지 설명한다. 제품 목표는 `PRD.md`, 선택한 이유는 `ADR.md`, API 형식은 실행 중인 OpenAPI, 테이블과 관계는 `ERD.md`에서 관리한다.

현재 Spring Boot API 서버와 Expo 기반 React Native 모바일 앱이 구현되어 있다. Rider Voice는 카카오 로그인 사용자의 리뷰를 공개하지만 실제 라이더 신분이나 방문 여부는 인증하지 않는다.

서버가 맡는 주요 역할은 다음과 같다.

- 카카오 로그인과 Rider Voice 로그인 상태 관리
- 음식점·주소 검색과 외부 검색 결과 확인
- 실제 픽업 장소와 배달 브랜드 관리
- 리뷰 작성 제한, 공개와 집계
- 자유 의견 공개, 신고와 음식점 정정

## 2. 기술과 실행 환경

### 서버

- Kotlin, JDK 25와 Gradle Kotlin DSL
- Spring Boot, Spring MVC와 Spring Security OAuth2 Client
- Spring Data JPA, Hibernate, Flyway와 MySQL 8.4.10
- Spring `RestClient`, Spring Cache와 Caffeine
- Spring Boot Actuator, Micrometer와 Prometheus registry
- springdoc-openapi와 RFC 7807 `ProblemDetail`
- JUnit 5, MockK, MockMvc와 HTTP stub server

### 모바일 앱

- Expo SDK 57, React Native 0.86과 TypeScript
- Expo Router, TanStack Query, React Hook Form과 Zod
- React Native StyleSheet와 앱 전용 디자인 토큰
- Expo SecureStore, Jest와 React Native Testing Library
- iOS bundle identifier와 Android application id는 `com.ridervoice.app`, 고정 callback은 `ridervoice://auth/callback`
- 사용자 위치와 거리 정보는 초기 모바일 범위에 포함하지 않는다.

API 서버와 `rider` MySQL 데이터베이스는 로컬 프로세스로 실행하고 모바일 앱은 Expo 개발 빌드로 실행한다. 로컬 관측 환경만 Docker Compose로 Prometheus와 Grafana를 실행해 host의 API를 수집한다. 운영 데이터베이스는 private RDS MySQL 8.4.10이며 CI도 같은 버전으로 검증한다. 백엔드 API는 별도의 `linux/amd64` Docker 이미지로 패키징하고, GitHub Actions가 master 대상 PR을 검증한 뒤 병합된 commit을 공개 Docker Hub 저장소에 게시해 기존 Ubuntu EC2 한 대에 자동 배포한다. 모바일 앱은 운영 이미지와 배포에서 제외한다. 초기 MVP는 API 서버 한 대를 기준으로 하며 전체 애플리케이션용 Docker Compose, Testcontainers, ALB, ECS, Route 53, NAT Gateway, Redis, Kafka와 Elasticsearch는 포함하지 않는다.

로컬 비밀값은 Git에서 제외한 프로젝트 루트 `.env`로 관리한다. `local` profile만 이 파일을 선택적으로 읽고 OS·IDE 환경 변수가 있으면 그 값을 우선한다.

로컬과 일반 통합 테스트에서는 Hibernate `ddl-auto=update`로 현재 Entity를 DB에 반영한다. 운영 profile은 Flyway가 versioned migration을 먼저 적용하고 Hibernate `ddl-auto=validate`가 Entity mapping과 schema 일치를 확인한다. 운영 애플리케이션의 Hibernate schema auto-generation은 활성화하지 않는다. migration 전용 profile은 빈 MySQL에 운영 migration을 적용하고 같은 validation을 수행한다.

운영 Flyway 연결은 `DB_MIGRATION_USERNAME`과 `DB_MIGRATION_PASSWORD`, 애플리케이션 DataSource는 `DB_USERNAME`과 `DB_PASSWORD`를 사용한다. migration 계정은 schema 변경과 필요한 데이터 보정 권한을 갖고 runtime 계정은 API에 필요한 DML 권한만 갖는다. `baseline-on-migrate`와 out-of-order 적용은 허용하지 않으며 운영에 적용된 migration 파일은 수정·삭제하지 않는다.

## 3. 전체 구조

저장소 지원 코드는 실행 책임에 따라 분리한다. `/scripts`에는 phase를 순차 실행하는 Harness 실행기와 그 테스트만 두고, `/ci`에는 PR 변경 영역 감지기와 workflow·Flyway·배포·모니터링 정적 계약 테스트를 둔다. 애플리케이션 소스와 두 자동화 영역을 서로 import하지 않는다.

서버는 기능별로 나누고, 각 기능 안에서 역할을 다시 구분한다.

```text
com.ridervoice.api
├── common       # 공통 설정과 보안 기반
├── auth         # 로그인, 사용자와 세션
├── restaurant   # 픽업 장소, 브랜드와 검색
├── review       # 리뷰 작성, 공개와 집계
└── moderation   # 신고와 정정
```

요청은 다음 방향으로 흐른다.

```text
HTTP 요청 또는 로그인 callback
  -> presentation: 요청 확인과 DTO 변환
  -> application: 업무 흐름과 트랜잭션
  -> domain: 핵심 정책과 상태
  -> output port: 필요한 기능의 인터페이스
  -> infrastructure: DB 또는 외부 API 연결
```

각 영역은 다음 규칙을 지킨다.

- Controller와 로그인 handler는 요청 확인, 사용자 식별, 변환과 use case 호출만 한다.
- 작성 제한, 소유권, 상태 변경과 트랜잭션은 application service가 처리한다.
- application은 HTTP DTO나 실제 JPA·외부 API 구현을 알지 않는다.
- JPA Entity를 API 요청·응답 또는 application 결과로 직접 사용하지 않는다.
- 외부 API는 infrastructure adapter에서만 호출하고 provider 전용 형식을 밖으로 노출하지 않는다.
- Entity 관계는 필요한 자식에서 부모 방향의 단방향 `LAZY` 관계만 사용한다.
- 모든 Entity는 `BaseEntity`의 `Long IDENTITY` 기본 키를 사용한다.
- 시각은 UTC로 저장하고 RFC 3339로 반환한다. 방문 연월만 한국 시간 기준으로 검사한다.

모바일 앱은 `/mobile`에서 독립적으로 관리한다.

```text
mobile
├── app          # Expo Router 화면과 route
├── src/features # 검색, 음식점, 리뷰와 인증 흐름
├── src/shared   # API client, 디자인 토큰과 공통 UI
└── assets       # 앱에 포함하는 폰트와 정적 자산
```

모바일 앱도 DB나 카카오 API를 직접 호출하지 않고 Spring Boot `/api/v1`만 호출한다. 공개 검색은 서버가 반환한 내부 음식점과 카카오 결과를 `리뷰 있음`, `등록됨·리뷰 없음`, `카카오 장소`로 구분하고 사용자 좌표, 거리와 가까운 순을 요청하거나 추정하지 않는다. 선택 대상과 원 검색어를 리뷰 작성까지 보존한다. 모바일 API 타입은 OpenAPI에서 생성하며 생성 파일을 직접 수정하지 않는다.

네이티브 앱은 OAuth handshake를 개발 빌드의 시스템 브라우저에서 시작하고 성공하면 2분 유효·일회용 무작위 교환 코드만 `ridervoice://auth/callback`에 전달한다. backend에는 코드 원문 대신 SHA-256 hash를 저장하며 사용·만료 코드는 같은 인증 실패로 처리한다. 앱은 코드를 Rider Voice access/refresh token과 교환하고, access token은 메모리, refresh token은 SecureStore에 보관한다. Expo Go와 Expo Web은 공개 mock 미리보기만 지원하며 인증 성공을 흉내 내지 않는다.

앱은 access token을 JavaScript 메모리에만 두며 refresh token만 SecureStore에 저장한다. 앱 시작 시 refresh를 한 번 시도하고, 동시 `401`은 하나의 refresh 요청을 공유한 뒤 원 요청을 한 번만 재시도한다. 로그아웃은 서버 폐기 성공 여부와 관계없이 로컬 token을 지운다. 로그인 전 사용자가 선택한 내 활동·기존 음식점·카카오 장소 작성 의도는 허용 목록 형태로 SecureStore에 임시 저장해 callback 뒤 이어간다.

## 4. 로그인과 권한

카카오 로그인은 Spring Security OAuth2 Client를 사용한다.

```text
모바일 카카오 로그인 시작
  -> 기존 카카오 세션과 관계없이 계정 재인증과 사용자 동의
  -> callback에서 카카오 사용자 id 확인
  -> ACTIVE 사용자 생성 후 2분 유효 일회용 grant 생성
  -> ridervoice://auth/callback에 교환 코드 전달
  -> 앱이 access/refresh token으로 교환
  -> access token은 메모리, refresh token은 SecureStore에 보관
```

- 카카오 사용자 정보에서는 `id`만 외부 계정 식별값으로 사용한다.
- 모든 카카오 인가 요청에 `prompt=login`을 사용해 이전 브라우저 세션의 계정으로 자동 로그인하지 않고 계정을 다시 인증한다.
- 요청 위조 방지 값(`state`)을 위해 로그인 중에만 임시 HTTP session을 사용하고 성공·실패 후 폐기한다.
- OAuth session은 REST API 로그인 상태로 사용할 수 없다.
- 카카오 access token은 사용자 정보를 확인한 뒤 저장하지 않는다.
- 카카오 client secret이 없으면 `none`, 있으면 `client_secret_post` 방식으로 카카오에 인증한다.
- 로그인 화면은 카카오 로그인이 계정 식별 수단이며 라이더 신분이나 실제 방문을 인증하지 않는다는 점을 알린다.
- 신규 사용자는 OAuth callback을 정상 완료할 때 처음부터 `ACTIVE`로 생성한다.
- 잘못되거나 만료되거나 다시 사용한 refresh token은 같은 인증 실패로 처리한다.
- access token과 refresh token은 URL에 넣지 않는다.
- 외부 provider 오류, token, secret과 내부 예외는 사용자에게 노출하지 않는다.

활성 사용자는 15분 access token과 30일 refresh token을 사용한다. refresh token은 backend에 hash로 저장하고 앱에는 SecureStore로 보관하며 갱신할 때마다 교체한다. 로그아웃은 Rider Voice session과 로컬 token만 종료하며 카카오 로그아웃은 호출하지 않는다. 다음 로그인에서 카카오 계정을 다시 인증하므로 사용자는 다른 카카오 계정을 선택할 수 있다.

모바일은 `/api/v1/auth/mobile/oauth2/authorization/kakao`에서 같은 Spring Security OAuth2 Client 흐름을 시작한다. callback 성공 handler는 로그인 시도 매체를 임시 session에서 확인하고 모바일이면 refresh session 대신 일회용 grant를 생성한다. `/api/v1/auth/mobile/exchange`가 grant를 원자적으로 소비하면서 token pair를 발급하며, `/mobile/refresh`는 refresh token을 회전하고 `/mobile/logout`은 해당 session을 폐기한다. provider 오류·token·내부 예외는 딥링크에 싣지 않고 고정된 `error=oauth_failed`만 전달한다.

사용자 권한은 `USER`와 `ADMIN`이다. 애플리케이션에서 생성하는 사용자는 항상 `USER`이며, `ADMIN`은 운영자가 DB에서 직접 부여한다. access token을 확인할 때 DB의 현재 권한도 함께 읽어 관리자 권한 변경이 기존 토큰에도 반영되게 한다.

## 5. 음식점 구조와 등록

실제 픽업 장소와 소비자에게 보이는 배달 브랜드는 분리한다.

```text
PickupLocation 1
  └── N Restaurant 1
        └── N RestaurantPlatform
```

`PickupLocation`은 표준 주소, 정규화 주소, 상세 위치와 좌표를 가진다. 주소와 상세 위치로 만든 `location_key`를 unique로 두어 같은 장소의 중복을 줄인다.

`Restaurant`은 NFKC와 공백 정리를 거친 브랜드명, 상태와 선택적인 카카오 장소 ID를 가진다. 별도 정규화 이름은 저장하지 않고 MySQL `utf8mb4_0900_ai_ci` collation이 적용된 `(pickup_location_id, brand_name)`을 unique로 두어 대소문자를 구분하지 않는 중복을 막는다. `kakao_place_id`도 unique로 관리하며 주소로 수동 등록한 음식점은 이 값이 없다. 상태는 `ACTIVE`, `CLOSED`다. 기본 키는 각 음식점 레코드를 식별하며 사후 병합 기능은 제공하지 않는다.

초기 MVP의 장소 검색 provider는 카카오 하나이므로 별도 외부 참조 테이블을 두지 않는다. 플랫폼 정보는 선택 항목이며 음식점의 동일성이나 운영 주체를 증명하지 않는다.

공개 검색은 내부 브랜드와 카카오 결과를 합쳐 보여준다. 카카오에만 있는 후보는 내부 음식점 ID 없이 반환한다. 같은 픽업 장소의 다른 브랜드 목록은 공개하지 않는다.

폐업 음식점은 검색과 새 리뷰에서 제외하지만 기존 주소로 직접 조회하면 폐업 상태와 과거 리뷰를 보여준다. 관리자는 폐업 상태를 해제할 수 있다.

첫 리뷰에서 선택할 수 있는 음식점은 다음 네 종류다.

- 이미 등록된 음식점
- 카카오 검색으로 찾은 장소
- 등록된 픽업 장소에 추가할 새 브랜드
- 주소 검색으로 확인한 새 장소와 브랜드

카카오 장소 ID와 주소는 클라이언트 값을 그대로 믿지 않고 같은 검색을 서버에서 반복해 확인한다. 외부 확인은 DB 트랜잭션 전에 끝낸다. 저장할 때는 장소와 브랜드를 다시 조회해 중복을 확인하고, 음식점과 첫 리뷰는 한 트랜잭션에서 함께 저장한다.

동시에 같은 장소나 브랜드를 등록해 unique 충돌이 발생하면 먼저 저장된 데이터를 다시 조회해 사용한다. 최초 등록자에게 음식점의 소유권이나 수정 권한은 주지 않는다.

## 6. 리뷰 구조와 작성 제한

`Review`는 작성자, 음식점, 방문 연월, 6개 평가, 선택 의견, 공개 상태와 삭제 시각을 가진다. 자세한 필드와 관계는 `ERD.md`에서 관리한다.

활성 리뷰만 `current_slot=1`을 사용하고 `(author_user_id, restaurant_id, current_slot)`을 unique로 둔다. 삭제되거나 전체 제외된 리뷰는 slot을 비우되 기록은 남긴다. 이 구조로 같은 사용자의 음식점별 활성 리뷰를 하나로 제한한다.

작성과 변경은 다음 규칙을 따른다.

- 방문 연월은 한국 시간 기준 이번 달 또는 지난달만 선택한다.
- 활성 리뷰가 있으면 시간이 지나도 새 리뷰를 작성할 수 없고 기존 리뷰만 수정한다.
- 활성 리뷰가 없을 때는 마지막 제출의 최초 작성 시각에서 90일이 지나야 다시 작성할 수 있다.
- 삭제와 전체 제외 기록도 90일 제한과 최근 24시간 작성 횟수에 포함한다.
- 방문 연월은 수정할 수 없으며 활성 리뷰만 수정·삭제할 수 있다.
- 다른 사용자의 리뷰와 비활성 리뷰 수정 요청은 `404 Not Found`로 처리한다.

## 7. 공개 결과와 신고 처리

유효한 개별 리뷰의 6개 평가와 자유 의견은 작성·수정 직후 공개한다. 모든 공개 리뷰와 평가 결과에는 `verificationStatus=UNVERIFIED`와 미인증 안내를 포함한다. 공개 작성자 정보는 활동 기간과 공개 리뷰 수만 제공하며 고정 공개 ID나 닉네임은 사용하지 않는다.

평가 결과 상태는 다음과 같다.

- `NO_REVIEWS`: 유효 작성자 0명
- `COLLECTING`: 유효 작성자 1~4명
- `PUBLISHED`: 서로 다른 유효 작성자 5명 이상

브랜드 결과는 작성자별 활성 리뷰 하나를 사용한다. 장소 결과는 같은 작성자의 여러 브랜드 리뷰 중 생성 시각과 ID가 가장 최근인 하나만 사용한다. `NOT_OBSERVED`는 작성자 수와 값별 개수에는 포함하지만 평가 비율에서는 제외한다. 한 항목에 관찰한 값이 하나도 없으면 비율 대신 관찰값 없음 상태를 반환한다.

초기에는 별도 집계 테이블이나 batch를 만들지 않고 조회 쿼리와 application service에서 계산한다. 삭제나 전체 제외로 작성자가 5명 미만이 되면 결과는 다시 `COLLECTING`으로 바뀐다.

자유 의견 상태는 `NONE`, `PENDING`, `PUBLISHED`, `REJECTED`, `HIDDEN_REPORTED`다. `PENDING`은 기존 데이터와 API 호환을 위해 유지하지만 새 의견에는 사용하지 않는다.

- 의견을 작성하거나 수정하면 `PUBLISHED`가 된다.
- 신고로 `HIDDEN_REPORTED`인 의견은 작성자가 수정해도 신고 처리 전까지 숨김을 유지한다.
- 공개 의견이 신고되면 `HIDDEN_REPORTED`로 바꾸고 정해진 평가는 유지한다.
- 신고를 기각하면 이전 공개 상태를 복원한다.
- 의견만 문제라면 의견만 숨기고, 허위·도배라면 리뷰 전체를 `EXCLUDED`로 바꾼다.
- 한 사용자는 같은 대상에 한 번만 신고할 수 있다.

음식점 정보 신고를 승인할 때는 이름 변경, 확인된 픽업 장소 재연결 또는 폐업 중 실제 정정을 함께 처리한다. 데이터 변경, 신고 종결과 감사 기록은 한 트랜잭션으로 완료한다.

리뷰 전체 제외로 대상이 사라지면 같은 대상의 나머지 대기 신고도 원인을 남기고 종결한다. 관리자는 조사에 필요한 리뷰와 음식점 상태를 볼 수 있지만 OAuth 계정 식별값과 서비스 토큰은 조회할 수 없다.

## 8. 호출 제한과 장애 처리

- 검색어는 정리한 뒤 2~100자만 허용하고 결과는 최대 20개다.
- 카카오 성공 검색 결과는 Caffeine에 5분 동안 저장한다.
- 검색은 호출자 기준 분당 30회로 제한한다.
- 리뷰는 계정당 최근 24시간 최대 10개, 신고는 하루 최대 20개다.
- 카카오 검색 장애 시 내부 음식점 결과와 외부 검색 불가 상태를 반환한다.
- 카카오·주소 확인이 필요한 새 음식점 등록은 외부 검색을 사용할 수 없으면 실패시킨다.

캐시와 검색 호출 제한은 현재 서버 한 대의 메모리에서만 동작한다. 여러 서버가 필요해질 때 분산 저장소 도입을 새로 결정한다.

## 9. 검증 기준

자동 테스트에서는 다음 경계를 우선 확인한다.

- OAuth 요청 위조 방지, 일회용 모바일 교환 코드와 토큰 회전
- 음식점·장소·카카오 장소 ID의 중복 및 동시 등록
- 활성 리뷰 중복, 삭제·전체 제외 후 90일 제한
- 브랜드·장소 작성자 4명과 5명 경계 및 `NOT_OBSERVED`
- 의견 즉시 공개, 신고, 리뷰 제외와 음식점 정정의 함께 성공·실패 여부
- 공개·사용자·관리자 권한과 OpenAPI 응답 형식

JPA schema, FK, index와 unique 제약은 실행 중인 로컬 MySQL로 확인한다. 카카오 API는 실제 호출 대신 stub server로 성공, timeout, 호출 제한과 잘못된 응답을 검증한다.

GitHub Actions의 master 대상 PR workflow는 변경 파일을 먼저 backend와 mobile 영역으로 분류한다. workflow 자체에는 event-level `paths`를 적용하지 않고 항상 최종 `PR CI gate` 상태를 남겨 branch protection의 필수 검사가 대기 상태가 되지 않게 한다. 문서만 바뀐 PR에서는 두 애플리케이션 검증을 모두 건너뛴다.

- backend 영향 변경은 JDK 25와 Gradle Wrapper로 단위·계약 테스트와 backend build를 수행한다.
- backend 영향 변경은 격리된 빈 MySQL 8.4.10 service container에 Flyway migration을 적용하고 Hibernate validation, 재실행 안전성과 schema 주요 제약을 확인한다.
- migration이 적용된 같은 MySQL에서 schema·unique·동시성 통합 테스트를 수행하고, 같은 schema와 CI 전용 dummy provider 설정으로 만든 Docker 이미지를 운영 profile로 실행해 `/actuator/health`를 확인한다.
- API, Prometheus와 Grafana를 격리된 Docker network에서 실행해 Prometheus target과 Grafana health를 확인한다.
- mobile 변경은 Node.js 24, Corepack과 고정된 pnpm을 사용해 frozen lockfile 설치, typecheck, Jest, Expo lint, Expo 의존성 호환성과 iOS·Android export를 검증한다.
- 변경 영역별 job 결과를 최종 `PR CI gate`가 모으며, 이 상태가 성공한 PR만 master에 병합할 수 있게 보호한다.
- master push에서는 백엔드 영향 경로가 바뀐 경우에만 전체 검증을 반복하지 않고 `linux/amd64` 이미지를 Docker Hub에 게시하고 EC2에 배포한다. mobile 배포는 이 workflow의 범위가 아니다.

실제 DB·카카오 secret은 Docker build와 이미지에 포함하지 않는다. CI는 일회용 DB 값과 dummy provider 값을 사용한다. Docker Hub PAT는 GitHub Environment secret으로, 자동 Draft PR용 fine-grained GitHub PAT는 최소 저장소 권한의 Repository secret으로 전달한다. 운영 secret은 아래 운영 배포 경계에 정의한 SSM Parameter Store에서만 읽는다.

## 10. 운영 배포 경계

운영 요청과 배포는 다음 경로로 흐른다.

```text
Internet
  -> Elastic IP와 <EIP>.sslip.io
  -> Nginx :80/:443 (Let's Encrypt TLS, HTTP -> HTTPS)
  -> Docker API 127.0.0.1:8080
  -> private RDS MySQL 8.4.10:3306 (TLS VERIFY_IDENTITY)

관리자 browser
  -> Nginx HTTPS /grafana/
  -> Grafana 127.0.0.1:3000 (로그인 필요)

운영자 진단
  -> SSM port forwarding
  -> Prometheus 127.0.0.1:9090
  -> Docker network의 API:8080/actuator/prometheus

master push
  -> Docker Hub sha-<12자리> 이미지 게시
  -> GitHub OIDC로 production deploy role 획득
  -> SSM Run Command
  -> 정확한 master commit의 release script와 monitoring 자산 다운로드
  -> EC2의 deploy script가 새 API container 기동과 health check
  -> Prometheus·Grafana Compose 갱신과 내부 health check
```

- EC2의 8080과 RDS의 3306은 인터넷에 공개하지 않는다. RDS는 EC2 security group에서만 접근한다.
- Grafana 3000과 Prometheus 9090은 EC2 localhost에만 bind하고 security group ingress를 추가하지 않는다. Grafana만 기존 HTTPS 도메인의 `/grafana/`에서 Nginx reverse proxy로 제공하며 Prometheus UI는 SSM port forwarding으로만 접근한다.
- API, Prometheus와 Grafana는 `rider-voice-observability` Docker network를 공유한다. Prometheus는 container DNS로 API의 `/actuator/prometheus`를 15초마다 수집한다.
- Prometheus와 Grafana는 운영 전용 Docker Compose가 관리한다. API는 새 image health check와 자동 rollback을 유지하기 위해 별도 배포 script가 관리한다.
- release script는 GitHub가 전달한 40자리 master commit SHA를 검증하고 해당 commit의 배포 자산만 사용한다. monitoring 갱신은 기존 `.env`, Grafana secret과 named volume을 보존하며, health check 실패 시 직전 Compose·provisioning 자산을 복원한다.
- Nginx는 외부 `/actuator/prometheus` 요청을 `404`로 차단한다. metric에는 사용자 ID, 음식점 ID, 검색어, token과 예외 메시지를 label로 사용하지 않는다.
- Prometheus 데이터는 7일과 2GB 중 먼저 도달하는 한도로 보관하고 Grafana 데이터와 함께 Docker volume에 저장한다.
- Grafana anonymous access와 사용자 가입은 비활성화하고 HTTPS secure cookie와 로그인 시도 제한을 적용한다. 초기 관리자 비밀번호는 SSM SecureString을 EC2 root 전용 파일로 내려받고 Compose secret으로 mount한다.
- Nginx가 외부에서 들어온 `X-Forwarded-For`를 폐기하고 직접 연결한 client의 `$remote_addr`로 덮어쓴다. API는 localhost Nginx만 접근할 수 있고 운영 profile만 forwarded header를 신뢰하므로 검색 호출 제한에 검증된 client IP가 사용된다.
- Nginx는 `Host`, `X-Forwarded-Proto`와 `X-Forwarded-For`를 API에 전달한다. ALB나 CloudFront가 앞에 추가되면 trusted proxy 정책을 새로 결정한다.
- 운영 secret은 SSM Parameter Store의 `/rider-voice/prod/` 아래에서 관리한다. EC2 instance role만 해당 경로를 복호화한다. API secret은 root 전용 임시 env 파일로 만들고 Grafana 비밀번호는 root 전용 Compose secret 파일로 mount하며 GitHub와 Docker image는 값을 읽지 않는다.
- RDS runtime 계정은 DML만, migration 계정은 Flyway에 필요한 DDL과 DML만 갖고 둘 다 TLS를 강제한다. RDS CA truststore는 EC2에 두고 container에 read-only로 mount한 뒤 MySQL Connector/J의 JDBC 연결에만 적용한다. JVM의 기본 truststore는 공개 HTTPS provider 인증에 사용하며 RDS 전용 truststore로 전역 교체하지 않는다.
- 새 container는 commit 기반 불변 태그로 실행하고 `/actuator/health`가 제한 시간 안에 `UP`이 아니면 이전 image를 다시 실행한다. monitoring은 Prometheus readiness, Grafana health와 API target 수집을 확인한 뒤 완료한다. Flyway schema와 monitoring named volume의 내부 데이터는 자동 rollback하지 않으므로 변경은 이전 application·data와 호환되게 유지한다.
- EC2는 SSM Session Manager로 운영하고 확인 후 SSH ingress를 제거한다. GitHub는 장기 AWS access key 대신 repository와 `production` environment가 제한된 OIDC role을 사용한다.
- 모바일 앱 스토어 배포 전에는 공개 API, OpenAPI와 health endpoint만 운영 검증 범위다. 실제 카카오 로그인은 네이티브 개발 빌드에서 별도로 검증한다.
- Prometheus, Grafana와 API가 같은 EC2에 있으므로 EC2 자체 장애 시 관측 화면도 중단된다. 외부 장애 감지나 고가용성이 필요해지면 별도 host 또는 관리형 관측 서비스를 새로 결정한다.
