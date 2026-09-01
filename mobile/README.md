# Rider Voice Mobile

Rider Voice의 React Native 앱입니다. Expo SDK 57과 Expo Router를 사용하며 iOS와 Android를 지원합니다.

## 현재 구현 범위

- 홈에서 음식점 이름·주소 검색
- Rider Voice 리뷰가 있는 음식점과 카카오 검색 장소 구분
- 음식점 상세, 항목별 헬멧 점수, 공개 경험 확인
- 개발 빌드의 실제 카카오 로그인과 세션 복원
- 카카오에 없는 브랜드의 주소 검색과 수동 등록
- 6단계 구조화 리뷰 작성과 선택 의견 입력
- 내 활동과 리뷰 공개 상태 확인
- 공식 배포본 LINE Seed Sans KR 서체 번들
- 로딩·오류·빈 결과 상태
- 검색 API 설정 누락·연결 실패 안내

거리와 가까운 순 정렬은 이번 MVP에서 제외했습니다.

## 실행 환경

- Node.js 24 (`nvm use`로 `mobile/.nvmrc` 적용)
- pnpm 11 이상
- iOS Simulator, Android Emulator 또는 실제 기기

## 시작하기

```bash
nvm use
corepack enable
pnpm install
pnpm start
```

검색 결과에 원하는 배달 브랜드가 없으면 직접 등록을 선택합니다. 로그인 후 주소를 검색하고 브랜드명, 선택적인 상세 위치와 플랫폼을 입력한 다음 첫 리뷰를 작성합니다. 앱은 선택한 주소가 기존 픽업 장소인지 확인해 알맞은 review target을 서버에 전달합니다.

실행 후 터미널에서 `i`를 누르면 iOS Simulator, `a`를 누르면 Android Emulator가 열립니다. Expo Go는 API가 설정된 공개 조회와 UI 확인에만 사용하고 실제 카카오 로그인은 개발 빌드로 확인합니다.

## 백엔드 연결

`mobile/.env.local`을 만들고 Spring Boot API 주소를 지정합니다.

```bash
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
```

- iOS Simulator: `http://localhost:8080`
- Android Emulator: `http://10.0.2.2:8080`
- 실제 기기 공개 조회: 개발 PC의 같은 네트워크 IP 또는 HTTPS 개발 주소 사용
- 실제 기기 카카오 로그인: iPhone과 Android가 모두 접근 가능한 HTTPS 개발 주소 사용

환경 변수가 없으면 검색 화면에 설정 오류가 표시됩니다. 앱은 카카오 API나 데이터베이스를 직접 호출하지 않고 Spring Boot의 `/api/v1` API만 호출합니다.

## 품질 검사

```bash
pnpm run typecheck
pnpm run lint
pnpm run test
pnpm exec expo install --check
```

## 실제 카카오 로그인 확인

Expo Go에서는 custom scheme OAuth callback을 사용할 수 없습니다. 백엔드와 로컬 MySQL을 실행하고 `mobile/.env.local`에 API 주소를 설정한 뒤 네이티브 개발 빌드를 실행합니다.

```bash
pnpm ios:device
pnpm android:device
```

첫 실행은 Xcode 또는 Android Studio를 사용해 개발 빌드를 생성합니다. 생성되는 `/ios`, `/android` 폴더는 Git에 포함하지 않습니다.

실제 기기 로그인에서는 같은 HTTPS 개발 주소를 앱과 백엔드에 적용합니다.

```bash
# mobile/.env.local
EXPO_PUBLIC_API_BASE_URL=https://<개발-호스트>

# 프로젝트 루트 .env
KAKAO_REDIRECT_URI=https://<개발-호스트>/api/v1/auth/oauth2/callback/kakao
```

카카오 개발자 콘솔의 REST API 키 Redirect URI에도 `KAKAO_REDIRECT_URI`와 완전히 같은 주소를 등록해야 합니다. 이 값이 다르면 카카오가 `KOE006`으로 로그인을 거부합니다.

## 오픈소스 고지

앱은 LY Corp.의 `LINE Seed KR`을 사용합니다. 폰트는 SIL Open Font License 1.1로 배포되며 라이선스 전문은 `assets/fonts/OFL.txt`에 포함되어 있습니다.
