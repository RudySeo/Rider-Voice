export type ReviewNavigationTarget =
  | { type: 'EXISTING'; restaurantId: number; place: string }
  | { type: 'KAKAO'; query: string; kakaoPlaceId: string; place: string };

export function reviewTargetRoute(authenticated: boolean, target: ReviewNavigationTarget) {
  const targetParams = target.type === 'EXISTING'
    ? { targetType: target.type, restaurantId: String(target.restaurantId), place: target.place }
    : { targetType: target.type, query: target.query, kakaoPlaceId: target.kakaoPlaceId, place: target.place };

  if (authenticated) return { pathname: '/review/new' as const, params: targetParams };
  return { pathname: '/login' as const, params: { next: '/review/new', ...targetParams } };
}

export function reviewSearchRoute(authenticated: boolean) {
  if (authenticated) return { pathname: '/search' as const, params: { mode: 'review' } };
  return { pathname: '/login' as const, params: { next: '/review/search' } };
}

export function reviewedRestaurantRoute(
  reviewMode: boolean,
  authenticated: boolean,
  target: { restaurantId: number; place: string },
) {
  if (reviewMode) return reviewTargetRoute(authenticated, { type: 'EXISTING', ...target });
  return `/restaurant/${target.restaurantId}` as const;
}
