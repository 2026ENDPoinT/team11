import { useState } from 'react';
import { Alert, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation, type StaticScreenProps } from '@react-navigation/native';
import { ApiError } from '../../api/client';
import * as api from '../../api/endpoints';
import { Button, Field, Screen, Spacer, Status, t } from '../../components/ui';
import { TimeWheel } from '../../components/Wheel';
import { addDays, fmtDateKo, fmtHM, kstIso, parseIso } from '../../kst';
import { C } from '../../theme';
import { useApiOnce } from '../../useApi';
import { MOOD } from './ReportScreen';

type Props = StaticScreenProps<{ sleepLogId?: number; date: string }>;

// 4-2 수면 기록(264:542) + 시간 휠(277:572). date = 기상일. 취침이 기상보다 늦으면 전날 밤으로 본다.
export default function SleepRecordScreen({ route }: Props) {
  const nav = useNavigation();
  const { sleepLogId, date } = route.params;
  const existing = useApiOnce(() => (sleepLogId ? api.getSleepLog(sleepLogId) : Promise.resolve(null)), [sleepLogId]);
  const [id, setId] = useState(sleepLogId);
  const [bed, setBed] = useState(1 * 60 + 30);
  const [wake, setWake] = useState(8 * 60);
  const [quality, setQuality] = useState<1 | 2 | 3 | undefined>(3);
  const [memo, setMemo] = useState('');
  const [wheel, setWheel] = useState<'bed' | 'wake' | null>(null);
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const [filled, setFilled] = useState(false);

  if (existing.data && !filled) {
    setFilled(true);
    setBed(parseIso(existing.data.bedAt).min);
    setWake(parseIso(existing.data.wakeAt).min);
    setQuality(existing.data.quality ?? undefined);
    setMemo(existing.data.memo ?? '');
  }

  const save = async () => {
    setBusy(true);
    setMsg('');
    const bedDate = bed >= wake ? addDays(date, -1) : date;
    const body = { bedAt: kstIso(bedDate, bed), wakeAt: kstIso(date, wake), quality, memo: memo.trim() || undefined };
    try {
      if (id) await api.updateSleepLog(id, body);
      else await api.createSleepLog(body);
      nav.goBack();
    } catch (e) {
      // 그 기상일에 이미 기록 → 서버가 알려준 id로 수정 모드 전환
      if (e instanceof ApiError && e.code === 'SLEEP_LOG_DUPLICATE_DATE' && e.details?.sleepLogId) {
        setId(e.details.sleepLogId);
        setMsg('이 날 기록이 이미 있어요. 저장을 다시 누르면 덮어써요.');
      } else setMsg(e instanceof ApiError ? e.message : '저장에 실패했어요.');
    } finally {
      setBusy(false);
    }
  };

  const remove = () =>
    Alert.alert('기록 삭제', '이 날 수면 기록을 삭제할까요?', [
      { text: '취소', style: 'cancel' },
      { text: '삭제', style: 'destructive', onPress: () => api.deleteSleepLog(id!).then(() => nav.goBack()).catch(e => setMsg(e.message)) },
    ]);

  return (
    <Screen
      title={fmtDateKo(date)}
      right={id ? <Pressable onPress={remove} hitSlop={8} accessibilityRole="button"><Text style={st.delete}>삭제</Text></Pressable> : undefined}
    >
      {existing.error && <Status error={existing.error} />}
      <View style={st.row}>
        <Pressable style={st.half} onPress={() => setWheel('bed')} accessibilityRole="button" accessibilityLabel="취침 시간">
          <Text style={t.label}>취침 시간</Text>
          <Text style={st.timeText}>{fmtHM(bed)}</Text>
        </Pressable>
        <Pressable style={st.half} onPress={() => setWheel('wake')} accessibilityRole="button" accessibilityLabel="기상 시간">
          <Text style={t.label}>기상 시간</Text>
          <Text style={st.timeText}>{fmtHM(wake)}</Text>
        </Pressable>
      </View>
      <View style={st.moods}>
        <Text style={t.label}>컨디션</Text>
        {([3, 2, 1] as const).map(q => (
          <Pressable key={q} onPress={() => setQuality(q)} accessibilityRole="radio" accessibilityState={{ selected: quality === q }} accessibilityLabel={['', '나쁨', '보통', '좋음'][q]}>
            <Image source={MOOD[q]} style={[st.mood, quality === q && st.moodOn]} />
          </Pressable>
        ))}
      </View>
      <Field label="메모" placeholder="메모를 작성해주세요." value={memo} onChangeText={setMemo} maxLength={200} />
      {!!msg && <Text style={t.error}>{msg}</Text>}
      <Spacer />
      <Button title="저장" loading={busy} onPress={save} />
      <TimeWheel visible={wheel === 'bed'} title="취침 시간" value={bed} onChange={setBed} onClose={() => setWheel(null)} />
      <TimeWheel visible={wheel === 'wake'} title="기상 시간" value={wake} onChange={setWake} onClose={() => setWheel(null)} />
    </Screen>
  );
}

const st = StyleSheet.create({
  row: { flexDirection: 'row', gap: 8 },
  half: { flex: 1, gap: 6 },
  timeText: { paddingHorizontal: 14, paddingVertical: 12, fontSize: 14, color: C.text, borderBottomWidth: 1, borderBottomColor: C.line },
  moods: { flexDirection: 'row', alignItems: 'center', gap: 60 },
  mood: { width: 28, height: 24, opacity: 0.6 },
  moodOn: { width: 34, height: 29, opacity: 1 },
  delete: { fontSize: 14, color: '#CE4444', paddingHorizontal: 8 },
});
