import { ApiError } from '@/shared/api/clientConfig';

export function riderVerificationErrorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 400) return '인증번호가 올바르지 않아요.';
  if (error instanceof ApiError && error.status === 429) return '실패 횟수가 많아 15분 동안 인증할 수 없어요.';
  if (error instanceof ApiError && error.status === 503) return '인증번호가 아직 준비되지 않았어요. 운영자에게 문의해주세요.';
  return '인증하지 못했어요. 잠시 후 다시 시도해주세요.';
}
