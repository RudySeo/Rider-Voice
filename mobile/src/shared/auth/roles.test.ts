import { canWriteReview } from '@/shared/auth/roles';

describe('review writing role', () => {
  it('allows only rider and admin users', () => {
    expect(canWriteReview(null)).toBe(false);
    expect(canWriteReview({ id: 1, status: 'ACTIVE', role: 'USER' })).toBe(false);
    expect(canWriteReview({ id: 2, status: 'ACTIVE', role: 'RIDER' })).toBe(true);
    expect(canWriteReview({ id: 3, status: 'ACTIVE', role: 'ADMIN' })).toBe(true);
  });
});
