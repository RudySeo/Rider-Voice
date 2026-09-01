import Constants from 'expo-constants';
import * as Device from 'expo-device';
import { Platform } from 'react-native';

export type ApiProfile = 'local' | 'aws';
export type ApiConfigurationErrorCode =
  | 'API_PROFILE_INVALID'
  | 'API_BASE_URL_INVALID'
  | 'AWS_API_URL_MISSING'
  | 'AWS_API_URL_INSECURE'
  | 'LOCAL_API_URL_UNRESOLVED';

type ApiPlatform = 'ios' | 'android';
type ResolveApiConfigurationInput = {
  profile?: string;
  platform: ApiPlatform;
  isDevice: boolean;
  hostUri?: string | null;
  localOverride?: string;
  awsBaseUrl?: string;
};

export type ApiConfiguration =
  | { profile: ApiProfile; baseUrl: string }
  | {
    profile: ApiProfile | 'invalid';
    baseUrl: undefined;
    errorCode: ApiConfigurationErrorCode;
    errorMessage: string;
  };

export function resolveApiConfiguration(input: ResolveApiConfigurationInput): ApiConfiguration {
  const requestedProfile = input.profile?.trim() || 'local';
  if (requestedProfile !== 'local' && requestedProfile !== 'aws') {
    return configurationError('invalid', 'API_PROFILE_INVALID', `지원하지 않는 API 프로필이에요: ${requestedProfile}`);
  }

  if (requestedProfile === 'aws') {
    if (!input.awsBaseUrl?.trim()) {
      return configurationError('aws', 'AWS_API_URL_MISSING', 'AWS API 주소가 설정되지 않았어요. mobile/.env.local을 확인해주세요.');
    }
    const url = normalizedOrigin(input.awsBaseUrl);
    if (!url) return configurationError('aws', 'API_BASE_URL_INVALID', 'AWS API 주소는 origin 형식이어야 해요.');
    if (!url.startsWith('https://')) {
      return configurationError('aws', 'AWS_API_URL_INSECURE', 'AWS API 주소는 HTTPS를 사용해야 해요.');
    }
    return { profile: 'aws', baseUrl: url };
  }

  if (input.localOverride?.trim()) {
    const override = normalizedOrigin(input.localOverride);
    if (!override) return configurationError('local', 'API_BASE_URL_INVALID', '로컬 API 주소는 HTTP 또는 HTTPS origin 형식이어야 해요.');
    return { profile: 'local', baseUrl: override };
  }

  if (!input.isDevice) {
    return {
      profile: 'local',
      baseUrl: input.platform === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080',
    };
  }

  const metroHost = privateMetroHost(input.hostUri);
  if (metroHost) return { profile: 'local', baseUrl: `http://${metroHost}:8080` };
  return configurationError(
    'local',
    'LOCAL_API_URL_UNRESOLVED',
    '실기기에서 로컬 API 주소를 자동으로 찾지 못했어요. EXPO_PUBLIC_LOCAL_API_BASE_URL을 설정해주세요.',
  );
}

function normalizedOrigin(value: string): string | null {
  try {
    const url = new URL(value.trim());
    if (!['http:', 'https:'].includes(url.protocol)) return null;
    if (url.username || url.password || url.search || url.hash || (url.pathname && url.pathname !== '/')) return null;
    return url.origin;
  } catch {
    return null;
  }
}

function privateMetroHost(hostUri: string | null | undefined): string | null {
  if (!hostUri) return null;
  try {
    const url = new URL(hostUri.includes('://') ? hostUri : `http://${hostUri}`);
    const octets = url.hostname.split('.').map(Number);
    if (octets.length !== 4 || octets.some((octet) => !Number.isInteger(octet) || octet < 0 || octet > 255)) return null;
    const [first, second] = octets;
    const isPrivate = first === 10 || (first === 172 && second >= 16 && second <= 31) || (first === 192 && second === 168);
    return isPrivate ? url.hostname : null;
  } catch {
    return null;
  }
}

function configurationError(
  profile: ApiProfile | 'invalid',
  errorCode: ApiConfigurationErrorCode,
  errorMessage: string,
): ApiConfiguration {
  return { profile, baseUrl: undefined, errorCode, errorMessage };
}

export const apiConfiguration = resolveApiConfiguration({
  profile: process.env.EXPO_PUBLIC_API_PROFILE,
  platform: Platform.OS as ApiPlatform,
  isDevice: Device.isDevice,
  hostUri: Constants.expoConfig?.hostUri,
  localOverride: process.env.EXPO_PUBLIC_LOCAL_API_BASE_URL,
  awsBaseUrl: process.env.EXPO_PUBLIC_AWS_API_BASE_URL,
});

export const apiProfile = apiConfiguration.profile;
export const apiBaseUrl = apiConfiguration.baseUrl;

export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly code?: string) {
    super(message);
  }
}
