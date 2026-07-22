# Step 0: auth-domain

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/0-backend-foundation/index.json`

## 작업
`User`, `OAuthAccount`, `UserSession` 도메인과 JPA persistence를 구현한다. 카카오 외부 타입은 domain에 노출하지 않는다. UserStatus와 session rotation/revocation 정책을 domain method로 표현하고 migration 및 repository 테스트를 추가한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- OAuth HTTP 호출을 구현하지 말 것. 이유: 이 step은 domain과 persistence만 담당한다.
