import { Pressable, StyleSheet, Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import Avatar from '../../assets/icons/settings/avatar.svg';
import { C, Screen, t } from '../../components/ui';
import { useStore } from '../../store';

// 5 설정 (14:1024)
export default function SettingsScreen() {
  const navigation = useNavigation();
  const { user } = useStore();

  return (
    <Screen title="설정" tab scroll>
      <Pressable style={styles.profile} onPress={() => navigation.navigate('Profile')}>
        <Avatar width={48} height={48} />
        <Text style={styles.name}>{user.name}</Text>
        <Text style={styles.chev}>›</Text>
      </Pressable>

      <Text style={t.sub}>알림</Text>
      <Pressable style={styles.item} onPress={() => navigation.navigate('NotificationSettings')}>
        <Text style={styles.itemText}>알림 설정</Text>
      </Pressable>

      <Text style={t.sub}>연동</Text>
      {/* 피그마 5에는 아직 'eclass 연동'으로 적혀 있지만 5-3이 에브리타임으로 바뀌어서 맞춤 */}
      <Pressable style={styles.item} onPress={() => navigation.navigate('TimetableImport', { back: true })}>
        <Text style={styles.itemText}>에브리타임 시간표 가져오기</Text>
      </Pressable>

      <Text style={t.sub}>계정</Text>
      <Pressable style={styles.item} onPress={() => navigation.navigate('ConsentManage')}>
        <Text style={styles.itemText}>약관 및 동의 관리</Text>
      </Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  profile: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: C.line,
    borderRadius: 12,
  },
  name: { flex: 1, fontSize: 16, fontWeight: '700', color: C.text },
  chev: { fontSize: 16, color: C.sub },
  item: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 4, paddingVertical: 14 },
  itemText: { flex: 1, fontSize: 15, color: C.text },
});
