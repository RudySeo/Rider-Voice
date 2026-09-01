import { destinationAfterLogin } from '@/shared/auth/loginContinuation';

describe('login screen completion', () => {
  it('stays on the login screen when the browser session is cancelled', () => {
    expect(destinationAfterLogin({ status: 'cancelled' })).toBeNull();
  });

  it('resumes the saved destination after a successful login', () => {
    expect(destinationAfterLogin({
      status: 'authenticated',
      intent: { kind: 'manualReview', query: '강남 분식' },
    })).toEqual({
      pathname: '/review/manual-target',
      params: { query: '강남 분식' },
    });
  });
});
