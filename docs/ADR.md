# Rider Voice 공개 리뷰 MVP Architecture Decision Records

현재 목표 아키텍처에 적용되는 결정만 기록한다. 기존 비공개 리뷰 MVP와 단일 카카오 장소 기반 음식점 결정은 이 문서의 새 결정으로 대체한다.

## ADR-001: Spring Boot 서버 API와 OpenAPI 우선

**결정**: Kotlin/JDK 25 기반 Spring Boot 서버와 `/api/v1` JSON REST API를 먼저 구현하고 OpenAPI를 클라이언트 계약의 기준으로 사용한다. 오류는 안정적인 `code`를 포함한 RFC 7807 `ProblemDetail`로 반환한다.

**이유**: 인증, 음식점 식별, 공개 범위와 집계 정책을 서버에서 일관되게 적용할 수 있다.

**트레이드오프**: 실제 사용자 화면 검증은 늦어진다. 클라이언트 구현 전에는 Swagger UI와 API 테스트로 흐름을 검증한다.

## ADR-002: MySQL, JPA와 로컬 Hibernate schema 관리

**결정**: MySQL 9.3, Spring Data JPA와 Hibernate를 사용한다. 모든 Entity는 `BaseEntity`의 `Long IDENTITY` PK를 사용한다. 로컬과 통합 테스트는 `ddl-auto=update`, 운영 profile은 `ddl-auto=none`으로 설정한다.

**이유**: 음식점 관계, 리뷰 이력, 현재 상태와 unique 제약을 데이터베이스에서도 보장해야 한다.

**트레이드오프**: migration 이력과 rollback이 없다. 현재 schema와 목표 모델이 호환되지 않으므로 로컬 `rider` DB를 구현 시 한 번 초기화하며 실제 배포 전에 migration 도구를 별도 결정한다.

## ADR-003: 가벼운 헥사고날 경계와 DTO 분리

**결정**: 기능 안에서 presentation, application, domain, infrastructure를 나누고 inbound use case와 outbound dependency에만 port를 둔다. HTTP DTO, application command/result와 provider DTO를 분리한다.

**이유**: Spring MVC, JPA와 카카오 형식이 비즈니스 정책과 공개 계약에 전파되는 것을 막는다.

**트레이드오프**: mapper와 모델 수가 늘어난다. domain entity에는 MVP 동안 JPA annotation을 허용하지만 Entity를 API DTO로 사용하지 않는다.

## ADR-004: Spring Security OAuth2 Client로 카카오 로그인

**결정**: 카카오 authorization, `state`, code 교환과 user info 호출은 Spring Security OAuth2 Client로 처리한다. 카카오는 사용자 정의 provider로 등록하며 user info의 `id`를 외부 subject로 사용한다.

OAuth handshake에만 임시 HTTP session을 사용하고 성공·실패 후 폐기한다. REST API는 별도 stateless security chain과 기존 opaque bearer token을 사용한다.

성공 callback은 service token을 직접 응답하거나 URL에 넣지 않는다. 서버가 60초 동안 한 번만 사용할 수 있는 opaque 교환 코드를 발급해 고정된 frontend callback URL에 query parameter로 전달하고, frontend는 별도 exchange API의 JSON 응답으로 token을 받는다. 실패 callback도 provider 세부사항을 숨긴 일반화된 실패 값만 같은 frontend URL로 전달한다.

**이유**: OAuth 보안 흐름을 직접 관리하는 범위를 줄이면서 웹과 향후 모바일 클라이언트가 같은 service token API를 사용할 수 있다.

**트레이드오프**: OAuth endpoint에는 일시적인 session과 단기 교환 코드 저장소가 필요하고 카카오 provider 속성 및 사용자 매핑을 직접 설정해야 한다. API chain은 OAuth session을 인증으로 받아들이지 않도록 분리해야 한다.

카카오 access token은 사용자 확인 후 저장하지 않는다. `KAKAO_CLIENT_SECRET`이 없으면 client authentication `none`, 있으면 `client_secret_post`를 사용한다.

## ADR-005: Rider Voice opaque access/refresh token 유지

**결정**: 로그인 화면은 카카오 로그인을 계속하면 현재 Rider Voice 필수 약관에 동의한다는 점을 고지한다. 카카오 로그인 성공 후 frontend가 단일 사용 교환 코드를 제출하면 서버는 신규·약관 미동의 사용자의 현재 약관 버전과 동의 시각을 기록하고 Rider Voice access token과 rotating refresh token을 JSON으로 발급한다. 별도 onboarding token은 사용하지 않으며 access와 refresh token은 redirect URL의 query string이나 fragment에 넣지 않는다.

**이유**: 소셜 로그인 과정과 서비스 API session을 분리하고 refresh session 폐기·회전을 서버에서 통제할 수 있다.

**트레이드오프**: service session 저장소를 조회해야 한다. refresh token은 hash만 저장하고 access token 인증 시 사용자의 현재 DB role을 확인한다.

## ADR-006: 픽업 장소와 배달 브랜드 분리

**결정**: 실제 픽업 장소 `PickupLocation`과 소비자에게 보이는 배달 브랜드 `Restaurant`를 분리한다. 한 장소에 여러 브랜드를 연결할 수 있으며 카카오 장소 ID는 `RestaurantExternalReference`로 관리한다.

**이유**: 하나의 사업장이나 주방에서 여러 배달 브랜드를 운영하고 일부 브랜드는 카카오 지도에 없을 수 있다.

**트레이드오프**: 주소 정규화, 장소 선택과 관리자 병합이 필요하다. 같은 장소의 다른 브랜드 목록은 운영 주체를 암시할 수 있으므로 소비자에게 공개하지 않는다.

## ADR-007: 카카오 우선 검색과 주소 기반 수동 지연 등록

**결정**: 공개 검색은 내부 브랜드와 카카오 키워드 결과를 병합한다. 카카오에 없는 브랜드는 검증된 표준 주소와 선택 상세 위치로 수동 등록할 수 있다. 장소·브랜드 등록은 첫 리뷰 생성과 같은 use case에서 수행한다.

카카오 장소와 주소는 클라이언트가 보낸 값만 신뢰하지 않고 원 검색어로 provider 검색을 반복해 검증한다.

**이유**: 미사용 장소를 미리 저장하지 않으면서 가상 브랜드를 지원하고 임의 주소·좌표 입력을 제한할 수 있다.

**트레이드오프**: 최초 리뷰 작성 시 외부 API를 다시 호출한다. 주소만으로 완전한 동일성을 보장할 수 없으므로 중복 신고와 관리자 병합을 제공한다.

## ADR-008: 미인증 리뷰를 첫 작성부터 공개

**결정**: 약관에 동의한 활성 사용자는 별도 라이더·방문 인증 없이 리뷰를 작성할 수 있다. 유효한 개별 리뷰의 구조화 평가는 첫 작성부터 공개한다.

모든 공개 응답은 `verificationStatus=UNVERIFIED`와 미인증 안내를 포함한다.

**이유**: 초기 사용자의 작성 마찰을 낮추고 정보 공유 수요를 먼저 검증한다.

**트레이드오프**: 카카오 로그인과 작성자 수는 라이더 신분이나 방문을 증명하지 않는다. 인증된 리뷰, 사실 보증 또는 공식 평가로 표현하지 않고 신고·제재와 투명한 안내로 위험을 완화한다.

## ADR-009: 서로 다른 작성자 5명부터 집계 공개

**결정**: 배달 브랜드 집계와 픽업 장소 집계를 독립적으로 계산하고 각각 서로 다른 유효 작성자 5명부터 공개한다. 종합 점수, 평균 별점과 순위는 만들지 않는다.

브랜드는 라이더별 활성 리뷰 하나, 장소는 같은 라이더의 여러 브랜드 활성 리뷰 중 가장 최근 하나만 집계한다. `NOT_OBSERVED`는 값별 개수에 포함하고 비율 분모에서는 제외한다.

**이유**: 단일 작성자의 영향과 중복 반영을 줄이고 항목별 관찰 정보를 그대로 제공한다.

**트레이드오프**: 5개 계정 기준은 방문 인증이나 조작 방지를 보장하지 않는다. 초기에는 조회 시 계산하고 규모가 커질 때 materialized aggregate를 검토한다.

## ADR-010: 음식점별 활성 리뷰 1개와 reviews 단일 상태

**결정**: 카카오 로그인과 필수 약관 동의를 완료한 활성 사용자(라이더)는 같은 음식점에 활성 리뷰를 하나만 가질 수 있다. 활성 리뷰가 있으면 경과 시간과 관계없이 새 리뷰를 작성할 수 없고 기존 리뷰만 수정할 수 있다.

삭제는 soft delete로 처리하고 관리자 전체 제외와 함께 활성 slot을 비운다. 활성 리뷰가 없을 때만 삭제·전체 제외 리뷰를 포함한 마지막 제출의 최초 생성 시각부터 90일 후 다시 작성할 수 있다. 작성 상태는 별도 `AuthorRestaurantReviewState` 없이 `reviews`에서 계산한다.

**이유**: 한 계정의 반복 리뷰가 같은 음식점 집계에 미치는 영향을 제한하고 삭제를 통한 즉시 재작성 우회를 막으면서 상태 모델을 단순화한다.

**트레이드오프**: 리뷰가 유지되는 동안 운영 환경 변화를 별도 이력이 아니라 기존 리뷰 수정으로 반영한다. nullable current slot unique 제약과 충돌 재시도로 동시 작성을 직렬화하며 soft-delete 내용은 내부 정책 계산과 관리자 조사 목적으로 남는다.

## ADR-011: 구조화 평가 즉시 공개와 자유 의견 사전 검수

**결정**: 6개 구조화 평가는 즉시 공개하고 최대 200자의 선택 의견은 관리자 승인 후 공개한다. 신고 접수 시 의견만 임시 숨기며 허위·도배로 인정되면 리뷰 전체를 공개와 집계에서 제외한다.

**이유**: 작은 운영 규모에서 개인 식별, 모욕과 무관한 내용을 공개 전에 줄이면서 구조화 정보는 빠르게 공유할 수 있다.

**트레이드오프**: 관리자 운영 부담과 의견 공개 지연이 생긴다. 모든 판단은 감사 기록에 남긴다.

## ADR-012: 단일 인스턴스 캐시와 호출 제한

**결정**: 카카오 성공 검색은 Caffeine에 5분간 캐시한다. 공개 검색은 메모리 기반 호출 제한을 적용하고 리뷰·신고 작성 제한은 DB 기록으로 확인한다.

**이유**: MAU 1,000 목표의 단일 인스턴스에서 별도 인프라 없이 provider 호출과 기본적인 도배를 줄일 수 있다.

**트레이드오프**: 메모리 캐시와 제한은 여러 인스턴스 사이에 공유되지 않는다. 다중 인스턴스가 필요해질 때 Redis 등의 도입을 별도 결정한다.

## ADR-013: `.env` 단일 로컬 설정

**결정**: 프로젝트 루트의 Git 제외 `.env` 하나를 `local` profile만 선택적으로 불러온다. IntelliJ EnvFile은 개인 설정으로 사용할 수 있으며 OS·IDE 환경 변수가 `.env`보다 우선한다.

**이유**: 터미널과 IDE가 같은 환경 변수 이름을 사용하면서 secret을 저장소에 포함하지 않을 수 있다.

**트레이드오프**: 실행 작업 디렉터리가 프로젝트 루트가 아니면 `.env`를 찾지 못하므로 해당 환경은 변수를 직접 제공해야 한다.

## ADR-014: 이미지와 OCR을 인증에 사용하지 않음

**결정**: 배달내역 캡처, 이미지 업로드, OCR와 배달 앱 화면 파싱을 방문 인증 방식으로 사용하지 않는다. 현재 MVP에는 라이더 또는 방문 인증 자체를 구현하지 않는다.

**이유**: 개인정보 노출, 화면 구조 의존성, 저장·처리 부담과 작성 마찰이 크다.

**트레이드오프**: 리뷰는 미인증 정보로 유지된다. 실제 운영에서 필요성과 실행 가능한 낮은 마찰의 방식이 확인될 때만 새 ADR로 검토한다.

## ADR-015: 관리자 정정과 신고 종결의 원자성

**결정**: 음식점 정보 신고를 `RESOLVE`하려면 이름 변경, 검증된 픽업 장소 재연결, 중복 병합 또는 폐업 중 실제 correction을 함께 제출한다. provider 검증은 DB 트랜잭션 전에 수행하고, 검증된 결과의 저장·음식점 변경·신고 종결·감사 기록은 하나의 DB 트랜잭션에서 완료한다. 신고 없이 발견한 오류는 같은 application 정책으로 직접 정정할 수 있다.

음식점 상태에 `CLOSED`를 추가한다. 폐업 음식점은 검색과 신규 리뷰에서 제외하지만 직접 상세와 기존 리뷰는 폐업 상태와 함께 유지하며 관리자가 재개장할 수 있다. 리뷰 전체 제외나 음식점 병합으로 판단 대상이 사라지면 같은 대상의 나머지 대기 신고를 자동 종결하고 그 원인을 기록한다.

**이유**: 신고 상태만 종결되고 실제 정보가 남는 불일치를 막고, 관리자 API만으로 조사부터 정정과 감사까지 완료할 수 있어야 한다.

**트레이드오프**: 관리자 command와 트랜잭션 범위가 커지고 자동 종결 이력을 구분해야 한다. 외부 API 호출은 트랜잭션 밖에서 수행해 DB lock 시간을 늘리지 않는다.

## ADR-016: 로컬 React SPA frontend prototype

**결정**: 루트 Spring Boot 프로젝트를 이동하지 않고 `/frontend`에 Node 24 LTS, React 19, Vite 8와 TypeScript 기반 SPA를 npm으로 관리한다. 서버 상태는 TanStack Query, route는 React Router, form은 React Hook Form과 Zod, component style은 CSS Modules, 테스트는 Vitest와 Testing Library를 사용한다. 실행 중인 `/v3/api-docs`에서 TypeScript API 타입을 생성하고 개발 요청은 Vite `/api` proxy를 통해 Spring Boot로 전달한다.

frontend source는 기능 중심의 `app`, `pages`, `features`, `shared` 경계를 사용한다. access token은 JavaScript module memory에, refresh token은 탭 단위 `sessionStorage`에 보관하며 browser 영구 저장소에는 저장하지 않는다.

**이유**: 이미 완성된 서버와 OpenAPI 계약을 유지하면서 공개 조회, 로그인 고지, 네 가지 음식점 target 작성과 내 리뷰 관리 흐름을 실제 브라우저 UI로 빠르게 검증할 수 있다.

**트레이드오프**: 탭을 닫으면 로그인 상태가 사라지고 frontend 개발 서버와 backend를 함께 실행해야 한다. `sessionStorage`는 XSS에 대한 보호 수단이 아니므로 이 SPA는 local prototype으로만 사용한다. 운영 배포, SEO, SSR, 관리자·신고 UI와 실제 카카오 브라우저 E2E를 다루지 않으며 실제 제품 클라이언트로 전환할 때 token 전달·보관과 배포 구성을 다시 결정한다.
