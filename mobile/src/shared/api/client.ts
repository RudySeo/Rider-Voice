import { ApiError, apiBaseUrl, apiConfiguration } from '@/shared/api/clientConfig';
import { getAccessToken, refreshMobileSession } from '@/shared/auth/session';

export { ApiError, apiBaseUrl } from '@/shared/api/clientConfig';
export const usesMockApi = !apiBaseUrl;

export async function requestJson<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
  if (!apiBaseUrl) {
    const failure = 'errorMessage' in apiConfiguration ? apiConfiguration : undefined;
    throw new ApiError(
      failure?.errorMessage ?? 'API 주소가 설정되지 않았어요.',
      0,
      failure?.errorCode ?? 'API_BASE_URL_MISSING',
    );
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
