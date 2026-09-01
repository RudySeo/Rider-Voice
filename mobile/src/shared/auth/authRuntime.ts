export type AuthAvailability = 'READY' | 'EXPO_GO_UNSUPPORTED' | 'API_URL_MISSING';

export function resolveAuthAvailability(
  appOwnership: string | null | undefined,
  baseUrl: string | undefined,
): AuthAvailability {
  if (appOwnership === 'expo') return 'EXPO_GO_UNSUPPORTED';
  if (!baseUrl) return 'API_URL_MISSING';
  return 'READY';
}

export function authAvailabilityMessage(availability: AuthAvailability): string {
  if (availability === 'EXPO_GO_UNSUPPORTED') {
    return 'Expo Go에서는 카카오 로그인을 완료할 수 없어요. Rider Voice 개발 빌드에서 다시 시도해주세요.';
  }
  if (availability === 'API_URL_MISSING') {
    return 'mobile/.env.local에 EXPO_PUBLIC_API_BASE_URL을 설정한 뒤 개발 빌드를 다시 시작해주세요.';
  }
  return '';
}
