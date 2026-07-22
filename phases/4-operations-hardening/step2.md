# Step 2: evidence-retention

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-visit-verification/step1.md`

## 작업
OCR 성공 원본 즉시 삭제, 수동 검수 원본 72시간 삭제, 실패 object 정리, S3 lifecycle 보조 정책과 삭제 관측을 구현한다. 삭제 실패는 DLQ 또는 관리자 알림으로 남긴다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- 원본 삭제 실패를 조용히 무시하지 말 것. 이유: 개인정보 보존 정책 위반을 숨기면 안 된다.
