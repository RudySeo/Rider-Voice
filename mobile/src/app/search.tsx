import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { useQuery } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { ReactNode, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { groupSearchResults, searchRestaurants } from '@/shared/api/restaurants';
import { AppText } from '@/shared/components/AppText';
import { BottomTabBar } from '@/shared/components/BottomTabBar';
import { RestaurantRow } from '@/shared/components/RestaurantRow';
import { Screen } from '@/shared/components/Screen';
import { ScreenHeader } from '@/shared/components/ScreenHeader';
import { colors, radius, spacing } from '@/shared/theme';
import { useAuth } from '@/shared/auth/AuthProvider';
import { reviewedRestaurantRoute, reviewTargetRoute } from '@/shared/navigation/reviewRoutes';

export default function SearchScreen() {
  const params = useLocalSearchParams<{ query?: string; mode?: string }>();
  const auth = useAuth();
  const reviewMode = params.mode === 'review';
  const initial = typeof params.query === 'string' ? params.query : '';
  const [draft, setDraft] = useState(initial);
  const [submitted, setSubmitted] = useState(initial);

  const validQuery = submitted.trim().length >= 2;
  const search = useQuery({ queryKey: ['restaurants', 'search', submitted], queryFn: () => searchRestaurants(submitted), enabled: validQuery });
  const groups = groupSearchResults(search.data?.candidates ?? []);
  const submit = () => { const normalized = draft.trim(); if (normalized.length >= 2) setSubmitted(normalized); };

  return (
    <Screen footer={<BottomTabBar active={reviewMode ? 'review' : 'home'} />}>
      <ScreenHeader title={reviewMode ? '리뷰할 음식점 찾기' : '검색 결과'} />
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.searchRow}>
          <View style={styles.field}><MaterialCommunityIcons color={colors.muted} name="magnify" size={20} /><TextInput onChangeText={setDraft} onSubmitEditing={submit} returnKeyType="search" style={styles.input} value={draft} /></View>
          <Pressable onPress={submit} style={({ pressed }) => [styles.button, pressed && styles.pressed]}><AppText color={colors.surface} weight="700">검색</AppText></Pressable>
        </View>
        <View style={styles.summary}><AppText variant="caption" weight="700">“{submitted}” 검색 결과</AppText></View>
        {!validQuery ? (
          <View style={styles.state}><AppText variant="label">검색어를 2자 이상 입력해주세요</AppText></View>
        ) : search.isPending ? (
          <View style={styles.state}><ActivityIndicator color={colors.jade} /><AppText color={colors.muted}>음식점을 찾고 있어요</AppText></View>
        ) : search.isError ? (
          <View style={styles.state}><AppText variant="label">검색 결과를 불러오지 못했어요</AppText><Pressable onPress={() => search.refetch()}><AppText color={colors.jade} weight="700">다시 시도</AppText></Pressable></View>
        ) : (
          <>
            {search.data?.externalSearchStatus === 'UNAVAILABLE' && <View style={styles.warning}><MaterialCommunityIcons color={colors.skyStrong} name="information-outline" size={20} /><AppText color={colors.skyStrong} variant="caption">카카오 장소 검색을 잠시 사용할 수 없어 등록된 음식점만 보여드려요.</AppText></View>}
            <ResultSection count={groups.reviewed.length} title="리뷰가 있는 음식점">
              {groups.reviewed.map((candidate) => <RestaurantRow key={`${candidate.candidateType}-${candidate.restaurantId}`} candidate={candidate} onPress={() => router.push(reviewedRestaurantRoute(reviewMode, Boolean(auth.user), { restaurantId: candidate.restaurantId!, place: candidate.name }))} />)}
            </ResultSection>
            <ResultSection count={groups.registered.length} description="Rider Voice에 등록됐지만 아직 리뷰가 없어요." title="등록된 음식점">
              {groups.registered.map((candidate) => <RestaurantRow key={`registered-${candidate.restaurantId}`} candidate={candidate} onPress={() => router.push(reviewTargetRoute(Boolean(auth.user), { type: 'EXISTING', restaurantId: candidate.restaurantId!, place: candidate.name }))} />)}
            </ResultSection>
            <ResultSection count={groups.kakao.length} description="선택한 장소는 서버가 같은 검색으로 다시 확인해요." title="카카오에서 찾은 장소">
              {groups.kakao.map((candidate) => <RestaurantRow key={`kakao-${candidate.kakaoPlaceId}`} candidate={candidate} onPress={() => router.push(reviewTargetRoute(Boolean(auth.user), { type: 'KAKAO', query: submitted, kakaoPlaceId: candidate.kakaoPlaceId!, place: candidate.name }))} />)}
            </ResultSection>
            {groups.reviewed.length + groups.registered.length + groups.kakao.length === 0 && <View style={styles.state}><AppText variant="label">검색 결과가 없어요</AppText><AppText color={colors.muted}>음식점 이름이나 주소를 다시 확인해주세요.</AppText></View>}
          </>
        )}
      </ScrollView>
    </Screen>
  );
}

function ResultSection({ title, description, count, children }: { title: string; description?: string; count: number; children: ReactNode }) {
  if (count === 0) return null;
  return <View style={styles.section}><View style={styles.sectionHeading}><View style={styles.sectionCopy}><AppText variant="section">{title}</AppText>{description && <AppText color={colors.muted} variant="caption">{description}</AppText>}</View><View style={styles.badge}><AppText color={colors.resultDeep} variant="caption" weight="700">{count}</AppText></View></View>{children}</View>;
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 22, paddingTop: spacing.lg, paddingBottom: spacing.xxl },
  searchRow: { flexDirection: 'row', gap: spacing.xs },
  field: { flex: 1, minHeight: 48, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing.sm, gap: spacing.xs },
  input: { flex: 1, color: colors.text, fontSize: 14, paddingVertical: 0 },
  button: { minWidth: 72, minHeight: 48, borderRadius: radius.md, backgroundColor: colors.jade, alignItems: 'center', justifyContent: 'center' },
  pressed: { opacity: 0.75 },
  summary: { paddingTop: spacing.md, paddingBottom: spacing.xs },
  section: { paddingTop: spacing.sm, paddingBottom: spacing.sm },
  sectionHeading: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.sm },
  sectionCopy: { flex: 1, gap: 2 },
  badge: { minWidth: 28, height: 28, borderRadius: radius.pill, backgroundColor: colors.resultSoft, alignItems: 'center', justifyContent: 'center' },
  state: { minHeight: 200, alignItems: 'center', justifyContent: 'center', gap: spacing.sm },
  warning: { backgroundColor: colors.skySoft, borderRadius: radius.md, padding: spacing.sm, flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
});
