# Step 0: evidence-domain

## 읽을 파일
- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/1-identity-restaurants/index.json`

## 작업
`VisitEvidence` 도메인과 상태 전이, 앱 종류, 업로드 메타데이터, 주문 HMAC·이미지 hash 저장 모델을 구현한다. 7일 만료, 중복 보류, 수동 검수 전환 규칙을 domain policy로 둔다.

## 인수 기준
```bash
./gradlew test
```

## 하지 말 것
- 원본 이미지 bytes를 PostgreSQL에 저장하지 말 것. 이유: 비공개 object storage 경계를 사용한다.
