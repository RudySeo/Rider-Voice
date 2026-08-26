import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { router } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { AppText } from '@/shared/components/AppText';
import { colors, radius, spacing } from '@/shared/theme';

type TrustBannerProps = {
  compact?: boolean;
};

export function TrustBanner({ compact }: TrustBannerProps) {
  return (
    <Pressable onPress={() => router.push('/trust')} style={[styles.banner, compact && styles.compact]}>
      <MaterialCommunityIcons color={colors.skyStrong} name="information-outline" size={21} />
      <View style={styles.copy}>
        <AppText style={styles.title} weight="700">작성자의 신분과 방문은 확인되지 않았어요</AppText>
        {!compact && <AppText color={colors.muted} variant="caption">카카오 로그인 사용자가 남긴 경험입니다.</AppText>}
      </View>
      <AppText color={colors.skyStrong} variant="caption" weight="700">자세히</AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  banner: {
    backgroundColor: colors.skySoft,
    borderRadius: radius.md,
    minHeight: 68,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  compact: { borderRadius: 0, minHeight: 58 },
  copy: { flex: 1, gap: 2 },
  title: { fontSize: 12, lineHeight: 17 },
});
