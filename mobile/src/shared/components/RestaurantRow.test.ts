import { candidateStatusLabel } from '@/shared/components/RestaurantRow';

const candidate = {
  candidateType: 'INTERNAL' as const,
  restaurantId: 1,
  kakaoPlaceId: null,
  name: '브랜드',
  address: '서울',
  aggregationStatus: 'NO_REVIEWS' as const,
  contributorCount: 0,
};

it('does not invite an ineligible user to write the first review', () => {
  expect(candidateStatusLabel(candidate, false)).toBe('아직 등록된 경험이 없어요');
  expect(candidateStatusLabel(candidate, true)).toContain('첫 리뷰');
});
