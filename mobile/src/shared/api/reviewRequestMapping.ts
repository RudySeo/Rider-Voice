import type { CreateReviewBody, UpdateReviewBody } from '@/shared/api/reviews';
import type { ReviewTarget } from '@/shared/api/reviewTargets';
import type { RatingValue } from '@/shared/api/types';

export type ValidReviewValues = {
  packagingStability: RatingValue;
  orderReadiness: RatingValue;
  handoffAccuracy: RatingValue;
  pickupSpaceCleanliness: RatingValue;
  staffInteraction: RatingValue;
  riderRespect: RatingValue;
  comment: string;
  visitMonth: string;
};

export function buildCreateReviewRequest(
  values: ValidReviewValues,
  restaurantTarget: ReviewTarget,
): CreateReviewBody {
  return { ...values, comment: values.comment.trim() || null, restaurantTarget };
}

export function buildUpdateReviewRequest(values: ValidReviewValues): UpdateReviewBody {
  const { visitMonth: _visitMonth, ...ratingsAndComment } = values;
  return { ...ratingsAndComment, comment: values.comment.trim() || null };
}
