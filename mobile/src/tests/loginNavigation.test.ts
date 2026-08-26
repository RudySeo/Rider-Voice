import { reviewSearchRoute } from '@/shared/navigation/reviewRoutes';

describe('login review return path', () => {
  it('returns anonymous review-tab users to target search after authentication', () => {
    expect(reviewSearchRoute(false)).toEqual({ pathname: '/login', params: { next: '/review/search' } });
  });
});
