import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { router } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { AppText } from '@/shared/components/AppText';
import { colors, spacing } from '@/shared/theme';

type TabKey = 'home' | 'review' | 'activity';

type BottomTabBarProps = {
  active: TabKey;
};

const tabs = [
  { key: 'home' as const, label: '홈', icon: 'home-outline' as const, href: '/' as const },
  { key: 'review' as const, label: '리뷰 작성', icon: 'square-edit-outline' as const, href: '/login?next=/review/new' as const },
  { key: 'activity' as const, label: '내 활동', icon: 'account-outline' as const, href: '/activity' as const },
];

export function BottomTabBar({ active }: BottomTabBarProps) {
  return (
    <View style={styles.bar}>
      {tabs.map((tab) => {
        const selected = active === tab.key;
        return (
          <Pressable
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            key={tab.key}
            onPress={() => router.replace(tab.href)}
            style={({ pressed }) => [styles.tab, pressed && styles.pressed]}
          >
            <MaterialCommunityIcons color={selected ? colors.jade : colors.muted} name={tab.icon} size={23} />
            <AppText color={selected ? colors.jade : colors.muted} style={styles.label} weight={selected ? '700' : '400'}>
              {tab.label}
            </AppText>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    minHeight: 70,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.line,
    backgroundColor: colors.surface,
    flexDirection: 'row',
    paddingHorizontal: spacing.sm,
  },
  tab: {
    flex: 1,
    minHeight: 60,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 3,
    borderRadius: 12,
  },
  pressed: { backgroundColor: colors.jadeSoft },
  label: { fontSize: 11, lineHeight: 15 },
});
