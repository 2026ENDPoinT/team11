import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation, type StaticScreenProps } from '@react-navigation/native';
import Timetable from '../../components/Timetable';
import { Button, C, Field, Screen, Spacer, TimeWheel, t } from '../../components/ui';
import { deleteClass, fmtHM, saveClass, useStore } from '../../store';

const DAYS = ['월', '화', '수', '목', '금'];

// 3-1 수업 추가 · 수정 (57:1091). id가 있으면 그 수업 수정
export default function ClassEditScreen({ route }: StaticScreenProps<{ id?: string }>) {
  const navigation = useNavigation();
  const { classes } = useStore();
  const editing = classes.find(c => c.id === route.params?.id);

  const [name, setName] = useState(editing?.name ?? '');
  const [room, setRoom] = useState(editing?.room ?? '');
  const [day, setDay] = useState(editing?.day ?? 0);
  // 시간(분): 누르면 시:분 휠 (일정 추가와 같은 휠)
  const [time, setTime] = useState({ start: editing?.start ?? 9 * 60, end: editing?.end ?? 10 * 60 + 30 });
  const [picking, setPicking] = useState<'start' | 'end' | null>(null);
  const error = time.start >= time.end ? '종료 시간이 시작보다 늦어야 해요.' : '';

  const save = () => {
    saveClass({ id: editing?.id, name, room, day, ...time });
    navigation.goBack();
  };

  return (
    <Screen title={editing ? '수업 수정' : '수업 추가'} scroll>
      <Timetable classes={classes} onDelete={c => deleteClass(c.id)} />
      <Field placeholder="과목명" value={name} onChangeText={setName} />
      <View style={styles.row}>
        <Pressable
          style={[styles.box, styles.dayBox]}
          onPress={() => setDay((day + 1) % 5)}
          accessibilityRole="button"
          accessibilityLabel={`요일 ${DAYS[day]}, 눌러서 바꾸기`}
        >
          <Text style={styles.boxText}>{DAYS[day]}</Text>
        </Pressable>
        {(['start', 'end'] as const).map(k => (
          <Pressable
            key={k}
            style={[styles.box, styles.flex]}
            onPress={() => setPicking(k)}
            accessibilityRole="button"
            accessibilityLabel={`${k === 'start' ? '시작' : '종료'} 시간 ${fmtHM(time[k])}`}
            accessibilityHint="눌러서 시간 고르기"
          >
            <Text style={styles.boxText}>{fmtHM(time[k])}</Text>
          </Pressable>
        ))}
      </View>
      <TimeWheel
        visible={!!picking}
        title={picking === 'end' ? '종료 시간' : '시작 시간'}
        value={picking ? time[picking] : 0}
        onChange={m => picking && setTime(x => ({ ...x, [picking]: m }))}
        onClose={() => setPicking(null)}
      />
      <Field placeholder="강의실" value={room} onChangeText={setRoom} />
      {/* 피그마 3-1에는 'e-class 연동하기'로 남아 있지만 eclass 화면이 없어져서 에브리타임으로 연결 */}
      <Pressable style={styles.link} onPress={() => navigation.navigate('TimetableImport', { back: true })}>
        <Text style={styles.linkText}>에브리타임에서 가져오기</Text>
      </Pressable>
      {!!error && <Text style={t.error}>{error}</Text>}
      <Spacer />
      <Button title="저장" disabled={!name || !!error} onPress={save} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  row: { flexDirection: 'row', gap: 8 },
  box: {
    borderWidth: 1,
    borderColor: C.line,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 14,
    color: C.text,
  },
  dayBox: { width: 42, paddingHorizontal: 0, alignItems: 'center' },
  boxText: { fontSize: 14, color: C.text },
  link: { alignItems: 'center', paddingVertical: 12 },
  linkText: { fontSize: 11, color: 'rgba(115,115,115,0.6)', textDecorationLine: 'underline' },
});
