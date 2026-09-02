import { reviewTargetRoute } from '@/shared/navigation/reviewRoutes';

describe('restaurant detail review navigation', () => {
  it('opens the selected restaurant review directly for an authenticated user', () => {
    expect(reviewTargetRoute(true, { type: 'EXISTING', restaurantId: 9, place: '식당' }).pathname).toBe('/review/new');
  });
});
