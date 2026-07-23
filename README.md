# Rider Voice

Rider Voice MVP는 카카오 로그인 사용자가 음식점을 찾아 픽업 과정의 경험을 구조화된 비공개 리뷰로 기록하는 API 서버입니다.

현재 MVP는 리뷰 작성 흐름에 집중합니다. 방문 증빙이 없으므로 작성된 리뷰는 인증 리뷰가 아니며 작성자 본인만 조회할 수 있습니다.

## MVP 흐름

```text
카카오 로그인과 약관 동의
  → 음식점 검색
  → 카카오 장소 선택
  → 내부 음식점 지연 등록
  → 비공개 리뷰 작성
  → 내 리뷰 조회·수정·삭제
```

음식점은 카카오 장소 ID로 식별합니다. 내부에 없는 장소를 선택하면 클라이언트가 원래 검색어와 장소 ID를 보내고, 서버가 같은 카카오 검색을 반복해 일치하는 provider 결과로만 등록합니다. 최초 선택 사용자는 음식점의 소유자나 관리자가 아닙니다.

리뷰는 다음 6개 필수 항목과 최대 200자의 선택 의견으로 구성합니다.

- 픽업 공간 청결
- 포장 안정성
- 주문 준비 상태
- 주문 확인·전달 정확성
- 직원 응대
- 라이더 존중

평가 값은 `VERY_GOOD`, `GOOD`, `NEEDS_IMPROVEMENT`, `MAJOR_IMPROVEMENT`, `NOT_OBSERVED` 중 하나입니다. 사용자 한 명은 음식점 한 곳에 리뷰 하나만 작성할 수 있습니다.

## 기술 스택

- Backend: Spring Boot, Kotlin, Spring MVC, Spring Security
- Persistence: MySQL 9.3, Spring Data JPA, Hibernate
- API: REST, OpenAPI, RFC 7807 `ProblemDetail`
- External: Kakao REST OAuth, Kakao Local REST API
- Test: JUnit 5, MockK, 로컬 MySQL 통합 테스트

## 현재 구현 상태

구현됨:

- Spring Boot/Kotlin 프로젝트와 MySQL/JPA/Hibernate 기반
- 공통 보안, 오류 응답과 OpenAPI 기반
- 카카오 OAuth 로그인
- 신규 사용자의 일회용 onboarding token과 약관 동의
- access token, rotating refresh session, 갱신과 로그아웃
- 현재 사용자 조회 API
- 카카오 로컬 음식점 검색과 내부 음식점 후보 병합
- 원래 검색어 재검증 기반의 선택 장소 멱등 등록
- 음식점 API의 `ROLE_USER` 권한, Bean Validation과 OpenAPI 계약

다음 구현 대상:

1. 로그인 사용자의 비공개 리뷰 CRUD
2. MVP API 권한, unique 제약과 OpenAPI 회귀 검증

방문 증빙, OCR, `WriteGrant`, 공개 리포트, 관리자 기능, 사용자 앱과 배포 인프라는 현재 범위가 아닙니다.

## API

### 구현됨

```text
GET  /api/v1/auth/kakao/authorize
GET  /api/v1/auth/kakao/callback
POST /api/v1/auth/consents
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/users/me
GET  /api/v1/restaurants/search
POST /api/v1/restaurants
```

### MVP 다음 구현 대상

```text
POST   /api/v1/reviews
GET    /api/v1/reviews
GET    /api/v1/reviews/{reviewId}
PATCH  /api/v1/reviews/{reviewId}
DELETE /api/v1/reviews/{reviewId}
```

음식점 API와 이후 리뷰 API는 `ROLE_USER`를 요구합니다. 사용자는 자신의 리뷰만 조회·수정·삭제할 수 있으며 중복 리뷰 생성은 `409 Conflict`, 타인 리뷰 접근은 `404 Not Found`로 처리합니다.

## Swagger / OpenAPI

로컬 서버 실행 후 다음 경로에서 API 계약을 확인할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

endpoint 또는 DTO를 변경할 때 Controller의 OpenAPI annotation, 공개 DTO schema와 계약 테스트를 함께 갱신합니다.

## 로컬 실행

### 요구사항

- JDK 21
- MySQL 9.3
- Gradle Wrapper

API 서버와 MySQL은 로컬 프로세스로 실행합니다. Docker, Docker Compose와 Testcontainers는 현재 사용하지 않습니다.

```bash
mysql.server start
mysql -u root -p -e "DROP DATABASE IF EXISTS rider; CREATE DATABASE rider CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

위 명령은 기존 개발 schema를 `BIGINT AUTO_INCREMENT` 기준으로 다시 시작할 때 한 번만 사용한다. 이후에는 Hibernate `ddl-auto=update`가 Entity mapping을 로컬 schema에 반영하므로 재시작할 때 DB를 삭제하지 않는다.

`.env.example`을 복사하고 로컬 환경에 맞게 수정합니다. 실제 secret은 커밋하지 않습니다.

```bash
cp .env.example .env
```

```env
KAKAO_CLIENT_ID=your-kakao-rest-api-key
KAKAO_CLIENT_SECRET=
KAKAO_REDIRECT_URI=http://localhost:8080/api/v1/auth/kakao/callback
KAKAO_LOCAL_REST_API_KEY=your-kakao-rest-api-key
KAKAO_LOCAL_BASE_URL=https://dapi.kakao.com
KAKAO_LOCAL_TIMEOUT=2s
DB_URL="jdbc:mysql://localhost:3306/rider?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
DB_USERNAME=root
DB_PASSWORD=1234
```

`KAKAO_LOCAL_REST_API_KEY`에는 카카오 디벨로퍼스 앱의 REST API 키를 설정합니다. `KAKAO_LOCAL_BASE_URL`과 `KAKAO_LOCAL_TIMEOUT`은 각각 위 기본값을 사용하므로 로컬에서 별도 조정이 필요한 경우에만 변경합니다.

카카오 디벨로퍼스에 다음 Redirect URI를 등록합니다.

```text
http://localhost:8080/api/v1/auth/kakao/callback
```

```bash
set -a
source .env
set +a
./gradlew bootRun
```

## 테스트

```bash
./gradlew test
./gradlew check
./gradlew build
```

기본 검증은 DB 통합 테스트를 제외합니다. 실행 중인 로컬 MySQL을 사용하는 JPA schema와 연관관계 검증은 별도로 실행합니다.

```bash
./gradlew integrationTest
```

## 프로젝트 구조

```text
src/main/kotlin/com/ridervoice/api
├── common
├── auth
├── restaurant
└── review
```

각 기능은 가벼운 헥사고날 구조를 사용합니다.

```text
feature
├── presentation
│   ├── FeatureController.kt
│   ├── dto
│   │   ├── FeatureRequests.kt
│   │   └── FeatureResponses.kt
│   └── FeatureHttpMapper.kt
├── application
│   ├── port
│   │   ├── in/FeatureUseCase.kt
│   │   └── out/FeatureRepository.kt
│   ├── model
│   │   ├── FeatureCommands.kt
│   │   └── FeatureResults.kt
│   └── FeatureService.kt
├── domain
└── infrastructure
```

Controller는 presentation DTO를 application command로 변환해 input port를 호출하고, application result를 response DTO로 변환합니다. repository와 외부 provider interface는 application output port에 두고 infrastructure adapter가 구현합니다. 모든 class에 interface를 만들지 않고 외부 경계에만 port를 둡니다.

자세한 현재 범위와 결정은 [PRD](docs/PRD.md), [아키텍처](docs/ARCHITECTURE.md), [ADR](docs/ADR.md)을 참고하세요. 브랜치 전략은 [Git Flow](docs/GIT_FLOW.md)에 정의되어 있습니다.
