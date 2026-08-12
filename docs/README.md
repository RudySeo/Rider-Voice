# Rider Voice 인증 코드 가이드

이 문서는 Rider Voice의 `auth` 패키지를 처음 읽는 개발자가 카카오 로그인부터 Rider Voice API 인증까지 빠르게 이해할 수 있도록 정리한 구현 안내서다. 제품 정책은 [PRD](PRD.md), 전체 경계는 [아키텍처](ARCHITECTURE.md), 선택 이유는 [ADR](ADR.md), 테이블은 [ERD](ERD.md)를 기준으로 한다.

## 1. 핵심 개념

- 카카오 로그인은 카카오 계정 식별 수단이며 라이더 신분이나 음식점 방문을 인증하지 않는다.
- Spring Security OAuth2 Client가 카카오 authorization code 교환과 user-info 조회를 담당한다.
- 카카오 user-info에서는 최상위 `id`만 계정 식별값으로 사용한다. 닉네임이나 이메일은 사용하지 않는다.
- OAuth handshake에만 임시 HTTP session을 사용하고 callback 성공·실패 후 즉시 폐기한다.
- Rider Voice REST API는 OAuth session을 인증으로 사용하지 않는 stateless Bearer 인증이다.
- refresh token은 browser의 `HttpOnly` cookie와 DB의 hash로 관리하고, access token은 frontend JavaScript 메모리에만 둔다.
- 로그인할 때마다 카카오 인가 요청에 `prompt=login`을 사용하므로 다른 카카오 계정으로 다시 인증할 수 있다.

## 2. 인증 API

| Method | 경로 | 인증 | 역할 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/auth/oauth2/authorization/kakao` | 공개 | 카카오 로그인 시작, `state`와 임시 session 생성 |
| `GET` | `/api/v1/auth/oauth2/callback/kakao` | 공개 | 카카오 사용자 확인, Rider Voice refresh cookie 설정, frontend redirect |
| `POST` | `/api/v1/auth/refresh` | refresh cookie | refresh token 회전, 새 cookie와 access token 발급 |
| `POST` | `/api/v1/auth/logout` | refresh cookie | Rider Voice session 폐기와 cookie 만료 |
| `GET` | `/api/v1/users/me` | Bearer `ROLE_USER` | 현재 사용자 상태·권한·약관 버전 조회 |

`refresh`와 `logout`은 Spring Security chain에서는 `permitAll`이다. access token 대신 application service가 `HttpOnly` refresh cookie를 직접 검증하기 때문이다. OpenAPI에는 `refreshCookie` 인증 방식으로 표시한다.

## 3. 로그인 전체 흐름

```text
브라우저
  -> GET /api/v1/auth/oauth2/authorization/kakao
  -> OAuth2SecurityConfig
       prompt=login 추가
       state를 임시 HTTP session에 저장
  -> 카카오 로그인과 사용자 동의
  -> GET /api/v1/auth/oauth2/callback/kakao?code=...&state=...
  -> Spring Security OAuth2 Client
       authorization code를 카카오 access token으로 교환
       카카오 user-info 요청
  -> KakaoOAuth2UserService
       최상위 id만 provider subject로 추출
  -> OAuth2LoginSuccessHandler
  -> AuthService.complete()
       기존 OAuthAccount 조회 또는 User + OAuthAccount 생성
       현재 필수 약관 기록과 User 활성화
       30일 refresh session 생성
  -> Set-Cookie: rider_voice_refresh=...; HttpOnly; SameSite=Lax
  -> frontend /auth/callback redirect
  -> POST /api/v1/auth/refresh
  -> 새 access token 응답 + refresh cookie 회전
  -> frontend 로그인 완료
```

카카오 access token은 user-info 조회에만 사용하고 Rider Voice에 저장하지 않는다. callback URL에는 카카오 token, Rider Voice token 또는 사용자 ID를 넣지 않는다.

## 4. token 저장과 수명

| 데이터 | 저장 위치 | 수명과 특징 |
| --- | --- | --- |
| OAuth `state` | 로그인용 임시 HTTP session | callback 성공·실패 후 폐기 |
| 카카오 access token | Spring Security 처리 중 | user-info 확인 후 저장하지 않음 |
| 카카오 사용자 ID | `oauth_accounts.provider_subject` | 외부 계정 식별에만 사용 |
| Rider Voice access token | backend 메모리 + frontend 메모리 | 15분, 서버 재시작 시 무효 |
| Rider Voice refresh token | browser `HttpOnly` cookie + `user_sessions` hash | 30일, refresh마다 회전 |
| 약관 동의 | `users` | 버전과 UTC 동의 시각 저장 |

refresh cookie의 기본 속성은 다음과 같다.

```text
Name: rider_voice_refresh
HttpOnly: true
SameSite: Lax
Path: /api/v1/auth
Max-Age: 30일
Domain: 설정하지 않음
Secure: 기본 true, local/test profile만 false
```

cookie의 `Path`를 인증 경로로 제한하므로 리뷰·음식점 API 요청에는 refresh token이 실리지 않는다. 일반 보호 API는 계속 `Authorization: Bearer <access-token>`을 사용한다.

## 5. refresh와 logout

### refresh

```text
POST /api/v1/auth/refresh
  -> cookie의 refresh token을 SHA-256 hash로 변환
  -> UserSession을 비관적 write lock으로 조회
  -> 만료·폐기·사용자 활성 상태 확인
  -> 새 UserSession과 access token 생성
  -> 기존 UserSession revoke 및 rotatedToSession 연결
  -> 새 refresh cookie 설정
  -> { accessToken, user } 응답
```

응답 body에는 refresh token이 없다. 동시에 같은 refresh token을 사용하면 DB lock과 session 상태 전이로 한 요청만 성공한다. 누락·만료·폐기·재사용 token은 모두 `401 AUTHENTICATION_REQUIRED`로 처리하고 cookie를 만료시킨다.

### logout

```text
POST /api/v1/auth/logout
  -> refresh cookie가 있으면 해당 UserSession revoke
  -> session과 연결된 메모리 access token 제거
  -> refresh cookie Max-Age=0
  -> 204 No Content
```

cookie가 없어도 `204`를 반환한다. 로그아웃은 Rider Voice session만 종료하며 카카오 로그아웃이나 앱 연결 해제는 호출하지 않는다. 다음 로그인은 `prompt=login`으로 카카오 계정을 다시 인증한다.

## 6. 패키지 구조

```text
com.ridervoice.api.auth
├── presentation
│   └── dto
├── application
│   └── port
│       ├── in
│       └── out
├── domain
└── infrastructure
    ├── oauth
    └── persistence
```

의존 방향은 `presentation -> application -> domain/output port -> infrastructure`다. Controller와 OAuth handler는 application input port만 호출하며 JPA repository나 외부 provider adapter를 직접 사용하지 않는다.

## 7. 운영 코드 파일

### presentation

| 파일 | 역할 |
| --- | --- |
| [AuthController.kt](../src/main/kotlin/com/ridervoice/api/auth/presentation/AuthController.kt) | refresh cookie를 application command로 변환하고 cookie 회전·만료 응답을 작성한다. 현재 사용자 API도 포함한다. |
| [AuthCookieManager.kt](../src/main/kotlin/com/ridervoice/api/auth/presentation/AuthCookieManager.kt) | refresh cookie의 이름, 보안 속성, 생성과 삭제를 한 곳에서 관리한다. |
| [OAuth2SecurityConfig.kt](../src/main/kotlin/com/ridervoice/api/auth/presentation/OAuth2SecurityConfig.kt) | OAuth 전용 security chain, 임시 session, callback, user service와 `prompt=login`을 설정한다. |
| [OAuth2LoginHandlers.kt](../src/main/kotlin/com/ridervoice/api/auth/presentation/OAuth2LoginHandlers.kt) | OAuth 성공 정보를 use case에 전달하고 refresh cookie를 설정한다. 실패 원인은 일반화해 redirect한다. |
| [AuthOpenApiConfiguration.kt](../src/main/kotlin/com/ridervoice/api/auth/presentation/AuthOpenApiConfiguration.kt) | Controller가 없는 authorization/callback 경로와 `Set-Cookie` 계약을 OpenAPI에 등록한다. |
| [AuthResponseMapper.kt](../src/main/kotlin/com/ridervoice/api/auth/presentation/AuthResponseMapper.kt) | application session 결과를 access token과 사용자 response로 변환한다. |
| [AuthResponses.kt](../src/main/kotlin/com/ridervoice/api/auth/presentation/dto/AuthResponses.kt) | `AccessSessionResponse`, `UserResponse`를 정의한다. |

### application

| 파일 | 역할 |
| --- | --- |
| [AuthService.kt](../src/main/kotlin/com/ridervoice/api/auth/application/AuthService.kt) | 계정 생성, 약관 활성화, refresh session 생성·회전·폐기와 access token 인증을 조정한다. |
| [CompleteSocialLoginUseCase.kt](../src/main/kotlin/com/ridervoice/api/auth/application/port/in/CompleteSocialLoginUseCase.kt) | OAuth 성공 시 provider와 subject를 받아 refresh session을 만드는 input port다. |
| [AuthSessionUseCases.kt](../src/main/kotlin/com/ridervoice/api/auth/application/port/in/AuthSessionUseCases.kt) | refresh, logout과 현재 사용자 조회 input port·command를 정의한다. |
| [AuthPersistencePorts.kt](../src/main/kotlin/com/ridervoice/api/auth/application/port/out/AuthPersistencePorts.kt) | 사용자, OAuth 계정과 UserSession 저장소 output port를 정의한다. |

### domain

| 파일 | 역할 |
| --- | --- |
| [User.kt](../src/main/kotlin/com/ridervoice/api/auth/domain/User.kt) | 사용자 role, 상태와 약관 동의를 관리한다. |
| [OAuthAccount.kt](../src/main/kotlin/com/ridervoice/api/auth/domain/OAuthAccount.kt) | 내부 User와 외부 `(provider, providerSubject)` 연결을 관리한다. |
| [UserSession.kt](../src/main/kotlin/com/ridervoice/api/auth/domain/UserSession.kt) | refresh hash, 만료·폐기와 후속 회전 session 상태를 관리한다. |
| [OAuthProvider.kt](../src/main/kotlin/com/ridervoice/api/auth/domain/OAuthProvider.kt) | 지원 OAuth provider를 정의한다. 현재는 `KAKAO`다. |
| [UserRole.kt](../src/main/kotlin/com/ridervoice/api/auth/domain/UserRole.kt) | `USER`, `ADMIN` 권한을 정의한다. |
| [UserStatus.kt](../src/main/kotlin/com/ridervoice/api/auth/domain/UserStatus.kt) | `PENDING_TERMS`, `ACTIVE`, 정지·탈퇴 상태를 정의한다. |

### infrastructure

| 파일 | 역할 |
| --- | --- |
| [KakaoOAuth2ClientConfiguration.kt](../src/main/kotlin/com/ridervoice/api/auth/infrastructure/oauth/KakaoOAuth2ClientConfiguration.kt) | 카카오 ClientRegistration과 provider endpoint를 구성한다. |
| [KakaoOAuth2Properties.kt](../src/main/kotlin/com/ridervoice/api/auth/infrastructure/oauth/KakaoOAuth2Properties.kt) | 카카오 client와 endpoint 설정을 바인딩한다. |
| [KakaoOAuth2UserService.kt](../src/main/kotlin/com/ridervoice/api/auth/infrastructure/oauth/KakaoOAuth2UserService.kt) | 카카오 응답에서 유효한 최상위 `id`만 추출한다. |
| [AuthPersistenceAdapter.kt](../src/main/kotlin/com/ridervoice/api/auth/infrastructure/persistence/AuthPersistenceAdapter.kt) | application output port를 Spring Data repository에 연결한다. |
| [UserRepository.kt](../src/main/kotlin/com/ridervoice/api/auth/infrastructure/persistence/UserRepository.kt) | 사용자 조회·저장과 비관적 lock을 제공한다. |
| [OAuthAccountRepository.kt](../src/main/kotlin/com/ridervoice/api/auth/infrastructure/persistence/OAuthAccountRepository.kt) | provider subject로 OAuth 계정을 조회한다. |
| [UserSessionRepository.kt](../src/main/kotlin/com/ridervoice/api/auth/infrastructure/persistence/UserSessionRepository.kt) | refresh hash 조회와 회전용 비관적 lock을 제공한다. |

## 8. auth 밖의 연결 코드

| 파일 | 역할 |
| --- | --- |
| [SecurityConfig.kt](../src/main/kotlin/com/ridervoice/api/common/security/SecurityConfig.kt) | stateless REST security chain과 endpoint 권한을 정의한다. |
| [OpaqueAccessTokenAuthenticationFilter.kt](../src/main/kotlin/com/ridervoice/api/common/security/OpaqueAccessTokenAuthenticationFilter.kt) | Bearer access token을 읽어 Spring Security 인증을 만든다. |
| [OpenApiConfiguration.kt](../src/main/kotlin/com/ridervoice/api/common/config/OpenApiConfiguration.kt) | `bearerAuth`, `refreshCookie` 보안 scheme을 정의한다. |
| [session.ts](../frontend/src/shared/api/session.ts) | refresh cookie로 access token을 복구하고 access token만 메모리에 보관한다. |
| [AuthFlow.tsx](../frontend/src/features/auth/AuthFlow.tsx) | 로그인 고지, callback, 보호 route와 로그아웃 UI를 처리한다. |
| [client.ts](../frontend/src/shared/api/client.ts) | cookie 전달을 위해 API 요청에 credentials를 포함하고 Bearer token을 설정한다. |

## 9. 처음 읽는 순서

1. 이 문서의 로그인 흐름과 token 저장 위치를 읽는다.
2. `OAuth2SecurityConfig`와 `OAuth2LoginHandlers`로 카카오 callback까지 따라간다.
3. `CompleteSocialLoginUseCase`와 `AuthService.complete()`에서 사용자 활성화와 최초 refresh session 생성을 확인한다.
4. `AuthController`와 `AuthService.refresh()`에서 cookie 회전과 access token 발급을 확인한다.
5. `UserSession`과 persistence adapter에서 hash·lock·회전 상태를 확인한다.
6. `SecurityConfig`, access token filter와 frontend `session.ts`로 전체 API 인증을 연결한다.

## 10. 변경 시 검증

- endpoint나 DTO를 바꾸면 OpenAPI annotation, 계약 테스트와 frontend 생성 타입을 함께 갱신한다.
- callback을 바꾸면 `state`, 임시 session 폐기, token URL 비노출과 일반화된 실패 redirect를 유지한다.
- cookie를 바꾸면 `HttpOnly`, `SameSite`, `Secure`, `Path`, 만료·삭제 속성을 성공·실패 테스트로 검증한다.
- refresh 정책을 바꾸면 DB lock, 이전 token 재사용 거부와 동시 refresh 테스트를 유지한다.
- token, secret, provider 오류와 stack trace가 response·URL·로그에 노출되지 않게 한다.

```bash
./gradlew test
./gradlew integrationTest  # 실행 중인 로컬 MySQL 필요

cd frontend
nvm use
npm test
npm run lint
npm run build
```

Docker와 Testcontainers는 이 검증에 사용하지 않는다.
