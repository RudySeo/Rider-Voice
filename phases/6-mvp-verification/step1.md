# Step 1: mvp-flow-regression

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/5-auth-hexagonal-refactor/index.json`
- `/phases/6-mvp-verification/index.json`
- `/src/main/kotlin/com/ridervoice/api/`
- `/src/test/kotlin/com/ridervoice/api/`
- `/README.md`

## 작업

카카오 adapter stub을 사용해 로그인·약관 동의, 음식점 검색·지연 등록과 비공개 리뷰 CRUD까지의 MVP 회귀 테스트를 완성한다. 정상 흐름과 미인증, 잘못된 장소 선택, 음식점 중복 등록, 리뷰 중복 생성, 타인 리뷰 접근, 수정과 삭제 실패를 검증한다. 문서의 구현 상태와 API 목록을 실제 코드에 맞추고 전체 기본 build를 확인한다.

## 인수 기준

```bash
./gradlew test
./gradlew check
./gradlew build
```

## 검증

1. 외부 네트워크, Docker와 Testcontainers 없이 기본 검증 명령이 통과하는지 확인한다.
2. 로컬 MySQL 환경이 준비된 경우 `./gradlew integrationTest`로 Hibernate schema, 연관관계와 transaction 제약을 추가 확인한다.
3. `phases/6-mvp-verification/index.json`의 step 1과 상위 phase를 완료 상태와 한 줄 summary로 갱신한다.

## 하지 말 것

- 방문 증빙, OCR, 공개 리포트, 관리자 기능이나 클라이언트를 추가하지 말 것. 이유: 현재 MVP 범위를 벗어난다.
- 실제 카카오 API나 secret에 테스트를 의존시키지 말 것. 이유: Harness 실행이 외부 상태로 막히지 않아야 한다.
- 기존 test를 깨뜨리지 말 것.
