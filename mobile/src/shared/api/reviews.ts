import { requestJson, usesMockApi } from '@/shared/api/client';
import type { components } from '@/shared/api/generated';
import type { MyReview, MyReviewListResponse, RatingValue } from '@/shared/api/types';

export type CreateReviewBody = components['schemas']['CreateReviewRequest'];
export type UpdateReviewBody = components['schemas']['UpdateReviewRequest'];

function requireRealApi() { if (usesMockApi) throw new Error('공개 미리보기에서는 리뷰를 변경할 수 없어요.'); }
export function createReview(body: CreateReviewBody): Promise<MyReview> { requireRealApi(); return requestJson('/api/v1/reviews', { method: 'POST', body: JSON.stringify(body) }); }
export function updateReview(reviewId: number, body: UpdateReviewBody): Promise<MyReview> {
  requireRealApi();
  return requestJson(`/api/v1/reviews/${reviewId}`, { method: 'PATCH', body: JSON.stringify(body) });
}
export function deleteReview(reviewId: number): Promise<void> { requireRealApi(); return requestJson(`/api/v1/reviews/${reviewId}`, { method: 'DELETE' }); }
export function getMyReview(reviewId: number): Promise<MyReview> { requireRealApi(); return requestJson(`/api/v1/reviews/${reviewId}`); }
export function getMyReviews(cursor?: string): Promise<MyReviewListResponse> {
  requireRealApi();
  return requestJson(`/api/v1/users/me/reviews${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`);
}
export const ratingLabels: Record<RatingValue, string> = {
  VERY_GOOD: '매우 좋음', GOOD: '좋음', NEEDS_IMPROVEMENT: '개선 필요', MAJOR_IMPROVEMENT: '큰 개선 필요', NOT_OBSERVED: '관찰하지 못함',
};
