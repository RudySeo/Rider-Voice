import { Text, TextProps, TextStyle } from 'react-native';

import { colors, typography } from '@/shared/theme';

type AppTextVariant = 'display' | 'title' | 'section' | 'label' | 'body' | 'caption';

type AppTextProps = TextProps & {
  variant?: AppTextVariant;
  color?: string;
  weight?: TextStyle['fontWeight'];
};

const variants: Record<AppTextVariant, TextStyle> = {
  display: { fontSize: 25, lineHeight: 33, fontWeight: '700' },
  title: { fontSize: 20, lineHeight: 27, fontWeight: '700' },
  section: { fontSize: typography.section, lineHeight: 26, fontWeight: '700' },
  label: { fontSize: typography.label, lineHeight: 21, fontWeight: '600' },
  body: { fontSize: typography.body, lineHeight: 21, fontWeight: '400' },
  caption: { fontSize: typography.caption, lineHeight: 17, fontWeight: '400' },
};

export function AppText({ variant = 'body', color = colors.text, weight, style, ...props }: AppTextProps) {
  const resolvedWeight = weight ?? variants[variant].fontWeight ?? '400';
  const numericWeight = resolvedWeight === 'bold' ? 700 : Number(resolvedWeight);
  const fontFamily = numericWeight >= 600 ? typography.familyBold : typography.familyRegular;

  return (
    <Text
      allowFontScaling
      maxFontSizeMultiplier={1.5}
      style={[{ fontFamily, color }, variants[variant], weight ? { fontWeight: weight } : null, style]}
      {...props}
    />
  );
}
