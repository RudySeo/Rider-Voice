import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { useMutation } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { searchAddresses, type AddressSearchCandidate } from '@/shared/api/addresses';
import { usesMockApi } from '@/shared/api/client';
import { buildManualReviewTarget, reviewTargetToRouteParams, type DeliveryPlatform } from '@/shared/api/reviewTargets';
import { useAuth } from '@/shared/auth/AuthProvider';
import { AppText } from '@/shared/components/AppText';
import { PrimaryButton } from '@/shared/components/PrimaryButton';
import { Screen } from '@/shared/components/Screen';
import { ScreenHeader } from '@/shared/components/ScreenHeader';
import { colors, radius, spacing } from '@/shared/theme';
import { canWriteReview } from '@/shared/auth/roles';

const platformChoices: { value: DeliveryPlatform; label: string }[] = [
  { value: 'BAEMIN', label: '배달의민족' },
  { value: 'COUPANG_EATS', label: '쿠팡이츠' },
  { value: 'YOGIYO', label: '요기요' },
  { value: 'OTHER', label: '기타' },
];

export default function ManualReviewTargetScreen() {
  const params = useLocalSearchParams<{ query?: string }>();
  const auth = useAuth();
  const initialQuery = typeof params.query === 'string' ? params.query.trim() : '';
  const [query, setQuery] = useState(initialQuery);
  const [selected, setSelected] = useState<AddressSearchCandidate | null>(null);
  const [brandName, setBrandName] = useState('');
  const [detailAddress, setDetailAddress] = useState('');
  const [platforms, setPlatforms] = useState<DeliveryPlatform[]>([]);
  const addressSearch = useMutation({
    mutationFn: searchAddresses,
    onSuccess: () => setSelected(null),
  });

  useEffect(() => {
    if (!auth.restoring && !auth.user && !usesMockApi) {
      router.replace({ pathname: '/login', params: { next: '/review/manual-target', manualQuery: query } });
    }
  }, [auth.restoring, auth.user, query]);
  useEffect(() => {
    if (!auth.restoring && auth.user && !canWriteReview(auth.user)) router.replace('/activity');
  }, [auth.restoring, auth.user]);

  const runSearch = () => {
    const normalized = query.trim();
    if (normalized.length < 2 || normalized.length > 100) {
      Alert.alert('주소를 확인해주세요', '주소 검색어는 2자 이상 100자 이하로 입력해주세요.');
      return;
    }
    setQuery(normalized);
    addressSearch.mutate(normalized);
  };

  const togglePlatform = (platform: DeliveryPlatform) => {
    setPlatforms((current) => current.includes(platform)
      ? current.filter((value) => value !== platform)
      : [...current, platform]);
  };

  const continueToReview = () => {
    if (!selected) {
      Alert.alert('주소를 선택해주세요', '검색 결과에서 실제 픽업 장소를 선택해주세요.');
      return;
    }
    try {
      const target = buildManualReviewTarget({
        addressQuery: addressSearch.data?.query ?? query,
        candidate: selected,
        detailAddress,
        name: brandName,
        platforms,
      });
      router.push({
        pathname: '/review/new',
        params: { ...reviewTargetToRouteParams(target), place: brandName.trim() },
      });
    } catch (error) {
      Alert.alert('입력 내용을 확인해주세요', error instanceof Error ? error.message : '잠시 후 다시 시도해주세요.');
    }
  };

  if (auth.restoring) {
    return <Screen><ScreenHeader title="직접 등록" /><View style={styles.state}><ActivityIndicator color={colors.jade} /><AppText color={colors.muted}>로그인 상태를 확인하고 있어요</AppText></View></Screen>;
  }
  if (!auth.user || usesMockApi) {
    return <Screen><ScreenHeader title="직접 등록" /><View style={styles.state}><AppText variant="label">{usesMockApi ? '공개 미리보기에서는 음식점을 등록할 수 없어요.' : '로그인 화면으로 이동하고 있어요.'}</AppText></View></Screen>;
  }
  if (!canWriteReview(auth.user)) {
    return <Screen><ScreenHeader title="직접 등록" /><View style={styles.state}><AppText variant="label">리뷰 작성에는 라이더 권한이 필요해요.</AppText><PrimaryButton label="내 활동에서 인증하기" onPress={() => router.replace('/activity')} /></View></Screen>;
  }

  const candidates = addressSearch.data?.candidates ?? [];
  return (
    <Screen>
      <ScreenHeader title="카카오에 없는 브랜드 등록" />
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.notice}><MaterialCommunityIcons color={colors.skyStrong} name="information-outline" size={20} /><AppText color={colors.muted} style={styles.flex} variant="caption">주소와 브랜드 정보는 리뷰와 함께 한 번에 등록돼요. 서버가 주소 검색을 다시 실행해 선택 결과를 검증합니다.</AppText></View>

        <View style={styles.fieldGroup}>
          <AppText variant="label">실제 픽업 장소 주소</AppText>
          <View style={styles.searchRow}>
            <TextInput accessibilityLabel="픽업 장소 주소" maxLength={100} onChangeText={setQuery} onSubmitEditing={runSearch} placeholder="도로명, 지번 또는 건물명" placeholderTextColor={colors.muted} returnKeyType="search" style={[styles.input, styles.flex]} value={query} />
            <Pressable disabled={addressSearch.isPending} onPress={runSearch} style={({ pressed }) => [styles.searchButton, pressed && styles.pressed]}><AppText color={colors.surface} weight="700">검색</AppText></Pressable>
          </View>
        </View>

        {addressSearch.isPending && <View style={styles.inlineState}><ActivityIndicator color={colors.jade} /><AppText color={colors.muted}>주소를 찾고 있어요</AppText></View>}
        {addressSearch.isError && <View style={styles.inlineState}><AppText>주소를 불러오지 못했어요.</AppText><Pressable onPress={runSearch}><AppText color={colors.jade} weight="700">다시 시도</AppText></Pressable></View>}
        {addressSearch.isSuccess && candidates.length === 0 && <View style={styles.inlineState}><AppText>일치하는 주소가 없어요.</AppText><AppText color={colors.muted} variant="caption">도로명이나 건물명을 바꿔 다시 검색해주세요.</AppText></View>}
        {candidates.length > 0 && <View style={styles.candidates}><AppText color={colors.muted} variant="caption">픽업 장소 한 곳을 선택해주세요.</AppText>{candidates.map((candidate) => {
          const active = selected === candidate;
          return <Pressable accessibilityRole="radio" accessibilityState={{ checked: active }} key={`${candidate.standardAddress}-${candidate.existingPickupLocationId ?? 'new'}`} onPress={() => setSelected(candidate)} style={[styles.candidate, active && styles.selected]}><View style={styles.flex}><AppText variant="label">{candidate.standardAddress}</AppText>{candidate.lotNumberAddress && <AppText color={colors.muted} variant="caption">지번 {candidate.lotNumberAddress}</AppText>}<AppText color={candidate.existingPickupLocationId ? colors.jade : colors.muted} variant="caption">{candidate.existingPickupLocationId ? '등록된 픽업 장소에 브랜드만 추가' : '새 픽업 장소로 검증 후 등록'}</AppText></View><MaterialCommunityIcons color={active ? colors.jade : colors.muted} name={active ? 'radiobox-marked' : 'radiobox-blank'} size={22} /></Pressable>;
        })}</View>}

        {selected && <>
          <View style={styles.fieldGroup}><AppText variant="label">소비자에게 보이는 배달 브랜드명</AppText><TextInput accessibilityLabel="배달 브랜드명" maxLength={255} onChangeText={setBrandName} placeholder="예: 강남 분식" placeholderTextColor={colors.muted} style={styles.input} value={brandName} /></View>
          {!selected.existingPickupLocationId && <View style={styles.fieldGroup}><AppText variant="label">상세 주소 <AppText color={colors.muted} variant="caption">선택</AppText></AppText><TextInput accessibilityLabel="상세 주소" maxLength={255} onChangeText={setDetailAddress} placeholder="층, 호수 등" placeholderTextColor={colors.muted} style={styles.input} value={detailAddress} /></View>}
          <View style={styles.fieldGroup}><AppText variant="label">확인한 배달 앱 <AppText color={colors.muted} variant="caption">선택</AppText></AppText><View style={styles.platforms}>{platformChoices.map((platform) => {
            const active = platforms.includes(platform.value);
            return <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: active }} key={platform.value} onPress={() => togglePlatform(platform.value)} style={[styles.platform, active && styles.selected]}><AppText color={active ? colors.jade : colors.text} variant="caption" weight="700">{platform.label}</AppText></Pressable>;
          })}</View></View>
          <PrimaryButton label="리뷰 작성으로 계속" onPress={continueToReview} />
        </>}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { padding: 22, paddingBottom: spacing.xxxl, gap: spacing.lg },
  state: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.sm, padding: 22 },
  notice: { borderRadius: radius.md, backgroundColor: colors.skySoft, padding: spacing.md, flexDirection: 'row', gap: spacing.sm },
  flex: { flex: 1 },
  fieldGroup: { gap: spacing.xs },
  searchRow: { flexDirection: 'row', gap: spacing.xs },
  input: { minHeight: 48, borderRadius: radius.md, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.surface, color: colors.text, paddingHorizontal: spacing.md },
  searchButton: { minWidth: 72, minHeight: 48, borderRadius: radius.md, backgroundColor: colors.jade, alignItems: 'center', justifyContent: 'center' },
  pressed: { opacity: 0.75 },
  inlineState: { minHeight: 88, alignItems: 'center', justifyContent: 'center', gap: spacing.xs },
  candidates: { gap: spacing.xs },
  candidate: { minHeight: 82, borderWidth: 1, borderColor: colors.line, borderRadius: radius.md, padding: spacing.md, flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  selected: { borderColor: colors.jade, backgroundColor: colors.jadeSoft },
  platforms: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.xs },
  platform: { minHeight: 40, borderWidth: 1, borderColor: colors.line, borderRadius: radius.pill, paddingHorizontal: spacing.md, alignItems: 'center', justifyContent: 'center' },
});
