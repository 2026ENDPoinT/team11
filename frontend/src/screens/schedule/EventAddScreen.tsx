import { useState } from 'react';
import { Image, StyleSheet, Text, TextInput, View } from 'react-native';
import { useNavigation, type StaticScreenProps } from '@react-navigation/native';
import Calendar from '../../components/Calendar';
import { Button, C, Field, Pill, Screen, Spacer, t } from '../../components/ui';
import { addEvent, parseHM, ymd, type EventType } from '../../store';

const TYPES: EventType[] = ['과제', '시험', '약속', '기타'];
const WEEK = ['일', '월', '화', '수', '목', '금', '토'];

// 3-3 일정 추가 (160:640). 3-2-1 일정 확인에서 들어옴
export default function EventAddScreen({ route }: StaticScreenProps<{ date?: string }>) {
  const navigation = useNavigation();
  const [date, setDate] = useState(route.params?.date ?? ymd(new Date()));
  const [title, setTitle] = useState('');
  const [type, setType] = useState<EventType>('과제');
  const [start, setStart] = useState('23:00');
  const [end, setEnd] = useState('23:59');

  const s = parseHM(start);
  const e = parseHM(end);
  const error = s === null || e === null ? '시간은 23:00 형식으로 입력해 주세요.' : '';
  const d = new Date(date + 'T00:00:00');

  const save = () => {
    addEvent({ title, type, date, start: s!, end: e! });
    navigation.goBack();
  };

  return (
    <Screen title="일정 추가" scroll>
      <Calendar month={d} selected={date} onSelect={setDate} />
      <Field placeholder="제목" value={title} onChangeText={setTitle} />
      <Text style={t.sub}>종류</Text>
      <View style={styles.row}>
        {TYPES.map(ty => (
          <View key={ty} style={styles.flex}>
            <Pill title={ty} outline={type !== ty} active={type === ty} onPress={() => setType(ty)} />
          </View>
        ))}
      </View>
      <View style={[styles.row, styles.end]}>
        <View style={styles.dateBox}>
          <Text style={styles.dateText}>
            {d.getMonth() + 1}/{d.getDate()} ({WEEK[d.getDay()]})
          </Text>
          <Image source={require('../../assets/images/calendar.png')} style={styles.calIcon} resizeMode="contain" />
        </View>
        <TextInput style={[styles.input, styles.flex]} value={start} onChangeText={setStart} placeholder="23:00" placeholderTextColor={C.sub} />
        <TextInput style={[styles.input, styles.flex]} value={end} onChangeText={setEnd} placeholder="23:59" placeholderTextColor={C.sub} />
      </View>
      {!!error && <Text style={t.error}>{error}</Text>}
      <Spacer />
      <Button title="저장" disabled={!title || !!error} onPress={save} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  row: { flexDirection: 'row', gap: 8 },
  end: { alignItems: 'flex-end' },
  dateBox: {
    width: 156,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    borderColor: C.line,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  dateText: { fontSize: 14, color: C.sub },
  calIcon: { width: 19, height: 17 },
  input: {
    borderBottomWidth: 1,
    borderColor: C.line,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 14,
    color: C.text,
  },
});
