import { MyReview, PublicReview, RestaurantDetail, RestaurantSearchResponse } from '@/shared/api/types';

export const mockSearchResponse: RestaurantSearchResponse = {
  externalSearchStatus: 'AVAILABLE',
  candidates: [
    {
      candidateType: 'INTERNAL',
      restaurantId: 1,
      kakaoPlaceId: 'kakao-1',
      name: '강남 김밥 역삼점',
      address: '서울 강남구 테헤란로 00',
      aggregationStatus: 'PUBLISHED',
      contributorCount: 8,
    },
    {
      candidateType: 'INTERNAL',
      restaurantId: 2,
      kakaoPlaceId: 'kakao-2',
      name: '역삼 한그릇',
      address: '서울 강남구 언주로 00',
      aggregationStatus: 'PUBLISHED',
      contributorCount: 5,
    },
    {
      candidateType: 'KAKAO',
      restaurantId: null,
      kakaoPlaceId: 'kakao-3',
      name: '강남분식 본점',
      address: '서울 강남구 역삼로 00',
      aggregationStatus: 'NO_REVIEWS',
      contributorCount: 0,
    },
  ],
};

export const mockRestaurantDetail: RestaurantDetail = {
  restaurantId: 1,
  name: '강남 김밥 역삼점',
  status: 'ACTIVE',
  pickupLocation: {
    pickupLocationId: 1,
    standardAddress: '서울 강남구 테헤란로 00',
    detailAddress: null,
  },
  brandReport: {
    status: 'PUBLISHED',
    contributorCount: 8,
    metrics: {
      packagingStability: { observedCount: 7, notObservedCount: 1, distribution: { VERY_GOOD: 3, GOOD: 3, NEEDS_IMPROVEMENT: 1 }, score: 3.9 },
      orderReadiness: { observedCount: 8, notObservedCount: 0, distribution: { VERY_GOOD: 3, GOOD: 3, NEEDS_IMPROVEMENT: 2 }, score: 3.8 },
      handoffAccuracy: { observedCount: 7, notObservedCount: 1, distribution: { VERY_GOOD: 3, GOOD: 3, NEEDS_IMPROVEMENT: 1 }, score: 3.9 },
    },
  },
  pickupLocationReport: {
    status: 'PUBLISHED', contributorCount: 8,
    metrics: {
      pickupSpaceCleanliness: { observedCount: 7, notObservedCount: 1, distribution: { VERY_GOOD: 42.9, GOOD: 42.8, NEEDS_IMPROVEMENT: 14.3 }, score: 4.0 },
      staffInteraction: { observedCount: 8, notObservedCount: 0, distribution: { VERY_GOOD: 37.5, GOOD: 50, NEEDS_IMPROVEMENT: 12.5 }, score: 4.0 },
      riderRespect: { observedCount: 6, notObservedCount: 2, distribution: { VERY_GOOD: 50, GOOD: 33.3, NEEDS_IMPROVEMENT: 16.7 }, score: 4.1 },
    },
  },
  verificationStatus: 'UNVERIFIED',
  verificationNotice: '카카오 로그인 사용자가 작성한 경험이며 라이더 신분과 실제 방문 여부는 인증되지 않았습니다.',
};

export const mockPublicReviews: PublicReview[] = [
  { reviewId: 1, visitMonth: '2026-08', comment: '포장은 안정적이었고 주문 확인이 빨랐어요.', createdAt: '2026-08-22T08:00:00Z', ratings: { pickupSpaceCleanliness: 'GOOD', packagingStability: 'VERY_GOOD', orderReadiness: 'GOOD', handoffAccuracy: 'VERY_GOOD', staffInteraction: 'GOOD', riderRespect: 'GOOD' }, authorActivity: { activityMonths: 3, publicReviewCount: 4 }, verificationStatus: 'UNVERIFIED', verificationNotice: '라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다.' },
  { reviewId: 2, visitMonth: '2026-08', comment: '주문은 준비되어 있었지만 픽업 공간이 조금 복잡했어요.', createdAt: '2026-08-19T10:00:00Z', ratings: { pickupSpaceCleanliness: 'NEEDS_IMPROVEMENT', packagingStability: 'GOOD', orderReadiness: 'GOOD', handoffAccuracy: 'GOOD', staffInteraction: 'GOOD', riderRespect: 'NOT_OBSERVED' }, authorActivity: { activityMonths: 2, publicReviewCount: 2 }, verificationStatus: 'UNVERIFIED', verificationNotice: '라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다.' },
];

export const mockMyReviews: MyReview[] = [
  { reviewId: 1, restaurant: { restaurantId: 1, name: '강남 김밥 역삼점', address: '서울 강남구 테헤란로 00' }, visitMonth: '2026-08', ratings: mockPublicReviews[0].ratings, comment: mockPublicReviews[0].comment, commentModerationStatus: 'PUBLISHED', visibilityStatus: 'ACTIVE', createdAt: '2026-08-22T08:00:00Z', updatedAt: '2026-08-22T08:00:00Z' },
  { reviewId: 2, restaurant: { restaurantId: 2, name: '역삼 한그릇', address: '서울 강남구 언주로 00' }, visitMonth: '2026-08', ratings: mockPublicReviews[1].ratings, comment: mockPublicReviews[1].comment, commentModerationStatus: 'PUBLISHED', visibilityStatus: 'ACTIVE', createdAt: '2026-08-17T08:00:00Z', updatedAt: '2026-08-17T08:00:00Z' },
];
