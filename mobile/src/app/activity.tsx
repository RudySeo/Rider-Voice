import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { router } from 'expo-router';
import { Alert, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useInfiniteQuery, useMutation } from '@tanstack/react-query';

import { deleteReview, getMyReviews, ratingLabels } from '@/shared/api/reviews';
import { AppText } from '@/shared/components/AppText';
import { BottomTabBar } from '@/shared/components/BottomTabBar';
import { PrimaryButton } from '@/shared/components/PrimaryButton';
import { Screen } from '@/shared/components/Screen';
import { colors, radius, spacing } from '@/shared/theme';
import { useAuth } from '@/shared/auth/AuthProvider';
import { queryClient } from '@/shared/queryClient';

export default function ActivityScreen() {
  const auth = useAuth();
  const reviews = useInfiniteQuery({ queryKey: ['my-reviews'], queryFn: ({ pageParam }) => getMyReviews(pageParam), initialPageParam: undefined as string | undefined, getNextPageParam: (page) => page.nextCursor ?? undefined, enabled: Boolean(auth.user) });
  const remove = useMutation({ mutationFn: deleteReview, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-reviews'] }), onError: () => Alert.alert('삭제하지 못했어요', '잠시 후 다시 시도해주세요.') });
  if (auth.restoring) return <Screen><View style={styles.center}><AppText color={colors.muted}>로그인 상태를 확인하고 있어요</AppText></View></Screen>;
  if (!auth.user) return <Screen footer={<BottomTabBar active="activity" />}><View style={styles.center}><AppText variant="section">내 활동은 로그인 후 볼 수 있어요</AppText><PrimaryButton label="카카오 로그인" onPress={() => router.replace('/login?next=/activity')} /></View></Screen>;
  const summary = reviews.data?.pages[0];
  const items = reviews.data?.pages.flatMap((page) => page.items) ?? [];
  return (
    <Screen footer={<BottomTabBar active="activity" />}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.header}><View style={styles.profile}><MaterialCommunityIcons color={colors.jade} name="account-outline" size={27} /></View><View style={styles.headerCopy}><AppText variant="display">내 활동</AppText><AppText color={colors.muted}>작성한 경험과 공개 상태를 확인해보세요.</AppText></View></View>
        <View style={styles.stats}><StatCard background={colors.jadeSoft} count={summary?.authoredCount ?? 0} label="작성한 리뷰" /><StatCard background={colors.skySoft} count={summary?.publiclyVisibleCount ?? 0} label="공개된 리뷰" /></View>
        <View style={styles.sectionHeading}><AppText variant="section">최근 작성</AppText><AppText color={colors.jade} variant="caption" weight="700">전체 보기</AppText></View>
        {reviews.isError ? <View style={styles.center}><AppText>내 리뷰를 불러오지 못했어요</AppText><Pressable onPress={() => reviews.refetch()}><AppText color={colors.jade}>다시 시도</AppText></Pressable></View> : <View>{items.map((review, index) => <View key={review.reviewId} style={styles.review}><Pressable onPress={() => router.push({ pathname: '/review/new', params: { reviewId: String(review.reviewId), place: review.restaurant.name } })} style={({ pressed }) => [styles.reviewMain, pressed && styles.pressed]}><View style={[styles.icon, { backgroundColor: index % 2 === 0 ? colors.apricot : colors.lavender }]}><MaterialCommunityIcons color={colors.text} name="cube-outline" size={21} /></View><View style={styles.reviewCopy}><AppText variant="label">{review.restaurant.name}</AppText><AppText color={colors.muted} variant="caption">포장 안정성 · {ratingLabels[review.ratings.packagingStability]}</AppText><View style={styles.meta}><MaterialCommunityIcons color={colors.jade} name="clock-outline" size={15} /><AppText color={colors.jade} variant="caption">{review.visitMonth} 방문 · 공개됨</AppText></View></View><MaterialCommunityIcons color={colors.muted} name="chevron-right" size={21} /></Pressable><Pressable onPress={() => Alert.alert('리뷰를 삭제할까요?', '삭제 후 최초 작성 시각부터 90일이 지나야 다시 작성할 수 있어요.', [{ text: '취소' }, { text: '삭제', style: 'destructive', onPress: () => remove.mutate(review.reviewId) }])}><AppText color={colors.muted} variant="caption">삭제</AppText></Pressable></View>)}</View>}
        {reviews.hasNextPage && <PrimaryButton label="더 보기" loading={reviews.isFetchingNextPage} onPress={() => reviews.fetchNextPage()} tone="outline" />}
        {!reviews.isPending && items.length === 0 && <View style={styles.center}><AppText color={colors.muted}>아직 작성한 리뷰가 없어요.</AppText></View>}
        <View style={styles.action}><PrimaryButton label="새 리뷰 작성" onPress={() => router.push('/')} tone="outline" /></View>
        <Pressable onPress={() => auth.logout()} style={styles.logout}><AppText color={colors.muted} variant="caption">로그아웃</AppText></Pressable>
      </ScrollView>
    </Screen>
  );
}

function StatCard({ count, label, background }: { count: number; label: string; background: string }) { return <View style={[styles.stat, { backgroundColor: background }]}><AppText variant="title">{count}</AppText><AppText color={colors.muted} variant="caption">{label}</AppText></View>; }

const styles = StyleSheet.create({
  content: { padding: 22, paddingBottom: spacing.xxxl }, header: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, paddingBottom: spacing.lg }, profile: { width: 54, height: 54, borderRadius: 20, backgroundColor: colors.jadeSoft, alignItems: 'center', justifyContent: 'center' }, headerCopy: { flex: 1, gap: spacing.xs },
  stats: { flexDirection: 'row', gap: spacing.sm }, stat: { flex: 1, minHeight: 84, borderRadius: radius.md, padding: spacing.md, justifyContent: 'center', gap: spacing.xs }, sectionHeading: { paddingTop: spacing.xl, paddingBottom: spacing.xs, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  review: { minHeight: 82, flexDirection: 'row', alignItems: 'center', gap: spacing.sm, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line }, reviewMain: { flex: 1, minHeight: 82, flexDirection: 'row', alignItems: 'center', gap: spacing.sm }, pressed: { opacity: 0.72 }, icon: { width: 42, height: 42, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center' }, reviewCopy: { flex: 1, gap: 2 }, meta: { flexDirection: 'row', alignItems: 'center', gap: 4 }, action: { paddingTop: spacing.lg }, center: { flex: 1, minHeight: 180, alignItems: 'center', justifyContent: 'center', gap: spacing.md, padding: 22 }, logout: { alignItems: 'center', padding: spacing.lg },
});
