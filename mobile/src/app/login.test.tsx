import { pendingIntentFromLoginParams, resumedDestination } from '@/app/login';

describe('login continuation', () => {
  it('keeps a manual registration query across Kakao login', () => {
    expect(pendingIntentFromLoginParams({
      next: '/review/manual-target',
      manualQuery: ' 강남 분식 ',
    })).toEqual({ kind: 'manualReview', query: '강남 분식' });

    expect(resumedDestination({ kind: 'manualReview', query: '강남 분식' })).toEqual({
      pathname: '/review/manual-target',
      params: { query: '강남 분식' },
    });
  });

  it('only creates an existing-review intent for a positive restaurant id', () => {
    expect(pendingIntentFromLoginParams({
      next: '/review/new',
      targetType: 'EXISTING',
      restaurantId: '0',
      place: '테스트',
    })).toBeUndefined();
  });
});
