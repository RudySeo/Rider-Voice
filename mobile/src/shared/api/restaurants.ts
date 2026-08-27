import { requestJson, usesMockApi } from '@/shared/api/client';
import { mockPublicReviews, mockRestaurantDetail, mockSearchResponse } from '@/shared/api/mockData';
import { PublicReviewListResponse, RestaurantDetail, RestaurantSearchCandidate, RestaurantSearchResponse } from '@/shared/api/types';

const wait = (duration: number) => new Promise((resolve) => setTimeout(resolve, duration));

export async function searchRestaurants(query: string): Promise<RestaurantSearchResponse> {
  if (usesMockApi) {
    await wait(260);
    return mockSearchResponse;
  }
  return requestJson(`/api/v1/restaurants/search?query=${encodeURIComponent(query)}`);
}

export async function getRestaurant(restaurantId: number): Promise<RestaurantDetail> {
  if (usesMockApi) {
    await wait(180);
    return { ...mockRestaurantDetail, restaurantId };
  }
  return requestJson(`/api/v1/restaurants/${restaurantId}`);
}

export async function getRestaurantReviews(restaurantId: number, cursor?: string): Promise<PublicReviewListResponse> {
  if (usesMockApi) {
    await wait(180);
    return { items: mockPublicReviews, nextCursor: null };
  }
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : '';
  return requestJson(`/api/v1/restaurants/${restaurantId}/reviews${query}`);
}

export function groupSearchResults(candidates: RestaurantSearchCandidate[]) {
  return {
    reviewed: candidates.filter((candidate) => candidate.candidateType === 'INTERNAL' && candidate.aggregationStatus !== 'NO_REVIEWS'),
    registered: candidates.filter((candidate) => candidate.candidateType === 'INTERNAL' && candidate.aggregationStatus === 'NO_REVIEWS'),
    kakao: candidates.filter((candidate) => candidate.candidateType === 'KAKAO'),
  };
}

export function experienceLabel(candidate: RestaurantSearchCandidate) {
  if (candidate.aggregationStatus === 'PUBLISHED') return `${candidate.contributorCount}명의 경험`;
  if (candidate.aggregationStatus === 'COLLECTING') return `리뷰 수집 중 · ${candidate.contributorCount}명`;
  return '첫 리뷰 작성';
}
