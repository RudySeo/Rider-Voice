# Rider Voice

Rider Voice는 픽업 과정에서 관찰한 음식점 운영 환경을 카카오 로그인 사용자가 구조화된 리뷰로 공유하고, 소비자 누구나 확인할 수 있게 하는 API 서버입니다. 완성된 Spring Boot 서버와 주요 사용자 흐름을 검증하는 로컬 `/frontend` React SPA prototype을 함께 제공합니다.

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
- Spring Data JPA, Hibernate, MySQL 9.3
- Spring `RestClient`, Spring Cache, Caffeine
- OpenAPI, RFC 7807 `ProblemDetail`
- JUnit 5, MockK, MockMvc, 로컬 MySQL 통합 테스트
- 카카오 REST OAuth와 카카오 로컬 REST API
- Node 24 LTS, React 19, Vite 8, TypeScript와 npm
- TanStack Query, React Router, React Hook Form, Zod
- Vitest, Testing Library와 CSS Modules

초기에는 단일 API 서버와 MySQL을 사용합니다. Redis, Kafka, Elasticsearch, Docker, Testcontainers와 AWS는 현재 범위가 아닙니다.

## 현재 구현 상태

서버 API MVP와 로컬 React frontend prototype이 구현되어 있습니다.

- Spring Security OAuth2 Client 기반 카카오 로그인과 로그인 화면 약관 고지
- opaque access token, rotating refresh token과 logout
- 픽업 장소·배달 브랜드·외부 참조를 분리한 음식점 모델
- 카카오 장소·주소 검색, 후보 병합, 서버 재검증과 중복 방지 등록
- 6개 구조화 평가, 방문 연월, 최대 200자 의견
- 리뷰 생성·수정·삭제·내 리뷰 조회, 90일 재작성 제한과 공개 이력
- 로그인 없이 사용할 수 있는 음식점 검색·상세·리뷰 조회
- 서로 다른 작성자 5명 기준 브랜드·픽업 장소 집계와 `NOT_OBSERVED` 처리
- 의견 공개, 리뷰·음식점 신고, 관리자 처리와 음식점 병합·재연결
- OpenAPI, RFC 7807 `ProblemDetail`, 공개·USER·ADMIN 권한 계약 테스트
- 로컬 MySQL schema·unique·동시성 회귀와 전체 test·integrationTest·build 검증
- 60초 단일 사용 OAuth 교환 코드와 `POST /api/v1/auth/oauth2/exchange`
- 공개 검색·상세·리뷰, 로그인 고지, 네 가지 음식점 target 리뷰 작성과 내 리뷰 관리 화면
- 실행 중인 OpenAPI 기반 TypeScript 타입, typed fetch client와 refresh token 회전

라이더 신분과 실제 방문 여부는 인증하지 않으며, 모든 공개 정보는 `UNVERIFIED`로 안내합니다. 배달내역 캡처, 이미지 업로드, OCR, 종합 별점, 순위와 인증 배지는 구현하지 않습니다.

관리자·신고 화면, 실제 카카오 계정을 사용하는 자동 브라우저 E2E, Docker·AWS·production 배포는 구현 범위에 포함하지 않습니다.

## frontend prototype

루트 Spring Boot 프로젝트는 그대로 유지하고 `/frontend`에 로컬 React SPA를 둡니다. 공개 음식점 검색·상세·리뷰 조회, 카카오 로그인과 약관 고지, 네 가지 음식점 target 리뷰 작성과 내 리뷰 수정·삭제를 브라우저에서 검증합니다. 관리자·신고 UI, 운영 배포와 실제 카카오 계정을 사용하는 브라우저 E2E는 포함하지 않습니다.

OAuth 성공 시 backend callback은 access/refresh token을 URL에 전달하지 않습니다. 고정된 `http://localhost:5173/auth/callback`에 60초 단일 사용 교환 코드만 전달하고, frontend가 `POST /api/v1/auth/oauth2/exchange`를 호출해 token을 JSON으로 받습니다. 신규·약관 미동의 사용자는 이 교환에서 현재 약관 동의가 기록되고 활성화됩니다. access token은 JavaScript module memory에, refresh token은 탭 단위 `sessionStorage`에 보관합니다. 새로고침 시 저장된 refresh token으로 access token을 한 번 복구하며 `localStorage`, cookie와 URL에는 service token을 저장하지 않습니다.

모든 endpoint와 DTO는 실행 중인 OpenAPI `/v3/api-docs`에서 TypeScript 타입을 생성해 사용합니다. 공개 리뷰와 리포트 UI에는 API의 `verificationStatus=UNVERIFIED`와 미인증 안내를 항상 표시합니다.

목표 기획과 기술 결정은 [PRD](docs/PRD.md), [아키텍처](docs/ARCHITECTURE.md), [ADR](docs/ADR.md), [ERD](docs/ERD.md)를 참고하세요. 자세한 API 계약은 실행 중인 OpenAPI에서 확인합니다.

## 현재 API

```text
# OAuth와 service token
GET    /api/v1/auth/oauth2/authorization/kakao
GET    /api/v1/auth/oauth2/callback/kakao
POST   /api/v1/auth/oauth2/exchange
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
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
POST   /api/v1/admin/restaurants/{restaurantId}/merge
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
- MySQL 9.3
- Gradle Wrapper
- Node 24와 npm 11 (`frontend/.nvmrc` 사용 가능)

API 서버와 MySQL은 로컬 프로세스로 실행합니다. Docker와 Testcontainers는 사용하지 않습니다.

Hibernate `ddl-auto=update`로 로컬 schema를 반영합니다. 기존 `rider` 데이터베이스를 자동으로 삭제하거나 초기화하지 않으므로, 기존 데이터가 필요한 환경에서는 DROP/truncate를 실행하지 않습니다.

자유 의견 즉시 공개 정책 적용 전에 기존 `PENDING` 의견을 한 번 전환합니다. 아래 migration은 의견이 있으면 `PUBLISHED`, 없으면 `NONE`으로 바꾸며 다른 의견·리뷰 상태는 변경하지 않습니다.

```bash
mysql --user=<사용자> --password --database=rider < scripts/migrations/20260812-publish-pending-review-comments.sql
```

프로젝트 루트에 Git에서 제외되는 `.env`를 만들고 로컬 환경에 맞게 설정합니다.

```env
KAKAO_CLIENT_ID=your-kakao-rest-api-key
KAKAO_CLIENT_SECRET=
KAKAO_REDIRECT_URI=http://localhost:8080/api/v1/auth/oauth2/callback/kakao
FRONTEND_BASE_URL=http://localhost:5173
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

frontend를 실행하기 전에 로컬 MySQL과 backend가 실행 중이어야 합니다. Vite 개발 서버는 브라우저의 `/api` 요청을 `http://localhost:8080`으로 proxy하므로 별도 frontend API URL 설정은 필요하지 않습니다. 다른 frontend origin을 사용할 때는 backend의 `FRONTEND_BASE_URL`도 같은 origin으로 설정하고 카카오 Redirect URI는 위 backend callback URI를 그대로 유지합니다.

```bash
cd frontend
nvm use
npm ci
npm run dev
```

`npm run dev`의 기본 주소는 `http://localhost:5173`입니다.

backend endpoint나 DTO 계약이 변경되면 backend를 실행한 상태에서 TypeScript generated type을 다시 생성하고 변경된 `src/shared/api/generated.ts`를 함께 커밋합니다. 생성 파일을 직접 수정하지 않습니다.

```bash
cd frontend
npm run api:generate
```

## 테스트

```bash
./gradlew test
./gradlew check
./gradlew build
```

실행 중인 로컬 MySQL을 사용하는 schema·연관관계·unique·동시성 검증입니다. Docker, Testcontainers를 시작하지 않고 로컬 MySQL이 실행 중인 경우에만 수행합니다.

```bash
./gradlew integrationTest
```

frontend 회귀 검증은 Node 24에서 실행합니다.

```bash
cd frontend
npm run lint
npm test
npm run build
```

## 개발 원칙

- 제품 범위가 바뀌면 코드보다 문서를 먼저 변경합니다.
- 새 기능은 실패하는 테스트부터 작성합니다.
- Controller와 OAuth handler에는 비즈니스 로직이나 JPA query를 두지 않습니다.
- 외부 API는 infrastructure adapter에서만 호출합니다.
- 현재 작업과 무관한 사용자 변경은 되돌리지 않습니다.

커밋 메시지는 Conventional Commits 형식을 사용합니다.
