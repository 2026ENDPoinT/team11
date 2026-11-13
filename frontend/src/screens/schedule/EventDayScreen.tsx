import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useNavigation, type StaticScreenProps } from '@react-navigation/native';
import Calendar from '../../components/Calendar';
import { C, Screen, t } from '../../components/ui';
import { deleteEvent, fmtHM, parseHM, updateEvent, useStore, type Ev } from '../../store';

// 3-2-1 일정 확인 (14:869): 고른 날짜의 일정 목록 + 일정 추가(3-3)로 이동
// 3-2-2 일정 수정 (188:275): 일정 줄을 누르면 그 줄이 입력칸 + 삭제로 바뀜
// 화면 제목은 피그마 두 프레임 모두 '일정 추가'
export default function EventDayScreen({ route }: StaticScreenProps<{ date: string }>) {
  const navigation = useNavigation();
  const { events } = useStore();
  const [date, setDate] = useState(route.params.date);
  const [editing, setEditing] = useState<string | null>(null);
  // 기간 일정(endDate)은 기간 안의 모든 날에 보임. YYYY-MM-DD라 문자열 비교로 충분
  const today = events.filter(e => e.date === date || (!!e.endDate && e.date <= date && date <= e.endDate));

  const pickDate = (d: string) => {
    setDate(d);
    setEditing(null);
  };

  return (
    <Screen title="일정 추가" scroll>
      <Calendar month={new Date(date + 'T00:00:00')} selected={date} onSelect={pickDate} />
      {today.map(e =>
        e.id === editing ? (
          <EditRow key={e.id} ev={e} onDone={() => setEditing(null)} />
        ) : (
          <Pressable
            key={e.id}
            style={styles.row}
            onPress={() => setEditing(e.id)}
            accessibilityRole="button"
            accessibilityHint="눌러서 수정"
          >
            <Text style={[styles.cell, styles.time]}>{e.start != null ? fmtHM(e.start) : '종일'}</Text>
            <Text style={[styles.cell, styles.time]}>{e.end != null ? fmtHM(e.end) : ''}</Text>
            <Text style={[styles.cell, styles.flex]}>{e.title}</Text>
          </Pressable>
        ),
      )}
      {!today.length && <Text style={t.sub}>이 날은 일정이 없어요.</Text>}
      <Pressable
        style={styles.add}
        accessibilityRole="button"
        onPress={() => navigation.navigate('EventAdd', { date })}
      >
        <Text style={styles.addText}>+ 일정 추가</Text>
      </Pressable>
    </Screen>
  );
}

// 3-2-2 수정 줄: 시작 80 · 종료 80 · 제목 · 삭제 60 (피그마 수치)
function EditRow({ ev, onDone }: { ev: Ev; onDone: () => void }) {
  const [start, setStart] = useState(ev.start != null ? fmtHM(ev.start) : '');
  const [end, setEnd] = useState(ev.end != null ? fmtHM(ev.end) : '');
  // 시간은 칸을 벗어날 때 저장. 형식이 틀리면 원래 값으로 되돌림
  const commit = (key: 'start' | 'end', text: string, reset: (s: string) => void) => {
    const m = parseHM(text);
    if (m !== null) updateEvent(ev.id, { [key]: m });
    else reset(ev[key] != null ? fmtHM(ev[key]!) : '');
  };
  return (
    <View style={styles.row}>
      <TextInput
        style={[styles.cell, styles.editTime]}
        value={start}
        onChangeText={setStart}
        onEndEditing={() => commit('start', start, setStart)}
        placeholder="00:00"
        placeholderTextColor={C.sub}
        accessibilityLabel="시작 시간"
      />
      <TextInput
        style={[styles.cell, styles.editTime]}
        value={end}
        onChangeText={setEnd}
        onEndEditing={() => commit('end', end, setEnd)}
        placeholder="00:00"
        placeholderTextColor={C.sub}
        accessibilityLabel="종료 시간"
      />
      <TextInput
        style={[styles.cell, styles.flex, styles.center]}
        value={ev.title}
        onChangeText={title => updateEvent(ev.id, { title })}
        onSubmitEditing={onDone}
        accessibilityLabel="일정 제목"
      />
      <Pressable
        style={styles.delete}
        accessibilityRole="button"
        onPress={() => {
          deleteEvent(ev.id);
          onDone();
        }}
      >
        <Text style={styles.deleteText}>삭제</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  center: { textAlign: 'center' },
  row: { flexDirection: 'row', gap: 8 },
  cell: {
    borderBottomWidth: 1,
    borderColor: C.line,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 14,
    color: C.sub,
  },
  time: { width: 94 },
  editTime: { width: 80 },
  delete: { width: 60, paddingHorizontal: 14, justifyContent: 'center' },
  deleteText: { fontSize: 14, color: '#CE4444' },
  add: {
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: C.sub,
    borderRadius: 10,
    paddingVertical: 13,
    alignItems: 'center',
  },
  addText: { fontSize: 15, color: C.sub },
});
