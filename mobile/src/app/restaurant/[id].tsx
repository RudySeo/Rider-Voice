import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { useQuery } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { getRestaurant, getRestaurantReviews } from '@/shared/api/restaurants';
import { AppText } from '@/shared/components/AppText';
import { BottomTabBar } from '@/shared/components/BottomTabBar';
import { HelmetRating } from '@/shared/components/HelmetRating';
import { Screen } from '@/shared/components/Screen';
import { ScreenHeader } from '@/shared/components/ScreenHeader';
import { TrustBanner } from '@/shared/components/TrustBanner';
import { colors, spacing } from '@/shared/theme';
import type { AggregateMetric, RatingValue } from '@/shared/api/types';
import { useAuth } from '@/shared/auth/AuthProvider';
import { reviewTargetRoute } from '@/shared/navigation/reviewRoutes';

export default function RestaurantDetailScreen() {
  const auth = useAuth();
  const params = useLocalSearchParams<{ id: string }>();
  const restaurantId = Number(params.id);
  const validId = Number.isInteger(restaurantId) && restaurantId > 0;
  const detail = useQuery({ queryKey: ['restaurant', restaurantId], queryFn: () => getRestaurant(restaurantId), enabled: validId });
  const reviews = useQuery({ queryKey: ['restaurant', restaurantId, 'reviews'], queryFn: () => getRestaurantReviews(restaurantId), enabled: validId });

  if (!validId) return <Screen><ScreenHeader title="음식점 상세" /><View style={styles.state}><AppText variant="label">올바르지 않은 음식점 주소예요</AppText></View></Screen>;
  if (detail.isPending) {
    return <Screen><ScreenHeader title="음식점 상세" /><View style={styles.state}><ActivityIndicator color={colors.jade} /><AppText color={colors.muted}>운영 경험을 불러오고 있어요</AppText></View></Screen>;
  }
  if (!detail.data || detail.isError) {
    return <Screen><ScreenHeader title="음식점 상세" /><View style={styles.state}><AppText variant="label">음식점 정보를 불러오지 못했어요</AppText><Pressable onPress={() => detail.refetch()}><AppText color={colors.jade} weight="700">다시 시도</AppText></Pressable></View></Screen>;
  }

  const restaurant = detail.data;
  return (
    <Screen footer={<BottomTabBar active="home" />}>
      <ScreenHeader title="음식점 상세" />
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.identity}>
          <View style={styles.context}><MaterialCommunityIcons color={colors.muted} name="store-outline" size={16} /><AppText color={colors.muted} variant="caption">배달 앱에 표시되는 가게</AppText></View>
          <AppText variant="display">{restaurant.name}</AppText>
          <View style={styles.context}><MaterialCommunityIcons color={colors.muted} name="map-marker-outline" size={16} /><AppText color={colors.muted} variant="caption">실제 픽업 장소 · {restaurant.pickupLocation.standardAddress}</AppText></View>
        </View>
        <TrustBanner />
        <View style={styles.resultsHeading}><AppText variant="section">항목별 결과</AppText><AppText color={colors.muted} variant="caption">작성자 5명부터 공개</AppText></View>
        <MetricSection contributorCount={restaurant.brandReport.contributorCount} metrics={restaurant.brandReport.metrics ? [['포장 안정성', restaurant.brandReport.metrics.packagingStability], ['주문 준비 상태', restaurant.brandReport.metrics.orderReadiness], ['전달 정확성', restaurant.brandReport.metrics.handoffAccuracy]] : null} title="배달 준비 · 브랜드" />
        <MetricSection contributorCount={restaurant.pickupLocationReport.contributorCount} metrics={restaurant.pickupLocationReport.metrics ? [['픽업 공간 청결', restaurant.pickupLocationReport.metrics.pickupSpaceCleanliness], ['직원 응대', restaurant.pickupLocationReport.metrics.staffInteraction], ['라이더 존중', restaurant.pickupLocationReport.metrics.riderRespect]] : null} title="픽업 환경 · 장소" />
        <View style={styles.write}><Pressable onPress={() => router.push(reviewTargetRoute(Boolean(auth.user), { type: 'EXISTING', restaurantId: restaurant.restaurantId, place: restaurant.name }))}><AppText color={colors.jade} weight="700">이 음식점 경험 작성하기</AppText></Pressable></View>
        <View style={styles.experienceHeading}><AppText variant="section">개별 경험</AppText><Pressable onPress={() => router.push({ pathname: '/restaurant/[id]/reviews', params: { id: String(restaurantId) } })}><AppText color={colors.jade} variant="caption" weight="700">전체 보기</AppText></Pressable></View>
        {reviews.isError ? <Pressable onPress={() => reviews.refetch()}><AppText color={colors.jade}>개별 경험 다시 불러오기</AppText></Pressable> : (reviews.data?.items ?? []).slice(0, 3).map((review) => (
          <View key={review.reviewId} style={styles.review}><AppText color={colors.text}>{review.comment ?? '자유 의견이 없는 리뷰예요.'}</AppText><AppText color={colors.muted} variant="caption">{review.visitMonth} 방문 · 공개 리뷰 {review.authorActivity.publicReviewCount}개</AppText><AppText color={colors.muted} variant="caption">포장 {ratingLabel(review.ratings.packagingStability)} · 준비 {ratingLabel(review.ratings.orderReadiness)} · 청결 {ratingLabel(review.ratings.pickupSpaceCleanliness)}</AppText><AppText color={colors.skyStrong} variant="caption">{review.verificationNotice}</AppText></View>
        ))}
        {!reviews.isPending && !reviews.isError && (reviews.data?.items.length ?? 0) === 0 && <View style={styles.collecting}><AppText color={colors.muted}>아직 공개된 개별 경험이 없어요.</AppText></View>}
      </ScrollView>
    </Screen>
  );
}

function MetricSection({ title, contributorCount, metrics }: { title: string; contributorCount: number; metrics: [string, AggregateMetric][] | null }) {
  const [open, setOpen] = useState(true);
  if (!metrics) return <View style={styles.collecting}><AppText variant="label">{title}</AppText><AppText color={colors.muted}>서로 다른 작성자 {contributorCount}명 · 5명부터 항목 결과를 공개해요.</AppText></View>;
  return <View style={styles.metricGroup}><Pressable onPress={() => setOpen((value) => !value)} style={styles.groupHeading}><AppText variant="label">{title}</AppText><AppText color={colors.jade} variant="caption" weight="700">{open ? '접기' : '펼치기'}</AppText></Pressable>{open && <><View style={styles.helmetGuide}><MaterialCommunityIcons color={colors.helmet} name="racing-helmet" size={18} /><AppText color={colors.muted} variant="caption">헬멧이 많을수록 원활한 운영 경험이에요</AppText></View>{metrics.map(([label, metric]) => <View key={label}><HelmetRating label={label} notObservedCount={metric.notObservedCount} observedCount={metric.observedCount} score={metric.score} /><Distribution metric={metric} /></View>)}</>}</View>;
}

function Distribution({ metric }: { metric: AggregateMetric }) {
  const [visible, setVisible] = useState(false);
  return <View><Pressable onPress={() => setVisible((value) => !value)}><AppText color={colors.jade} variant="caption">{visible ? '응답 분포 닫기' : '응답 분포 보기'}</AppText></Pressable>{visible && Object.entries(metric.distribution).map(([key, value]) => <AppText color={colors.muted} key={key} variant="caption">{ratingLabel(key as RatingValue)} {value}%</AppText>)}</View>;
}

const ratingLabel = (rating: RatingValue) => ({ VERY_GOOD: '매우 좋음', GOOD: '좋음', NEEDS_IMPROVEMENT: '개선 필요', MAJOR_IMPROVEMENT: '큰 개선 필요', NOT_OBSERVED: '관찰하지 못함' })[rating];

const styles = StyleSheet.create({
  content: { paddingHorizontal: 22, paddingTop: spacing.lg, paddingBottom: spacing.xxxl },
  state: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.sm },
  identity: { gap: spacing.xs, paddingBottom: spacing.lg },
  context: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  resultsHeading: { paddingTop: spacing.xl, paddingBottom: spacing.sm, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  metricGroup: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  groupHeading: { minHeight: 54, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  helmetGuide: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, paddingBottom: spacing.xs },
  collecting: { marginTop: spacing.sm, paddingVertical: spacing.xl, borderTopWidth: 1, borderBottomWidth: 1, borderColor: colors.line, gap: spacing.xs },
  experienceHeading: { paddingTop: spacing.xl, paddingBottom: spacing.xs, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  review: { paddingVertical: spacing.md, gap: spacing.xs, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  write: { paddingVertical: spacing.lg, alignItems: 'center' },
});
