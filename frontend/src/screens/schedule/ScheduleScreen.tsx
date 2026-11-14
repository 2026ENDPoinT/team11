import { useState } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import * as api from '../../api/endpoints';
import type { Event } from '../../api/types';
import { Calendar, eventDays, firstOfMonth, lastOfMonth, monthOf } from '../../components/Calendar';
import { Timetable } from '../../components/Timetable';
import { AddButton, Screen, Segmented, Status } from '../../components/ui';
import { fmtHM, parseIso, todayKst } from '../../kst';
import { C } from '../../theme';
import { useApi } from '../../useApi';

// 3 일정·시간표(260:286) / 3-2 캘린더(262:266) / 3-2-1 일정 확인(262:430) — segmented로 한 화면.
// ponytail: 3-2-2 인라인 수정은 EventEdit 화면 재사용(행 누르면 이동). 인라인 편집이 필요해지면 그때.
export default function ScheduleScreen() {
  const nav = useNavigation();
  const [tab, setTab] = useState<'timetable' | 'calendar'>('timetable');
  const [month, setMonth] = useState(monthOf(todayKst()));
  const [selected, setSelected] = useState<string | null>(todayKst());

  const tt = useApi(() => api.getTimetable(), []);
  const ev = useApi(() => api.getEvents(firstOfMonth(month), lastOfMonth(month)), [month]);

  const dayEvents = (ev.data?.events ?? [])
    .filter(e => {
      if (!selected) return false;
      const { from, to } = eventDays(e);
      return from <= selected && selected <= to;
    })
    .sort((a, b) => a.startAt.localeCompare(b.startAt));

  return (
    <Screen>
      <Segmented
        options={[
          { value: 'timetable', label: '시간표' },
          { value: 'calendar', label: '캘린더' },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === 'timetable' ? (
        <>
          {tt.error ? (
            <Status error={tt.error} onRetry={tt.reload} />
          ) : (
            <Timetable entries={tt.data?.entries ?? []} onPress={e => nav.navigate('ClassEdit', { entryId: e.entryId })} />
          )}
          <AddButton title="+ 수업 추가" onPress={() => nav.navigate('ClassEdit', {})} />
        </>
      ) : (
        <>
          <Calendar month={month} selected={selected} events={ev.data?.events ?? []} onChangeMonth={setMonth} onSelect={setSelected} />
          {ev.error && <Status error={ev.error} onRetry={ev.reload} />}
          {dayEvents.map(e => (
            <EventRow key={e.eventId} event={e} onPress={() => nav.navigate('EventEdit', { eventId: e.eventId })} />
          ))}
          <AddButton title="+ 일정 추가" onPress={() => nav.navigate('EventEdit', { date: selected ?? todayKst() })} />
        </>
      )}
    </Screen>
  );
}

// 3-2-1 행: [시작][종료][제목]
function EventRow({ event, onPress }: { event: Event; onPress: () => void }) {
  const s = event.allDay ? '종일' : fmtHM(parseIso(event.startAt).min);
  const e = event.allDay || !event.endAt ? ' ' : fmtHM(parseIso(event.endAt).min);
  return (
    <Pressable style={st.row} onPress={onPress} accessibilityRole="button">
      <Text style={[st.cell, st.cellFixed]}>{s}</Text>
      <Text style={[st.cell, st.cellFixed]}>{e}</Text>
      <Text style={[st.cell, st.cellFlex]} numberOfLines={1}>{event.title}</Text>
    </Pressable>
  );
}

const st = StyleSheet.create({
  row: { flexDirection: 'row', gap: 8 },
  cell: { paddingHorizontal: 14, paddingVertical: 12, fontSize: 14, color: C.sub, borderBottomWidth: 1, borderBottomColor: C.line },
  cellFixed: { width: 94 },
  cellFlex: { flex: 1 },
});
