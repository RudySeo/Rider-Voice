import { act, renderHook } from '@testing-library/react-native';
import { PropsWithChildren } from 'react';

import { AuthProvider, useAuth } from '@/shared/auth/AuthProvider';
import { parsePendingIntent } from '@/shared/auth/pendingIntent';
import type { LoginResult } from '@/shared/auth/loginContinuation';
import { exchangeMobileCode } from '@/shared/auth/session';
import * as SecureStore from 'expo-secure-store';
import * as WebBrowser from 'expo-web-browser';

jest.mock('expo-auth-session', () => ({
  makeRedirectUri: jest.fn(() => 'ridervoice://auth/callback'),
}));
jest.mock('expo-secure-store', () => ({
  deleteItemAsync: jest.fn(async () => undefined),
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => undefined),
}));
jest.mock('expo-web-browser', () => ({
  WebBrowserResultType: { CANCEL: 'cancel' },
  maybeCompleteAuthSession: jest.fn(),
  openAuthSessionAsync: jest.fn(),
}));
jest.mock('@/shared/api/clientConfig', () => ({
  apiBaseUrl: 'https://api.example.com',
}));
jest.mock('@/shared/auth/session', () => ({
  exchangeMobileCode: jest.fn(),
  getCurrentUser: jest.fn(() => null),
  logoutMobileSession: jest.fn(async () => undefined),
  nativeAuthAvailability: 'READY',
  refreshMobileSession: jest.fn(async () => null),
}));

const openAuthSessionMock = jest.mocked(WebBrowser.openAuthSessionAsync);
const exchangeMobileCodeMock = jest.mocked(exchangeMobileCode);
const getSecureItemMock = jest.mocked(SecureStore.getItemAsync);

function wrapper({ children }: PropsWithChildren) {
  return <AuthProvider>{children}</AuthProvider>;
}

describe('pending authentication intent', () => {
  it('supports the four allow-listed intent kinds', () => {
    const intents = [
      { kind: 'activity' },
      { kind: 'existingReview', restaurantId: 1, place: '브랜드' },
      { kind: 'kakaoReview', query: '강남 분식', kakaoPlaceId: 'kakao-1', place: '브랜드' },
      { kind: 'manualReview', query: '강남 분식' },
    ];

    expect(intents.map((intent) => parsePendingIntent(JSON.stringify(intent))?.kind)).toEqual([
      'activity',
      'existingReview',
      'kakaoReview',
      'manualReview',
    ]);
  });
});

describe('Kakao authentication browser flow', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    getSecureItemMock.mockResolvedValue(null);
    exchangeMobileCodeMock.mockResolvedValue({ id: 1, status: 'ACTIVE', role: 'USER' });
  });

  it('opens the backend authorization endpoint and exchanges the returned one-time code', async () => {
    openAuthSessionMock.mockResolvedValue({
      type: 'success',
      url: 'ridervoice://auth/callback?code=one-time-code',
    });
    getSecureItemMock.mockResolvedValue(JSON.stringify({ kind: 'manualReview', query: '강남 분식' }));
    const { result } = await renderHook(() => useAuth(), { wrapper });
    let loginResult: LoginResult | undefined;

    await act(async () => {
      loginResult = await result.current.login({ kind: 'manualReview', query: '강남 분식' });
    });

    expect(loginResult).toEqual({
      status: 'authenticated',
      intent: { kind: 'manualReview', query: '강남 분식' },
    });

    expect(openAuthSessionMock).toHaveBeenCalledWith(
      'https://api.example.com/api/v1/auth/mobile/oauth2/authorization/kakao',
      'ridervoice://auth/callback',
    );
    expect(exchangeMobileCodeMock).toHaveBeenCalledWith('one-time-code');
  });

  it('does not exchange a code when the browser session is cancelled', async () => {
    openAuthSessionMock.mockResolvedValue({ type: WebBrowser.WebBrowserResultType.CANCEL });
    const { result } = await renderHook(() => useAuth(), { wrapper });
    let loginResult: LoginResult | undefined;

    await act(async () => {
      loginResult = await result.current.login({ kind: 'activity' });
    });

    expect(loginResult).toEqual({ status: 'cancelled' });

    expect(exchangeMobileCodeMock).not.toHaveBeenCalled();
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith(
      'rider-voice.pending-intent',
      JSON.stringify({ kind: 'activity' }),
    );
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('rider-voice.pending-intent');
  });
});
