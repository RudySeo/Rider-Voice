import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { StyleSheet, View } from 'react-native';

import { AppText } from '@/shared/components/AppText';
import { colors, radius, spacing } from '@/shared/theme';

type HelmetRatingProps = {
  label: string;
  score?: number | null;
  observedCount: number;
  notObservedCount: number;
};

export function HelmetRating({ label, score, observedCount, notObservedCount }: HelmetRatingProps) {
  return (
    <View accessibilityLabel={`${label}, 항목별 점수 ${score?.toFixed(1) ?? '없음'}, 관찰 응답 ${observedCount}명, 관찰하지 못함 ${notObservedCount}명`} style={styles.wrapper}>
      <View style={styles.heading}>
        <AppText variant="label">{label}</AppText>
        <AppText color={colors.muted} variant="caption">{observedCount}명 평가 · 관찰하지 못함 {notObservedCount}명</AppText>
      </View>
      {score == null ? (
        <AppText color={colors.muted}>관찰된 응답이 없어요</AppText>
      ) : (
        <View style={styles.ratingLine}>
          <View style={styles.helmets}>
            {[1, 2, 3, 4, 5].map((position) => {
              const remaining = score - (position - 1);
              const opacity = remaining >= 1 ? 1 : remaining > 0 ? Math.max(0.35, remaining) : 1;
              const active = remaining > 0;
              return (
                <MaterialCommunityIcons
                  color={active ? colors.helmet : colors.helmetMuted}
                  key={position}
                  name="racing-helmet"
                  size={23}
                  style={{ opacity }}
                />
              );
            })}
          </View>
          <AppText color={colors.helmetDeep} variant="label" weight="700">{score.toFixed(1)}<AppText color={colors.muted} variant="caption"> / 5</AppText></AppText>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { paddingVertical: spacing.md, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line, gap: spacing.sm },
  heading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing.sm },
  ratingLine: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  helmets: { flexDirection: 'row', gap: 2, backgroundColor: colors.helmetSoft, borderRadius: radius.sm, paddingHorizontal: spacing.xs, paddingVertical: 4 },
});
