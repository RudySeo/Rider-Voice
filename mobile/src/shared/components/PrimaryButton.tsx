import { ActivityIndicator, Pressable, StyleSheet } from 'react-native';

import { AppText } from '@/shared/components/AppText';
import { colors, radius } from '@/shared/theme';

type PrimaryButtonProps = {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  tone?: 'jade' | 'kakao' | 'outline';
};

export function PrimaryButton({ label, onPress, disabled, loading, tone = 'jade' }: PrimaryButtonProps) {
  const palette = tone === 'kakao'
    ? { background: colors.kakao, text: colors.kakaoInk, border: colors.kakao }
    : tone === 'outline'
      ? { background: colors.surface, text: colors.jade, border: colors.jade }
      : { background: colors.jade, text: colors.surface, border: colors.jade };

  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled || loading}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: palette.background, borderColor: palette.border },
        (disabled || loading) && styles.disabled,
        pressed && styles.pressed,
      ]}
    >
      {loading ? <ActivityIndicator color={palette.text} /> : <AppText color={palette.text} weight="700">{label}</AppText>}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 48,
    borderRadius: radius.md,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 18,
  },
  disabled: { opacity: 0.45 },
  pressed: { opacity: 0.82 },
});
