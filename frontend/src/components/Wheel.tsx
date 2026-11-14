import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { Picker } from '@react-native-picker/picker';
import { C } from '../theme';

// 시간 휠 모달(272:531 등): 제목 + 세로 휠 N개. iOS 네이티브 UIPickerView가 Figma 휠 모양 그대로.
// 바깥을 누르면 닫히고 굴린 값은 즉시 반영.
export type WheelColumn = { values: (number | string)[]; labels?: string[]; value: number | string; onChange: (v: any) => void };

export function WheelModal({
  visible,
  title,
  columns,
  separator,
  onClose,
}: {
  visible: boolean;
  title: string;
  columns: WheelColumn[];
  separator?: string;
  onClose: () => void;
}) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={st.wrap}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} accessibilityLabel="닫기" />
        <View style={st.picker}>
          <Text style={st.title}>{title}</Text>
          <View style={st.wheels}>
            {columns.map((c, i) => (
              <View key={i} style={st.wheelRow}>
                {i > 0 && !!separator && <Text style={st.sep}>{separator}</Text>}
                <Picker selectedValue={c.value} onValueChange={c.onChange} style={st.wheel} itemStyle={st.item}>
                  {c.values.map((v, j) => (
                    <Picker.Item key={String(v)} label={c.labels?.[j] ?? String(v)} value={v} />
                  ))}
                </Picker>
              </View>
            ))}
          </View>
        </View>
      </View>
    </Modal>
  );
}

const p2 = (n: number) => String(n).padStart(2, '0');
const HOURS = Array.from({ length: 24 }, (_, i) => i);
const MINUTES = Array.from({ length: 60 }, (_, i) => i);

/** 시:분 두 휠. value는 분(0~1439) */
export function TimeWheel({
  visible,
  title,
  value,
  onChange,
  onClose,
  minuteStep = 1,
}: {
  visible: boolean;
  title: string;
  value: number;
  onChange: (min: number) => void;
  onClose: () => void;
  minuteStep?: number;
}) {
  const h = Math.floor(value / 60);
  const m = value % 60;
  const minutes = minuteStep === 1 ? MINUTES : MINUTES.filter(x => x % minuteStep === 0);
  return (
    <WheelModal
      visible={visible}
      title={title}
      separator=":"
      onClose={onClose}
      columns={[
        { values: HOURS, labels: HOURS.map(p2), value: h, onChange: v => onChange(Number(v) * 60 + m) },
        { values: minutes, labels: minutes.map(p2), value: m, onChange: v => onChange(h * 60 + Number(v)) },
      ]}
    />
  );
}

const st = StyleSheet.create({
  wrap: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: C.dim },
  picker: { alignItems: 'center', gap: 8, padding: 20, borderRadius: 16, backgroundColor: '#FFFFFF' },
  title: { fontSize: 16, lineHeight: 19, fontWeight: '700', color: C.ink },
  wheels: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  wheelRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  sep: { fontSize: 15, fontWeight: '700', color: C.text },
  wheel: { width: 140, height: 180 },
  // item height를 wheel height와 같게 두면 한 칸이 180px짜리가 돼 숫자가 보이는 영역 밖으로 밀려난다.
  item: { fontSize: 15, height: 40, color: C.text },
});
