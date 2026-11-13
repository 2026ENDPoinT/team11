import { useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation, type StaticScreenProps } from '@react-navigation/native';
import { ApiError } from '../../api/client';
import * as api from '../../api/endpoints';
import type { DayOfWeek } from '../../api/types';
import { DAY_KO, Timetable } from '../../components/Timetable';
import { Button, Field, Screen, Spacer, Status, t } from '../../components/ui';
import { TimeWheel, WheelModal } from '../../components/Wheel';
import { fmtHM, parseHM } from '../../kst';
import { C } from '../../theme';
import { useApi } from '../../useApi';

const DAYS: DayOfWeek[] = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

type Props = StaticScreenProps<{ entryId?: number }>;

// 3-1 수업 추가·수정(260:365) + 시간 휠(272:531). 위 시간표의 ×로 바로 삭제.
export default function ClassEditScreen({ route }: Props) {
  const nav = useNavigation();
  const entryId = route.params?.entryId;
  const tt = useApi(() => api.getTimetable(), []);
  const editing = tt.data?.entries.find(e => e.entryId === entryId);

  const [name, setName] = useState('');
  const [day, setDay] = useState<DayOfWeek>('MON');
  const [start, setStart] = useState(9 * 60);
  const [end, setEnd] = useState(10 * 60 + 30);
  const [room, setRoom] = useState('');
  const [wheel, setWheel] = useState<'day' | 'start' | 'end' | null>(null);
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const [filled, setFilled] = useState(false);

  // 수정 모드: 서버 값 한 번만 채움
  if (editing && !filled) {
    setFilled(true);
    setName(editing.courseName);
    setDay(editing.dayOfWeek);
    setStart(parseHM(editing.startTime) ?? 540);
    setEnd(parseHM(editing.endTime) ?? 630);
    setRoom(editing.room ?? '');
  }

  const save = async () => {
    if (!tt.data) return;
    setBusy(true);
    setMsg('');
    const body = { semester: tt.data.semester, dayOfWeek: day, startTime: fmtHM(start), endTime: fmtHM(end), courseName: name.trim(), room: room.trim() || undefined };
    try {
      if (entryId) await api.updateEntry(entryId, body);
      else await api.createEntry(body);
      nav.goBack();
    } catch (e) {
      if (e instanceof ApiError && e.code === 'TIMETABLE_OVERLAP') {
        const c = (e.details?.conflicts ?? []) as { courseName?: string }[];
        setMsg(`같은 시간에 수업이 있어요${c.length ? `: ${c.map(x => x.courseName).filter(Boolean).join(', ')}` : ''}`);
      } else setMsg(e instanceof ApiError ? e.message : '저장에 실패했어요.');
    } finally {
      setBusy(false);
    }
  };

  const remove = (id: number, label: string) =>
    Alert.alert('수업 삭제', `${label}을(를) 삭제할까요?`, [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: async () => {
          try {
            await api.deleteEntry(id);
            if (id === entryId) nav.goBack();
            else tt.reload();
          } catch (e) {
            setMsg(e instanceof ApiError ? e.message : '삭제에 실패했어요.');
          }
        },
      },
    ]);

  const ok = !!name.trim() && start < end && !!tt.data;

  return (
    <Screen title={entryId ? '수업 수정' : '수업 추가'} scroll>
      {tt.error ? <Status error={tt.error} onRetry={tt.reload} /> : <Timetable entries={tt.data?.entries ?? []} editable onDelete={e => remove(e.entryId, e.courseName)} />}
      <Field placeholder="과목명" value={name} onChangeText={setName} maxLength={50} />
      <View style={st.row}>
        <Pressable style={st.dayBox} onPress={() => setWheel('day')} accessibilityRole="button" accessibilityLabel="요일">
          <Text style={st.boxText}>{DAY_KO[day]}</Text>
        </Pressable>
        <Pressable style={st.timeBox} onPress={() => setWheel('start')} accessibilityRole="button" accessibilityLabel="시작 시간">
          <Text style={st.boxText}>{fmtHM(start)}</Text>
        </Pressable>
        <Pressable style={st.timeBox} onPress={() => setWheel('end')} accessibilityRole="button" accessibilityLabel="종료 시간">
          <Text style={st.boxText}>{fmtHM(end)}</Text>
        </Pressable>
      </View>
      <Field placeholder="강의실" value={room} onChangeText={setRoom} maxLength={50} />
      <Pressable style={st.link} onPress={() => nav.navigate('TimetableImport', { fromSettings: true })} accessibilityRole="link">
        <Text style={st.linkText}>에브리타임에서 가져오기</Text>
      </Pressable>
      {!!msg && <Text style={t.error}>{msg}</Text>}
      {start >= end && <Text style={t.error}>종료 시간이 시작보다 늦어야 해요.</Text>}
      <Spacer />
      <Button title="저장" disabled={!ok} loading={busy} onPress={save} />

      <WheelModal
        visible={wheel === 'day'}
        title="요일"
        onClose={() => setWheel(null)}
        columns={[{ values: DAYS, labels: DAYS.map(d => DAY_KO[d]), value: day, onChange: setDay }]}
      />
      <TimeWheel visible={wheel === 'start'} title="시작 시간" value={start} onChange={setStart} onClose={() => setWheel(null)} />
      <TimeWheel visible={wheel === 'end'} title="종료 시간" value={end} onChange={setEnd} onClose={() => setWheel(null)} />
    </Screen>
  );
}

const st = StyleSheet.create({
  row: { flexDirection: 'row', gap: 8 },
  dayBox: { width: 42, alignItems: 'center', justifyContent: 'center', paddingVertical: 12, borderWidth: 1, borderColor: C.line, borderRadius: 10 },
  timeBox: { flex: 1, paddingHorizontal: 14, paddingVertical: 12, borderWidth: 1, borderColor: C.line, borderRadius: 10 },
  boxText: { fontSize: 14, color: C.text },
  link: { alignItems: 'center', paddingVertical: 12 },
  linkText: { fontSize: 11, color: 'rgba(115,115,115,0.6)', textDecorationLine: 'underline' },
});
