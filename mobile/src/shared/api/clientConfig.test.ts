import { ApiError } from '@/shared/api/clientConfig';

describe('API error', () => {
  it('keeps the stable HTTP status and problem code', () => {
    const error = new ApiError('failed', 409, 'STATE_CONFLICT');
    expect(error.status).toBe(409);
    expect(error.code).toBe('STATE_CONFLICT');
  });
});
