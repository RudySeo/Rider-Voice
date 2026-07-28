# Step 6: review-create

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/API_SPEC.md`
- phase 11 step 3~5의 session, auth, search와 detail frontend
- review create/address search OpenAPI generated types

## 작업

- 네 가지 restaurant target과 review validation의 실패하는 Testing Library 테스트를 먼저 작성한다.
- `/reviews/new`의 명시적인 단계형 흐름을 구현한다: 음식점 선택 또는 전달된 후보 확인, 필요 시 주소/브랜드 정보, 방문·평가 입력, 최종 확인과 제출.
- INTERNAL 후보는 `EXISTING`, KAKAO 후보는 검색 원문과 place ID를 보존해 `KAKAO` payload로 전송한다.
- 카카오에 없는 브랜드 흐름은 인증된 주소 검색 API만 사용한다. 선택 결과의 `existingPickupLocationId`가 있으면 `MANUAL_EXISTING_LOCATION`, 없으면 `MANUAL_ADDRESS` payload를 만들고 표준 주소를 사용자 임의 문자열로 대체하지 않는다.
- 수동 브랜드명, 선택 플랫폼, 선택 상세 주소를 입력받되 API 길이와 필수 조건을 Zod로 검증한다.
- 방문 연월은 Asia/Seoul 기준 현재 또는 직전 달 선택지만 제공한다. 6개 평가는 모두 필수이고 `VERY_GOOD`, `GOOD`, `NEEDS_IMPROVEMENT`, `MAJOR_IMPROVEMENT`, `NOT_OBSERVED`의 한국어 설명을 제공한다.
- comment는 선택, trim 후 최대 200자이며 사전 검수 후 공개된다는 안내를 표시한다.
- 성공 시 응답의 restaurant ID 상세로 이동한다. 409는 90일 작성 제한, 503은 provider 확인 불가, validation/401/기타 오류는 각각 안전한 메시지로 구분한다.

## 인수 기준

```bash
cd frontend && npm run lint
cd frontend && npm test
cd frontend && npm run build
git diff --check
```

## 검증

1. EXISTING, KAKAO, MANUAL_EXISTING_LOCATION, MANUAL_ADDRESS 각각의 정확한 discriminator payload를 테스트한다.
2. 6개 필수 평가, visit month, 200자 trim, 플랫폼/브랜드/주소 validation과 409/503 UI를 테스트한다.
3. 성공 시 step 6을 `completed`로 바꾸고 review wizard, 네 target mapper와 tests를 summary에 기록한다.
4. 3회 수정 후에도 실패하면 `error`, API 계약 불일치는 `blocked`로 기록한다.

## 하지 말 것

- 사용자가 입력한 주소, 좌표 또는 카카오 place ID를 provider 검증 없이 별도 endpoint로 저장하지 말 것. 이유: 음식점 신뢰 규칙을 위반한다.
- 이미지 업로드, OCR 또는 방문·라이더 인증 UI를 만들지 말 것. 이유: 명시적 제품 제외 범위다.
- `NOT_OBSERVED`를 선택 불가나 누락 값으로 처리하지 말 것. 이유: 유효한 필수 평가 값이다.
- 기존 테스트를 삭제하거나 약화하지 말 것.
