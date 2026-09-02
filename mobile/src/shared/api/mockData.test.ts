import { mockRestaurantDetail } from '@/shared/api/mockData';

describe('public mock preview', () => {
  it('does not expose internal rider verification metadata', () => {
    expect(mockRestaurantDetail).not.toHaveProperty('verificationStatus');
    expect(mockRestaurantDetail).not.toHaveProperty('verificationNotice');
  });
});
