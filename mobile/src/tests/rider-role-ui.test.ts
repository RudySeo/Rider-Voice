import { canWriteReview } from '@/shared/auth/roles';

describe('role-gated mobile writing UI', () => {
  it('keeps USER read-only and allows RIDER and ADMIN to write', () => {
    expect(canWriteReview({ id: 1, status: 'ACTIVE', role: 'USER' })).toBe(false);
    expect(canWriteReview({ id: 2, status: 'ACTIVE', role: 'RIDER' })).toBe(true);
    expect(canWriteReview({ id: 3, status: 'ACTIVE', role: 'ADMIN' })).toBe(true);
  });
});
