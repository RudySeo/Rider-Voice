import * as SecureStore from 'expo-secure-store';

import { logoutMobileSession } from '@/shared/auth/session';

jest.mock('expo-constants', () => ({
  __esModule: true,
  default: { appOwnership: 'standalone' },
}));

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

jest.mock('@/shared/api/clientConfig', () => ({
  apiBaseUrl: 'https://api.ridervoice.test',
  ApiError: class ApiError extends Error {},
}));

const secureStore = jest.mocked(SecureStore);
const fetchMock = jest.fn();

describe('mobile auth session', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    globalThis.fetch = fetchMock;
    secureStore.getItemAsync.mockResolvedValue('refresh-token');
    secureStore.deleteItemAsync.mockResolvedValue();
  });

  it('revokes the server session and always clears the local refresh token', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(logoutMobileSession()).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.ridervoice.test/api/v1/auth/mobile/logout',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ refreshToken: 'refresh-token' }) }),
    );
    expect(secureStore.deleteItemAsync).toHaveBeenCalledWith('rider-voice.refresh-token');
  });

  it('still completes local logout when the server cannot be reached', async () => {
    fetchMock.mockRejectedValue(new TypeError('network unavailable'));

    await expect(logoutMobileSession()).resolves.toBeUndefined();

    expect(secureStore.deleteItemAsync).toHaveBeenCalledWith('rider-voice.refresh-token');
  });
});
