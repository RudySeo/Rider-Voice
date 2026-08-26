# Rider Voice

Rider Voice는 픽업 과정에서 관찰한 음식점 운영 환경을 카카오 로그인 사용자가 구조화된 리뷰로 공유하고, 소비자 누구나 확인할 수 있게 하는 서비스입니다. Spring Boot API와 Expo 기반 iOS·Android 모바일 앱으로 구성됩니다.

초기 리뷰는 라이더 신분과 실제 방문이 인증된 정보가 아닙니다. 카카오 로그인은 서비스 계정 식별 수단이며 모든 공개 리뷰와 리포트는 `UNVERIFIED` 상태와 미인증 안내를 제공합니다.

## 목표 MVP

```text
Spring Security OAuth2 Client 기반 카카오 로그인
  → 내부·카카오 음식점 공개 검색
  → 픽업 장소 또는 배달 브랜드 선택·등록
  → 6개 구조화 평가와 선택 의견 작성
  → 개별 리뷰 공개
  → 서로 다른 작성자 5명부터 브랜드·장소 집계 공개
  → 의견 공개, 신고와 관리자 정정
```

하나의 실제 픽업 장소에 여러 배달 브랜드가 연결될 수 있습니다. 카카오에 없는 브랜드는 검증된 표준 주소와 상세 위치를 사용해 첫 리뷰 작성 시 등록합니다.

리뷰 평가 항목:

- 픽업 공간 청결
- 포장 안정성
- 주문 준비 상태
- 주문 확인·전달 정확성
- 직원 응대
- 라이더 존중

평가 값은 `VERY_GOOD`, `GOOD`, `NEEDS_IMPROVEMENT`, `MAJOR_IMPROVEMENT`, `NOT_OBSERVED`입니다. 같은 음식점에는 활성 리뷰를 하나만 둘 수 있고, 삭제·전체 제외된 경우 최초 작성 시각부터 90일 후 다시 작성할 수 있습니다. 구조화 평가와 최대 200자의 선택 의견은 작성·수정 즉시 공개하며, 신고된 의견은 처리 전까지 숨깁니다.

배달내역 캡처, 이미지 업로드, OCR와 배달 앱 화면 파싱은 사용하지 않습니다. 종합 별점, 음식점 순위와 인증 배지도 제공하지 않습니다.

## 기술 스택

- Kotlin, JDK 25, Gradle Kotlin DSL
- Spring Boot, Spring MVC, Spring Security OAuth2 Client
- Spring Data JPA, Hibernate, Flyway, MySQL 8.4.10
- Spring `RestClient`, Spring Cache, Caffeine
- Spring Boot Actuator, Micrometer, Prometheus와 Grafana
- OpenAPI, RFC 7807 `ProblemDetail`
- JUnit 5, MockK, MockMvc, 로컬 MySQL 통합 테스트
- 카카오 REST OAuth와 카카오 로컬 REST API
- Node 24 LTS, Expo SDK 57, React Native 0.86과 TypeScript
- Expo Router, TanStack Query, React Hook Form과 Zod
- Jest, React Native Testing Library와 Expo SecureStore

초기에는 단일 API 서버와 MySQL을 사용합니다. 백엔드 API는 Docker 이미지로 패키징해 master 대상 PR에서 검증한 뒤, 병합된 commit을 Docker Hub에 게시하고 기존 단일 EC2에 배포합니다. 운영 메트릭은 같은 EC2의 Prometheus와 Grafana 컨테이너가 수집·표시합니다. Redis, Kafka, Elasticsearch, 전체 애플리케이션용 Docker Compose, Testcontainers와 ECS는 현재 범위가 아닙니다.

## 현재 구현 상태

서버 API MVP와 Expo 기반 모바일 앱이 구현되어 있습니다.

- Spring Security OAuth2 Client 기반 카카오 로그인과 미인증 안내
- opaque access token, rotating refresh token과 logout
- 픽업 장소·배달 브랜드·외부 참조를 분리한 음식점 모델
- 카카오 장소·주소 검색, 후보 통합, 서버 재검증과 중복 방지 등록
- 6개 구조화 평가, 방문 연월, 최대 200자 의견
- 리뷰 생성·수정·삭제·내 리뷰 조회, 90일 재작성 제한과 공개 이력
- 로그인 없이 사용할 수 있는 음식점 검색·상세·리뷰 조회
- 서로 다른 작성자 5명 기준 브랜드·픽업 장소 집계와 `NOT_OBSERVED` 처리
- 의견 공개, 리뷰·음식점 신고, 관리자 처리와 음식점 이름·장소·상태 정정
- OpenAPI, RFC 7807 `ProblemDetail`, 공개·USER·ADMIN 권한 계약 테스트
- 로컬 MySQL schema·unique·동시성 회귀와 전체 test·integrationTest·build 검증
- 일회용 OAuth 교환 코드, 앱 메모리 access token과 SecureStore refresh token 회전
- 모바일 공개 검색·상세·리뷰, 로그인 고지, 네 가지 음식점 target 리뷰 작성과 내 리뷰 관리 화면
- 실행 중인 OpenAPI 기반 TypeScript 타입과 typed fetch client
- JDK 25 백엔드 Docker 이미지와 master 대상 PR 검증 성공 후 Docker Hub 게시 자동화
- 기존 EC2 자동 배포, 비공개 Prometheus와 HTTPS `/grafana/` 운영 대시보드

라이더 신분과 실제 방문 여부는 인증하지 않으며, 모든 공개 정보는 `UNVERIFIED`로 안내합니다. 배달내역 캡처, 이미지 업로드, OCR, 종합 별점, 순위와 인증 배지는 구현하지 않습니다.

관리자·신고 화면, 실제 카카오 계정을 사용하는 자동 E2E와 모바일 앱 스토어 배포는 구현 범위에 포함하지 않습니다.

## 모바일 앱

루트 Spring Boot 프로젝트는 그대로 유지하고 `/mobile`에 Expo 기반 React Native 앱을 둡니다. 공개 음식점 검색·상세·리뷰 조회, 카카오 로그인과 미인증 안내, 네 가지 음식점 target 리뷰 작성과 내 리뷰 수정·삭제를 iOS·Android 개발 빌드에서 검증합니다.

OAuth 성공 시 backend callback은 신규 사용자를 `ACTIVE` 상태로 생성하고 2분 유효 일회용 코드만 `ridervoice://auth/callback`으로 전달합니다. 앱은 코드를 access/refresh token으로 한 번 교환하고 access token은 메모리, refresh token은 SecureStore에 보관합니다.

모든 endpoint와 DTO는 실행 중인 OpenAPI `/v3/api-docs`에서 TypeScript 타입을 생성해 사용합니다. 공개 리뷰와 리포트 UI에는 API의 `verificationStatus=UNVERIFIED`와 미인증 안내를 항상 표시합니다.

목표 기획과 기술 결정은 [PRD](docs/PRD.md), [아키텍처](docs/ARCHITECTURE.md), [ADR](docs/ADR.md), [ERD](docs/ERD.md)를 참고하세요. 자세한 API 계약은 실행 중인 OpenAPI에서 확인합니다.

## 현재 API

```text
# 모바일 OAuth와 service token
GET    /api/v1/auth/mobile/oauth2/authorization/kakao
GET    /api/v1/auth/oauth2/callback/kakao
POST   /api/v1/auth/mobile/exchange
POST   /api/v1/auth/mobile/refresh
POST   /api/v1/auth/mobile/logout
GET    /api/v1/users/me

# 공개 조회
GET    /api/v1/restaurants/search
GET    /api/v1/restaurants/{restaurantId}
GET    /api/v1/restaurants/{restaurantId}/reviews

# 작성자
GET    /api/v1/addresses/search
POST   /api/v1/reviews
GET    /api/v1/users/me/reviews
PATCH  /api/v1/reviews/{reviewId}
DELETE /api/v1/reviews/{reviewId}
POST   /api/v1/reviews/{reviewId}/reports
POST   /api/v1/restaurants/{restaurantId}/reports

# 관리자
GET    /api/v1/admin/review-reports
PATCH  /api/v1/admin/review-reports/{reportId}
GET    /api/v1/admin/restaurant-reports
PATCH  /api/v1/admin/restaurant-reports/{reportId}
PATCH  /api/v1/admin/restaurants/{restaurantId}/pickup-location
```

## Swagger / OpenAPI

로컬 서버 실행 후 다음 경로를 사용합니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

endpoint와 DTO를 변경할 때 OpenAPI annotation, schema와 계약 테스트를 함께 갱신합니다.

## 로컬 실행

요구사항:

- JDK 25
- MySQL 8.4.10
- Gradle Wrapper
- Node 24와 Corepack/pnpm 11
- 로컬 모니터링 실행 시 Docker Engine과 Docker Compose v2

기본 로컬 개발에서는 API 서버와 MySQL을 로컬 프로세스로 실행합니다. Prometheus와 Grafana를 확인할 때만 `monitoring/compose.yml`을 사용하며 전체 애플리케이션용 Docker Compose와 Testcontainers는 사용하지 않습니다.

Hibernate `ddl-auto=update`로 로컬 schema를 반영합니다. 기존 `rider` 데이터베이스를 자동으로 삭제하거나 초기화하지 않으므로, 기존 데이터가 필요한 환경에서는 DROP/truncate를 실행하지 않습니다.

운영 profile은 별도 migration 계정으로 Flyway versioned migration을 적용하고 runtime 계정으로 Hibernate `ddl-auto=validate`를 수행합니다. 운영에 적용된 migration 파일은 수정하지 않고 다음 version migration을 추가합니다.

자유 의견 즉시 공개 정책 적용 전에 기존 `PENDING` 의견을 한 번 전환합니다. 아래 migration은 의견이 있으면 `PUBLISHED`, 없으면 `NONE`으로 바꾸며 다른 의견·리뷰 상태는 변경하지 않습니다.

```bash
mysql --user=<사용자> --password --database=rider < scripts/migrations/20260812-publish-pending-review-comments.sql
```

프로젝트 루트에 Git에서 제외되는 `.env`를 만들고 로컬 환경에 맞게 설정합니다.

```env
KAKAO_CLIENT_ID=your-kakao-rest-api-key
KAKAO_CLIENT_SECRET=
KAKAO_REDIRECT_URI=http://localhost:8080/api/v1/auth/oauth2/callback/kakao
KAKAO_LOCAL_REST_API_KEY=your-kakao-rest-api-key
DB_URL=jdbc:mysql://localhost:3306/rider?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
DB_USERNAME=root
DB_PASSWORD=1234
```

`KAKAO_LOCAL_REST_API_KEY`를 생략하면 `KAKAO_CLIENT_ID`의 REST API 키를 재사용합니다. IntelliJ IDEA에서는 EnvFile 플러그인을 개인 Run/Debug Configuration에 설정할 수 있습니다. 플러그인 없이도 `local` profile이 프로젝트 루트 `.env`를 선택적으로 읽습니다.

카카오 디벨로퍼스에는 다음 Redirect URI를 등록합니다.

```text
http://localhost:8080/api/v1/auth/oauth2/callback/kakao
```

```bash
./gradlew bootRun
```

백엔드 Docker 이미지는 mobile 앱을 포함하지 않는다. 로컬 컨테이너 실행이 필요하면 예제 파일을 복사한 뒤 실제 로컬 값으로 채운다. `.env.docker.local`은 Git에서 제외된다.

```bash
cp .env.docker.example .env.docker.local
docker build --platform linux/amd64 -t rider-voice-api:local .
docker run --rm --env-file .env.docker.local -p 8080:8080 rider-voice-api:local
```

Mac이나 Windows에서 호스트 MySQL에 연결할 때는 `DB_URL`의 host로 `host.docker.internal`을 사용한다. Linux에서는 실행 환경에 맞는 host 또는 Docker network 주소를 사용한다. 실제 DB·카카오 secret은 Dockerfile, build argument와 이미지에 넣지 않는다.

모바일 앱은 `/mobile`에서 실행합니다. Expo Go와 Expo Web은 공개 mock 미리보기에만 사용하고 실제 OAuth와 리뷰 변경은 `com.ridervoice.app` 개발 빌드에서 확인합니다.

```bash
cd mobile
corepack enable
pnpm install --frozen-lockfile
pnpm start
```

## 로컬 모니터링

Spring Boot API를 `localhost:8080`에서 먼저 실행한 뒤 Prometheus와 Grafana만 Compose로 시작합니다. 실제 비밀번호 파일은 Git에서 제외됩니다.

```bash
cp monitoring/.env.example monitoring/.env
# monitoring/.env의 GRAFANA_ADMIN_PASSWORD를 변경
docker compose --env-file monitoring/.env -f monitoring/compose.yml up -d
```

- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`
- Spring metric: `http://localhost:8080/actuator/prometheus`

종료할 때는 영속 volume을 보존하기 위해 `down`만 사용합니다. 데이터를 의도적으로 초기화할 때만 `down --volumes`를 사용합니다.

```bash
docker compose --env-file monitoring/.env -f monitoring/compose.yml down
```

운영 Grafana는 기존 API HTTPS 도메인의 `/grafana/`에서 관리자 로그인으로 접근합니다. Grafana `3000`과 Prometheus `9090`은 EC2 localhost binding을 유지하며 security group에 추가하지 않습니다. 최초 설치와 운영 확인은 [AWS 배포 가이드](deploy/aws/README.md)를 따릅니다.

backend endpoint나 DTO 계약이 변경되면 backend를 실행한 상태에서 모바일 TypeScript generated type을 다시 생성하고 변경된 `src/shared/api/generated.ts`를 함께 커밋합니다. 생성 파일을 직접 수정하지 않습니다.

```bash
cd mobile
pnpm run api:generate
```

## 테스트

```bash
./gradlew test
./gradlew check
./gradlew build
```

실행 중인 로컬 MySQL을 사용하는 schema·연관관계·unique·동시성 검증입니다. 로컬에서는 Docker와 Testcontainers를 시작하지 않고 로컬 MySQL이 실행 중인 경우에만 수행합니다.

```bash
./gradlew integrationTest
```

운영 migration은 기존 로컬 `rider` DB와 분리한 빈 MySQL schema에서 검증합니다. `migrationTest`는 Flyway 최초 적용, 재실행과 Hibernate validation을 확인하며 대상 schema를 자동 삭제하지 않습니다.

```bash
DB_URL='jdbc:mysql://localhost:3306/rider_migration_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
DB_USERNAME=<runtime-user> \
DB_PASSWORD=<runtime-password> \
DB_MIGRATION_USERNAME=<migration-user> \
DB_MIGRATION_PASSWORD=<migration-password> \
./gradlew migrationTest
```

mobile 회귀 검증은 Node 24에서 실행합니다.

```bash
cd mobile
pnpm run typecheck
pnpm run lint
pnpm run test
pnpm exec expo install --check
pnpm exec expo export --platform ios --output-dir /tmp/rider-voice-mobile-ios
pnpm exec expo export --platform android --output-dir /tmp/rider-voice-mobile-android
```

## 백엔드 Docker CI/CD

`feat/**`와 `feature/**` 브랜치를 push하면 master 대상 Draft PR만 자동 생성한다. Draft PR을 포함한 master 대상 PR에서는 변경 경로에 따라 backend 또는 mobile 검증을 수행한다. 필수 검증이 성공하고 최신 master 기준으로 확인된 PR만 병합할 수 있으며, backend 영향 변경이 master에 반영될 때만 Docker Hub에 `latest`와 `sha-<commit>` 태그를 게시하고 EC2에 배포한다. mobile은 백엔드 이미지와 배포 workflow에서 제외된다.

GitHub `docker-hub` Environment에는 다음 값만 등록한다.

- variable `DOCKERHUB_USERNAME`: 이미지가 게시될 Docker Hub ID
- secret `DOCKERHUB_TOKEN`: Read/Write 권한 Docker Hub PAT

Repository secret `PR_AUTOMATION_TOKEN`에는 이 저장소의 Contents Read와 Pull requests Read/Write 권한만 가진 fine-grained GitHub PAT를 등록한다. 이 token으로 Draft PR을 생성해야 PR 전체 CI가 별도 승인 없이 바로 시작된다. 값이 없으면 기본 `GITHUB_TOKEN`으로 PR을 생성하지만 최초 CI 실행은 GitHub 화면에서 승인이 필요할 수 있다.

실제 DB·카카오 값은 GitHub Actions와 Docker Hub에 저장하지 않는다. CI는 일회용 MySQL 값과 dummy provider 값을 사용하며, 실행 서버와 운영 secret store는 별도 배포 단계에서 결정한다.

## 개발 원칙

- 제품 범위가 바뀌면 코드보다 문서를 먼저 변경합니다.
- 새 기능은 실패하는 테스트부터 작성합니다.
- Controller와 OAuth handler에는 비즈니스 로직이나 JPA query를 두지 않습니다.
- 외부 API는 infrastructure adapter에서만 호출합니다.
- 현재 작업과 무관한 사용자 변경은 되돌리지 않습니다.

커밋 메시지는 Conventional Commits 형식을 사용합니다.
