# Step 0: moderation-admin-api

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/3-reviews-reports/step3.md`

## 작업
관리자 role 전용으로 OCR 수동 검수, 자유 의견 검수, 위험 리뷰 보류와 신고 처리 API를 구현한다. 상태 변경마다 사유와 audit event를 남긴다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- 관리자 API의 role 검사를 client parameter로 대체하지 말 것. 이유: 권한 상승을 막아야 한다.
