import { buildManualReviewTarget, reviewTargetFromRouteParams, reviewTargetToRouteParams } from '@/shared/api/reviewTargets';
import type { AddressSearchCandidate } from '@/shared/api/addresses';

const candidate = (existingPickupLocationId: number | null): AddressSearchCandidate => ({
  standardAddress: '서울 강남구 테헤란로 1',
  lotNumberAddress: null,
  latitude: 37.5,
  longitude: 127,
  existingPickupLocationId,
});

describe('manual review targets', () => {
  it('reuses an existing pickup location without sending a new detail address', () => {
    expect(buildManualReviewTarget({
      addressQuery: '테헤란로 1',
      candidate: candidate(10),
      detailAddress: '지하 1층',
      name: ' 새 브랜드 ',
      platforms: ['BAEMIN'],
    })).toEqual({
      type: 'MANUAL_EXISTING_LOCATION',
      pickupLocationId: 10,
      name: '새 브랜드',
      platforms: ['BAEMIN'],
    });
  });

  it('preserves the original address query for a new pickup location', () => {
    const target = buildManualReviewTarget({
      addressQuery: ' 테헤란로 1 ',
      candidate: candidate(null),
      detailAddress: ' 지하 1층 ',
      name: '새 브랜드',
      platforms: ['COUPANG_EATS', 'COUPANG_EATS'],
    });
    expect(target).toEqual({
      type: 'MANUAL_ADDRESS',
      addressQuery: '테헤란로 1',
      selectedStandardAddress: '서울 강남구 테헤란로 1',
      detailAddress: '지하 1층',
      name: '새 브랜드',
      platforms: ['COUPANG_EATS'],
    });
    expect(reviewTargetFromRouteParams(reviewTargetToRouteParams(target))).toEqual(target);
  });
});
