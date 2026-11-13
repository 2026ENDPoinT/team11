import { useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { ApiError } from '../../api/client';
import * as api from '../../api/endpoints';
import { useSession } from '../../session';
import { Button, Screen, Spacer, t } from '../../components/ui';
import { C } from '../../theme';
import { draft } from './onboarding';
import { examDraft } from './SleepProfileScreen';

// 0-5 온보딩 3 · 알림 허용(258:171). 여기서 온보딩 전체를 한 번에 제출.
// ponytail: OS 푸시 권한 요청은 아직 없음(알림 발송 기능 자체가 서버에 없음). 서버 플래그만 저장.
export default function NotificationPermissionScreen() {
  const nav = useNavigation();
  const { setMe } = useSession();
  const [busy, setBusy] = useState<'allow' | 'later' | null>(null);
  const [msg, setMsg] = useState('');

  const finish = async (allowed: boolean) => {
    setBusy(allowed ? 'allow' : 'later');
    setMsg('');
    try {
      draft.notificationAllowed = allowed;
      await api.submitOnboarding(draft);
      // 시험 기간 화면의 '목표 최소 수면'은 온보딩 바디에 없어서 따로. 실패해도 온보딩은 끝난 상태라 무시.
      await api.updateSettings({ minSleepMin: Math.min(examDraft.minSleepMin, draft.sleepNeedMin) }).catch(() => {});
      setMe(await api.getMe());
      nav.reset({ index: 0, routes: [{ name: 'TimetableImport' }] });
    } catch (e) {
      setMsg(e instanceof ApiError ? e.message : '저장에 실패했어요.');
    } finally {
      setBusy(null);
    }
  };

  return (
    <Screen>
      <Text style={t.step}>3 / 3</Text>
      <Spacer />
      <View style={st.illust}>
        <Image source={require('../../assets/images/notification.png')} style={st.img} />
        <Text style={st.title}>취침·기상 알림을 받아 보세요</Text>
        <Text style={st.desc}>{'권장 취침 30분 전과 카페인 마감 시각에\n알려 드려요.'}</Text>
      </View>
      <Spacer />
      {!!msg && <Text style={t.error}>{msg}</Text>}
      <Button title="알림 허용하기" onPress={() => finish(true)} loading={busy === 'allow'} disabled={!!busy} />
      <Button title="나중에" kind="ghost" onPress={() => finish(false)} loading={busy === 'later'} disabled={!!busy} />
    </Screen>
  );
}

const st = StyleSheet.create({
  illust: { alignItems: 'center', gap: 16 },
  img: { width: 160, height: 160, borderRadius: 80 },
  title: { fontSize: 20, fontWeight: '700', color: C.text },
  desc: { fontSize: 14, color: C.sub, textAlign: 'center' },
});
