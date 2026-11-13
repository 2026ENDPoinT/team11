import { useState } from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation, type StaticScreenProps } from '@react-navigation/native';
import Calendar from '../../components/Calendar';
import { Button, C, Field, Pill, Screen, Spacer, TimeWheel, t } from '../../components/ui';
import { addEvent, fmtHM, ymd, type EventType } from '../../store';

const TYPES: EventType[] = ['과제', '시험', '약속', '기타'];
const WEEK = ['일', '월', '화', '수', '목', '금', '토'];

// 3-3 일정 추가 (160:640). 캘린더 탭(3-2-1)에서 들어옴
export default function EventAddScreen({ route }: StaticScreenProps<{ date?: string }>) {
  const navigation = useNavigation();
  const [date, setDate] = useState(route.params?.date ?? ymd(new Date()));
  const [title, setTitle] = useState('');
  const [type, setType] = useState<EventType>('과제');
  const [time, setTime] = useState({ start: 23 * 60, end: 23 * 60 + 59 }); // 분
  const [picking, setPicking] = useState<'start' | 'end' | null>(null); // 휠 열린 칸
  const d = new Date(date + 'T00:00:00');

  const save = () => {
    addEvent({ title, type, date, ...time });
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
        {/* 시간은 직접 입력 대신 누르면 휠 */}
        {(['start', 'end'] as const).map(k => (
          <Pressable
            key={k}
            style={[styles.input, styles.flex]}
            onPress={() => setPicking(k)}
            accessibilityRole="button"
            accessibilityLabel={`${k === 'start' ? '시작' : '종료'} 시간 ${fmtHM(time[k])}`}
            accessibilityHint="눌러서 시간 고르기"
          >
            <Text style={styles.inputText}>{fmtHM(time[k])}</Text>
          </Pressable>
        ))}
      </View>
      <Spacer />
      <Button title="저장" disabled={!title} onPress={save} />

      <TimeWheel
        visible={!!picking}
        title={picking === 'end' ? '종료 시간' : '시작 시간'}
        value={picking ? time[picking] : 0}
        onChange={m => picking && setTime(x => ({ ...x, [picking]: m }))}
        onClose={() => setPicking(null)}
      />
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
  },
  inputText: { fontSize: 14, color: C.text },
});
