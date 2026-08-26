import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { ScrollView, StyleSheet, View } from 'react-native';

import { AppText } from '@/shared/components/AppText';
import { Screen } from '@/shared/components/Screen';
import { ScreenHeader } from '@/shared/components/ScreenHeader';
import { colors, radius, spacing } from '@/shared/theme';

const notVerified = ['작성자의 배달 라이더 신분', '음식점 방문 또는 픽업 사실', '주문 내역과 배달 플랫폼 활동'];

export default function TrustScreen() {
  return <Screen><ScreenHeader title="리뷰 안내" /><ScrollView contentContainerStyle={styles.content}><View style={styles.icon}><MaterialCommunityIcons color={colors.skyStrong} name="information-outline" size={30} /></View><AppText variant="display">경험을 볼 때 알아두세요</AppText><AppText color={colors.muted}>Rider Voice는 매장 운영 경험을 공유하지만 작성자의 자격이나 방문 사실을 인증하지 않아요.</AppText><View style={styles.section}><AppText variant="section">확인하지 않는 정보</AppText>{notVerified.map((item) => <View key={item} style={styles.row}><MaterialCommunityIcons color={colors.muted} name="minus-circle-outline" size={19} /><AppText>{item}</AppText></View>)}</View><View style={styles.card}><AppText variant="label">통계는 서로 다른 작성자 5명부터 공개해요</AppText><AppText color={colors.muted}>5명 기준은 방문 인증이나 조작 방지를 보장하는 기준이 아니라, 소수 의견을 바로 통계처럼 보이지 않게 하는 공개 기준입니다.</AppText></View></ScrollView></Screen>;
}

const styles = StyleSheet.create({ content: { padding: 22, gap: spacing.md }, icon: { width: 58, height: 58, borderRadius: 20, backgroundColor: colors.skySoft, alignItems: 'center', justifyContent: 'center' }, section: { paddingTop: spacing.md, gap: spacing.sm }, row: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, minHeight: 42 }, card: { marginTop: spacing.sm, padding: spacing.md, borderRadius: radius.md, backgroundColor: colors.jadeSoft, gap: spacing.xs } });
