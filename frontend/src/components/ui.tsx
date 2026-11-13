import { useRef, useState, type ComponentRef, type ReactNode } from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextInputProps,
  type ViewStyle,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';

// 피그마 와이어프레임에서 반복되는 색
export const C = {
  text: '#212121',
  sub: '#737373',
  line: '#D1D1D1',
  dark: '#383838',
  danger: '#BF3333',
  fill: '#F2F2F2',
};

export const MOOD_IMG = {
  happy: require('../assets/images/happy.png'),
  neutral: require('../assets/images/neutral.png'),
  sad: require('../assets/images/sad.png'),
};

export const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토'];
// "2026-11-09" → "11월 9일 (월)"
export const fmtDate = (date: string) => {
  const d = new Date(date + 'T00:00:00');
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAY[d.getDay()]})`;
};

// 분 → "6h 52m"
export const fmtHm = (m: number) =>
  `${Math.floor(m / 60)}h ${String(Math.round(m % 60)).padStart(2, '0')}m`;

// 분 → "4시간 30분" / "50분"
export const fmtMin = (m: number) => {
  const h = Math.floor(m / 60);
  const r = m % 60;
  if (!h) return `${r}분`;
  return r ? `${h}시간 ${r}분` : `${h}시간`;
};

// 화면 틀: 상태바 영역 + (선택) 헤더 + body(padding 16, gap 12)
export function Screen({
  title,
  right,
  scroll,
  tab,
  children,
}: {
  title?: string;
  right?: ReactNode;
  scroll?: boolean;
  tab?: boolean; // 탭 화면이면 뒤로가기 없음
  children: ReactNode;
}) {
  const Body = scroll ? ScrollView : View;
  return (
    <SafeAreaView style={s.screen} edges={['top']}>
      {title && <Header title={title} right={right} back={!tab} />}
      <Body
        style={scroll ? s.flex : s.body}
        contentContainerStyle={scroll ? s.bodyScroll : undefined}
      >
        {children}
      </Body>
    </SafeAreaView>
  );
}

export function Header({
  title,
  right,
  back = true,
}: {
  title: string;
  right?: ReactNode;
  back?: boolean;
}) {
  const navigation = useNavigation();
  return (
    <View style={s.header}>
      {back && (
        <Pressable
          style={s.pill}
          accessibilityRole="button"
          accessibilityLabel="뒤로"
          onPress={() => navigation.goBack()}
        >
          <Text style={s.pillText}>←</Text>
        </Pressable>
      )}
      <Text style={s.headerTitle}>{title}</Text>
      {right}
    </View>
  );
}

export const Spacer = () => <View style={s.flex} />;

export function Field({ label, ...props }: { label?: string } & TextInputProps) {
  return (
    <View style={s.field}>
      {label && <Text style={s.label}>{label}</Text>}
      <TextInput
        style={s.input}
        placeholderTextColor={C.sub}
        autoCapitalize="none"
        {...props}
      />
    </View>
  );
}

export function Button({
  title,
  onPress,
  ghost,
  disabled,
  style,
}: {
  title: string;
  onPress?: () => void;
  ghost?: boolean;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={[
        s.btn,
        !ghost && { backgroundColor: disabled ? C.line : C.dark },
        style,
      ]}
    >
      <Text style={ghost ? s.btnGhostText : s.btnText}>{title}</Text>
    </Pressable>
  );
}

// 작은 둥근 버튼 (회원가입 링크, ± 등)
export function Pill({
  title,
  onPress,
  outline,
  active,
}: {
  title: string;
  onPress?: () => void;
  outline?: boolean;
  active?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={[s.pill, outline && s.pillOutline, active && s.pillActive]}
    >
      <Text style={[s.pillText, outline && { color: C.text }, active && { color: '#FFFFFF' }]}>
        {title}
      </Text>
    </Pressable>
  );
}

export function Check({
  label,
  checked,
  onPress,
}: {
  label: string;
  checked: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      style={s.row}
      onPress={onPress}
      accessibilityRole="checkbox"
      accessibilityState={{ checked }}
    >
      <View style={[s.box, checked ? s.boxOn : s.boxOff]} />
      <Text style={s.rowText}>{label}</Text>
    </Pressable>
  );
}

// "통학 시간  − 50분 +" 행. 값 칸 너비를 고정해서 값이 길어져도 −, + 위치가 안 움직임
export function Stepper({
  label,
  value,
  onChange,
  step = 10,
  min = 0,
  max = 24 * 60,
  fmt = fmtMin,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
  step?: number;
  min?: number;
  max?: number;
  fmt?: (v: number) => string;
}) {
  return (
    <View style={[s.row, s.gap8]}>
      <Text style={s.rowText}>{label}</Text>
      <Pill outline title="−" onPress={() => onChange(Math.max(min, value - step))} />
      <Text style={s.value} numberOfLines={1}>
        {fmt(value)}
      </Text>
      <Pill outline title="+" onPress={() => onChange(Math.min(max, value + step))} />
    </View>
  );
}

// iOS식 휠 피커: 위아래로 굴리거나 숫자를 눌러서 가운데 줄 값을 고름. onPick은 숫자를 눌렀을 때 (확정하고 닫기 등)
const WH = 36; // 한 줄 높이
export function Wheel({
  values,
  value,
  onChange,
  onPick,
  fmt = fmtMin,
}: {
  values: number[];
  value: number;
  onChange: (v: number) => void;
  onPick?: (v: number) => void;
  fmt?: (v: number) => string;
}) {
  const ref = useRef<ComponentRef<typeof ScrollView>>(null);
  const i = Math.max(0, values.indexOf(value));
  const pick = (y: number) => {
    const v = values[Math.min(values.length - 1, Math.max(0, Math.round(y / WH)))];
    if (v !== value) onChange(v);
  };
  const go = (to: number) => {
    const v = values[Math.min(values.length - 1, Math.max(0, to))];
    onChange(v);
    ref.current?.scrollTo({ y: values.indexOf(v) * WH });
  };
  return (
    <View
      style={s.wheel}
      accessibilityRole="adjustable"
      accessibilityValue={{ text: fmt(value) }}
      accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
      onAccessibilityAction={e => go(i + (e.nativeEvent.actionName === 'increment' ? 1 : -1))}
    >
      <View style={s.wheelBand} pointerEvents="none" />
      <ScrollView
        ref={ref}
        nestedScrollEnabled
        showsVerticalScrollIndicator={false}
        snapToInterval={WH}
        decelerationRate="fast"
        contentContainerStyle={s.wheelPad}
        onLayout={() => ref.current?.scrollTo({ y: i * WH, animated: false })}
        onMomentumScrollEnd={e => pick(e.nativeEvent.contentOffset.y)}
        onScrollEndDrag={e => pick(e.nativeEvent.contentOffset.y)}
      >
        {values.map(v => (
          <Text
            key={v}
            style={[s.wheelItem, v === value && s.wheelOn]}
            onPress={() => {
              go(values.indexOf(v)); // 누른 숫자를 가운데로 굴리고 값 바꿈
              onPick?.(v);
            }}
          >
            {fmt(v)}
          </Text>
        ))}
      </ScrollView>
    </View>
  );
}

// 시 : 분 휠 팝업 (1분 단위, 3-3 일정 추가 · 3-2-2 일정 수정). 굴리거나 숫자를 눌러서 고르고,
// 분을 누르면 확정되고 닫힘. 시는 눌러도 안 닫힘(이어서 분 고르기). 바깥을 눌러도 닫힘
const HOURS = Array.from({ length: 24 }, (_, i) => i);
const MINUTES = Array.from({ length: 60 }, (_, i) => i);
const pad2 = (n: number) => String(n).padStart(2, '0');
export function TimeWheel({
  visible,
  title,
  value,
  onChange,
  onClose,
}: {
  visible: boolean;
  title: string;
  value: number; // 분 (23:00 → 1380)
  onChange: (m: number) => void;
  onClose: () => void;
}) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={s.popWrap}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} accessibilityLabel="닫기" />
        <View style={s.pop}>
          <Text style={s.popTitle}>{title}</Text>
          <View style={s.wheels}>
            <Wheel values={HOURS} value={Math.floor(value / 60)} onChange={h => onChange(h * 60 + (value % 60))} fmt={pad2} />
            <Text style={s.colon}>:</Text>
            <Wheel values={MINUTES} value={value % 60} onChange={m => onChange(Math.floor(value / 60) * 60 + m)} onPick={onClose} fmt={pad2} />
          </View>
        </View>
      </View>
    </Modal>
  );
}

// 시간표/캘린더, 주간/월간 전환 버튼
export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: readonly T[];
  value: T;
  onChange: (v: T) => void;
}) {
  return (
    <View style={s.seg}>
      {options.map(o => (
        <Pressable
          key={o}
          style={[s.segBtn, value === o && s.pillActive]}
          onPress={() => onChange(o)}
          accessibilityRole="tab"
          accessibilityState={{ selected: value === o }}
        >
          <Text style={[s.segText, value === o && s.segTextOn]}>{o}</Text>
        </Pressable>
      ))}
    </View>
  );
}

export function Toggle({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityState={{ checked: value }}
      onPress={() => onChange(!value)}
      style={[s.toggle, { backgroundColor: value ? C.dark : C.line }]}
    >
      <View style={[s.knob, { alignSelf: value ? 'flex-end' : 'flex-start' }]} />
    </Pressable>
  );
}

// 드래그/탭으로 값을 고르는 막대 슬라이더 (피그마 Slider Component: 높이 26, 손잡이 40)
export function Slider({
  value,
  min,
  max,
  step,
  onChange,
}: {
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (v: number) => void;
}) {
  const [w, setW] = useState(0);
  const pick = (x: number) => {
    if (!w) return;
    const ratio = Math.min(1, Math.max(0, (x - 20) / (w - 40)));
    onChange(min + Math.round((ratio * (max - min)) / step) * step);
  };
  const left = w ? ((value - min) / (max - min)) * (w - 40) : 0;
  return (
    <View
      style={s.slider}
      onLayout={e => setW(e.nativeEvent.layout.width)}
      onStartShouldSetResponder={() => true}
      onMoveShouldSetResponder={() => true}
      onResponderGrant={e => pick(e.nativeEvent.locationX)}
      onResponderMove={e => pick(e.nativeEvent.locationX)}
      accessibilityRole="adjustable"
      accessibilityValue={{ min, max, now: value }}
    >
      <View style={[s.sliderFill, { width: left + 40 }]} />
      <View style={[s.sliderKnob, { left }]} pointerEvents="none" />
    </View>
  );
}

export const t = StyleSheet.create({
  step: { fontSize: 12, fontWeight: '700', color: C.sub },
  h1: { fontSize: 20, fontWeight: '700', color: C.text },
  h2: { fontSize: 15, fontWeight: '700', color: C.text },
  body: { fontSize: 14, color: C.text },
  sub: { fontSize: 12, color: C.sub },
  hint: { fontSize: 9, color: '#9C9C9C' },
  error: { fontSize: 13, fontWeight: '700', color: C.danger },
});

const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#FFFFFF' },
  flex: { flex: 1 },
  body: { flex: 1, padding: 16, gap: 12 },
  bodyScroll: { flexGrow: 1, padding: 16, gap: 12 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  headerTitle: { flex: 1, fontSize: 19, fontWeight: '700', color: C.text },
  field: { gap: 6 },
  label: { fontSize: 12, color: C.sub },
  input: {
    borderBottomWidth: 1,
    borderColor: C.line,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 14,
    color: C.text,
  },
  btn: {
    borderRadius: 10,
    paddingHorizontal: 16,
    paddingVertical: 13,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnText: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },
  btnGhostText: { fontSize: 15, color: C.sub },
  pill: {
    borderRadius: 16,
    paddingHorizontal: 12,
    paddingVertical: 6,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillOutline: { borderWidth: 1, borderColor: C.line, backgroundColor: '#FFFFFF' },
  pillActive: { backgroundColor: C.dark, borderColor: C.dark },
  pillText: { fontSize: 12, color: C.sub },
  seg: { flexDirection: 'row', borderWidth: 1, borderColor: C.line, borderRadius: 16, overflow: 'hidden' },
  segBtn: { flex: 1, height: 28, alignItems: 'center', justifyContent: 'center', borderRadius: 16 },
  segText: { fontSize: 12, color: C.text },
  segTextOn: { color: '#FFFFFF', fontWeight: '700' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 4 },
  gap8: { gap: 8, paddingVertical: 0 },
  rowText: { flex: 1, fontSize: 14, color: C.text },
  // 피그마 0-4 시험 기간: 값 칸 72px ("4시간 30분"이 꽉 차는 너비). 폰 글꼴이 조금 넓어도 안 잘리게 +4
  value: { width: 76, textAlign: 'center', fontSize: 15, fontWeight: '700', color: C.text },
  wheel: { width: 140, height: WH * 5 },
  wheelPad: { paddingVertical: WH * 2 },
  wheelBand: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: WH * 2,
    height: WH,
    borderRadius: 8,
    backgroundColor: C.fill,
  },
  wheelItem: { height: WH, lineHeight: WH, textAlign: 'center', fontSize: 15, color: C.sub },
  wheelOn: { fontWeight: '700', color: C.text },
  // 홈 목표 수면 휠 모달과 같은 모양
  popWrap: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(31,33,48,0.38)' },
  pop: { alignItems: 'center', gap: 8, padding: 20, borderRadius: 16, backgroundColor: '#FFFFFF' },
  popTitle: { fontSize: 16, lineHeight: 19, fontWeight: '700', color: '#2A2C3E' },
  wheels: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  colon: { fontSize: 15, fontWeight: '700', color: C.text },
  box: { width: 20, height: 20, borderRadius: 5 },
  boxOn: { backgroundColor: C.dark },
  boxOff: { borderWidth: 1, borderColor: C.sub },
  toggle: { width: 44, height: 24, borderRadius: 12, padding: 3 },
  knob: { width: 18, height: 18, borderRadius: 9, backgroundColor: '#FFFFFF' },
  slider: {
    height: 26,
    borderRadius: 4,
    backgroundColor: 'rgba(46,40,54,0.1)',
    overflow: 'hidden',
  },
  sliderFill: { position: 'absolute', top: 0, bottom: 0, left: 0, backgroundColor: '#2E2836' },
  sliderKnob: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: 40,
    borderRadius: 4,
    borderWidth: 2,
    borderColor: '#2E2836',
    backgroundColor: '#FFFFFF',
  },
});
