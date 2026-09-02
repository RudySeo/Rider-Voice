# Step 7: mobile-role-gating

## 읽을 파일

- `/docs/design.md`
- `/mobile/src/shared/auth`
- `/mobile/src/app/activity.tsx`
- `/mobile/src/shared/components/BottomTabBar.tsx`
- `/phases/12-rider-role-review-access/step6.md`

## 작업

내 활동 화면에 USER용 6자리 인증 카드를 추가하고 성공 시 AuthContext의 role을 즉시 RIDER로 갱신한다. 리뷰 탭, 작성·수정·첫 리뷰·수동 등록 진입점은 RIDER/ADMIN에만 표시하며 직접 route도 차단한다. 방문 인증 안내 화면·배너·문구는 제거한다.

## 인수 기준

```bash
cd mobile && pnpm run typecheck && pnpm run lint && pnpm run test
```

## 검증

익명·USER·RIDER·ADMIN별 UI 가시성과 인증 실패·잠금 메시지를 확인하고 phase index를 갱신한다.

## 하지 말 것

- UI 숨김만으로 서버 권한을 대체하지 말 것. 이유: 직접 API 호출을 막지 못한다.
- Expo Go에서 인증 성공을 mock으로 가장하지 말 것.
