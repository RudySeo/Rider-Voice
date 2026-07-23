# Step 3: auth-inbound-adapters

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/5-auth-hexagonal-refactor/index.json`
- `/src/main/kotlin/com/ridervoice/api/auth/application/port/in/`
- `/src/main/kotlin/com/ridervoice/api/auth/application/model/`
- `/src/main/kotlin/com/ridervoice/api/auth/presentation/AuthController.kt`
- `/src/main/kotlin/com/ridervoice/api/common/security/`

## 작업

인증 HTTP와 security inbound adapter를 새 application 계약에 연결한다. request/response DTO를 `auth/presentation/dto/AuthRequests.kt`, `AuthResponses.kt`로 분리하고 HTTP mapper를 둔다. Controller는 input port에만 의존하며 request를 command로, result를 response로 변환한다. token authentication adapter는 token 인증 use case 결과를 기존 `BearerPrincipal`로 변환해 filter 계약을 유지한다. OpenAPI schema와 endpoint wire shape은 변경하지 않는다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. Controller 파일에 공개 DTO가 남지 않고 application result를 직접 반환하지 않는지 확인한다.
2. ROLE_ONBOARDING과 ROLE_USER mapping, 인증 filter와 MockMvc 계약을 검증한다.
3. `phases/5-auth-hexagonal-refactor/index.json`의 step 3을 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- Controller에서 token hash, 사용자 상태 전이 또는 repository 조회를 수행하지 말 것. 이유: application 책임을 침범하면 안 된다.
- 기존 endpoint path, status 또는 JSON field를 변경하지 말 것. 이유: 호환 리팩터링이다.
- 기존 test를 깨뜨리지 말 것.
