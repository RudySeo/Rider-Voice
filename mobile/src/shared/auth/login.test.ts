import { destinationAfterLogin } from '@/shared/auth/loginContinuation';

describe('login completion', () => {
  it('does not navigate when the native browser session is cancelled', () => {
    expect(destinationAfterLogin({ status: 'cancelled' })).toBeNull();
  });

  it('opens activity after a direct login succeeds', () => {
    expect(destinationAfterLogin({ status: 'authenticated', intent: null })).toBe('/activity');
  });
});
