import { mockRestaurantDetail } from '@/shared/api/mockData';

describe('public mock preview', () => {
  it('includes unverified trust metadata', () => {
    expect(mockRestaurantDetail.verificationStatus).toBe('UNVERIFIED');
  });
});
