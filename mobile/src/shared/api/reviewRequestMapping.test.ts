import { buildCreateReviewRequest, buildUpdateReviewRequest } from '@/shared/api/reviewRequestMapping';

const values = {
  packagingStability: 'GOOD' as const,
  orderReadiness: 'GOOD' as const,
  handoffAccuracy: 'GOOD' as const,
  pickupSpaceCleanliness: 'NOT_OBSERVED' as const,
  staffInteraction: 'GOOD' as const,
  riderRespect: 'VERY_GOOD' as const,
  comment: '  도움이 됐어요  ',
  visitMonth: '2026-09',
};

describe('review request mapping', () => {
  it('includes a manual target and visit month only when creating', () => {
    const target = {
      type: 'MANUAL_EXISTING_LOCATION' as const,
      pickupLocationId: 8,
      name: '강남 분식',
      platforms: ['BAEMIN' as const],
    };

    expect(buildCreateReviewRequest(values, target)).toEqual({
      ...values,
      comment: '도움이 됐어요',
      restaurantTarget: target,
    });
  });

  it('does not send immutable creation fields when updating', () => {
    expect(buildUpdateReviewRequest(values)).toEqual({
      packagingStability: 'GOOD',
      orderReadiness: 'GOOD',
      handoffAccuracy: 'GOOD',
      pickupSpaceCleanliness: 'NOT_OBSERVED',
      staffInteraction: 'GOOD',
      riderRespect: 'VERY_GOOD',
      comment: '도움이 됐어요',
    });
  });
});
