import { buildManualReviewTarget } from '@/shared/api/reviewTargets';

describe('manual target screen contract', () => {
  it('does not trust client coordinates when continuing to review', () => {
    const target = buildManualReviewTarget({
      addressQuery: '서울 강남구 역삼동',
      candidate: {
        standardAddress: '서울 강남구 테헤란로 1',
        lotNumberAddress: null,
        latitude: 37.5,
        longitude: 127.0,
        existingPickupLocationId: null,
      },
      detailAddress: '2층',
      name: '강남 분식',
      platforms: ['YOGIYO'],
    });

    expect(target).toEqual({
      type: 'MANUAL_ADDRESS',
      addressQuery: '서울 강남구 역삼동',
      selectedStandardAddress: '서울 강남구 테헤란로 1',
      detailAddress: '2층',
      name: '강남 분식',
      platforms: ['YOGIYO'],
    });
    expect(target).not.toHaveProperty('latitude');
    expect(target).not.toHaveProperty('longitude');
  });
});
