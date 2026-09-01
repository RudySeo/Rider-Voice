import { reviewDestination } from '@/shared/navigation/reviewNavigation';

describe('restaurant detail write destination', () => {
  it('preserves the selected restaurant through login', () => {
    expect(reviewDestination(false, {
      targetType: 'EXISTING',
      restaurantId: '33',
      place: '배달 브랜드',
    })).toEqual({
      pathname: '/login',
      params: {
        next: '/review/new',
        targetType: 'EXISTING',
        restaurantId: '33',
        place: '배달 브랜드',
      },
    });
  });
});
