# Step 5: security-performance

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/4-operations-hardening/step3.md`
- `/phases/4-operations-hardening/step4.md`

## 작업
인증·권한·presigned URL·WriteGrant 동시성·rate limit·개인정보 응답 누출을 점검하고, 파일럿 목표 수준의 검색·리포트·리뷰 제출 부하 테스트를 수행한다.

## 인수 기준
```bash
./gradlew check
./gradlew build
```

## 하지 말 것
- 성능을 위해 개인정보 보호 규칙이나 WriteGrant 원자성을 완화하지 말 것. 이유: 핵심 불변식이 우선이다.
