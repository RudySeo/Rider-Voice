import { resolveAuthAvailability } from '@/shared/auth/authRuntime';

describe('native authentication runtime', () => {
  it('requires a development build before checking the backend URL', () => {
    expect(resolveAuthAvailability('expo', undefined)).toBe('EXPO_GO_UNSUPPORTED');
    expect(resolveAuthAvailability('expo', 'https://api.example.com')).toBe('EXPO_GO_UNSUPPORTED');
  });

  it('requires an API URL in a native development build', () => {
    expect(resolveAuthAvailability(null, undefined)).toBe('API_URL_MISSING');
    expect(resolveAuthAvailability(undefined, '')).toBe('API_URL_MISSING');
  });

  it('enables the backend OAuth flow only in a configured development build', () => {
    expect(resolveAuthAvailability(null, 'https://api.example.com')).toBe('READY');
  });
});
