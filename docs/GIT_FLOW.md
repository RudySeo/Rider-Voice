# Rider Voice Git Flow 전략

## 브랜치 구조

```text
master       운영·배포 기준
develop      다음 릴리스 통합 기준
feature/*    기능 개발
release/*    릴리스 준비
hotfix/*     운영 긴급 수정
```

## 기본 규칙

- `master`는 운영 배포 가능한 코드만 보관한다.
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

## 릴리스

```text
develop → release/v0.1.0 → master
                         ↘ develop 동기화
```

릴리스 브랜치에서 버전, migration, 문서와 운영 설정을 점검한다. `master` merge 후 태그를 생성한다.

## 긴급 수정

```text
master → hotfix/<short-name> → master
                             ↘ develop 동기화
```

운영 장애나 보안 취약점만 `hotfix/*`를 사용한다. 수정 후 `master`와 `develop` 양쪽에 반영한다.

## 프로젝트 개발 순서

서버 API와 OpenAPI 계약을 먼저 완성한다.

1. 인증·인가
2. 음식점·지역 제한
3. 방문 증빙·OCR
4. WriteGrant·리뷰
5. 리포트·관리자 기능
6. 보안·통합 검증
7. React Native 클라이언트
