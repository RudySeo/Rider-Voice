import type { CreateReviewBody } from '@/shared/api/reviews';

describe('review write contract', () => {
  it('requires the selected target and visit month for creation', () => {
    const body = {
      restaurantTarget: { type: 'EXISTING' as const, restaurantId: 1 },
      visitMonth: '2026-08',
      pickupSpaceCleanliness: 'GOOD' as const,
      packagingStability: 'GOOD' as const,
      orderReadiness: 'GOOD' as const,
      handoffAccuracy: 'GOOD' as const,
      staffInteraction: 'GOOD' as const,
      riderRespect: 'GOOD' as const,
      comment: null,
    } satisfies CreateReviewBody;

    expect(body.restaurantTarget.type).toBe('EXISTING');
    expect(body.visitMonth).toBe('2026-08');
  });
});
