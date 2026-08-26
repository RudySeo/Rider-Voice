import { groupSearchResults } from '@/shared/api/restaurants';
import type { RestaurantSearchCandidate } from '@/shared/api/types';

describe('mobile review target grouping', () => {
  it('keeps reviewed, registered and Kakao targets separate', () => {
    const rows: RestaurantSearchCandidate[] = [
      { candidateType: 'INTERNAL', restaurantId: 1, kakaoPlaceId: null, name: 'A', address: 'a', aggregationStatus: 'PUBLISHED', contributorCount: 5 },
      { candidateType: 'INTERNAL', restaurantId: 2, kakaoPlaceId: null, name: 'B', address: 'b', aggregationStatus: 'NO_REVIEWS', contributorCount: 0 },
      { candidateType: 'KAKAO', restaurantId: null, kakaoPlaceId: 'k3', name: 'C', address: 'c', aggregationStatus: 'NO_REVIEWS', contributorCount: 0 },
    ];
    const groups = groupSearchResults(rows);
    expect(groups.reviewed).toHaveLength(1);
    expect(groups.registered).toHaveLength(1);
    expect(groups.kakao).toHaveLength(1);
  });
});
