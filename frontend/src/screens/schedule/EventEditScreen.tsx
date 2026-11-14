import { useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation, type StaticScreenProps } from '@react-navigation/native';
import { ApiError } from '../../api/client';
import * as api from '../../api/endpoints';
import type { EventType } from '../../api/types';
import { Calendar, firstOfMonth, lastOfMonth, monthOf } from '../../components/Calendar';
import { Button, Check, Field, Pill, Screen, Spacer, t } from '../../components/ui';
import { TimeWheel } from '../../components/Wheel';
import { fmtDateKo, fmtHM, kstIso, parseIso, todayKst } from '../../kst';
import { C } from '../../theme';
import { useApi, useApiOnce } from '../../useApi';

const TYPES: { value: EventType; label: string }[] = [
  { value: 'ASSIGNMENT', label: '과제' },
  { value: 'EXAM', label: '시험' },
  { value: 'PERSONAL', label: '약속' },
  { value: 'ETC', label: '기타' },
];

type Props = StaticScreenProps<{ eventId?: number; date?: string }>;

// 3-3 일정 추가(262:776) + 3-2-2 일정 수정(삭제 포함) + 시간 휠(267:495). 캘린더에서 날짜를 고른다.
// 종일 체크 시: 캘린더 탭이 시작일→종료일 순으로 범위를 잡는다(수업철회기간 같은 밴드).
export default function EventEditScreen({ route }: Props) {
  const nav = useNavigation();
  const eventId = route.params?.eventId;
  const existing = useApiOnce(() => (eventId ? api.getEvent(eventId) : Promise.resolve(null)), [eventId]);

  const [title, setTitle] = useState('');
  const [type, setType] = useState<EventType>('ASSIGNMENT');
  const [date, setDate] = useState(route.params?.date ?? todayKst());
  const [endDate, setEndDate] = useState<string | null>(null);
  const [allDay, setAllDay] = useState(false);
  const [start, setStart] = useState(23 * 60);
  const [end, setEnd] = useState(23 * 60 + 59);
  const [month, setMonth] = useState(monthOf(route.params?.date ?? todayKst()));
  const [wheel, setWheel] = useState<'start' | 'end' | null>(null);
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const [filled, setFilled] = useState(false);
  const ev = useApi(() => api.getEvents(firstOfMonth(month), lastOfMonth(month)), [month]);

  if (existing.data && !filled) {
    const e = existing.data;
    setFilled(true);
    setTitle(e.title);
    setType(e.type);
    setAllDay(e.allDay);
    const s = parseIso(e.startAt);
    setDate(s.ymd);
    setMonth(monthOf(s.ymd));
    setStart(s.min);
    if (e.endAt) {
      const en = parseIso(e.endAt);
      setEnd(en.min);
      if (e.allDay && en.ymd > s.ymd) setEndDate(en.ymd);
    }
  }

  const pick = (d: string) => {
    if (allDay && d > date && !endDate) setEndDate(d);
    else {
      setDate(d);
      setEndDate(null);
    }
  };

  const save = async () => {
    setBusy(true);
    setMsg('');
    const body = allDay
      ? { title: title.trim(), type, startAt: kstIso(date, 0), endAt: kstIso(endDate ?? date, 23 * 60 + 59), allDay: true }
      : { title: title.trim(), type, startAt: kstIso(date, start), endAt: kstIso(date, end), allDay: false };
    try {
      if (eventId) await api.updateEvent(eventId, body);
      else await api.createEvent(body);
      nav.goBack();
    } catch (e) {
      setMsg(e instanceof ApiError ? e.message : '저장에 실패했어요.');
    } finally {
      setBusy(false);
    }
  };

  const remove = () =>
    Alert.alert('일정 삭제', `${title}을(를) 삭제할까요?`, [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: async () => {
          try {
            await api.deleteEvent(eventId!);
            nav.goBack();
          } catch (e) {
            setMsg(e instanceof ApiError ? e.message : '삭제에 실패했어요.');
          }
        },
      },
    ]);

  const ok = !!title.trim() && (allDay || start < end);
  // 편집 중인 일정은 캘린더 표시에서 현재 입력값으로 대체
  const others = (ev.data?.events ?? []).filter(e => e.eventId !== eventId);

  return (
    <Screen
      title={eventId ? '일정 수정' : '일정 추가'}
      right={eventId ? <Pressable onPress={remove} hitSlop={8} accessibilityRole="button"><Text style={st.delete}>삭제</Text></Pressable> : undefined}
    >
      <Calendar month={month} selected={date} events={others} onChangeMonth={setMonth} onSelect={pick} />
      <Field placeholder="제목" value={title} onChangeText={setTitle} maxLength={100} />
      <Text style={t.label}>종류</Text>
      <View style={st.types}>
        {TYPES.map(o => (
          <Pill key={o.value} title={o.label} kind={o.value === type ? 'selected' : 'outline'} onPress={() => setType(o.value)} style={st.typePill} />
        ))}
      </View>
      <Check label="종일" checked={allDay} onPress={() => { setAllDay(a => !a); setEndDate(null); }} />
      <View style={st.row}>
        <View style={[st.cell, st.dateCell]}>
          <Text style={st.cellText}>{fmtDateKo(date)}</Text>
        </View>
        {allDay ? (
          <View style={[st.cell, st.flex]}>
            <Text style={st.cellText}>{endDate ? `~ ${fmtDateKo(endDate)}` : '하루 (종료일은 캘린더에서)'}</Text>
          </View>
        ) : (
          <>
            <Pressable style={[st.cell, st.flex]} onPress={() => setWheel('start')} accessibilityRole="button" accessibilityLabel="시작 시간">
              <Text style={st.cellTextDark}>{fmtHM(start)}</Text>
            </Pressable>
            <Pressable style={[st.cell, st.flex]} onPress={() => setWheel('end')} accessibilityRole="button" accessibilityLabel="종료 시간">
              <Text style={st.cellTextDark}>{fmtHM(end)}</Text>
            </Pressable>
          </>
        )}
      </View>
      {!!msg && <Text style={t.error}>{msg}</Text>}
      {!allDay && start >= end && <Text style={t.error}>종료 시간이 시작보다 늦어야 해요.</Text>}
      <Spacer />
      <Button title="저장" disabled={!ok} loading={busy} onPress={save} />
      <TimeWheel visible={wheel === 'start'} title="시작 시간" value={start} onChange={setStart} onClose={() => setWheel(null)} />
      <TimeWheel visible={wheel === 'end'} title="종료 시간" value={end} onChange={setEnd} onClose={() => setWheel(null)} />
    </Screen>
  );
}

const st = StyleSheet.create({
  types: { flexDirection: 'row', gap: 8 },
  typePill: { flex: 1, alignItems: 'center', alignSelf: 'stretch' },
  row: { flexDirection: 'row', gap: 8, alignItems: 'flex-end' },
  cell: { paddingHorizontal: 14, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: C.line },
  dateCell: { width: 156 },
  flex: { flex: 1 },
  cellText: { fontSize: 14, color: C.sub },
  cellTextDark: { fontSize: 14, color: C.text },
  delete: { fontSize: 14, color: '#CE4444', paddingHorizontal: 8 },
});
