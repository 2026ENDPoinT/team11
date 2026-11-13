import { useState } from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation, type StaticScreenProps } from '@react-navigation/native';
import { Button, Field, MOOD_IMG, Screen, Spacer, fmtDate, t } from '../../components/ui';
import { fmtHM, parseHM, saveRecord, useStore, type Mood } from '../../store';

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
  const [bed, setBed] = useState(rec ? fmtHM(rec.bed) : '');
  const [wake, setWake] = useState(rec ? fmtHM(rec.wake) : '');
  const [mood, setMood] = useState<Mood | undefined>(rec?.mood);
  const [memo, setMemo] = useState(rec?.memo ?? '');

  const b = parseHM(bed);
  const w = parseHM(wake);
  const save = () => {
    saveRecord({ date, bed: b!, wake: w!, mood, memo });
    navigation.goBack();
  };

  return (
    <Screen title={fmtDate(date)}>
      <View style={styles.row}>
        <View style={styles.flex}>
          <Field label="취침 시간" placeholder="03:30" value={bed} onChangeText={setBed} />
        </View>
        <View style={styles.flex}>
          <Field label="기상 시간" placeholder="08:57" value={wake} onChangeText={setWake} />
        </View>
      </View>
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
      {(bed && b === null) || (wake && w === null) ? (
        <Text style={t.error}>시간은 03:30 형식으로 입력해 주세요.</Text>
      ) : null}
      <Spacer />
      <Button title="저장" disabled={b === null || w === null} onPress={save} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  row: { flexDirection: 'row', gap: 8 },
  moodRow: { flexDirection: 'row', alignItems: 'center', gap: 60 },
  mood: { width: 28, height: 24, opacity: 0.6, resizeMode: 'contain' },
  moodOn: { opacity: 1, transform: [{ scale: 1.2 }] },
});
