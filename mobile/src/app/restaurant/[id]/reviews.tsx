import { useInfiniteQuery } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { getRestaurantReviews } from '@/shared/api/restaurants';
import type { PublicReview, RatingValue } from '@/shared/api/types';
import { AppText } from '@/shared/components/AppText';
import { PrimaryButton } from '@/shared/components/PrimaryButton';
import { Screen } from '@/shared/components/Screen';
import { ScreenHeader } from '@/shared/components/ScreenHeader';
import { colors, spacing } from '@/shared/theme';

export default function PublicReviewListScreen() {
  const params = useLocalSearchParams<{ id: string }>();
  const restaurantId = Number(params.id);
  const validId = Number.isInteger(restaurantId) && restaurantId > 0;
  const reviews = useInfiniteQuery({
    queryKey: ['restaurant', restaurantId, 'reviews', 'all'],
    queryFn: ({ pageParam }) => getRestaurantReviews(restaurantId, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    enabled: validId,
  });
  const items = reviews.data?.pages.flatMap((page) => page.items) ?? [];

  if (!validId) return <Screen><ScreenHeader title="개별 경험" /><View style={styles.state}><AppText variant="label">올바르지 않은 음식점 주소예요</AppText></View></Screen>;

  return (
    <Screen>
      <ScreenHeader title="개별 경험 전체 보기" />
      <ScrollView contentContainerStyle={styles.content}>
        {reviews.isPending && <View style={styles.state}><ActivityIndicator color={colors.jade} /><AppText color={colors.muted}>개별 경험을 불러오고 있어요</AppText></View>}
        {reviews.isError && <View style={styles.state}><AppText variant="label">개별 경험을 불러오지 못했어요</AppText><Pressable onPress={() => reviews.refetch()}><AppText color={colors.jade} weight="700">다시 시도</AppText></Pressable></View>}
        {!reviews.isPending && !reviews.isError && items.length === 0 && <View style={styles.state}><AppText color={colors.muted}>아직 공개된 개별 경험이 없어요.</AppText></View>}
        {items.map((review) => <PublicReviewRow key={review.reviewId} review={review} />)}
        {reviews.hasNextPage && <PrimaryButton label="더 보기" loading={reviews.isFetchingNextPage} onPress={() => reviews.fetchNextPage()} tone="outline" />}
      </ScrollView>
    </Screen>
  );
}

function PublicReviewRow({ review }: { review: PublicReview }) {
  return (
    <View style={styles.review}>
      <AppText color={colors.text}>{review.comment ?? '자유 의견이 없는 리뷰예요.'}</AppText>
      <AppText color={colors.muted} variant="caption">{review.visitMonth} 방문 · 공개 리뷰 {review.authorActivity.publicReviewCount}개</AppText>
      <AppText color={colors.muted} variant="caption">포장 {ratingLabel(review.ratings.packagingStability)} · 준비 {ratingLabel(review.ratings.orderReadiness)} · 청결 {ratingLabel(review.ratings.pickupSpaceCleanliness)}</AppText>
      <AppText color={colors.skyStrong} variant="caption">{review.verificationNotice}</AppText>
    </View>
  );
}

const ratingLabel = (rating: RatingValue) => ({ VERY_GOOD: '매우 좋음', GOOD: '좋음', NEEDS_IMPROVEMENT: '개선 필요', MAJOR_IMPROVEMENT: '큰 개선 필요', NOT_OBSERVED: '관찰하지 못함' })[rating];

const styles = StyleSheet.create({
  content: { paddingHorizontal: 22, paddingTop: spacing.sm, paddingBottom: spacing.xxxl, gap: spacing.sm },
  state: { minHeight: 220, alignItems: 'center', justifyContent: 'center', gap: spacing.sm },
  review: { paddingVertical: spacing.md, gap: spacing.xs, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
});
