import { riderVerificationErrorMessage } from '@/shared/auth/riderVerification';
import { ApiError } from '@/shared/api/clientConfig';

it('maps verification failures without exposing code details', () => {
  expect(riderVerificationErrorMessage(new ApiError('raw', 400, 'RIDER_VERIFICATION_FAILED')))
    .toBe('인증번호가 올바르지 않아요.');
  expect(riderVerificationErrorMessage(new ApiError('raw', 429, 'RIDER_VERIFICATION_RATE_LIMITED')))
    .toContain('15분');
  expect(riderVerificationErrorMessage(new ApiError('raw', 503, 'RIDER_VERIFICATION_UNAVAILABLE')))
    .toContain('준비되지');
});
