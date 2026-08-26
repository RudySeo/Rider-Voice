import { PropsWithChildren, ReactNode } from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors } from '@/shared/theme';

type ScreenProps = PropsWithChildren<{
  footer?: ReactNode;
}>;

export function Screen({ children, footer }: ScreenProps) {
  return (
    <View style={styles.outer}>
      <SafeAreaView edges={['top', 'bottom']} style={styles.safeArea}>
        <View style={styles.content}>{children}</View>
        {footer}
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  outer: {
    flex: 1,
    backgroundColor: Platform.OS === 'web' ? '#EEF1F3' : colors.background,
    alignItems: 'center',
  },
  safeArea: {
    flex: 1,
    width: '100%',
    maxWidth: Platform.OS === 'web' ? 430 : undefined,
    backgroundColor: colors.background,
    ...Platform.select({
      web: {
        borderLeftWidth: 1,
        borderRightWidth: 1,
        borderColor: colors.line,
      },
      default: {},
    }),
  },
  content: {
    flex: 1,
  },
});
