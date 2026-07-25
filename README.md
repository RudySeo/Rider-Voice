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

## 현재 구현과 다음 변경

현재 코드에 구현된 내용:

- Spring Boot/Kotlin과 MySQL/JPA 기반
- 공통 보안, 오류 응답과 OpenAPI
- 직접 구현한 카카오 OAuth 로그인
- onboarding token, opaque access token과 rotating refresh session
- 카카오 장소 검색과 단일 `Restaurant.kakaoPlaceId` 기반 지연 등록

새 목표 문서에 따라 다음 순서로 교체·확장합니다.

1. 직접 구현한 카카오 OAuth를 Spring Security OAuth2 Client로 전환
2. 단일 음식점을 픽업 장소·배달 브랜드·외부 참조 구조로 교체
3. 공개 리뷰 이력과 90일 작성 제한 구현
4. 공개 검색·상세·리뷰와 작성자 5명 집계 구현
5. 의견 검수, 신고, 관리자 처리와 음식점 병합 구현

목표 기획과 기술 계약은 [PRD](docs/PRD.md), [아키텍처](docs/ARCHITECTURE.md), [ADR](docs/ADR.md), [API 계약](docs/API_SPEC.md)을 참고하세요.

## 목표 API

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

현재 구현 API와 목표 API의 차이는 [API 계약](docs/API_SPEC.md#10-현재-구현과-교체-대상)에 정리되어 있습니다.

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

목표 모델 적용으로 기존 개발 schema를 초기화해야 할 때만 다음 명령을 한 번 사용합니다. 기존 데이터가 삭제되는 명령이므로 실행 전 필요한 데이터를 확인해야 합니다.

```bash
mysql.server start
mysql -u root -p -e "DROP DATABASE IF EXISTS rider; CREATE DATABASE rider CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

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

카카오 디벨로퍼스에는 다음 목표 Redirect URI를 등록합니다.

```text
http://localhost:8080/api/v1/auth/oauth2/callback/kakao
```

현재 코드가 Spring Security OAuth2 Client로 전환되기 전까지는 기존 callback 경로를 사용합니다. 목표 인증 구현과 설정 변경은 같은 코드 변경에서 적용해야 합니다.

```bash
./gradlew bootRun
```

## 테스트

```bash
./gradlew test
./gradlew check
./gradlew build
```

실행 중인 로컬 MySQL을 사용하는 schema·연관관계·unique·동시성 검증:

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
