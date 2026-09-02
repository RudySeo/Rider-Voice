import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { router, useLocalSearchParams, type Href } from 'expo-router';
import { Alert, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useState } from 'react';

import { AppText } from '@/shared/components/AppText';
import { PrimaryButton } from '@/shared/components/PrimaryButton';
import { Screen } from '@/shared/components/Screen';
import { colors, radius, spacing } from '@/shared/theme';
import { useAuth } from '@/shared/auth/AuthProvider';
import { authAvailabilityMessage } from '@/shared/auth/authRuntime';
import {
  destinationAfterLogin,
  pendingIntentFromLoginParams,
  type LoginParams,
} from '@/shared/auth/loginContinuation';

export default function LoginScreen() {
  const params = useLocalSearchParams<LoginParams>();
  const auth = useAuth();
  const [loading, setLoading] = useState(false);
  const purpose = '내 활동을 이용하려면';

  const startLogin = async () => {
    if (auth.availability !== 'READY') {
      Alert.alert(
        auth.availability === 'EXPO_GO_UNSUPPORTED' ? '개발 빌드가 필요해요' : 'API 주소를 설정해주세요',
        authAvailabilityMessage(auth.availability),
      );
      return;
    }
    const intent = pendingIntentFromLoginParams(params);
    try {
      setLoading(true);
      const resumed = await auth.login(intent);
      const destination = destinationAfterLogin(resumed);
      if (destination) router.replace(destination as Href);
    } catch (error) {
      Alert.alert('로그인하지 못했어요', error instanceof Error ? error.message : '잠시 후 다시 시도해주세요.');
    } finally { setLoading(false); }
  };

  return (
    <Screen>
      <View style={styles.topBar}><View style={styles.side} /><AppText variant="label" weight="700">로그인</AppText><Pressable accessibilityLabel="닫기" onPress={() => router.back()} style={styles.side}><MaterialCommunityIcons color={colors.ink} name="close" size={22} /></Pressable></View>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.lock}><MaterialCommunityIcons color={colors.jade} name="lock-outline" size={30} /></View>
        <AppText color={colors.jade} variant="label" weight="700">Rider Voice</AppText>
        <View style={styles.hero}><AppText variant="display">경험을 이어서 확인해보세요</AppText><AppText color={colors.muted}>{purpose} 카카오 로그인이 필요해요.</AppText></View>
        <View style={styles.benefits}>
          <Benefit title="검색과 음식점 정보는 그대로 열려 있어요" description="둘러보기에는 로그인이 필요하지 않아요." />
          <Benefit title="작성자 이름과 프로필은 공개되지 않아요" description="내 활동에서 작성한 경험만 관리할 수 있어요." />
        </View>
        <View style={styles.actions}><PrimaryButton label="카카오로 계속하기" loading={loading} onPress={startLogin} tone="kakao" /><Pressable onPress={() => router.back()} style={styles.later}><AppText color={colors.muted} variant="caption">나중에 하기</AppText></Pressable></View>
        <View style={styles.notice}><MaterialCommunityIcons color={colors.skyStrong} name="information-outline" size={20} /><AppText color={colors.muted} style={styles.noticeCopy} variant="caption">카카오 로그인은 계정 식별에 사용해요. 리뷰 작성에는 로그인 후 별도 라이더 권한이 필요합니다.</AppText></View>
      </ScrollView>
    </Screen>
  );
}

function Benefit({ title, description }: { title: string; description: string }) {
  return <View style={styles.benefit}><MaterialCommunityIcons color={colors.jade} name="check-circle-outline" size={20} /><View style={styles.benefitCopy}><AppText variant="label">{title}</AppText><AppText color={colors.muted} variant="caption">{description}</AppText></View></View>;
}

const styles = StyleSheet.create({
  topBar: { height: 58, paddingHorizontal: spacing.md, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  side: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  content: { flexGrow: 1, alignItems: 'center', paddingHorizontal: 22, paddingTop: spacing.xxxl, paddingBottom: spacing.xxl },
  lock: { width: 62, height: 62, borderRadius: 22, backgroundColor: colors.jadeSoft, alignItems: 'center', justifyContent: 'center', marginBottom: spacing.sm },
  hero: { width: '100%', alignItems: 'center', gap: spacing.xs, paddingTop: spacing.lg, paddingBottom: spacing.lg },
  benefits: { width: '100%', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  benefit: { minHeight: 72, flexDirection: 'row', alignItems: 'center', gap: spacing.sm, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  benefitCopy: { flex: 1, gap: 2 },
  actions: { width: '100%', paddingTop: spacing.lg, gap: spacing.xs },
  later: { minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  notice: { marginTop: spacing.sm, width: '100%', borderRadius: radius.md, backgroundColor: colors.skySoft, padding: spacing.md, flexDirection: 'row', gap: spacing.sm },
  noticeCopy: { flex: 1 },
});
