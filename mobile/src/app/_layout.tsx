import { QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { queryClient } from '@/shared/queryClient';
import { AuthProvider } from '@/shared/auth/AuthProvider';
import { colors } from '@/shared/theme';

export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts({
    'LINESeedKR-Regular': require('../../assets/fonts/LINESeedKR-Rg.ttf'),
    'LINESeedKR-Bold': require('../../assets/fonts/LINESeedKR-Bd.ttf'),
  });

  if (!fontsLoaded && !fontError) return null;

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <StatusBar style="dark" />
            <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background }, animation: 'slide_from_right' }} />
          </AuthProvider>
        </QueryClientProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
