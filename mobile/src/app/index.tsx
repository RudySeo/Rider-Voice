import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { AppText } from '@/shared/components/AppText';
import { BottomTabBar } from '@/shared/components/BottomTabBar';
import { Screen } from '@/shared/components/Screen';
import { TrustBanner } from '@/shared/components/TrustBanner';
import { colors, radius, spacing } from '@/shared/theme';

const discoveryItems = [
  { title: '주문 준비가 안정적인가요?', description: '주문 접수부터 준비 완료까지의 흐름을 공유해요.', icon: 'check-circle-outline' as const, background: colors.apricot },
  { title: '포장은 깔끔하게 전달되나요?', description: '포장 상태와 누수, 마감 등을 솔직한 경험으로 확인해요.', icon: 'cube-outline' as const, background: colors.lavender },
  { title: '픽업 공간과 응대는 어떤가요?', description: '픽업 위치, 대기 공간과 응대 경험을 살펴봐요.', icon: 'map-marker-outline' as const, background: colors.mint },
];

export default function HomeScreen() {
  const [query, setQuery] = useState('');

  const submitSearch = () => {
    const normalized = query.trim();
    if (normalized.length < 2) return;
    router.push({ pathname: '/search', params: { query: normalized } });
  };

  return (
    <Screen footer={<BottomTabBar active="home" />}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.brandRow}>
          <AppText color={colors.jade} variant="label" weight="700">Rider Voice</AppText>
          <Pressable accessibilityLabel="로그인" hitSlop={8} onPress={() => router.push('/login')} style={styles.accountButton}>
            <MaterialCommunityIcons color={colors.jade} name="account-outline" size={24} />
          </Pressable>
        </View>
        <View style={styles.hero}>
          <AppText variant="display">주문 전, 이 음식점 어떤가요?</AppText>
          <AppText color={colors.muted}>준비 상태부터 포장과 픽업 환경까지 미리 확인해보세요.</AppText>
        </View>
        <View style={styles.searchPanel}>
          <View style={styles.inputWrap}>
            <MaterialCommunityIcons color={colors.muted} name="magnify" size={21} />
            <TextInput accessibilityLabel="음식점 검색" autoCapitalize="none" onChangeText={setQuery} onSubmitEditing={submitSearch} placeholder="음식점 이름 또는 주소" placeholderTextColor="#9AA1AA" returnKeyType="search" style={styles.input} value={query} />
          </View>
          <Pressable disabled={query.trim().length < 2} onPress={submitSearch} style={({ pressed }) => [styles.searchButton, query.trim().length < 2 && styles.searchDisabled, pressed && styles.pressed]}>
            <AppText color={colors.surface} weight="700">확인하기</AppText>
          </Pressable>
        </View>
        <View style={styles.fullBleedBanner}><TrustBanner compact /></View>
        <View style={styles.infoSection}>
          <AppText variant="section">검색하면 이런 정보를 볼 수 있어요</AppText>
          <View style={styles.list}>
            {discoveryItems.map((item) => (
              <Pressable key={item.title} onPress={() => router.push({ pathname: '/search', params: { query: '강남 김밥' } })} style={({ pressed }) => [styles.infoRow, pressed && styles.pressed]}>
                <View style={[styles.infoIcon, { backgroundColor: item.background }]}><MaterialCommunityIcons color={colors.text} name={item.icon} size={22} /></View>
                <View style={styles.infoCopy}><AppText variant="label">{item.title}</AppText><AppText color={colors.muted} variant="caption">{item.description}</AppText></View>
                <MaterialCommunityIcons color={colors.jade} name="chevron-right" size={22} />
              </Pressable>
            ))}
          </View>
          <View style={styles.experienceNote}><MaterialCommunityIcons color={colors.jade} name="information-outline" size={17} /><AppText color={colors.muted} variant="caption">이용자가 남긴 매장 운영 경험</AppText></View>
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { flexGrow: 1, paddingTop: spacing.md, paddingBottom: spacing.xl },
  brandRow: { paddingHorizontal: 22, minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  accountButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.pill },
  hero: { paddingHorizontal: 22, paddingTop: spacing.lg, paddingBottom: spacing.lg, gap: spacing.xs },
  searchPanel: { marginHorizontal: 22, backgroundColor: colors.jadeSoft, borderRadius: radius.lg, padding: spacing.sm, flexDirection: 'row', gap: spacing.xs },
  inputWrap: { flex: 1, minHeight: 48, borderRadius: radius.md, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing.sm, gap: spacing.xs },
  input: { flex: 1, color: colors.text, fontSize: 14, paddingVertical: 0 },
  searchButton: { minWidth: 82, minHeight: 48, borderRadius: radius.md, backgroundColor: colors.jade, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing.sm },
  searchDisabled: { opacity: 0.55 },
  pressed: { opacity: 0.72 },
  fullBleedBanner: { marginTop: spacing.md },
  infoSection: { paddingHorizontal: 22, paddingTop: spacing.lg },
  list: { marginTop: spacing.sm, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  infoRow: { minHeight: 76, flexDirection: 'row', alignItems: 'center', gap: spacing.sm, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  infoIcon: { width: 42, height: 42, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center' },
  infoCopy: { flex: 1, gap: 3 },
  experienceNote: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, paddingTop: spacing.sm, paddingBottom: spacing.xxl },
});
