# Step 3: audit-observability

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/4-operations-hardening/step0.md`
- `/phases/4-operations-hardening/step2.md`

## 작업
관리자 감사 로그, OCR 처리시간·성공률, queue backlog, 삭제 실패, API 오류와 rate limit metric을 추가한다. 민감한 원문과 token은 로그에 남기지 않는다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- 이미지 원문, OAuth token, refresh token을 로그로 출력하지 말 것. 이유: 로그도 개인정보 경계다.
