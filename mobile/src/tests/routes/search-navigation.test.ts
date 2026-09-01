import { manualRegistrationDestination, reviewDestination } from '@/shared/navigation/reviewNavigation';

describe('search write destinations', () => {
  it('opens review creation directly for an authenticated user', () => {
    expect(reviewDestination(true, {
      targetType: 'EXISTING',
      restaurantId: '12',
      place: '강남 분식',
    })).toEqual({
      pathname: '/review/new',
      params: { targetType: 'EXISTING', restaurantId: '12', place: '강남 분식' },
    });
  });

  it('keeps the search query when manual registration requires login', () => {
    expect(manualRegistrationDestination(false, '강남 분식')).toEqual({
      pathname: '/login',
      params: { next: '/review/manual-target', manualQuery: '강남 분식' },
    });
  });
});
