# Step 0: moderation-domain

## 읽을 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/API_SPEC.md`
- review/restaurant states from phases 6~8

## 작업

- moderation domain에 review report, restaurant info report, decision과 audit policy를 정의한다.
- report status, reason과 admin decision enum을 provider/HTTP 비종속으로 둔다.
- comment approval/rejection, report dismiss, comment hide와 full review exclusion 전이를 정의한다.
- full exclusion은 review visibility와 current pointer 제거를 요구하고 cooldown은 유지한다.
- duplicate restaurant merge와 pickup relink audit action을 정의한다.

## 인수 기준

```bash
./gradlew test --no-daemon
```

## 검증

1. 모든 허용/거부 state transition을 domain test로 검증한다.
2. 성공 시 step 0을 `completed`로 기록한다.
3. 실패 3회 시 `error`, 정책 결정 필요 시 `blocked`로 기록한다.

## 하지 말 것

- report 접수만으로 구조화 rating을 숨기지 말 것.
- admin 판단을 audit 없이 적용하지 말 것.
- 음식점 운영 주체나 같은 장소 브랜드를 공개하는 모델을 추가하지 말 것.
