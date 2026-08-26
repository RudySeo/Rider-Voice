import { Platform } from 'react-native';
import { ApiError, apiBaseUrl } from '@/shared/api/clientConfig';
import { getAccessToken, refreshMobileSession } from '@/shared/auth/session';

export { ApiError, apiBaseUrl } from '@/shared/api/clientConfig';
export const usesMockApi = !apiBaseUrl;

export async function requestJson<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
  if (!apiBaseUrl) {
    throw new Error('EXPO_PUBLIC_API_BASE_URL is not configured');
  }

  const token = getAccessToken();
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  });

  if (response.status === 401 && !retried && token && await refreshMobileSession()) return requestJson<T>(path, init, true);

  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { detail?: string; code?: string } | null;
    throw new ApiError(problem?.detail ?? '요청을 처리하지 못했어요.', response.status, problem?.code);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function localApiBaseUrlHint() {
  if (Platform.OS === 'android') return 'http://10.0.2.2:8080';
  return 'http://localhost:8080';
}
