export type AuthAvailability =
  | 'READY'
  | 'WEB_UNSUPPORTED'
  | 'EXPO_GO_UNSUPPORTED'
  | 'API_URL_MISSING';

export function resolveAuthAvailability(
  platform: string,
  appOwnership: string | null | undefined,
  baseUrl: string | undefined,
): AuthAvailability {
  if (platform === 'web') return 'WEB_UNSUPPORTED';
  if (appOwnership === 'expo') return 'EXPO_GO_UNSUPPORTED';
  if (!baseUrl) return 'API_URL_MISSING';
  return 'READY';
}

export function authAvailabilityMessage(availability: AuthAvailability): string {
  if (availability === 'WEB_UNSUPPORTED') {
    return '웹에서는 카카오 로그인을 사용할 수 없어요. iOS 또는 Android Rider Voice 개발 빌드에서 다시 시도해주세요.';
  }
  if (availability === 'EXPO_GO_UNSUPPORTED') {
    return 'Expo Go에서는 카카오 로그인을 완료할 수 없어요. Rider Voice 개발 빌드에서 다시 시도해주세요.';
  }
  if (availability === 'API_URL_MISSING') {
    return '선택한 API 프로필의 주소를 확인한 뒤 개발 빌드를 다시 시작해주세요.';
  }
  return '';
}
