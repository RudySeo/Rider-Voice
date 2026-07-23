# Step 1: core-flow-tests

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/phases/5-local-hardening/index.json`
- `/src/main/kotlin/com/ridervoice/api/`

## 작업

카카오 로그인과 onboarding token adapter stub부터 음식점 검색, 리뷰 초안, 증빙 검증, WriteGrant, 초안의 정식 리뷰 전환과 리포트 공개까지 핵심 흐름의 Docker 없는 테스트를 작성한다. 외부 카카오와 선택된 증빙 추출 provider는 stub, 저장소는 계층별 fake 또는 실행 중인 로컬 MySQL 정책을 사용한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
```

## 검증

1. 정상 흐름과 각 경계 실패가 ProblemDetail로 일관되게 반환되는지 확인한다.
2. index step 1을 `completed`로 바꾸고 summary를 추가한다.

## 하지 말 것

- Docker/Testcontainers를 사용하지 말 것. 이유: 현재 로컬 실행 결정과 충돌한다.
- 기존 테스트를 삭제하거나 약화하지 말 것. 이유: 회귀 보호가 줄어든다.
