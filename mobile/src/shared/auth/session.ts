import Constants from 'expo-constants';
import * as SecureStore from 'expo-secure-store';

import { apiBaseUrl, apiConfiguration, ApiError } from '@/shared/api/clientConfig';
import type { MobileSession, User } from '@/shared/api/types';
import { resolveAuthAvailability } from '@/shared/auth/authRuntime';

const REFRESH_KEY = 'rider-voice.refresh-token';
let accessToken: string | null = null;
let currentUser: User | null = null;
let refreshPromise: Promise<string | null> | null = null;

export const nativeAuthAvailability = resolveAuthAvailability(Constants.appOwnership, apiBaseUrl);
export const nativeAuthAvailable = nativeAuthAvailability === 'READY';
export const getAccessToken = () => accessToken;
export const getCurrentUser = () => currentUser;

async function postSession(path: string, body: object): Promise<MobileSession> {
  if (!apiBaseUrl) {
    const failure = 'errorMessage' in apiConfiguration ? apiConfiguration : undefined;
    throw new ApiError(
      failure?.errorMessage ?? 'API 주소가 설정되지 않았어요.',
      0,
      failure?.errorCode ?? 'API_BASE_URL_MISSING',
    );
  }
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    });
  } catch {
    throw new ApiError('API 서버에 연결할 수 없어요. 주소와 서버 실행 상태를 확인해주세요.', 0, 'API_UNREACHABLE');
  }
  const payload = await response.json().catch(() => null) as (MobileSession & { detail?: string; code?: string }) | null;
  if (!response.ok || !payload) throw new ApiError(payload?.detail ?? '로그인을 처리하지 못했어요.', response.status, payload?.code);
  return payload;
}

async function acceptMobileSession(session: MobileSession) {
  accessToken = session.accessToken;
  currentUser = session.user;
  await SecureStore.setItemAsync(REFRESH_KEY, session.refreshToken);
}

export async function exchangeMobileCode(code: string) {
  const session = await postSession('/api/v1/auth/mobile/exchange', { code });
  await acceptMobileSession(session);
  return session.user;
}

export async function refreshMobileSession(): Promise<string | null> {
  if (!nativeAuthAvailable) return null;
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    const refreshToken = await SecureStore.getItemAsync(REFRESH_KEY);
    if (!refreshToken) return null;
    try {
      const session = await postSession('/api/v1/auth/mobile/refresh', { refreshToken });
      await acceptMobileSession(session);
      return session.accessToken;
    } catch {
      await clearLocalSession();
      return null;
    }
  })().finally(() => { refreshPromise = null; });
  return refreshPromise;
}

export async function logoutMobileSession() {
  const refreshToken = await SecureStore.getItemAsync(REFRESH_KEY);
  try {
    if (refreshToken && apiBaseUrl) await fetch(`${apiBaseUrl}/api/v1/auth/mobile/logout`, {
      method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken }),
    });
  } finally {
    await clearLocalSession();
  }
}

export async function clearLocalSession() {
  accessToken = null;
  currentUser = null;
  await SecureStore.deleteItemAsync(REFRESH_KEY);
}
