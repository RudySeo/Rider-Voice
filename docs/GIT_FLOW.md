# Rider Voice Git Flow 전략

## 브랜치 구조

```text
master       향후 배포 기준
develop      현재 통합 개발 기준
feature/*    기능 개발
release/*    릴리스 준비
hotfix/*     운영 긴급 수정
```

## 기본 규칙

- 현재 개발은 `develop`에서 진행하며 `master`는 향후 배포 기준으로 유지한다.
- `develop`은 다음 릴리스의 통합 브랜치다.
- 새 작업은 항상 `develop`에서 `feature/<short-name>`을 생성한다.
- `master`와 `develop`에 직접 push하지 않고 Pull Request를 사용한다.
- 기존 작업 브랜치는 이력 보존을 위해 삭제하지 않는다.
- `.env`, API key, secret, 개인정보를 커밋하지 않는다.
- 커밋 메시지는 Conventional Commits를 따른다.

## 기능 개발

```bash
git checkout develop
git pull origin develop
git checkout -b feature/kakao-auth
```

작업 완료 후 `feature/*`에서 `develop`으로 Pull Request를 생성한다.

PR 필수 조건:

- `./gradlew test` 통과
- 관련 문서와 OpenAPI 계약 반영
- 보안정보 미포함
- 기존 테스트 삭제·약화 금지
- 리뷰 승인 후 merge

## 자동 Draft PR, CI와 Codex 리뷰

`feature/*`와 기존 `feat/*` 브랜치에 `develop`보다 앞선 변경 커밋을 처음 push하면 `.github/workflows/feature-pr.yml`이 `develop` 대상 Draft PR을 생성한다. 로컬 브랜치 생성이나 변경 커밋이 없는 최초 push만으로는 PR을 만들 수 없으며, 다음 변경 push에서 다시 시도한다. 같은 브랜치에 열린 PR이 있으면 새 PR을 만들지 않는다.

workflow는 push마다 JDK 21에서 backend `./gradlew build`를 실행한다. 자동 생성되는 Draft PR 본문은 한글 `변경 요약`과 `확인 사항` 체크리스트로 구성한다.

로컬 MySQL이 필요한 `integrationTest`는 이 GitHub-hosted CI에 포함하지 않는다. 현재 자동화는 CI 실패나 리뷰 결과로 merge를 강제 차단하지 않으며 결과를 PR 판단 자료로 제공한다.

Codex 리뷰를 사용하려면 Codex Cloud에 저장소를 연결하고 해당 저장소의 Code review와 Automatic reviews를 활성화한다. Draft PR의 변경이 검토 가능한 상태가 되면 Ready for review로 전환해 자동 리뷰를 요청하며, 재검토가 필요하면 PR 댓글에 `@codex review`를 작성한다. Codex는 루트와 변경 파일에 적용되는 `AGENTS.md`의 `Code Review Rules`를 따른다.

자동 PR 생성에는 GitHub 저장소의 `Settings > Actions > General > Workflow permissions`에서 `Allow GitHub Actions to create and approve pull requests` 설정이 필요하다. workflow 자체는 `contents: read`와 PR 생성 job의 `pull-requests: write`만 사용한다.

## 릴리스 (후속 단계)

```text
develop → release/v0.1.0 → master
                         ↘ develop 동기화
```

로컬 API 기능과 클라이언트 계약이 안정화된 후에만 릴리스를 진행한다. 릴리스 브랜치에서 버전, Entity schema mapping, 문서와 운영 설정을 점검한다.

## 긴급 수정 (배포 후)

```text
master → hotfix/<short-name> → master
                             ↘ develop 동기화
```

운영 장애나 보안 취약점만 `hotfix/*`를 사용한다. 수정 후 `master`와 `develop` 양쪽에 반영한다.

## 프로젝트 개발 순서

서버 API와 OpenAPI 계약을 먼저 완성한다.

1. 공개 리뷰 PRD·ADR·아키텍처와 OpenAPI 계약
2. Spring Security OAuth2 Client 기반 카카오 로그인 전환
3. 픽업 장소·배달 브랜드·외부 참조 모델
4. 음식점별 활성 리뷰 1개, 삭제·전체 제외 후 90일 제한과 의견 검수
5. 공개 음식점 조회와 작성자 5명 집계
6. 신고, 관리자 처리와 음식점 병합
7. 보안·동시성·로컬 MySQL 통합 검증
8. 실제 사용자용 웹 또는 React Native 클라이언트
9. schema migration과 배포 인프라는 운영 결정 후 진행
