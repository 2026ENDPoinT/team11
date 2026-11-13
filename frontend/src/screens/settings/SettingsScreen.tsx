import { Pressable, StyleSheet, Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import AvatarIcon from '../../assets/icons/avatar.svg';
import { Screen, t } from '../../components/ui';
import { useSession } from '../../session';
import { C } from '../../theme';

// 5 설정(264:571)
export default function SettingsScreen() {
  const nav = useNavigation();
  const { me } = useSession();
  return (
    <Screen title="설정" back={false}>
      <Pressable style={st.profile} onPress={() => nav.navigate('Profile')} accessibilityRole="button">
        <AvatarIcon width={48} height={48} />
        <Text style={st.name}>{me?.name ?? ''}</Text>
        <Text style={st.chev}>›</Text>
      </Pressable>
      <Text style={t.label}>알림</Text>
      <Row title="알림 설정" onPress={() => nav.navigate('NotificationSettings')} />
      <Text style={t.label}>연동</Text>
      <Row title="에브리타임 시간표 가져오기" onPress={() => nav.navigate('TimetableImport', { fromSettings: true })} />
      <Text style={t.label}>계정</Text>
      <Row title="약관 및 동의 관리" onPress={() => nav.navigate('ConsentManage')} />
    </Screen>
  );
}

function Row({ title, onPress }: { title: string; onPress: () => void }) {
  return (
    <Pressable style={st.row} onPress={onPress} accessibilityRole="button">
      <Text style={st.rowText}>{title}</Text>
    </Pressable>
  );
}

const st = StyleSheet.create({
  profile: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 14, borderWidth: 1, borderColor: C.line, borderRadius: 12 },
  name: { flex: 1, fontSize: 16, fontWeight: '700', color: C.text },
  chev: { fontSize: 16, color: C.sub },
  row: { paddingHorizontal: 4, paddingVertical: 14 },
  rowText: { fontSize: 15, color: C.text },
});
