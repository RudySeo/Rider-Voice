export type AggregationStatus = 'NO_REVIEWS' | 'COLLECTING' | 'PUBLISHED';
export type CandidateType = 'INTERNAL' | 'KAKAO';
export type RatingValue = 'VERY_GOOD' | 'GOOD' | 'NEEDS_IMPROVEMENT' | 'MAJOR_IMPROVEMENT' | 'NOT_OBSERVED';

export type RestaurantSearchCandidate = {
  candidateType: CandidateType;
  restaurantId: number | null;
  kakaoPlaceId: string | null;
  name: string;
  address: string;
  aggregationStatus: AggregationStatus;
  contributorCount: number;
};

export type RestaurantSearchResponse = {
  externalSearchStatus: 'AVAILABLE' | 'UNAVAILABLE';
  candidates: RestaurantSearchCandidate[];
};

export type AggregateMetric = {
  observedCount: number;
  notObservedCount: number;
  distribution: Partial<Record<RatingValue, number>>;
  score: number | null;
};

export type RestaurantDetail = {
  restaurantId: number;
  name: string;
  status: 'ACTIVE' | 'CLOSED';
  pickupLocation: {
    pickupLocationId: number;
    standardAddress: string;
    detailAddress: string | null;
  };
  brandReport: {
    status: AggregationStatus;
    contributorCount: number;
    metrics: {
      packagingStability: AggregateMetric;
      orderReadiness: AggregateMetric;
      handoffAccuracy: AggregateMetric;
    } | null;
  };
  pickupLocationReport: {
    status: AggregationStatus;
    contributorCount: number;
    metrics: {
      pickupSpaceCleanliness: AggregateMetric;
      staffInteraction: AggregateMetric;
      riderRespect: AggregateMetric;
    } | null;
  };
  verificationStatus: 'UNVERIFIED';
  verificationNotice: string;
};

export type PublicReview = {
  reviewId: number;
  visitMonth: string;
  comment: string | null;
  createdAt: string;
  ratings: ReviewRatings;
  authorActivity: { activityMonths: number; publicReviewCount: number };
  verificationStatus: 'UNVERIFIED';
  verificationNotice: string;
};

export type PublicReviewListResponse = { items: PublicReview[]; nextCursor: string | null };

export type ReviewRatings = {
  pickupSpaceCleanliness: RatingValue;
  packagingStability: RatingValue;
  orderReadiness: RatingValue;
  handoffAccuracy: RatingValue;
  staffInteraction: RatingValue;
  riderRespect: RatingValue;
};

export type MyReview = {
  reviewId: number;
  restaurant: { restaurantId: number; name: string; address: string };
  visitMonth: string;
  ratings: ReviewRatings;
  comment: string | null;
  commentModerationStatus: string;
  visibilityStatus: string;
  createdAt: string;
  updatedAt: string;
};

export type MyReviewListResponse = { items: MyReview[]; nextCursor: string | null; authoredCount: number; publiclyVisibleCount: number };
export type DeleteReviewResponse = { reviewId: number };
export type User = { id: number; status: string; role: 'USER' | 'ADMIN' };
export type MobileSession = { accessToken: string; refreshToken: string; user: User };
