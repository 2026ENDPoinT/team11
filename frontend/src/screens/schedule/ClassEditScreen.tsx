import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useNavigation, type StaticScreenProps } from '@react-navigation/native';
import Timetable from '../../components/Timetable';
import { Button, C, Field, Screen, Spacer, t } from '../../components/ui';
import { deleteClass, fmtHM, parseHM, saveClass, useStore } from '../../store';

const DAYS = ['월', '화', '수', '목', '금'];

// 3-1 수업 추가 · 수정 (57:1091). id가 있으면 그 수업 수정
export default function ClassEditScreen({ route }: StaticScreenProps<{ id?: string }>) {
  const navigation = useNavigation();
  const { classes } = useStore();
  const editing = classes.find(c => c.id === route.params?.id);

  const [name, setName] = useState(editing?.name ?? '');
  const [room, setRoom] = useState(editing?.room ?? '');
  const [day, setDay] = useState(editing?.day ?? 0);
  const [start, setStart] = useState(editing ? fmtHM(editing.start) : '09:00');
  const [end, setEnd] = useState(editing ? fmtHM(editing.end) : '10:30');

  const s = parseHM(start);
  const e = parseHM(end);
  const error =
    s === null || e === null ? '시간은 09:00 형식으로 입력해 주세요.' : s >= e ? '종료 시간이 시작보다 늦어야 해요.' : '';

  const save = () => {
    saveClass({ id: editing?.id, name, room, day, start: s!, end: e! });
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
        <TextInput style={[styles.box, styles.flex]} value={start} onChangeText={setStart} placeholder="09:00" placeholderTextColor={C.sub} />
        <TextInput style={[styles.box, styles.flex]} value={end} onChangeText={setEnd} placeholder="10:30" placeholderTextColor={C.sub} />
      </View>
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
