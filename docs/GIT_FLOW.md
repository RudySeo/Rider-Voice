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
4. 리뷰 이력, 90일 작성 제한과 의견 검수
5. 공개 음식점 조회와 작성자 5명 집계
6. 신고, 관리자 처리와 음식점 병합
7. 보안·동시성·로컬 MySQL 통합 검증
8. 실제 사용자용 웹 또는 React Native 클라이언트
9. schema migration과 배포 인프라는 운영 결정 후 진행
