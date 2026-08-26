import { reviewedRestaurantRoute, reviewTargetRoute } from '@/shared/navigation/reviewRoutes';

describe('search review target navigation', () => {
  it('does not send an authenticated Kakao target through login again', () => {
    expect(reviewTargetRoute(true, { type: 'KAKAO', query: '김밥', kakaoPlaceId: 'place-1', place: '김밥집' })).toEqual({
      pathname: '/review/new',
      params: { targetType: 'KAKAO', query: '김밥', kakaoPlaceId: 'place-1', place: '김밥집' },
    });
  });

  it('uses a reviewed restaurant as the selected target in review mode', () => {
    expect(reviewedRestaurantRoute(true, true, { restaurantId: 9, place: '김밥집' })).toEqual({
      pathname: '/review/new',
      params: { targetType: 'EXISTING', restaurantId: '9', place: '김밥집' },
    });
    expect(reviewedRestaurantRoute(false, true, { restaurantId: 9, place: '김밥집' })).toBe('/restaurant/9');
  });
});
