# Step 4: session-lifecycle-hardening

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/1-auth-onboarding/index.json`
- `/src/main/kotlin/com/ridervoice/api/auth/application/AuthService.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/domain/UserSession.kt`
- `/src/main/kotlin/com/ridervoice/api/auth/infrastructure/persistence/UserSessionRepository.kt`
- `/src/main/kotlin/com/ridervoice/api/common/security/`

## 작업

현재 로컬 opaque service token 수명주기를 보강한다. access token record는 user ID, session ID와 15분 만료 시각을 메모리에 보관하고 인증 때 만료 및 현재 `UserStatus.ACTIVE`를 확인한다. logout은 refresh session을 비관적 lock으로 폐기하고 해당 session의 access token도 제거한다. refresh는 token hash 조회를 비관적 write lock으로 수행해 동시 요청 하나만 successor session을 만들게 하며, 기존 session 회전과 새 session 저장을 하나의 transaction에서 처리한다. refresh token 유효기간 30일은 유지한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. access token 만료, 로그아웃, SUSPENDED/WITHDRAWN 사용자와 서버 재시작 후 무효화를 테스트한다.
2. 같은 refresh token의 동시 요청에서 한 요청만 성공하고 successor가 하나인지 로컬 MySQL transaction test로 검증한다.
3. phase index step 4를 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- access token을 만료 시각 없이 user ID에만 연결하지 말 것. 이유: 탈취 token이 서버 종료 전까지 영구 유효해진다.
- refresh 조회와 회전을 잠금 없는 read-then-write로 처리하지 말 것. 이유: concurrent replay로 여러 session이 발급될 수 있다.
- 현재 로컬 단계에서 JWT나 외부 session store를 도입하지 말 것. 이유: ADR-014의 로컬 실행 범위를 벗어난다.
- 기존 test를 깨뜨리지 말 것.
