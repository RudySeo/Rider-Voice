import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { Pressable, StyleSheet, View } from 'react-native';

import { experienceLabel } from '@/shared/api/restaurants';
import { RestaurantSearchCandidate } from '@/shared/api/types';
import { AppText } from '@/shared/components/AppText';
import { colors, radius, spacing } from '@/shared/theme';

type RestaurantRowProps = {
  candidate: RestaurantSearchCandidate;
  onPress?: () => void;
  writeEligible?: boolean;
};

export const candidateStatusLabel = (candidate: RestaurantSearchCandidate, writeEligible: boolean) =>
  candidate.aggregationStatus === 'NO_REVIEWS' && !writeEligible ? '아직 등록된 경험이 없어요' : experienceLabel(candidate);

export function RestaurantRow({ candidate, onPress, writeEligible = false }: RestaurantRowProps) {
  const reviewed = candidate.aggregationStatus !== 'NO_REVIEWS';
  const accent = reviewed ? colors.resultDeep : colors.jade;
  return (
    <Pressable disabled={!onPress} onPress={onPress} style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
      <View style={[styles.icon, { backgroundColor: reviewed ? colors.resultSoft : '#F2F4F5' }]}>
        <MaterialCommunityIcons color={reviewed ? colors.resultAccent : colors.muted} name={reviewed ? 'store-outline' : 'map-marker-outline'} size={21} />
      </View>
      <View style={styles.copy}>
        <AppText variant="label">{candidate.name}</AppText>
        <AppText color={colors.muted} numberOfLines={1} variant="caption">{candidate.address}</AppText>
        <AppText color={accent} style={styles.status} variant="caption" weight="700">{candidateStatusLabel(candidate, writeEligible)}</AppText>
      </View>
      {onPress && <MaterialCommunityIcons color={colors.muted} name="chevron-right" size={21} />}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    minHeight: 82,
    paddingVertical: spacing.sm,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.line,
  },
  pressed: { backgroundColor: '#F5F6F8' },
  icon: { width: 42, height: 42, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center' },
  copy: { flex: 1, gap: 2 },
  status: { marginTop: 2 },
});
