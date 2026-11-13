import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { C, TimeWheel, t } from '../../components/ui';
import { deleteEvent, fmtHM, updateEvent, useStore, type Ev } from '../../store';

// 3-2-1 일정 확인 (YS_github): 캘린더 탭에서 날짜를 누르면 다른 화면으로 안 가고 달력 아래에 그날 일정 + 일정 추가(3-3)
// 3-2-2 일정 수정: 일정 줄을 누르면 그 줄이 입력칸 + 삭제로 바뀜
export default function DayEvents({ date }: { date: string }) {
  const navigation = useNavigation();
  const { events } = useStore();
  const [editing, setEditing] = useState<string | null>(null);
  // 기간 일정(endDate)은 기간 안의 모든 날에 보임. YYYY-MM-DD라 문자열 비교로 충분
  const today = events.filter(e => e.date === date || (!!e.endDate && e.date <= date && date <= e.endDate));

  return (
    <>
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
    </>
  );
}

// 3-2-2 수정 줄: 시작 80 · 종료 80 · 제목 · 삭제 60 (피그마 수치). 시간은 누르면 시:분 휠, 고르는 즉시 저장
// 시간 없는 일정(종일)은 00:00으로 보이고, 휠도 00:00에서 시작
function EditRow({ ev, onDone }: { ev: Ev; onDone: () => void }) {
  const [picking, setPicking] = useState<'start' | 'end' | null>(null);
  return (
    <View style={styles.row}>
      {(['start', 'end'] as const).map(k => (
        <Pressable
          key={k}
          style={[styles.cellBox, styles.editTime]}
          onPress={() => setPicking(k)}
          accessibilityRole="button"
          accessibilityLabel={`${k === 'start' ? '시작' : '종료'} 시간 ${fmtHM(ev[k] ?? 0)}`}
          accessibilityHint="눌러서 시간 고르기"
        >
          <Text style={styles.cellText}>{fmtHM(ev[k] ?? 0)}</Text>
        </Pressable>
      ))}
      <TimeWheel
        visible={!!picking}
        title={picking === 'end' ? '종료 시간' : '시작 시간'}
        value={picking ? ev[picking] ?? 0 : 0}
        onChange={m => picking && updateEvent(ev.id, { [picking]: m })}
        onClose={() => setPicking(null)}
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
  // 누르는 시간 칸: Text 대신 Pressable이라 글자 스타일은 안쪽 Text에
  cellBox: { borderBottomWidth: 1, borderColor: C.line, paddingHorizontal: 14, paddingVertical: 12 },
  cellText: { fontSize: 14, color: C.sub },
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
