# Rider Voice

Rider Voice는 픽업 과정에서 관찰한 음식점 운영 환경을 카카오 로그인 사용자가 구조화된 리뷰로 공유하고, 소비자 누구나 확인할 수 있게 하는 API 서버입니다.

초기 리뷰는 라이더 신분과 실제 방문이 인증된 정보가 아닙니다. 카카오 로그인은 서비스 계정 식별 수단이며 모든 공개 리뷰와 리포트는 `UNVERIFIED` 상태와 미인증 안내를 제공합니다.

## 목표 MVP

```text
Spring Security OAuth2 Client 기반 카카오 로그인
  → 내부·카카오 음식점 공개 검색
  → 픽업 장소 또는 배달 브랜드 선택·등록
  → 6개 구조화 평가와 선택 의견 작성
  → 개별 리뷰 공개
  → 서로 다른 작성자 5명부터 브랜드·장소 집계 공개
  → 의견 검수, 신고와 관리자 정정
```

하나의 실제 픽업 장소에 여러 배달 브랜드가 연결될 수 있습니다. 카카오에 없는 브랜드는 검증된 표준 주소와 상세 위치를 사용해 첫 리뷰 작성 시 등록합니다.

리뷰 평가 항목:

- 픽업 공간 청결
- 포장 안정성
- 주문 준비 상태
- 주문 확인·전달 정확성
- 직원 응대
- 라이더 존중

평가 값은 `VERY_GOOD`, `GOOD`, `NEEDS_IMPROVEMENT`, `MAJOR_IMPROVEMENT`, `NOT_OBSERVED`입니다. 같은 음식점에는 마지막 제출 후 90일마다 새 리뷰를 작성할 수 있습니다. 구조화 평가는 즉시 공개하고 최대 200자의 선택 의견은 관리자 승인 후 공개합니다.

배달내역 캡처, 이미지 업로드, OCR와 배달 앱 화면 파싱은 사용하지 않습니다. 종합 별점, 음식점 순위와 인증 배지도 제공하지 않습니다.

## 기술 스택

- Kotlin, JDK 21, Gradle Kotlin DSL
- Spring Boot, Spring MVC, Spring Security OAuth2 Client
- Spring Data JPA, Hibernate, MySQL 9.3
- Spring `RestClient`, Spring Cache, Caffeine
- OpenAPI, RFC 7807 `ProblemDetail`
- JUnit 5, MockK, MockMvc, 로컬 MySQL 통합 테스트
- 카카오 REST OAuth와 카카오 로컬 REST API

초기에는 단일 API 서버와 MySQL을 사용합니다. Redis, Kafka, Elasticsearch, Docker, Testcontainers와 AWS는 현재 범위가 아닙니다.

## 현재 구현 상태

Phase 0~9의 MVP 구현은 완료되어 `master`에 반영되어 있습니다. 현재 구현된 내용은 다음과 같습니다.

- Spring Security OAuth2 Client 기반 카카오 로그인과 약관 동의
- onboarding token, opaque access token, rotating refresh token, logout
- 픽업 장소·배달 브랜드·외부 참조를 분리한 음식점 모델
- 카카오 장소·주소 검색, 후보 병합, 서버 재검증과 중복 방지 등록
- 6개 구조화 평가, 방문 연월, 최대 200자 의견
- 리뷰 생성·수정·삭제·내 리뷰 조회, 90일 재작성 제한과 공개 이력
- 로그인 없이 사용할 수 있는 음식점 검색·상세·리뷰 조회
- 서로 다른 작성자 5명 기준 브랜드·픽업 장소 집계와 `NOT_OBSERVED` 처리
- 의견 검수, 리뷰·음식점 신고, 관리자 처리와 음식점 병합·재연결
- OpenAPI, RFC 7807 `ProblemDetail`, 공개·USER·ADMIN 권한 계약 테스트

라이더 신분과 실제 방문 여부는 인증하지 않으며, 모든 공개 정보는 `UNVERIFIED`로 안내합니다. 배달내역 캡처, 이미지 업로드, OCR, 종합 별점, 순위와 인증 배지는 구현하지 않습니다.

현재 Phase 10에서는 API 보안·계약 회귀 테스트가 완료되었습니다. 로컬 MySQL 스키마·동시성 검증과 전체 최종 회귀 검증은 아직 남아 있으며, Harness 실행 한도 때문에 일시 중단된 상태입니다.

목표 기획과 기술 계약은 [PRD](docs/PRD.md), [아키텍처](docs/ARCHITECTURE.md), [ADR](docs/ADR.md), [API 계약](docs/API_SPEC.md)을 참고하세요.

## 현재 API

```text
# OAuth와 service token
GET    /api/v1/auth/oauth2/authorization/kakao
GET    /api/v1/auth/oauth2/callback/kakao
POST   /api/v1/auth/consents
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
GET/PATCH /api/v1/admin/review-comments/**
GET/PATCH /api/v1/admin/review-reports/**
GET/PATCH /api/v1/admin/restaurant-reports/**
POST      /api/v1/admin/restaurants/{restaurantId}/merge
PATCH     /api/v1/admin/restaurants/{restaurantId}/pickup-location
```

## Swagger / OpenAPI

로컬 서버 실행 후 다음 경로를 사용합니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

endpoint와 DTO를 변경할 때 OpenAPI annotation, schema와 계약 테스트를 함께 갱신합니다.

## 로컬 실행

요구사항:

- JDK 21
- MySQL 9.3
- Gradle Wrapper

API 서버와 MySQL은 로컬 프로세스로 실행합니다. Docker와 Testcontainers는 사용하지 않습니다.

Hibernate `ddl-auto=update`로 로컬 schema를 반영합니다. 기존 `rider` 데이터베이스를 자동으로 삭제하거나 초기화하지 않으므로, 기존 데이터가 필요한 환경에서는 DROP/truncate를 실행하지 않습니다.

프로젝트 루트에 Git에서 제외되는 `.env`를 만들고 로컬 환경에 맞게 설정합니다.

```env
KAKAO_CLIENT_ID=your-kakao-rest-api-key
KAKAO_CLIENT_SECRET=
KAKAO_REDIRECT_URI=http://localhost:8080/api/v1/auth/oauth2/callback/kakao
KAKAO_LOCAL_REST_API_KEY=your-kakao-rest-api-key
KAKAO_LOCAL_BASE_URL=https://dapi.kakao.com
KAKAO_LOCAL_TIMEOUT=2s
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

## 개발 원칙

- 제품 범위가 바뀌면 코드보다 문서를 먼저 변경합니다.
- 새 기능은 실패하는 테스트부터 작성합니다.
- Controller와 OAuth handler에는 비즈니스 로직이나 JPA query를 두지 않습니다.
- 외부 API는 infrastructure adapter에서만 호출합니다.
- 현재 작업과 무관한 사용자 변경은 되돌리지 않습니다.

브랜치와 커밋 규칙은 [Git Flow](docs/GIT_FLOW.md)를 참고하세요.
