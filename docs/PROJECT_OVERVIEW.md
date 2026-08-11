# Rider Voice 프로젝트 개요

## 1. 프로젝트 소개

Rider Voice는 음식 배달 픽업 과정에서 관찰한 음식점 운영 환경을 카카오 로그인 사용자가 구조화된 리뷰로 공유하고, 소비자 누구나 확인할 수 있게 하는 백엔드 API 서버다.

기존 음식점 리뷰가 맛과 소비자 경험에 집중한다면 Rider Voice는 픽업 공간, 포장 상태, 주문 준비와 전달 과정, 직원 응대처럼 배달 픽업 현장에서 확인할 수 있는 정보를 다룬다. 또한 소비자에게 보이는 배달 브랜드와 실제 픽업 장소가 다를 수 있다는 점을 모델에 반영한다.

현재 서버 API MVP와 주요 사용자 흐름을 검증하는 로컬 `/frontend` React SPA prototype이 구현되어 있다. 운영용 웹 또는 모바일 클라이언트는 현재 범위에 포함되지 않는다.

## 2. 신뢰 경계

카카오 로그인은 서비스 계정을 식별하기 위한 수단이며 작성자가 라이더인지, 실제로 음식점을 방문했는지를 인증하지 않는다.

따라서 다음 원칙을 모든 기능과 공개 응답에 적용한다.

- 모든 공개 리뷰와 리포트에 `verificationStatus=UNVERIFIED`와 미인증 안내를 포함한다.
- 리뷰를 인증된 라이더 리뷰, 방문 인증 정보 또는 사실이 보장된 평가로 표현하지 않는다.
- 서로 다른 작성자 5명 기준은 집계를 공개하기 위한 기준일 뿐 방문 인증이나 조작 방지를 보장하지 않는다.
- 종합 점수, 평균 별점, 음식점 순위와 인증 배지를 제공하지 않는다.
- 배달내역 캡처, 이미지 업로드, OCR와 배달 앱 화면 파싱을 사용하지 않는다.
- 같은 픽업 장소에 연결된 다른 배달 브랜드 목록을 소비자에게 공개하지 않는다.

## 3. 사용자와 주요 기능

### 3.1 비로그인 소비자

- 음식점명이나 주소로 내부 음식점과 카카오 장소 후보를 통합 검색한다.
- 음식점 상세와 공개된 개별 리뷰를 조회한다.
- 브랜드 단위와 픽업 장소 단위의 공개 가능한 집계를 확인한다.
- 카카오 검색 장애 시 이미 등록된 내부 음식점 결과와 외부 검색 불가 상태를 확인한다.

### 3.2 카카오 로그인 사용자

- 카카오 OAuth로 로그인하고 필수 약관에 동의한다.
- Rider Voice access token과 rotating refresh token으로 REST API를 사용한다.
- 기존 음식점, 카카오 검색 후보, 기존 픽업 장소의 신규 브랜드 또는 검증된 신규 주소를 대상으로 리뷰를 작성한다.
- 본인의 활성 리뷰를 조회·수정·삭제한다.
- 리뷰와 음식점 정보에 대해 신고한다.

### 3.3 관리자

- 자유 의견을 승인하거나 거절한다.
- 리뷰 및 음식점 정보 신고를 처리한다.
- 의견만 비공개하거나 허위·도배 리뷰 전체를 공개와 집계에서 제외한다.
- 잘못 연결된 픽업 장소를 정정한다.
- 음식점 이름과 영업 상태를 정정하고, provider가 재검증한 신규 주소로 픽업 장소를 옮긴다.
- 중복 음식점을 canonical 음식점으로 병합한다.
- 원문 리뷰·작성자 활동·음식점 외부 참조를 조사하고 감사 이력을 조건별로 조회한다.
- 검수, 신고 처리, 정정과 병합 결정을 감사 기록으로 남긴다.

## 4. 음식점 도메인

Rider Voice는 실제 픽업 장소와 소비자에게 보이는 배달 브랜드를 분리한다.

```text
PickupLocation 1 <- N Restaurant 1 <- N RestaurantExternalReference
                           |
                           + <- N RestaurantPlatform
```

### `PickupLocation`

실제로 음식을 픽업하는 물리적 장소다. 표준 주소, 정규화 주소, 선택 상세 위치, 위도·경도와 등록 출처를 저장한다. 정규화된 주소와 상세 위치를 바탕으로 `locationKey`를 생성해 중복을 줄인다.

### `Restaurant`

소비자와 배달 플랫폼에 보이는 배달 브랜드다. 하나의 픽업 장소에 여러 브랜드를 연결할 수 있으며 같은 장소와 같은 정규화 브랜드명 조합은 하나만 허용한다.

음식점 상태는 `ACTIVE`, `CLOSED`, `MERGED`다. 폐업 음식점은 검색과 신규 리뷰 대상에서 제외하지만 직접 상세와 기존 리뷰는 `CLOSED` 표시와 함께 유지한다. 중복 음식점은 hard delete하지 않고 `MERGED` 상태와 canonical 음식점 ID를 유지하며 기존 ID 요청은 canonical 음식점으로 해석한다.

### 외부 참조와 플랫폼

카카오 장소 ID는 `RestaurantExternalReference`에서 provider와 external place ID 조합으로 unique 관리한다. 배민, 쿠팡이츠, 요기요 등의 플랫폼은 선택 메타데이터이며 음식점 동일성이나 운영 주체의 증거로 사용하지 않는다.

클라이언트가 전달한 주소, 좌표, 브랜드명이나 카카오 장소 ID를 그대로 신뢰하지 않는다. 카카오 장소 또는 주소 기반 등록에서는 서버가 원 검색어로 provider 검색을 반복해 선택 결과를 검증한다. 음식점 등록과 첫 리뷰 저장은 같은 application use case와 트랜잭션에서 함께 성공하거나 실패한다.

## 5. 리뷰 정책

리뷰에는 방문 연월과 다음 6개 구조화 평가가 필요하다.

1. 픽업 공간 청결
2. 포장 안정성
3. 주문 준비 상태
4. 주문 확인·전달 정확성
5. 직원 응대
6. 라이더 존중

각 평가는 다음 값 중 하나다.

- `VERY_GOOD`
- `GOOD`
- `NEEDS_IMPROVEMENT`
- `MAJOR_IMPROVEMENT`
- `NOT_OBSERVED`

자유 의견은 선택 사항이며 trim 후 최대 200자다. 구조화 평가는 첫 리뷰부터 즉시 공개하고 자유 의견은 관리자 승인 후 공개한다.

작성과 활성 상태 정책은 다음과 같다.

- 같은 음식점에는 활성 리뷰를 하나만 작성할 수 있다.
- 활성 리뷰가 있으면 경과 시간과 관계없이 새 리뷰를 추가할 수 없다.
- 활성 리뷰만 수정하거나 삭제할 수 있고 방문 연월은 수정할 수 없다.
- 삭제는 soft delete이며 삭제·전체 제외 후 최초 제출 시각부터 90일이 지나야 다시 작성할 수 있다.
- 타인의 리뷰와 비활성 리뷰는 `404 Not Found`로 처리한다.

`Review`가 내용과 활성·삭제 상태의 단일 원본이다. nullable current slot unique 제약과 충돌 재시도로 활성 리뷰 하나와 동시 요청을 직렬화한다.

## 6. 공개 집계

개별 구조화 리뷰는 유효하면 첫 작성부터 공개한다. 집계는 브랜드와 픽업 장소를 독립적으로 계산한다.

- 유효 활성 라이더 0명: `NO_REVIEWS`
- 서로 다른 유효 활성 라이더 1~4명: `COLLECTING`
- 서로 다른 유효 활성 라이더 5명 이상: `PUBLISHED`

브랜드 집계는 라이더별 해당 브랜드의 활성 리뷰 하나를 사용한다. 장소 집계는 같은 라이더가 한 장소의 여러 브랜드에 활성 리뷰를 가지고 있더라도 생성 시각과 ID가 가장 최신인 하나만 반영한다.

`NOT_OBSERVED`는 작성자 표본과 값별 개수에는 포함하지만 평가 비율 분모에서는 제외한다. 삭제나 관리자 제외로 유효 작성자가 5명 미만이 되면 집계는 다시 `COLLECTING`으로 전환한다.

## 7. 의견 검수와 신고

자유 의견 상태는 `NONE`, `PENDING`, `PUBLISHED`, `REJECTED`, `HIDDEN_REPORTED`로 관리한다.

- 의견 입력과 수정은 `PENDING`으로 전환한다.
- 승인 전에는 구조화 평가만 공개한다.
- 공개 의견이 신고되면 의견만 `HIDDEN_REPORTED`로 전환하고 구조화 평가는 유지한다.
- 신고 기각 시 이전 의견 상태를 복원한다.
- 의견 위반은 의견만 비공개 처리한다.
- 허위·도배로 인정된 리뷰는 전체를 공개와 집계에서 제외한다.
- 한 사용자는 같은 대상에 한 번만 신고할 수 있다.

리뷰 전체 제외나 음식점 병합으로 판단 대상이 사라지면 같은 대상의 나머지 대기 신고를 자동 종결한다. 음식점 신고를 승인할 때는 이름 변경, 픽업 장소 재연결, 검증된 주소 재연결, 병합 또는 폐업 정정과 신고 결정을 한 트랜잭션으로 처리한다.

관리자 결정, 음식점 정정과 병합은 `ModerationAudit`에 기록한다.

## 8. 인증과 권한 구조

인증은 카카오 OAuth 로그인과 Rider Voice REST API 인증을 분리한다.

```text
카카오 OAuth
  -> 카카오 사용자 ID 확인
  -> 약관 동의 상태 확인
  -> Rider Voice 전용 opaque token 발급
  -> stateless REST API 인증
```

- Spring Security OAuth2 Client가 authorization, `state`, code 교환과 user info 조회를 처리한다.
- 사용자 식별에는 카카오 user info의 `id`만 사용한다.
- OAuth handshake 동안만 임시 HTTP session을 사용하고 성공·실패 후 폐기한다.
- OAuth session의 `SecurityContext`를 REST API 인증으로 사용하지 않는다.
- 로그인 화면에서 현재 필수 약관 동의를 고지하고 유효한 OAuth 교환 시 신규·약관 미동의 사용자를 활성화한다.
- 활성화된 사용자에게 15분 access token과 30일 rotating refresh token을 발급한다.
- refresh token은 hash로 저장하고 갱신할 때 회전한다.
- 카카오 access token은 사용자 확인 후 저장하지 않는다.
- access token 인증 시 현재 데이터베이스의 `USER` 또는 `ADMIN` 역할을 확인한다.

## 9. 애플리케이션 아키텍처

기능별 모듈과 가벼운 헥사고날 아키텍처를 사용한다.

```text
com.ridervoice.api
├── common       공통 설정, 오류, 보안, 영속성 기반과 호출 제한
├── auth         카카오 로그인, 약관과 서비스 토큰
├── restaurant   검색, 픽업 장소, 브랜드와 외부 참조
├── review       활성 리뷰, 작성 정책, 공개 조회와 집계
└── moderation   의견 검수, 신고, 정정과 병합
```

각 기능 내부는 다음 흐름을 따른다.

```text
HTTP / Security adapter
  -> presentation DTO
  -> application input port(command)
  -> domain policy
  -> application output port
  -> JPA / external provider adapter

application result
  -> presentation mapper
  -> response DTO
```

주요 경계는 다음과 같다.

- Controller와 OAuth handler는 validation, principal 추출, command 변환, input port 호출과 응답 변환만 담당한다.
- application 계층은 Spring MVC, Swagger, presentation DTO와 infrastructure 구현을 알지 못한다.
- JPA Entity를 API request/response, application command/result로 사용하지 않는다.
- 트랜잭션, 소유권, 작성 제한과 상태 전이는 application service에 둔다.
- repository와 외부 provider 의존성은 `application/port/out`으로 정의한다.
- 외부 API와 provider 전용 DTO는 infrastructure adapter 안에 격리한다.
- Entity 관계는 필요한 자식에서 부모로 향하는 단방향 `LAZY` 관계만 사용한다.
- 모든 Entity는 `BaseEntity`의 `Long` ID와 `GenerationType.IDENTITY`를 사용한다.
- 시각은 UTC로 저장하고 API에서는 RFC 3339로 반환한다. 방문 연월 검증에만 `Asia/Seoul`을 사용한다.

## 10. API 계약과 장애 처리

모든 API는 `/api/v1` prefix를 사용하며 `/v3/api-docs`를 최종 실행 계약으로 유지한다.

- 공개 API: OAuth 시작·callback, 음식점 검색·상세·리뷰 조회, token 갱신
- 온보딩 API: 필수 약관 동의
- 사용자 API: 주소 검색, 리뷰 작성·수정·삭제, 내 리뷰 조회와 신고
- 관리자 API: 의견 검수, 신고 처리, 리뷰·음식점 조사, 이름·상태·픽업 장소 정정, 음식점 병합과 감사 조회

목록 API는 생성 시각과 ID 기반 cursor pagination을 사용한다. 성공 응답은 기능별 DTO, 오류 응답은 안정적인 `code`를 포함한 RFC 7807 `ProblemDetail`을 사용한다. 외부 provider 오류, token, secret과 stack trace는 클라이언트에 노출하지 않는다.

카카오 검색 장애 시 공개 검색은 내부 음식점 결과와 `externalSearchStatus=UNAVAILABLE`을 반환한다. 카카오 또는 주소 기반 신규 등록은 provider 재검증이 불가능하면 `503`으로 실패한다.

## 11. 캐시와 호출 제한

- 검색어는 정규화 후 2~100자로 제한한다.
- 검색 결과는 최대 20개다.
- 카카오 성공 검색 결과는 Caffeine에 5분간 저장한다.
- 공개 검색은 호출자 기준 분당 30회로 제한한다.
- 리뷰 작성은 계정당 최근 24시간 최대 10개다.
- 신고는 계정당 하루 최대 20개다.

현재 캐시와 공개 검색 호출 제한은 단일 API 인스턴스의 메모리 안에서만 유효하다. 다중 인스턴스가 필요할 때 Redis와 같은 분산 저장소 도입을 별도로 결정한다.

## 12. 기술 스택과 실행 경계

- Kotlin 2.3, JDK 25, Gradle Kotlin DSL
- Spring Boot 4.1, Spring MVC, Spring Security OAuth2 Client
- Bean Validation, Spring Data JPA, Hibernate, MySQL 9.3
- Spring `RestClient`, Spring Cache, Caffeine
- springdoc-openapi, RFC 7807 `ProblemDetail`
- JUnit 5, Mockito, MockMvc와 HTTP stub server
- Node 24, React 19, Vite 8, TypeScript와 npm
- TanStack Query, React Router, React Hook Form, Zod, Vitest와 Testing Library

현재는 로컬 단일 API 인스턴스와 `rider` MySQL 데이터베이스를 전제로 한다. 로컬과 통합 테스트에서는 Hibernate `ddl-auto=update`, 운영 profile에서는 `ddl-auto=none`을 사용한다.

다음 항목은 현재 범위에 포함되지 않는다.

- 운영용 웹 또는 모바일 클라이언트와 frontend 배포
- 관리자·신고 frontend 화면과 실제 카카오 계정 자동 브라우저 E2E
- 라이더 신분과 실제 방문 인증
- 이미지 업로드, OCR와 배달 앱 화면 분석
- Redis, Kafka와 Elasticsearch
- Docker와 Testcontainers
- AWS 배포와 production readiness 작업
- 데이터베이스 migration 도구와 다중 인스턴스 운영

## 13. 테스트와 현재 구현 상태

현재 구현에는 다음 테스트가 포함되어 있다.

- 도메인 정책 단위 테스트
- MockMvc 기반 API, 권한과 OpenAPI 계약 테스트
- OAuth redirect, `state`, code 교환과 임시 session 폐기 테스트
- 카카오 adapter의 성공, timeout, rate limit과 손상 응답 테스트
- 로컬 MySQL schema, FK, index와 unique 제약 테스트
- 음식점·리뷰 생성, 신고와 음식점 병합 동시성 테스트
- 활성 리뷰 중복 작성과 삭제·전체 제외 후 90일 우회 방지 테스트
- 작성자 4명과 5명의 집계 공개 경계 테스트
- 장소 집계 작성자 중복 제거와 `NOT_OBSERVED` 처리 테스트
- 의견 검수, 신고, 전체 제외와 canonical 음식점 처리 테스트
- 관리자 조사·정정 API, 신고와 정정의 원자성, 형제 신고 자동 종결과 폐업 음식점 공개 경계 테스트
- frontend 공개 조회, OAuth 교환·약관 동의, 네 가지 리뷰 target 작성과 내 리뷰 관리 Testing Library 테스트

현재 backend `test`, 로컬 MySQL `integrationTest`, `check`, `build`와 frontend `api:generate`, `lint`, `test`, `build`가 통과한 상태다.

## 14. 관련 문서

- [PRD](PRD.md): 제품 목표, 사용자 흐름, 정책과 MVP 범위
- [Architecture](ARCHITECTURE.md): 계층, 도메인 모델, 데이터 흐름과 실행 경계
- [ADR](ADR.md): 주요 기술·제품 결정과 트레이드오프
- [API Specification](API_SPEC.md): endpoint, 인증 요구사항과 공개 DTO 계약
- [Git Flow](GIT_FLOW.md): 브랜치와 커밋 운영 규칙
