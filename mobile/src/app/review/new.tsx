import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { Alert, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { z } from 'zod';

import { RatingValue } from '@/shared/api/types';
import { AppText } from '@/shared/components/AppText';
import { PrimaryButton } from '@/shared/components/PrimaryButton';
import { Screen } from '@/shared/components/Screen';
import { ScreenHeader } from '@/shared/components/ScreenHeader';
import { colors, radius, spacing } from '@/shared/theme';
import { ApiError, usesMockApi } from '@/shared/api/client';
import { createReview, getMyReview, updateReview, type CreateReviewBody, type UpdateReviewBody } from '@/shared/api/reviews';
import { reviewTargetFromRouteParams, type ReviewTarget, type ReviewTargetRouteParams } from '@/shared/api/reviewTargets';
import { useAuth } from '@/shared/auth/AuthProvider';
import { queryClient } from '@/shared/queryClient';

const questions = [
  { key: 'packagingStability', title: '포장 안정성은 어땠나요?', category: '배달 준비', icon: 'cube-outline' as const, color: colors.apricot },
  { key: 'orderReadiness', title: '주문은 제때 준비됐나요?', category: '배달 준비', icon: 'clock-outline' as const, color: colors.apricot },
  { key: 'handoffAccuracy', title: '주문 확인과 전달은 정확했나요?', category: '배달 준비', icon: 'check-decagram-outline' as const, color: colors.apricot },
  { key: 'pickupSpaceCleanliness', title: '픽업 공간은 깨끗했나요?', category: '픽업 환경', icon: 'map-marker-outline' as const, color: colors.mint },
  { key: 'staffInteraction', title: '직원 응대는 어땠나요?', category: '픽업 환경', icon: 'message-outline' as const, color: colors.mint },
  { key: 'riderRespect', title: '라이더를 존중하는 환경이었나요?', category: '픽업 환경', icon: 'account-heart-outline' as const, color: colors.mint },
] as const;

const choices: { value: RatingValue; label: string; description: string }[] = [
  { value: 'VERY_GOOD', label: '매우 좋음', description: '기대보다 훨씬 원활했어요' },
  { value: 'GOOD', label: '좋음', description: '대체로 원활했어요' },
  { value: 'NEEDS_IMPROVEMENT', label: '개선 필요', description: '불편한 점이 있었어요' },
  { value: 'MAJOR_IMPROVEMENT', label: '큰 개선 필요', description: '중요한 개선이 필요해요' },
  { value: 'NOT_OBSERVED', label: '관찰하지 못함', description: '확인하지 않은 항목이에요' },
];

type FormValues = Record<(typeof questions)[number]['key'], RatingValue | undefined> & { comment: string; visitMonth: string };

const reviewSchema = z.object({
  packagingStability: z.enum(['VERY_GOOD', 'GOOD', 'NEEDS_IMPROVEMENT', 'MAJOR_IMPROVEMENT', 'NOT_OBSERVED']), orderReadiness: z.enum(['VERY_GOOD', 'GOOD', 'NEEDS_IMPROVEMENT', 'MAJOR_IMPROVEMENT', 'NOT_OBSERVED']), handoffAccuracy: z.enum(['VERY_GOOD', 'GOOD', 'NEEDS_IMPROVEMENT', 'MAJOR_IMPROVEMENT', 'NOT_OBSERVED']), pickupSpaceCleanliness: z.enum(['VERY_GOOD', 'GOOD', 'NEEDS_IMPROVEMENT', 'MAJOR_IMPROVEMENT', 'NOT_OBSERVED']), staffInteraction: z.enum(['VERY_GOOD', 'GOOD', 'NEEDS_IMPROVEMENT', 'MAJOR_IMPROVEMENT', 'NOT_OBSERVED']), riderRespect: z.enum(['VERY_GOOD', 'GOOD', 'NEEDS_IMPROVEMENT', 'MAJOR_IMPROVEMENT', 'NOT_OBSERVED']), comment: z.string().trim().max(200), visitMonth: z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/),
});

type ValidReviewValues = z.infer<typeof reviewSchema>;

export function buildCreateReviewRequest(values: ValidReviewValues, restaurantTarget: ReviewTarget): CreateReviewBody {
  return { ...values, comment: values.comment.trim() || null, restaurantTarget };
}

export function buildUpdateReviewRequest(values: ValidReviewValues): UpdateReviewBody {
  const { visitMonth: _visitMonth, ...ratingsAndComment } = values;
  return { ...ratingsAndComment, comment: values.comment.trim() || null };
}

export default function ReviewCreateScreen() {
  const params = useLocalSearchParams<ReviewTargetRouteParams & { place?: string; reviewId?: string }>();
  const auth = useAuth();
  const reviewId = Number(params.reviewId);
  const editing = Number.isInteger(reviewId) && reviewId > 0;
  const months = seoulVisitMonths();
  const [step, setStep] = useState(0);
  const { control, setValue, handleSubmit, reset } = useForm<FormValues>({ defaultValues: { comment: '', visitMonth: months[0] } });
  const existing = useQuery({ queryKey: ['my-review', reviewId], queryFn: () => getMyReview(reviewId), enabled: editing && Boolean(auth.user) });
  useEffect(() => {
    if (existing.data) reset({ ...existing.data.ratings, comment: existing.data.comment ?? '', visitMonth: existing.data.visitMonth });
  }, [existing.data, reset]);
  const save = useMutation({ mutationFn: async (values: z.infer<typeof reviewSchema>) => {
    const target = reviewTargetFromRouteParams(params);
    if (!editing && !target) throw new Error('작성할 음식점을 다시 선택해주세요.');
    return editing
      ? updateReview(reviewId, buildUpdateReviewRequest(values))
      : createReview(buildCreateReviewRequest(values, target!));
  }, onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['my-reviews'] });
    Alert.alert(editing ? '경험을 수정했어요' : '경험을 등록했어요', '내 활동에서 공개 상태를 확인할 수 있어요.', [{ text: '내 활동 보기', onPress: () => router.replace('/activity') }]);
  }, onError: (error) => {
    const message = error instanceof ApiError && error.status === 409 ? '이미 활성 리뷰가 있거나 90일 재작성 제한 중이에요.' : error instanceof ApiError && error.status === 429 ? '최근 작성 횟수가 많아요. 잠시 후 다시 시도해주세요.' : error instanceof ApiError && error.status === 503 ? '장소를 확인할 수 없어요. 잠시 후 다시 시도해주세요.' : error instanceof Error ? error.message : '저장하지 못했어요.';
    Alert.alert('저장하지 못했어요', message);
  } });
  const question = questions[step];
  const selected = useWatch({ control, name: question.key });
  const lastStep = step === questions.length - 1;

  const submit = handleSubmit((values) => {
    const parsed = reviewSchema.safeParse(values);
    if (!parsed.success) { Alert.alert('확인해주세요', '모든 항목을 선택해주세요.'); return; }
    save.mutate(parsed.data);
  });

  const continueReview = () => {
    if (!selected) return;
    if (lastStep) submit(); else setStep((current) => current + 1);
  };

  if (!auth.user || usesMockApi) return <Screen><ScreenHeader title={editing ? '리뷰 수정' : '리뷰 작성'} /><View style={styles.locked}><AppText variant="label">{usesMockApi ? '공개 미리보기에서는 리뷰를 변경할 수 없어요.' : '리뷰를 작성하려면 로그인이 필요해요.'}</AppText><PrimaryButton label="로그인 화면으로" onPress={() => router.replace('/login?next=/review/new')} /></View></Screen>;

  return (
    <Screen>
      <ScreenHeader title={editing ? '리뷰 수정' : '리뷰 작성'} />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.keyboard}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <View style={styles.questionHeader}><View style={[styles.icon, { backgroundColor: question.color }]}><MaterialCommunityIcons color={colors.text} name={question.icon} size={23} /></View><View style={styles.questionCopy}><AppText color={colors.muted} variant="caption">{params.place ?? '강남분식 본점'} · {question.category}</AppText><AppText variant="label">{question.title}</AppText></View></View>
          <View style={styles.progress}><View style={[styles.progressFill, { width: `${((step + 1) / questions.length) * 100}%` }]} /></View>
          <View style={styles.tip}><MaterialCommunityIcons color={colors.jade} name="information-outline" size={20} /><View style={styles.tipCopy}><AppText color={colors.jadeDark} variant="label">{editing ? '기존 방문 연월은 변경할 수 없어요' : params.targetType === 'KAKAO' ? '선택한 장소를 다시 확인해요' : '공개 경험을 작성해요'}</AppText><AppText color={colors.muted} variant="caption">{params.targetType === 'KAKAO' ? '서버가 원 검색어와 카카오 장소 ID를 다시 검증해요.' : '구조화 평가와 의견은 저장 후 바로 공개돼요.'}</AppText></View></View>
          {!editing && <Controller control={control} name="visitMonth" render={({ field: { value, onChange } }) => <View style={styles.months}><AppText variant="label">방문 연월</AppText>{months.map((month) => <Pressable key={month} onPress={() => onChange(month)} style={[styles.month, value === month && styles.choiceActive]}><AppText color={value === month ? colors.jade : colors.text}>{month === months[0] ? `이번 달 · ${month}` : `지난 달 · ${month}`}</AppText></Pressable>)}</View>} />}
          <AppText color={colors.muted} variant="caption">직접 확인한 경험에 가장 가까운 답을 선택해주세요.</AppText>
          <View style={styles.choices}>
            {choices.map((choice) => {
              const active = selected === choice.value;
              return <Pressable accessibilityRole="radio" accessibilityState={{ checked: active }} key={choice.value} onPress={() => setValue(question.key, choice.value, { shouldDirty: true })} style={({ pressed }) => [styles.choice, active && styles.choiceActive, pressed && styles.pressed]}><View style={styles.choiceCopy}><AppText variant="label">{choice.label}</AppText><AppText color={colors.muted} variant="caption">{choice.description}</AppText></View><View style={[styles.radio, active && styles.radioActive]}>{active && <View style={styles.radioDot} />}</View></Pressable>;
            })}
          </View>
          {lastStep && <Controller control={control} name="comment" render={({ field: { value, onChange } }) => <View style={styles.commentWrap}><View style={styles.commentHeading}><AppText color={colors.muted} variant="caption">선택 사항</AppText><AppText color={colors.muted} variant="caption">{value.length}/200</AppText></View><TextInput maxLength={200} multiline onChangeText={onChange} placeholder="다른 이용자에게 도움이 될 내용을 적어주세요." placeholderTextColor="#8A929D" style={styles.comment} textAlignVertical="top" value={value} /></View>} />}
          <View style={styles.privacy}><MaterialCommunityIcons color={colors.muted} name="information-outline" size={17} /><AppText color={colors.muted} variant="caption">작성자 이름과 프로필은 공개되지 않아요.</AppText></View>
        </ScrollView>
        <View style={styles.footer}><PrimaryButton disabled={!selected || (editing && existing.isPending)} loading={save.isPending} label={lastStep ? (editing ? '수정 저장하기' : '경험 등록하기') : '다음 항목'} onPress={continueReview} /></View>
      </KeyboardAvoidingView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  keyboard: { flex: 1 }, content: { padding: 22, paddingBottom: spacing.lg, gap: spacing.md },
  questionHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm }, icon: { width: 46, height: 46, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center' }, questionCopy: { flex: 1, gap: 2 },
  progress: { height: 4, borderRadius: 2, backgroundColor: colors.line, overflow: 'hidden' }, progressFill: { height: 4, backgroundColor: colors.jade },
  tip: { borderRadius: radius.md, backgroundColor: colors.jadeSoft, padding: spacing.md, flexDirection: 'row', alignItems: 'center', gap: spacing.sm }, tipCopy: { flex: 1, gap: 2 },
  choices: { gap: spacing.xs }, choice: { minHeight: 66, borderRadius: radius.md, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, paddingHorizontal: spacing.md, flexDirection: 'row', alignItems: 'center', gap: spacing.sm }, choiceActive: { borderColor: colors.jade, backgroundColor: colors.jadeSoft }, choiceCopy: { flex: 1, gap: 2 }, pressed: { opacity: 0.75 },
  radio: { width: 22, height: 22, borderRadius: 11, borderWidth: 1, borderColor: '#B8BEC5', alignItems: 'center', justifyContent: 'center' }, radioActive: { borderColor: colors.jade }, radioDot: { width: 12, height: 12, borderRadius: 6, backgroundColor: colors.jade },
  commentWrap: { gap: spacing.xs }, commentHeading: { flexDirection: 'row', justifyContent: 'space-between' }, comment: { minHeight: 116, borderRadius: radius.md, borderWidth: 1, borderColor: colors.line, padding: spacing.md, color: colors.text, backgroundColor: colors.surface, fontSize: 14 },
  privacy: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs }, footer: { paddingHorizontal: 22, paddingTop: spacing.sm, paddingBottom: spacing.sm, backgroundColor: colors.surface, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  months: { gap: spacing.xs }, month: { minHeight: 44, justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, paddingHorizontal: spacing.md }, locked: { flex: 1, padding: 22, justifyContent: 'center', gap: spacing.md },
});

function seoulVisitMonths() {
  const parts = new Intl.DateTimeFormat('en', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit' }).formatToParts(new Date());
  const year = Number(parts.find((part) => part.type === 'year')?.value);
  const month = Number(parts.find((part) => part.type === 'month')?.value);
  const previous = month === 1 ? [year - 1, 12] : [year, month - 1];
  return [`${year}-${String(month).padStart(2, '0')}`, `${previous[0]}-${String(previous[1]).padStart(2, '0')}`];
}
