# Step 3: production-readiness

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/5-aws-deployment/step0.md`
- `/phases/5-aws-deployment/step1.md`
- `/phases/5-aws-deployment/step2.md`

## 작업
health/readiness, backup·restore, secret rotation, alert threshold, OCR DLQ 처리, 증빙 삭제 모니터링과 파일럿 운영 runbook을 검증한다. React Native 작업은 이 phase 완료 후 별도 task로 시작한다.

## 인수 기준
```bash
terraform validate
./gradlew check
```

## 하지 말 것
- 운영 준비 검증 전에 React Native phase를 추가 실행하지 말 것. 이유: 서버 API 우선 범위 때문이다.
