import { manualRegistrationDestination, reviewDestination } from '@/shared/navigation/reviewNavigation';

describe('review navigation', () => {
  it('sends active users directly to review creation', () => {
    expect(reviewDestination(true, { targetType: 'EXISTING', restaurantId: '10', place: '가게' })).toEqual({
      pathname: '/review/new',
      params: { targetType: 'EXISTING', restaurantId: '10', place: '가게' },
    });
  });

  it('preserves a manual registration intent through login', () => {
    expect(manualRegistrationDestination(false, '강남 분식')).toEqual({
      pathname: '/login',
      params: { next: '/review/manual-target', manualQuery: '강남 분식' },
    });
  });
});
