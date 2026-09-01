import type { ReviewTargetRouteParams } from '@/shared/api/reviewTargets';

type ReviewParams = ReviewTargetRouteParams & { place: string };

export function reviewDestination(authenticated: boolean, params: ReviewParams) {
  return authenticated
    ? { pathname: '/review/new' as const, params }
    : { pathname: '/login' as const, params: { ...params, next: '/review/new' } };
}

export function manualRegistrationDestination(authenticated: boolean, query: string) {
  return authenticated
    ? { pathname: '/review/manual-target' as const, params: { query } }
    : { pathname: '/login' as const, params: { next: '/review/manual-target', manualQuery: query } };
}
