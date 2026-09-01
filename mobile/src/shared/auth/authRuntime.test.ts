import { resolveAuthAvailability } from '@/shared/auth/authRuntime';

describe('native authentication runtime', () => {
  it('disables native authentication on web', () => {
    expect(resolveAuthAvailability('web', null, 'https://api.example.com')).toBe(
      'WEB_UNSUPPORTED',
    );
    expect(resolveAuthAvailability('web', 'expo', 'https://api.example.com')).toBe(
      'WEB_UNSUPPORTED',
    );
  });

  it('requires a development build before checking the backend URL', () => {
    expect(resolveAuthAvailability('ios', 'expo', undefined)).toBe('EXPO_GO_UNSUPPORTED');
    expect(resolveAuthAvailability('android', 'expo', 'https://api.example.com')).toBe(
      'EXPO_GO_UNSUPPORTED',
    );
  });

  it('requires an API URL in a native development build', () => {
    expect(resolveAuthAvailability('ios', null, undefined)).toBe('API_URL_MISSING');
    expect(resolveAuthAvailability('android', undefined, '')).toBe('API_URL_MISSING');
  });

  it('enables the backend OAuth flow only in a configured development build', () => {
    expect(resolveAuthAvailability('ios', null, 'https://api.example.com')).toBe('READY');
    expect(resolveAuthAvailability('android', undefined, 'https://api.example.com')).toBe('READY');
  });
});
