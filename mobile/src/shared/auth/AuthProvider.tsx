import { makeRedirectUri } from 'expo-auth-session';
import * as SecureStore from 'expo-secure-store';
import * as WebBrowser from 'expo-web-browser';
import { createContext, PropsWithChildren, useContext, useEffect, useMemo, useState } from 'react';

import { apiBaseUrl, apiConfiguration } from '@/shared/api/clientConfig';
import type { User } from '@/shared/api/types';
import { authAvailabilityMessage, type AuthAvailability } from '@/shared/auth/authRuntime';
import type { LoginResult } from '@/shared/auth/loginContinuation';
import { parsePendingIntent, type PendingIntent } from '@/shared/auth/pendingIntent';
import { exchangeMobileCode, getCurrentUser, logoutMobileSession, nativeAuthAvailability, refreshMobileSession } from '@/shared/auth/session';

WebBrowser.maybeCompleteAuthSession();

const CALLBACK = makeRedirectUri({
  scheme: 'ridervoice',
  path: 'auth/callback',
  native: 'ridervoice://auth/callback',
});
const INTENT_KEY = 'rider-voice.pending-intent';
type AuthContextValue = { user: User | null; restoring: boolean; availability: AuthAvailability; login: (intent?: PendingIntent) => Promise<LoginResult>; logout: () => Promise<void> };
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<User | null>(getCurrentUser());
  const [restoring, setRestoring] = useState(nativeAuthAvailability === 'READY');
  useEffect(() => {
    if (nativeAuthAvailability !== 'READY') return;
    refreshMobileSession().finally(() => { setUser(getCurrentUser()); setRestoring(false); });
  }, []);
  const value = useMemo<AuthContextValue>(() => ({
    user, restoring, availability: nativeAuthAvailability,
    login: async (intent) => {
      if (nativeAuthAvailability !== 'READY' || !apiBaseUrl) {
        if ('errorMessage' in apiConfiguration) throw new Error(apiConfiguration.errorMessage);
        throw new Error(authAvailabilityMessage(nativeAuthAvailability));
      }
      await SecureStore.deleteItemAsync(INTENT_KEY);
      if (intent) await SecureStore.setItemAsync(INTENT_KEY, JSON.stringify(intent));
      try {
        const result = await WebBrowser.openAuthSessionAsync(`${apiBaseUrl}/api/v1/auth/mobile/oauth2/authorization/kakao`, CALLBACK);
        if (result.type !== 'success') return { status: 'cancelled' };
        const callback = new URL(result.url);
        const code = callback.searchParams.get('code');
        if (!code || callback.searchParams.has('error')) throw new Error('카카오 로그인을 완료하지 못했어요.');
        setUser(await exchangeMobileCode(code));
        return {
          status: 'authenticated',
          intent: parsePendingIntent(await SecureStore.getItemAsync(INTENT_KEY)),
        };
      } finally {
        await SecureStore.deleteItemAsync(INTENT_KEY);
      }
    },
    logout: async () => { await logoutMobileSession(); setUser(null); },
  }), [restoring, user]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used within AuthProvider');
  return value;
}
