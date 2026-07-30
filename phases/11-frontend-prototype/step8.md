# Step 8: frontend-regression-docs

## 읽을 파일

- `/AGENTS.md`
- `/README.md`
- `/docs/*.md`
- phase 11 step 0~7 결과와 전체 backend/frontend source/test
- `/frontend/package.json`
- `/frontend/.env.example`

## 작업

- 전체 backend와 frontend 회귀 결과를 확인하고 발견한 결함만 최소 수정한다. 기능 범위를 새로 넓히지 않는다.
- README에 Node 24 준비, `frontend` 설치·개발·test·build, backend/MySQL 선행 실행, `/api` proxy와 카카오 redirect URI/`FRONTEND_BASE_URL` 로컬 설정을 정확히 추가한다.
- OpenAPI generated type 갱신 절차와 backend 계약 변경 시 `npm run api:generate`가 필요함을 기록한다.
- 문서의 frontend 구현 상태를 실제 완료 범위로 갱신하고 관리자·신고 화면, 실제 카카오 자동 E2E, Docker/AWS/production 배포는 여전히 제외임을 유지한다.
- tracked secret, service token, 카카오 token, provider error, stack trace와 frontend build 산출물이 없는지 검사한다.
- 접근성 기본 요소, UNVERIFIED 표시, 같은 장소 다른 브랜드 비노출, 종합 점수·별점·인증 배지 부재를 최종 확인한다.

## 인수 기준

```bash
./gradlew test --no-daemon
./gradlew integrationTest --no-daemon
./gradlew check build --no-daemon
cd frontend && npm ci
cd frontend && npm run api:generate
cd frontend && npm run lint
cd frontend && npm test
cd frontend && npm run build
git diff --check
```

## 검증

1. 모든 command와 architecture checklist를 확인한다.
2. 실제 카카오 로그인은 자동화하지 않고 callback/exchange는 backend와 frontend mock/contract test로 검증한다.
3. 성공 시 step 8을 `completed`로 바꾸고 전체 구현 범위와 검증 command를 summary에 기록한다.
4. MySQL/backend/OpenAPI/Node 24가 없으면 구체적인 원인을 `blocked`, 코드 결함은 3회 수정 후 `error`로 기록한다.

## 하지 말 것

- Docker, Docker Compose, Testcontainers, AWS 또는 production 배포 설정을 추가·실행하지 말 것. 이유: 현재 실행 경계 밖이다.
- test를 skip/disable/delete해 완료 처리하지 말 것. 이유: 회귀 보장을 약화한다.
- 관리자·신고 UI 또는 새로운 backend 기능을 추가하지 말 것. 이유: 승인된 frontend prototype 범위 밖이다.
- 자동 push하지 말 것. 이유: 사용자가 전체 결과를 검토한 뒤 원격 반영을 결정한다.
