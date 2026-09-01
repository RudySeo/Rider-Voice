import type { AddressSearchCandidate } from '@/shared/api/addresses';
import type { components } from '@/shared/api/generated';

export type ReviewTarget = components['schemas']['RestaurantTargetRequest'];
export type DeliveryPlatform = 'BAEMIN' | 'COUPANG_EATS' | 'YOGIYO' | 'OTHER';

export type ReviewTargetRouteParams = {
  targetType?: string;
  restaurantId?: string;
  query?: string;
  kakaoPlaceId?: string;
  pickupLocationId?: string;
  addressQuery?: string;
  selectedStandardAddress?: string;
  detailAddress?: string;
  brandName?: string;
  platforms?: string;
};

const platformValues = new Set<DeliveryPlatform>(['BAEMIN', 'COUPANG_EATS', 'YOGIYO', 'OTHER']);

function parsePlatforms(value?: string): DeliveryPlatform[] {
  if (!value) return [];
  return value.split(',').filter((platform): platform is DeliveryPlatform => platformValues.has(platform as DeliveryPlatform));
}

export function buildManualReviewTarget(input: {
  addressQuery: string;
  candidate: AddressSearchCandidate;
  detailAddress: string;
  name: string;
  platforms: DeliveryPlatform[];
}): ReviewTarget {
  const name = input.name.trim();
  if (!name || name.length > 255) throw new Error('배달 브랜드명을 확인해주세요.');
  const platforms = [...new Set(input.platforms)];
  if (input.candidate.existingPickupLocationId) {
    return {
      type: 'MANUAL_EXISTING_LOCATION',
      pickupLocationId: input.candidate.existingPickupLocationId,
      name,
      platforms,
    };
  }
  const addressQuery = input.addressQuery.trim();
  const detailAddress = input.detailAddress.trim();
  if (addressQuery.length < 2 || addressQuery.length > 100 || detailAddress.length > 255) {
    throw new Error('주소 정보를 확인해주세요.');
  }
  return {
    type: 'MANUAL_ADDRESS',
    addressQuery,
    selectedStandardAddress: input.candidate.standardAddress,
    detailAddress: detailAddress || null,
    name,
    platforms,
  };
}

export function reviewTargetToRouteParams(target: ReviewTarget): ReviewTargetRouteParams {
  switch (target.type) {
    case 'EXISTING':
      return { targetType: target.type, restaurantId: String(target.restaurantId) };
    case 'KAKAO':
      return { targetType: target.type, query: target.query, kakaoPlaceId: target.kakaoPlaceId };
    case 'MANUAL_EXISTING_LOCATION':
      return {
        targetType: target.type,
        pickupLocationId: String(target.pickupLocationId),
        brandName: target.name,
        platforms: target.platforms.join(','),
      };
    case 'MANUAL_ADDRESS':
      return {
        targetType: target.type,
        addressQuery: target.addressQuery,
        selectedStandardAddress: target.selectedStandardAddress,
        detailAddress: target.detailAddress ?? '',
        brandName: target.name,
        platforms: target.platforms.join(','),
      };
  }
  throw new Error('지원하지 않는 리뷰 대상이에요.');
}

export function reviewTargetFromRouteParams(params: ReviewTargetRouteParams): ReviewTarget | undefined {
  if (params.targetType === 'EXISTING') {
    const restaurantId = Number(params.restaurantId);
    return Number.isInteger(restaurantId) && restaurantId > 0 ? { type: 'EXISTING', restaurantId } : undefined;
  }
  if (params.targetType === 'KAKAO' && params.query?.trim() && params.kakaoPlaceId?.trim()) {
    return { type: 'KAKAO', query: params.query.trim(), kakaoPlaceId: params.kakaoPlaceId.trim() };
  }
  if (params.targetType === 'MANUAL_EXISTING_LOCATION' && params.brandName?.trim()) {
    const pickupLocationId = Number(params.pickupLocationId);
    return Number.isInteger(pickupLocationId) && pickupLocationId > 0
      ? {
        type: 'MANUAL_EXISTING_LOCATION',
        pickupLocationId,
        name: params.brandName.trim(),
        platforms: parsePlatforms(params.platforms),
      }
      : undefined;
  }
  if (
    params.targetType === 'MANUAL_ADDRESS'
    && params.addressQuery?.trim()
    && params.selectedStandardAddress?.trim()
    && params.brandName?.trim()
  ) {
    return {
      type: 'MANUAL_ADDRESS',
      addressQuery: params.addressQuery.trim(),
      selectedStandardAddress: params.selectedStandardAddress.trim(),
      detailAddress: params.detailAddress?.trim() || null,
      name: params.brandName.trim(),
      platforms: parsePlatforms(params.platforms),
    };
  }
  return undefined;
}
