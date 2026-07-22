# Step 2: auth-api

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-identity-restaurants/step0.md`
- `/phases/1-identity-restaurants/step1.md`

## 작업
카카오 callback, User upsert, access/refresh token 발급·회전·로그아웃 API를 구현한다. `/api/v1/auth/**`와 `/api/v1/users/me` 계약을 OpenAPI에 기록하고 인증 principal을 security context에 연결한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- 카카오 access token을 장기 저장하지 말 것. 이유: 서비스 세션과 provider credential을 분리해야 한다.
