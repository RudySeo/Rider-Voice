# Step 3: foundation-verification

## 읽을 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/0-local-foundation/index.json`
- `/src/main/kotlin/com/ridervoice/api/common/config/OpenApiConfiguration.kt`
- `/src/main/kotlin/com/ridervoice/api/common/error/`
- `/src/main/kotlin/com/ridervoice/api/common/security/`

## 작업

foundation 검증 전용 테스트와 로컬 실행 문서를 완성한다. `/v3/api-docs`에 `/api/v1` endpoint와 Bearer scheme이 포함되고 `/swagger-ui.html`이 공개되는지 검증한다. 기본 Gradle 검증 경로가 Docker 없이 끝나고 로컬 MySQL 실행 방법이 README와 일치해야 한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. Architecture와 ADR checklist를 확인한다.
2. `phases/0-local-foundation/index.json`의 step 3을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- Docker 기반 테스트를 acceptance criteria에 추가하지 말 것. 이유: 현재 로컬 전용 결정과 충돌한다.
- Swagger 문서에 secret이나 실제 token 예시를 넣지 말 것. 이유: 자격 증명 노출 위험이 있다.
- 기존 test를 깨뜨리지 말 것.
