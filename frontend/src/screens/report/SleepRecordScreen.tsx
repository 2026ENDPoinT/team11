import { useState } from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation, type StaticScreenProps } from '@react-navigation/native';
import { Button, C, Field, MOOD_IMG, Screen, Spacer, TimeWheel, fmtDate, t } from '../../components/ui';
import { fmtHM, saveRecord, useStore, type Mood } from '../../store';

const LABEL = { bed: '취침 시간', wake: '기상 시간' };
const HINT = { bed: 3 * 60 + 30, wake: 8 * 60 + 57 }; // 피그마 예시 03:30 / 08:57

const MOODS: [Mood, string][] = [
  ['happy', '좋음'],
  ['neutral', '보통'],
  ['sad', '나쁨'],
];

// 4-2 수면 기록 (102:462)
export default function SleepRecordScreen({ route }: StaticScreenProps<{ date: string }>) {
  const navigation = useNavigation();
  const { date } = route.params;
  const rec = useStore().records.find(r => r.date === date);
  // 시간(분): 누르면 시:분 휠. 기록이 없으면 회색 예시 시간이 보이고 휠도 거기서 시작
  const [time, setTime] = useState<Record<'bed' | 'wake', number | null>>({ bed: rec?.bed ?? null, wake: rec?.wake ?? null });
  const [picking, setPicking] = useState<'bed' | 'wake' | null>(null);
  const [mood, setMood] = useState<Mood | undefined>(rec?.mood);
  const [memo, setMemo] = useState(rec?.memo ?? '');

  const save = () => {
    saveRecord({ date, bed: time.bed!, wake: time.wake!, mood, memo });
    navigation.goBack();
  };

  return (
    <Screen title={fmtDate(date)}>
      <View style={styles.row}>
        {(['bed', 'wake'] as const).map(k => (
          <View key={k} style={[styles.flex, styles.field]}>
            <Text style={t.sub}>{LABEL[k]}</Text>
            <Pressable
              style={styles.input}
              onPress={() => setPicking(k)}
              accessibilityRole="button"
              accessibilityLabel={`${LABEL[k]} ${time[k] == null ? '없음' : fmtHM(time[k])}`}
              accessibilityHint="눌러서 시간 고르기"
            >
              <Text style={[styles.inputText, time[k] == null && styles.hint]}>{fmtHM(time[k] ?? HINT[k])}</Text>
            </Pressable>
          </View>
        ))}
      </View>
      <TimeWheel
        visible={!!picking}
        title={picking ? LABEL[picking] : ''}
        value={picking ? time[picking] ?? HINT[picking] : 0}
        onChange={m => picking && setTime(x => ({ ...x, [picking]: m }))}
        onClose={() => setPicking(null)}
      />
      <View style={styles.moodRow}>
        <Text style={t.sub}>컨디션</Text>
        {MOODS.map(([m, label]) => (
          <Pressable
            key={m}
            onPress={() => setMood(m)}
            accessibilityRole="radio"
            accessibilityLabel={label}
            accessibilityState={{ selected: mood === m }}
          >
            <Image source={MOOD_IMG[m]} style={[styles.mood, mood === m && styles.moodOn]} />
          </Pressable>
        ))}
      </View>
      <Field label="메모" placeholder="메모를 작성해주세요." value={memo} onChangeText={setMemo} multiline />
      <Spacer />
      <Button title="저장" disabled={time.bed == null || time.wake == null} onPress={save} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  row: { flexDirection: 'row', gap: 8 },
  // Field(라벨 + 밑줄 칸)와 같은 모양, 입력 대신 누르기
  field: { gap: 6 },
  input: { borderBottomWidth: 1, borderColor: C.line, paddingHorizontal: 14, paddingVertical: 12 },
  inputText: { fontSize: 14, color: C.text },
  hint: { color: C.sub },
  moodRow: { flexDirection: 'row', alignItems: 'center', gap: 60 },
  mood: { width: 28, height: 24, opacity: 0.6, resizeMode: 'contain' },
  moodOn: { opacity: 1, transform: [{ scale: 1.2 }] },
});
