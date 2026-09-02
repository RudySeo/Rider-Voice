import { reviewSearchRoute, reviewTargetRoute } from '@/shared/navigation/reviewRoutes';

describe('review navigation', () => {
  it('sends authenticated users directly to an existing restaurant review', () => {
    expect(reviewTargetRoute(true, { type: 'EXISTING', restaurantId: 42, place: '테스트 식당' })).toEqual({
      pathname: '/review/new',
      params: { targetType: 'EXISTING', restaurantId: '42', place: '테스트 식당' },
    });
  });

  it('preserves a Kakao target through login for anonymous users', () => {
    expect(reviewTargetRoute(false, { type: 'KAKAO', query: '테스트', kakaoPlaceId: 'kakao-1', place: '테스트 식당' })).toEqual({
      pathname: '/login',
      params: { next: '/review/new', targetType: 'KAKAO', query: '테스트', kakaoPlaceId: 'kakao-1', place: '테스트 식당' },
    });
  });

  it('opens review search directly only for authenticated users', () => {
    expect(reviewSearchRoute(true)).toEqual({ pathname: '/search', params: { mode: 'review' } });
    expect(reviewSearchRoute(false)).toEqual({ pathname: '/login', params: { next: '/review/search' } });
  });
});
