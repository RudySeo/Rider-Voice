# Step 7: visit-api

## 읽을 파일
- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/phases/2-visit-verification/step0.md`
- `/phases/2-visit-verification/step1.md`
- `/phases/2-visit-verification/step5.md`
- `/phases/2-visit-verification/step6.md`

## 작업
`POST /api/v1/visits/upload-url`, `POST /api/v1/visits`, `GET /api/v1/visits/{id}`, `POST /api/v1/visits/{id}/confirm-restaurant`를 구현한다. 사용자 소유권, 상태별 허용 action, 비동기 처리 상태와 승인 결과를 DTO로 제공한다.

## 인수 기준
```bash
./gradlew test
./gradlew check
```

## 하지 말 것
- 방문 상태를 controller에서 임의 변경하지 말 것. 이유: domain policy가 단일 상태 전이 소유자다.
