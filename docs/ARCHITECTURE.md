# Rider Voice 공개 리뷰 MVP 아키텍처

## 1. 이 문서의 목적

이 문서는 Rider Voice MVP가 어떤 구조로 동작하고 각 영역이 어떤 책임을 가지는지 설명한다. 제품 목표는 `PRD.md`, 선택한 이유는 `ADR.md`, 자세한 API 형식은 `API_SPEC.md`, 테이블과 관계는 `ERD.md`에서 관리한다.

현재 Spring Boot API 서버와 로컬 React 화면이 구현되어 있다. Rider Voice는 카카오 로그인 사용자의 리뷰를 공개하지만 실제 라이더 신분이나 방문 여부는 인증하지 않는다.

서버가 맡는 주요 역할은 다음과 같다.

- 카카오 로그인과 Rider Voice 로그인 상태 관리
- 음식점·주소 검색과 외부 검색 결과 확인
- 실제 픽업 장소와 배달 브랜드 관리
- 리뷰 작성 제한, 공개와 집계
- 자유 의견 검수, 신고와 음식점 정정

## 2. 기술과 실행 환경

### 서버

- Kotlin, JDK 25와 Gradle Kotlin DSL
- Spring Boot, Spring MVC와 Spring Security OAuth2 Client
- Spring Data JPA, Hibernate와 MySQL 9.3
- Spring `RestClient`, Spring Cache와 Caffeine
- springdoc-openapi와 RFC 7807 `ProblemDetail`
- JUnit 5, MockK, MockMvc와 HTTP stub server

### 로컬 화면

- Node 24 LTS, React 19, Vite 8와 TypeScript
- TanStack Query, React Router, React Hook Form과 Zod
- CSS Modules, Vitest와 Testing Library

API 서버, frontend와 `rider` MySQL 데이터베이스는 각각 로컬 프로세스로 실행한다. 초기 MVP는 API 서버 한 대를 기준으로 하며 Docker, Testcontainers, Redis, Kafka, Elasticsearch와 AWS 배포는 사용하지 않는다.

로컬 비밀값은 Git에서 제외한 프로젝트 루트 `.env`로 관리한다. `local` profile만 이 파일을 선택적으로 읽고 OS·IDE 환경 변수가 있으면 그 값을 우선한다.

로컬과 통합 테스트에서는 Hibernate `ddl-auto=update`로 현재 Entity를 DB에 반영한다. 운영 profile은 `ddl-auto=none`을 사용하며 자동으로 테이블을 만들거나 변경하지 않는다.

## 3. 전체 구조

서버는 기능별로 나누고, 각 기능 안에서 역할을 다시 구분한다.

```text
com.ridervoice.api
├── common       # 공통 설정과 보안 기반
├── auth         # 로그인, 사용자와 약관
├── restaurant   # 픽업 장소, 브랜드와 검색
├── review       # 리뷰 작성, 공개와 집계
└── moderation   # 검수, 신고와 정정
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

frontend는 루트 서버 프로젝트를 옮기지 않고 `/frontend`에서 독립적으로 관리한다.

```text
frontend/src
├── app       # 실행 시작, route와 전역 설정
├── pages     # 주소별 화면과 기능 조합
├── features  # 로그인, 음식점과 리뷰 흐름
└── shared    # API 타입, HTTP client와 공용 UI
```

의존 방향은 `app -> pages -> features -> shared`다. 화면이 DB나 카카오 API를 직접 호출하지 않고 Vite의 `/api` proxy를 통해 Spring Boot 서버만 호출한다. API 타입은 실행 중인 `/v3/api-docs`에서 생성하며 생성 파일을 직접 수정하지 않는다.

access token은 JavaScript 메모리에, refresh token은 탭 단위 `sessionStorage`에 둔다. `localStorage`, IndexedDB, cookie, URL, console과 analytics에는 서비스 토큰을 기록하지 않는다. 새로고침할 때는 refresh token으로 access token을 한 번 복구하고, 갱신·로그아웃·인증 실패 시 두 토큰을 함께 교체하거나 제거한다.

## 4. 로그인과 권한

카카오 로그인은 Spring Security OAuth2 Client를 사용한다.

```text
카카오 로그인 시작
  -> 카카오 인증과 사용자 동의
  -> callback에서 카카오 사용자 id 확인
  -> 60초 단일 사용 교환 코드 생성
  -> 고정된 frontend callback 주소로 이동
  -> frontend가 교환 코드를 API에 제출
  -> 약관 기록 후 Rider Voice access/refresh token 발급
```

- 카카오 사용자 정보에서는 `id`만 외부 계정 식별값으로 사용한다.
- 요청 위조 방지 값(`state`)을 위해 로그인 중에만 임시 HTTP session을 사용하고 성공·실패 후 폐기한다.
- OAuth session은 REST API 로그인 상태로 사용할 수 없다.
- 카카오 access token은 사용자 정보를 확인한 뒤 저장하지 않는다.
- 카카오 client secret이 없으면 `none`, 있으면 `client_secret_post` 방식으로 카카오에 인증한다.
- 로그인 화면은 계속 진행하면 현재 Rider Voice 필수 약관에 동의한다는 점을 알린다.
- 신규·약관 미동의 사용자는 유효한 교환 코드를 제출할 때 현재 약관 버전과 동의 시각을 기록하고 활성화한다.
- 교환 코드는 원문 대신 hash로 저장하고 60초 뒤 만료되며 한 번만 사용할 수 있다.
- 잘못되거나 만료되거나 다시 사용한 코드는 같은 인증 실패로 처리한다.
- access token과 refresh token은 URL에 넣지 않는다.
- 외부 provider 오류, token, secret과 내부 예외는 사용자에게 노출하지 않는다.

활성 사용자는 15분 access token과 30일 refresh token을 사용한다. refresh token은 hash로 저장하고 갱신할 때마다 교체한다. 로그아웃은 Rider Voice session만 종료하며 카카오 로그아웃은 호출하지 않는다.

사용자 권한은 `USER`와 `ADMIN`이다. access token을 확인할 때 DB의 현재 권한도 함께 읽어 관리자 권한 변경이 기존 토큰에도 반영되게 한다.

## 5. 음식점 구조와 등록

실제 픽업 장소와 소비자에게 보이는 배달 브랜드는 분리한다.

```text
PickupLocation 1
  └── N Restaurant 1
        ├── N RestaurantExternalReference
        └── N RestaurantPlatform
```

`PickupLocation`은 표준 주소, 정규화 주소, 상세 위치와 좌표를 가진다. 주소와 상세 위치로 만든 `location_key`를 unique로 두어 같은 장소의 중복을 줄인다.

`Restaurant`은 브랜드명, 정규화한 이름과 상태를 가진다. `(pickup_location_id, normalized_name)`을 unique로 두며 상태는 `ACTIVE`, `CLOSED`, `MERGED`다. 중복 병합된 음식점은 삭제하지 않고 대표 음식점 ID를 남긴다.

`RestaurantExternalReference`는 카카오 같은 외부 서비스의 장소 ID를 저장한다. `(provider, external_place_id)`는 unique다. 플랫폼 정보는 선택 항목이며 음식점의 동일성이나 운영 주체를 증명하지 않는다.

공개 검색은 내부 브랜드와 카카오 결과를 합쳐 보여준다. 카카오에만 있는 후보는 내부 음식점 ID 없이 반환한다. 같은 픽업 장소의 다른 브랜드 목록은 공개하지 않는다.

폐업 음식점은 검색과 새 리뷰에서 제외하지만 기존 주소로 직접 조회하면 폐업 상태와 과거 리뷰를 보여준다. 관리자는 폐업 상태를 해제할 수 있다. 병합 전 음식점 ID로 조회하거나 리뷰를 작성하면 대표 음식점으로 연결한다.

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

## 7. 공개 결과와 검수

유효한 개별 리뷰의 6개 평가는 작성 직후 공개한다. 자유 의견은 검수 상태가 `PUBLISHED`일 때만 공개한다. 모든 공개 리뷰와 평가 결과에는 `verificationStatus=UNVERIFIED`와 미인증 안내를 포함한다. 공개 작성자 정보는 활동 기간과 공개 리뷰 수만 제공하며 고정 공개 ID나 닉네임은 사용하지 않는다.

평가 결과 상태는 다음과 같다.

- `NO_REVIEWS`: 유효 작성자 0명
- `COLLECTING`: 유효 작성자 1~4명
- `PUBLISHED`: 서로 다른 유효 작성자 5명 이상

브랜드 결과는 작성자별 활성 리뷰 하나를 사용한다. 장소 결과는 같은 작성자의 여러 브랜드 리뷰 중 생성 시각과 ID가 가장 최근인 하나만 사용한다. `NOT_OBSERVED`는 작성자 수와 값별 개수에는 포함하지만 평가 비율에서는 제외한다. 한 항목에 관찰한 값이 하나도 없으면 비율 대신 관찰값 없음 상태를 반환한다.

초기에는 별도 집계 테이블이나 batch를 만들지 않고 조회 쿼리와 application service에서 계산한다. 삭제나 전체 제외로 작성자가 5명 미만이 되면 결과는 다시 `COLLECTING`으로 바뀐다.

자유 의견 상태는 `NONE`, `PENDING`, `PUBLISHED`, `REJECTED`, `HIDDEN_REPORTED`다.

- 의견을 작성하거나 수정하면 `PENDING`이 된다.
- 공개 의견이 신고되면 `HIDDEN_REPORTED`로 바꾸고 정해진 평가는 유지한다.
- 신고를 기각하면 이전 공개 상태를 복원한다.
- 의견만 문제라면 의견만 숨기고, 허위·도배라면 리뷰 전체를 `EXCLUDED`로 바꾼다.
- 한 사용자는 같은 대상에 한 번만 신고할 수 있다.

음식점 정보 신고를 승인할 때는 이름 변경, 확인된 픽업 장소 재연결, 병합 또는 폐업 중 실제 정정을 함께 처리한다. 데이터 변경, 신고 종결과 감사 기록은 한 트랜잭션으로 완료한다. 중복 음식점은 hard delete하지 않고 대표 음식점 연결과 기록을 남긴다.

리뷰 제외나 음식점 병합으로 대상이 사라지면 같은 대상의 나머지 대기 신고도 원인을 남기고 종결한다. 관리자는 조사에 필요한 리뷰와 음식점 상태를 볼 수 있지만 OAuth 계정 식별값과 서비스 토큰은 조회할 수 없다.

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

- OAuth 요청 위조 방지, 60초 교환 코드, 약관 기록과 토큰 회전
- 음식점·장소·외부 참조의 중복 및 동시 등록
- 활성 리뷰 중복, 삭제·전체 제외 후 90일 제한
- 브랜드·장소 작성자 4명과 5명 경계 및 `NOT_OBSERVED`
- 의견 검수, 신고, 리뷰 제외와 음식점 정정의 함께 성공·실패 여부
- 공개·사용자·관리자 권한과 OpenAPI 응답 형식

JPA schema, FK, index와 unique 제약은 실행 중인 로컬 MySQL로 확인한다. 카카오 API는 실제 호출 대신 stub server로 성공, timeout, 호출 제한과 잘못된 응답을 검증한다.
