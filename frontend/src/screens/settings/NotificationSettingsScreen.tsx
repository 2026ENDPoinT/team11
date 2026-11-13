import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { C, Pill, Screen, Toggle } from '../../components/ui';

const BEFORE = [15, 30, 60];

// 5-2 알림 설정 (14:1160)
// ponytail: 설정값만 들고 있음. 실제 알림 예약은 알림 라이브러리(notifee 등) 붙일 때
export default function NotificationSettingsScreen() {
  const [wake, setWake] = useState(true);
  const [bed, setBed] = useState(true);
  const [before, setBefore] = useState(30);

  return (
    <Screen title="알림 설정">
      <Row title="기상 알람" desc="권장 기상 시간에 울려요" value={wake} onChange={setWake} />
      <Row title="취침 알림" desc={`권장 취침 ${before}분 전`} value={bed} onChange={setBed} />
      {bed && (
        <View style={styles.chips}>
          {BEFORE.map(m => (
            <View key={m} style={styles.flex}>
              <Pill title={`${m}분`} outline={before !== m} active={before === m} onPress={() => setBefore(m)} />
            </View>
          ))}
        </View>
      )}
    </Screen>
  );
}

function Row({ title, desc, value, onChange }: { title: string; desc: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <View style={styles.row}>
      <View style={styles.flex}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.desc}>{desc}</Text>
      </View>
      <Toggle value={value} onChange={onChange} />
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 8 },
  title: { fontSize: 14, color: C.text },
  desc: { fontSize: 11, color: C.sub, marginTop: 2 },
  chips: { flexDirection: 'row', gap: 8 },
});
