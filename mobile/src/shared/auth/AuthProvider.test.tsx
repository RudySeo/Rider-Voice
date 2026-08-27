import * as SecureStore from 'expo-secure-store';
import * as WebBrowser from 'expo-web-browser';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { Pressable, Text } from 'react-native';

import { AuthProvider, useAuth } from '@/shared/auth/AuthProvider';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

jest.mock('expo-web-browser', () => ({
  openAuthSessionAsync: jest.fn(),
  WebBrowserResultType: { CANCEL: 'cancel' },
}));

jest.mock('@/shared/api/clientConfig', () => ({ apiBaseUrl: 'https://api.ridervoice.test' }));

jest.mock('@/shared/auth/session', () => ({
  exchangeMobileCode: jest.fn(),
  getCurrentUser: jest.fn(() => null),
  logoutMobileSession: jest.fn(),
  nativeAuthAvailable: true,
  refreshMobileSession: jest.fn(async () => null),
}));

function LoginProbe() {
  const auth = useAuth();
  return (
    <Pressable testID="login" onPress={() => void auth.login({ kind: 'reviewSearch' })}>
      <Text>login</Text>
    </Pressable>
  );
}

describe('pending authentication intent', () => {
  beforeEach(() => jest.clearAllMocks());

  it('removes the pending review search intent when login is cancelled', async () => {
    jest.mocked(WebBrowser.openAuthSessionAsync).mockResolvedValue({ type: WebBrowser.WebBrowserResultType.CANCEL });

    const screen = await render(<AuthProvider><LoginProbe /></AuthProvider>);
    fireEvent.press(screen.getByTestId('login'));

    await waitFor(() => expect(SecureStore.deleteItemAsync).toHaveBeenCalledTimes(2));
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith(
      'rider-voice.pending-intent',
      JSON.stringify({ kind: 'reviewSearch' }),
    );
    expect(SecureStore.deleteItemAsync).toHaveBeenLastCalledWith('rider-voice.pending-intent');
  });
});
