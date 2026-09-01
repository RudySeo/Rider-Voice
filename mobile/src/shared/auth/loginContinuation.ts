import type { PendingIntent } from '@/shared/auth/pendingIntent';

export type LoginParams = {
  next?: string;
  place?: string;
  targetType?: string;
  restaurantId?: string;
  query?: string;
  kakaoPlaceId?: string;
  manualQuery?: string;
};

export function pendingIntentFromLoginParams(params: LoginParams): PendingIntent | undefined {
  if (params.next === '/activity') return { kind: 'activity' };
  if (params.next === '/review/manual-target') {
    return { kind: 'manualReview', query: params.manualQuery?.trim() ?? '' };
  }
  if (params.targetType === 'EXISTING' && Number(params.restaurantId) > 0) {
    return {
      kind: 'existingReview',
      restaurantId: Number(params.restaurantId),
      place: params.place ?? '음식점',
    };
  }
  if (params.targetType === 'KAKAO' && params.query && params.kakaoPlaceId) {
    return {
      kind: 'kakaoReview',
      query: params.query,
      kakaoPlaceId: params.kakaoPlaceId,
      place: params.place ?? '음식점',
    };
  }
  return undefined;
}

export function resumedDestination(intent: PendingIntent | null) {
  if (intent?.kind === 'activity') return '/activity' as const;
  if (intent?.kind === 'existingReview') {
    return {
      pathname: '/review/new' as const,
      params: {
        targetType: 'EXISTING',
        restaurantId: String(intent.restaurantId),
        place: intent.place,
      },
    };
  }
  if (intent?.kind === 'kakaoReview') {
    return {
      pathname: '/review/new' as const,
      params: {
        targetType: 'KAKAO',
        query: intent.query,
        kakaoPlaceId: intent.kakaoPlaceId,
        place: intent.place,
      },
    };
  }
  if (intent?.kind === 'manualReview') {
    return {
      pathname: '/review/manual-target' as const,
      params: { query: intent.query },
    };
  }
  return '/activity' as const;
}
