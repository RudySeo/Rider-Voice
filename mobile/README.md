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

실행 후 터미널에서 `i`를 누르면 iOS Simulator, `a`를 누르면 Android Emulator가 열립니다. Expo Go는 API가 설정된 공개 조회와 UI 확인에만 사용하고 실제 카카오 로그인은 개발 빌드로 확인합니다. 웹은 카카오 로그인과 SecureStore 세션을 지원하지 않습니다.

## 백엔드 연결

기본 실행은 별도 환경 변수 없이 로컬 Spring Boot API를 사용합니다.

```bash
pnpm start
```

- iOS Simulator: `http://localhost:8080`
- Android Emulator: `http://10.0.2.2:8080`
- 실제 기기: Metro의 private LAN IP에 `8080`을 적용

Expo tunnel 또는 특수한 네트워크에서 자동 감지가 불가능하면 `mobile/.env.local`의 `EXPO_PUBLIC_LOCAL_API_BASE_URL`로 로컬 주소를 재정의합니다. 앱은 카카오 API나 데이터베이스를 직접 호출하지 않고 Spring Boot의 `/api/v1` API만 호출합니다.

AWS API를 사용할 때는 callback 경로가 없는 HTTPS origin을 설정하고 AWS 전용 실행 명령을 사용합니다.

```bash
# mobile/.env.local
EXPO_PUBLIC_AWS_API_BASE_URL=https://<AWS-DOMAIN>

pnpm start:aws
```

프로필을 전환할 때는 실행 중인 Metro를 종료하고 `pnpm start` 또는 `pnpm start:aws`로 다시 시작합니다. 각 명령은 프로필별 Metro 변환 캐시를 사용하므로 별도로 `--clear`를 붙일 필요가 없습니다. `NODE_ENV`로 API 환경을 선택하지 않습니다.

## 품질 검사

```bash
pnpm run typecheck
pnpm run lint
pnpm run test
pnpm exec expo install --check
```

## 실제 카카오 로그인 확인

Expo Go에서는 custom scheme OAuth callback을 사용할 수 없습니다. 백엔드와 로컬 MySQL을 실행하고 선택한 프로필의 API에 기기가 접근할 수 있는지 확인한 뒤 네이티브 개발 빌드를 실행합니다.

```bash
pnpm ios:device
pnpm android:device
```

첫 실행은 Xcode 또는 Android Studio를 사용해 개발 빌드를 생성합니다. 생성되는 `/ios`, `/android` 폴더는 Git에 포함하지 않습니다.

개발 빌드가 이미 설치되어 있다면 선택한 프로필의 Metro를 다음 명령으로 다시 연결합니다. 터미널의 `w`는 로그인 확인 대상이 아닙니다.

```bash
pnpm ios:aws
# 또는
pnpm android:aws
```

실제 기기 로그인에서는 AWS 프로필과 같은 HTTPS 주소를 앱과 백엔드에 적용합니다.

```bash
# mobile/.env.local
EXPO_PUBLIC_AWS_API_BASE_URL=https://<개발-호스트>

# 프로젝트 루트 .env
KAKAO_REDIRECT_URI=https://<개발-호스트>/api/v1/auth/oauth2/callback/kakao

pnpm ios:device:aws
# 또는
pnpm android:device:aws
```

카카오 개발자 콘솔의 REST API 키 Redirect URI에도 `KAKAO_REDIRECT_URI`와 완전히 같은 주소를 등록해야 합니다. 이 값이 다르면 카카오가 `KOE006`으로 로그인을 거부합니다.

## 오픈소스 고지

앱은 LY Corp.의 `LINE Seed KR`을 사용합니다. 폰트는 SIL Open Font License 1.1로 배포되며 라이선스 전문은 `assets/fonts/OFL.txt`에 포함되어 있습니다.
