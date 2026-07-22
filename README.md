# Rider Voice

배달 완료 화면으로 음식점 방문을 확인한 사용자가 픽업 과정에서 직접 관찰한 운영 경험을 기록하고, 음식점별 리포트로 제공하는 서비스입니다.

## 해결하려는 문제

- 기존 리뷰는 맛과 배달 만족도 중심이라 픽업 공간, 포장, 주문 준비 상태를 알기 어렵습니다.
- 경험담은 실제 방문 여부를 확인하기 어렵고 작성자의 신원이 노출될 수 있습니다.
- 음식점은 단편적인 비난이 아닌 반복 관찰 기반의 개선 신호와 공식 정정 절차가 필요합니다.

Rider Voice는 계정의 라이더 자격을 별도로 심사하지 않습니다. 대신 각 리뷰마다 배달 완료 화면을 통한 방문 인증을 요구하며, 공개 화면에는 작성자의 카카오 계정 정보를 노출하지 않습니다.

## 핵심 사용자 흐름

```text
카카오 로그인
  → 필수 약관 동의
  → 배달 완료 화면 업로드
  → OCR 방문 인증
  → 1회용 WriteGrant 발급
  → 구조화 리뷰 작성
  → 음식점별 운영 리포트 집계
```

리뷰는 픽업 과정에서 관찰 가능한 다음 항목을 기록합니다.

- 픽업 공간 청결
- 포장 안정성
- 주문 준비 상태
- 주문 확인·전달 정확성
- 직원 응대
- 라이더 존중

## 기술 스택

- Backend: Spring Boot, Kotlin, Spring MVC, Spring Security
- Persistence: PostgreSQL, Spring Data JPA, Hibernate, Flyway
- API: REST, OpenAPI, RFC 7807 ProblemDetail
- External: Kakao REST OAuth, Kakao Local API, NAVER CLOVA OCR
- Test: JUnit 5, MockK, Testcontainers
- Client 예정: React Native iOS/Android

서버 API와 OpenAPI 계약을 먼저 완성한 뒤 React Native 앱을 개발합니다.

## 현재 구현 범위

현재 `develop`으로 통합 예정인 인증 기반에는 다음이 구현되어 있습니다.

- Spring Boot/Kotlin 프로젝트 기반
- PostgreSQL/JPA/Flyway persistence 기반
- 사용자·OAuth 계정·서비스 세션·OAuth state 도메인
- Kakao OAuth authorization URL 생성
- Kakao authorization code 교환
- Kakao 사용자 정보 조회 adapter
- 카카오 로그인 callback 기본 흐름
- 약관 동의에 따른 사용자 활성화
- access/refresh token 발급·갱신·로그아웃 API
- 현재 사용자 조회 API

현재 access token은 로컬 개발을 위한 메모리 저장 방식입니다. 운영 배포 전 JWT 검증과 영속 세션 저장소로 교체해야 합니다.

## API

```text
GET  /api/v1/auth/kakao/authorize
GET  /api/v1/auth/kakao/callback
POST /api/v1/auth/consents
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/users/me
```

## 로컬 실행

### 사전 요구사항

- JDK 21
- PostgreSQL
- Gradle Wrapper

Docker는 현재 기본 실행에 필요하지 않습니다. 로컬 PostgreSQL을 실행하고 `rider_voice` 데이터베이스를 준비합니다.

### 환경변수

`.env.example`을 참고해 루트에 `.env`를 만듭니다. 실제 키는 커밋하지 않습니다.

```env
KAKAO_CLIENT_ID=your-kakao-rest-api-key
KAKAO_CLIENT_SECRET=
KAKAO_REDIRECT_URI=http://localhost:8080/api/v1/auth/kakao/callback
AUTH_JWT_SECRET=local-only-secret
DB_URL=jdbc:postgresql://localhost:5432/rider_voice
DB_USERNAME=rider_voice
DB_PASSWORD=your-password
```

카카오 디벨로퍼스에 다음 Redirect URI를 등록해야 합니다.

```text
http://localhost:8080/api/v1/auth/kakao/callback
```

`.env`를 셸에 로드한 뒤 서버를 실행합니다.

```bash
set -a
source .env
set +a
./gradlew bootRun
```

### 테스트

```bash
./gradlew test
./gradlew check
./gradlew build
```

PostgreSQL Testcontainers 통합 테스트는 Docker 환경을 준비한 뒤 다음 명령으로 별도 실행합니다.

```bash
./gradlew integrationTest
```

## 프로젝트 구조

```text
src/main/kotlin/com/ridervoice/api
├── common        # 공통 persistence, security, error
├── auth          # 카카오 인증과 서비스 세션
├── restaurant    # 음식점과 지역 제한
├── visit         # 방문 증빙과 OCR
├── review        # WriteGrant와 리뷰
├── report        # 음식점 리포트 집계
├── moderation    # 관리자 검수와 신고
└── correction    # 음식점 정정 요청
```

기능 패키지는 `presentation`, `application`, `domain`, `infrastructure` 계층으로 나눕니다.

## 개발 로드맵

1. 카카오 인증·인가와 OpenAPI 계약 안정화
2. 음식점 검색 및 파일럿 지역 제한
3. 증빙 업로드와 CLOVA OCR 방문 인증
4. WriteGrant와 구조화 리뷰
5. 리포트 집계
6. 관리자 검수·신고·정정 API
7. 보안·통합·성능 검증
8. React Native 클라이언트

자세한 제품·아키텍처·의사결정 문서는 [`docs/`](./docs)를 참고하세요.

## Git Flow

브랜치 전략은 [`docs/GIT_FLOW.md`](./docs/GIT_FLOW.md)에 정의되어 있습니다.

- `master`: 운영·배포 기준
- `develop`: 통합 개발 기준
- `feature/*`: 기능 개발
- `release/*`: 릴리스 준비
- `hotfix/*`: 운영 긴급 수정

새 작업은 `develop`에서 feature 브랜치를 만들어 시작합니다.

```bash
git checkout develop
git pull origin develop
git checkout -b feature/<short-name>
```
