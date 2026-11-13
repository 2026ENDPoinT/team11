import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import * as api from '../../api/endpoints';
import type { NotificationSettings } from '../../api/types';
import { Pill, Screen, Status, Toggle } from '../../components/ui';
import { C } from '../../theme';
import { useApi } from '../../useApi';

const LEADS = [15, 30, 60] as const;

// 5-2 알림 설정(264:642). 바꿀 때마다 바로 PUT(3필드 전부 필수).
export default function NotificationSettingsScreen() {
  const s = useApi(() => api.getNotificationSettings(), []);
  const [msg, setMsg] = useState('');
  const v = s.data;

  const update = async (patch: Partial<NotificationSettings>) => {
    if (!v) return;
    const next = { ...v, ...patch };
    s.setData(next);
    try {
      await api.updateNotificationSettings(next);
    } catch (e: any) {
      setMsg(e?.message ?? '저장에 실패했어요.');
      s.reload();
    }
  };

  return (
    <Screen title="알림 설정">
      {!v ? (
        <Status loading={s.loading} error={s.error} onRetry={s.reload} />
      ) : (
        <>
          <View style={st.row}>
            <View style={st.texts}>
              <Text style={st.title}>기상 알람</Text>
              <Text style={st.sub}>권장 기상 시간에 울려요</Text>
            </View>
            <Toggle value={v.wakeAlarmEnabled} onChange={x => update({ wakeAlarmEnabled: x })} />
          </View>
          <View style={st.row}>
            <View style={st.texts}>
              <Text style={st.title}>취침 알림</Text>
              <Text style={st.sub}>권장 취침 {v.bedtimeLeadMin}분 전</Text>
            </View>
            <Toggle value={v.bedtimeAlarmEnabled} onChange={x => update({ bedtimeAlarmEnabled: x })} />
          </View>
          <View style={st.leads}>
            {LEADS.map(m => (
              <Pill key={m} title={`${m}분`} kind={v.bedtimeLeadMin === m ? 'selected' : 'outline'} style={st.lead} onPress={() => update({ bedtimeLeadMin: m })} />
            ))}
          </View>
          {!!msg && <Text style={st.err}>{msg}</Text>}
        </>
      )}
    </Screen>
  );
}

const st = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 8 },
  texts: { flex: 1, gap: 2 },
  title: { fontSize: 14, color: C.text },
  sub: { fontSize: 11, color: C.sub },
  leads: { flexDirection: 'row', gap: 8 },
  lead: { flex: 1, alignItems: 'center' },
  err: { fontSize: 13, fontWeight: '700', color: C.error },
});
