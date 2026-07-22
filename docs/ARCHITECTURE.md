# Rider Voice 아키텍처

## 1. 개요

Rider Voice는 API 서버를 먼저 완성한 뒤 React Native 클라이언트를 연결하는 서버 우선 프로젝트다. 서버는 인증, 음식점, 방문 증빙, OCR, 글쓰기 권한, 리뷰, 리포트 집계, 검수와 정정 요청의 유일한 업무 규칙 소유자다.

React Native 앱은 서버가 발행한 OpenAPI 계약만 사용하며 데이터베이스, S3, CLOVA OCR 또는 카카오 로컬 API를 직접 호출하지 않는다.

## 2. 기술 스택

### 서버

- Spring Boot
- Kotlin
- Gradle Kotlin DSL
- Spring MVC
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway
- Bean Validation
- springdoc-openapi
- JUnit 5, Kotest 또는 AssertJ, MockK, Testcontainers

### 외부 서비스 및 인프라

- 카카오 REST OAuth: 소셜 로그인
- 카카오 로컬 REST API: 음식점 검색과 장소 식별
- NAVER Cloud CLOVA OCR: 배달 완료 화면 문자 인식
- Amazon RDS PostgreSQL
- Amazon S3 비공개 버킷
- Amazon SQS와 Dead Letter Queue
- Amazon ECS Fargate
- AWS KMS, Secrets Manager, CloudWatch, WAF

### 후속 클라이언트

- React Native
- iOS와 Android 동시 지원
- 서버 API와 OpenAPI 계약이 안정화된 후 개발
- Expo 또는 Bare React Native 선택은 클라이언트 착수 시 별도 ADR로 결정

## 3. 프로젝트 구조

서버 코드는 기능 중심 패키지와 기능 내부 계층을 함께 사용한다.

```text
src/
├── main/
│   ├── kotlin/com/ridervoice/api/
│   │   ├── RiderVoiceApplication.kt
│   │   ├── common/
│   │   │   ├── config/
│   │   │   ├── error/
│   │   │   ├── security/
│   │   │   ├── persistence/
│   │   │   └── time/
│   │   ├── auth/
│   │   ├── restaurant/
│   │   ├── visit/
│   │   ├── review/
│   │   ├── report/
│   │   ├── moderation/
│   │   └── correction/
│   └── resources/
│       ├── application.yml
│       └── db/migration/
└── test/
    └── kotlin/com/ridervoice/api/
```

각 기능 패키지는 필요한 범위에서 다음 계층을 사용한다.

```text
feature/
├── presentation/      # Controller, request/response DTO
├── application/       # use case, transaction boundary
├── domain/            # entity behavior, value object, policy
└── infrastructure/    # JPA repository, provider adapter
```

## 4. 계층 규칙

- Controller는 HTTP 요청 검증, application use case 호출, 응답 변환만 담당한다.
- 비즈니스 규칙과 상태 전이는 application 및 domain 계층에 둔다.
- 트랜잭션 경계는 application service에 둔다.
- JPA Entity를 API 요청 또는 응답 DTO로 직접 사용하지 않는다.
- domain/application 계층은 카카오, CLOVA, S3 같은 외부 SDK 타입에 의존하지 않는다.
- 외부 연동은 port interface와 infrastructure adapter로 격리한다.
- Repository interface는 기능 패키지 내부에 두고 구현 세부사항을 외부로 노출하지 않는다.
- 기능 간 호출은 공개 application interface 또는 식별자 기반으로 수행한다.

## 5. 핵심 데이터 흐름

### 5.1 카카오 로그인

```text
React Native 또는 테스트 클라이언트
  -> GET /api/v1/auth/kakao/authorize
  -> 카카오 로그인 및 authorization code
  -> GET /api/v1/auth/kakao/callback
  -> 카카오 token/user 조회 adapter
  -> User upsert
  -> 서비스 access token + rotating refresh token 발급
```

- 카카오 access token은 계정 확인 후 장기 보관하지 않는다.
- 서비스 refresh token은 원문이 아니라 해시로 저장한다.
- access token은 짧은 만료 시간을 사용하고 refresh token은 회전시킨다.

### 5.2 방문 증빙과 OCR

```text
POST /api/v1/visits/upload-url
  -> 권한과 파일 제한 검증
  -> S3 presigned upload URL 발급
  -> 클라이언트가 비공개 S3에 직접 업로드
POST /api/v1/visits
  -> VisitEvidence 생성
  -> SQS OCR 작업 발행
OCR worker
  -> CLOVA OCR 호출
  -> 배민 화면 파싱
  -> 주문 HMAC/이미지 해시 중복 검사
  -> 카카오 장소 후보 연결
  -> 자동 승인 또는 MANUAL_REVIEW
  -> 승인 시 WriteGrant 발급
  -> 원본 즉시 삭제
```

### 5.3 리뷰와 리포트

```text
유효한 WriteGrant
  -> POST /api/v1/write-grants/{id}/review
  -> WriteGrant 원자적 소진
  -> Review + ReviewAnswer 저장
  -> 자유 의견 검수 큐 생성
  -> 리포트 집계 대상 반영
  -> 주기적 ReportSnapshot 계산
  -> 공개 조회 API 제공
```

WriteGrant 확인과 리뷰 생성은 같은 트랜잭션에서 수행하고 비관적 잠금 또는 조건부 갱신으로 중복 소진을 방지한다.

## 6. 도메인 상태

```text
UserStatus
- ACTIVE
- RATE_LIMITED
- SUSPENDED
- WITHDRAWN

VisitStatus
- UPLOADED
- OCR_PROCESSING
- NEEDS_CONFIRMATION
- MANUAL_REVIEW
- VERIFIED
- REJECTED
- DUPLICATE
- EXPIRED

WriteGrantStatus
- AVAILABLE
- CONSUMED
- EXPIRED
- REVOKED

ReviewStatus
- SUBMITTED
- INCLUDED
- HELD
- REMOVED

CommentStatus
- PENDING
- APPROVED
- REDACTED
- REVISION_REQUIRED
- REJECTED

ReportStatus
- COLLECTING
- PUBLISHED
- TEMPORARILY_HIDDEN

CorrectionStatus
- RECEIVED
- VERIFYING_OWNER
- REVIEWING
- RESOLVED
- REJECTED
```

상태 전이는 enum 값을 임의로 덮어쓰지 않고 domain method를 통해서만 수행한다.

## 7. 핵심 데이터 모델

### 인증

- `users`: 내부 사용자 ID, 상태, 약관 버전과 동의 시각
- `oauth_accounts`: provider, 외부 subject, 사용자 연결
- `user_sessions`: refresh token hash, 만료, 폐기, rotation 정보

### 음식점과 방문

- `restaurants`: 카카오 장소 ID, 이름, 주소, 좌표, 파일럿 포함 여부
- `visit_evidences`: 사용자, 앱 종류, OCR 상태, 완료 시각, 주문 HMAC, 이미지 해시, 장소 매칭 신뢰도, 원본 삭제 시각
- `write_grants`: 사용자, 방문, 음식점, 상태, 만료 및 소진 시각

### 리뷰와 리포트

- `reviews`: 작성자, 음식점, 방문 증빙, 집계 상태, 제출 시각
- `review_answers`: 리뷰, 평가 항목, 응답 값
- `review_comments`: 원문, 공개용 본문, 검수 상태
- `report_snapshots`: 음식점, 집계 기간, 항목별 표본 수·분포·긍정 비율

### 운영

- `moderation_cases`: 대상, 탐지 규칙, 상태, 관리자 판단
- `correction_requests`: 음식점, 요청자 연락 수단, 요청 사유, 처리 결과
- `audit_logs`: 행위자, 작업 종류, 대상, 사유, 변경 전후 메타데이터

모든 기본 키는 UUID를 사용한다. 모든 시각은 UTC로 저장하고 API에서는 RFC 3339 형식으로 반환한다.

## 8. API 규칙

- API prefix는 `/api/v1`을 사용한다.
- 성공 응답은 기능별 response DTO를 직접 반환한다.
- 오류는 RFC 7807 `ProblemDetail` 형식을 사용한다.
- 요청 DTO는 Bean Validation으로 검증한다.
- 목록 API는 cursor pagination을 기본으로 한다.
- 외부 provider 오류 메시지, stack trace, secret을 클라이언트에 노출하지 않는다.
- OpenAPI 문서를 API 계약의 기준으로 사용한다.
- React Native 타입과 클라이언트는 OpenAPI에서 생성한다.
- 멱등성이 필요한 방문 생성과 리뷰 제출 요청에는 idempotency key를 지원한다.

주요 API:

```text
GET    /api/v1/auth/kakao/authorize
GET    /api/v1/auth/kakao/callback
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/users/me

GET    /api/v1/restaurants/search
GET    /api/v1/restaurants/{id}/report
GET    /api/v1/restaurants/{id}/methodology

POST   /api/v1/visits/upload-url
POST   /api/v1/visits
GET    /api/v1/visits/{id}
POST   /api/v1/visits/{id}/confirm-restaurant

GET    /api/v1/write-grants/{id}
POST   /api/v1/write-grants/{id}/review
GET    /api/v1/users/me/reviews

POST   /api/v1/restaurants/{id}/corrections
GET    /api/v1/corrections/{publicToken}
POST   /api/v1/comments/{id}/reports
```

관리자 API는 `/api/v1/admin` 아래에 두고 관리자 role을 요구한다.

## 9. 데이터베이스 규칙

- PostgreSQL을 단일 source of truth로 사용한다.
- JPA `ddl-auto`는 로컬·운영 모두 schema 생성 용도로 사용하지 않는다.
- 모든 스키마 변경은 순서가 있는 Flyway migration으로 작성한다.
- 조회 패턴을 기준으로 인덱스를 명시한다.
- N+1 문제를 방지하기 위해 fetch join, entity graph 또는 projection을 의도적으로 사용한다.
- 집계 조회는 JPA projection 또는 명시적 query를 사용하고 entity graph 전체 로딩을 피한다.
- 운영 리포트는 원본 리뷰를 매번 전체 계산하지 않고 `report_snapshots`로 제공한다.

## 10. 보안과 개인정보

- 증빙 버킷은 public access를 완전히 차단한다.
- presigned URL은 짧은 만료 시간과 파일 형식·크기 제한을 적용한다.
- 클라이언트가 제출한 S3 object key를 그대로 신뢰하지 않는다.
- OCR 성공 원본은 즉시 삭제하고 수동 검수 원본은 최대 72시간 후 삭제한다.
- 주문 식별자는 server-side secret을 사용하는 HMAC으로 저장한다.
- 소셜 계정 정보와 공개 리뷰 응답을 분리한다.
- 관리자 작업에는 role 검사와 감사 로그를 적용한다.
- refresh token, provider secret, HMAC secret과 KMS key 정보는 Secrets Manager에서 주입한다.
- 위험 제출에는 rate limit과 검수 보류를 적용한다.

## 11. 테스트 전략

### 단위 테스트

- 상태 전이와 도메인 정책
- WriteGrant 만료·소진·재사용 방지
- 리뷰 집계 포함 한도
- 공개 표본과 기간 계산
- OCR 파싱과 장소 후보 점수 계산
- 개인정보 및 금지 표현 탐지

### 통합 테스트

- Testcontainers PostgreSQL을 사용한 JPA와 Flyway 검증
- 카카오·CLOVA·카카오 로컬 adapter의 stub server 테스트
- S3와 SQS 경계 테스트
- 로그인부터 방문, 글쓰기 권한, 리뷰, 리포트까지 전체 흐름
- 동시에 같은 WriteGrant를 제출했을 때 하나만 성공하는지 검증

### API 계약 테스트

- OpenAPI 문서 생성과 schema 변경 검증
- 인증·권한별 성공 및 실패 응답
- ProblemDetail의 type, status, code, detail 일관성

## 12. 개발 및 배포 순서

1. Spring Boot/Kotlin 프로젝트 기반
2. PostgreSQL, JPA, Flyway와 공통 테스트 환경
3. 공통 오류, 보안, OpenAPI
4. 카카오 로그인과 서비스 세션
5. 음식점과 파일럿 지역
6. 증빙 업로드와 비동기 OCR
7. WriteGrant와 리뷰
8. 집계 리포트
9. 관리자 검수, 신고, 정정
10. 보안·통합·성능 테스트
11. OpenAPI 계약 확정
12. React Native 앱 개발
