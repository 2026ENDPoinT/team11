import { useCallback, useState } from 'react';
import type { ReactNode } from 'react';
import { ActivityIndicator, Alert, Image, Pressable, StyleSheet, Text } from 'react-native';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import AvatarIcon from '../../assets/icons/avatar.svg';
import { Screen, t } from '../../components/ui';
import { useSession } from '../../session';
import { importSleepFromHealthKit, isHealthKitSupported } from '../../healthImport';
import { ApiError } from '../../api/client';
import { C } from '../../theme';
import { AVATAR_KEY } from './ProfileScreen';

// 5 설정(264:571)
export default function SettingsScreen() {
  const nav = useNavigation();
  const { me } = useSession();
  const [syncing, setSyncing] = useState(false);
  const [avatarUri, setAvatarUri] = useState<string | null>(null);

  useFocusEffect(useCallback(() => {
    AsyncStorage.getItem(AVATAR_KEY).then(setAvatarUri);
  }, []));

  const syncHealthKit = async () => {
    setSyncing(true);
    try {
      const r = await importSleepFromHealthKit();
      const skippedNote = r.skipped ? ` (이미 있는 기록 ${r.skipped}건은 건너뜀)` : '';
      Alert.alert('', `수면 기록 ${r.imported}건을 가져왔어요.${skippedNote}`);
    } catch (e) {
      Alert.alert('', e instanceof ApiError ? e.message : e instanceof Error ? e.message : '가져오기에 실패했어요.');
    } finally {
      setSyncing(false);
    }
  };

  return (
    <Screen title="설정" back={false}>
      <Pressable style={st.profile} onPress={() => nav.navigate('Profile')} accessibilityRole="button">
        {avatarUri ? <Image source={{ uri: avatarUri }} style={st.avatarImg} /> : <AvatarIcon width={48} height={48} />}
        <Text style={st.name}>{me?.name ?? ''}</Text>
        <Text style={st.chev}>›</Text>
      </Pressable>
      <Text style={t.label}>알림</Text>
      <Row title="알림 설정" onPress={() => nav.navigate('NotificationSettings')} />
      <Text style={t.label}>연동</Text>
      <Row title="에브리타임 시간표 가져오기" onPress={() => nav.navigate('TimetableImport', { fromSettings: true })} />
      {isHealthKitSupported && (
        <Row title="건강 앱에서 수면 정보 가져오기" onPress={syncHealthKit} disabled={syncing} right={syncing && <ActivityIndicator />} />
      )}
      <Text style={t.label}>계정</Text>
      <Row title="약관 및 동의 관리" onPress={() => nav.navigate('ConsentManage')} />
    </Screen>
  );
}

function Row({
  title,
  onPress,
  disabled,
  right,
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  right?: ReactNode;
}) {
  return (
    <Pressable style={st.row} onPress={onPress} disabled={disabled} accessibilityRole="button">
      <Text style={st.rowText}>{title}</Text>
      {right}
    </Pressable>
  );
}

const st = StyleSheet.create({
  profile: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 14, borderWidth: 1, borderColor: C.line, borderRadius: 12 },
  avatarImg: { width: 48, height: 48, borderRadius: 24 },
  name: { flex: 1, fontSize: 16, fontWeight: '700', color: C.text },
  chev: { fontSize: 16, color: C.sub },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 4, paddingVertical: 14 },
  rowText: { fontSize: 15, color: C.text },
});
