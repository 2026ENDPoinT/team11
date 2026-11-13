import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
  type PressableProps,
  type StyleProp,
  type TextInputProps,
  type ViewStyle,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { C, S } from '../theme';

// Figma '컴포넌트 (앱 ui.tsx 기준)' 섹션(257:302) 1:1 대응.

/** 화면 틀: SafeArea + body padding 16 / gap 12. title을 주면 header(257:402) 포함 */
export function Screen({
  title,
  back = true,
  scroll,
  children,
  right,
  style,
}: {
  title?: string;
  back?: boolean;
  scroll?: boolean;
  children: ReactNode;
  right?: ReactNode;
  style?: StyleProp<ViewStyle>;
}) {
  const Body = scroll ? ScrollView : View;
  return (
    <SafeAreaView style={st.safe} edges={['top']}>
      {title !== undefined && <Header title={title} back={back} right={right} />}
      <Body
        style={[st.body, style]}
        contentContainerStyle={scroll ? [st.body, style] : undefined}
        keyboardShouldPersistTaps="handled"
      >
        {children}
      </Body>
    </SafeAreaView>
  );
}

/** header(257:402): ← pill + 제목 19 bold */
export function Header({ title, back = true, right }: { title: string; back?: boolean; right?: ReactNode }) {
  const nav = useNavigation();
  return (
    <View style={st.header}>
      {back && (
        <Pressable onPress={() => nav.goBack()} style={st.backBtn} accessibilityRole="button" accessibilityLabel="뒤로">
          <Text style={st.backText}>←</Text>
        </Pressable>
      )}
      <Text style={st.headerTitle}>{title}</Text>
      {right}
    </View>
  );
}

/** button(257:412): 기본 #383838 / 비활성 #d1d1d1 / 고스트 투명 */
export function Button({
  title,
  kind = 'primary',
  disabled,
  loading,
  style,
  ...rest
}: PressableProps & { title: string; kind?: 'primary' | 'ghost'; loading?: boolean; style?: StyleProp<ViewStyle> }) {
  const off = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!off }}
      disabled={off}
      style={[st.btn, kind === 'primary' && (off ? st.btnOff : st.btnOn), style]}
      {...rest}
    >
      {loading ? (
        <ActivityIndicator color={kind === 'primary' ? '#fff' : C.sub} />
      ) : (
        <Text style={[st.btnText, kind === 'ghost' && st.btnGhostText]}>{title}</Text>
      )}
    </Pressable>
  );
}

/** pill(257:419): 기본(텍스트만) / 테두리 / 선택(채움) */
export function Pill({
  title,
  kind = 'plain',
  style,
  ...rest
}: PressableProps & { title: string; kind?: 'plain' | 'outline' | 'selected'; style?: StyleProp<ViewStyle> }) {
  return (
    <Pressable
      accessibilityRole="button"
      style={[st.pill, kind === 'outline' && st.pillOutline, kind === 'selected' && st.pillSelected, style]}
      {...rest}
    >
      <Text style={[st.pillText, kind === 'outline' && st.pillOutlineText, kind === 'selected' && st.pillSelectedText]}>
        {title}
      </Text>
    </Pressable>
  );
}

/** field(257:420): 라벨 12 sub + 밑줄 입력 */
export function Field({ label, style, ...rest }: TextInputProps & { label?: string; style?: StyleProp<ViewStyle> }) {
  return (
    <View style={[st.field, style]}>
      {!!label && <Text style={st.fieldLabel}>{label}</Text>}
      <TextInput
        placeholderTextColor={C.sub}
        autoCapitalize="none"
        autoCorrect={false}
        style={st.input}
        {...rest}
      />
    </View>
  );
}

/** stepper(257:424): 항목 ─ (−) 값 (+) */
export function Stepper({
  label,
  value,
  onChange,
  step = 10,
  min = 0,
  max,
  fmt = v => `${v}분`,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
  step?: number;
  min?: number;
  max: number;
  fmt?: (v: number) => string;
}) {
  return (
    <View style={st.stepper}>
      <Text style={st.stepperLabel}>{label}</Text>
      <Pill title="−" kind="outline" onPress={() => onChange(Math.max(min, value - step))} accessibilityLabel={`${label} 줄이기`} />
      <Text style={st.stepperValue}>{fmt(value)}</Text>
      <Pill title="+" kind="outline" onPress={() => onChange(Math.min(max, value + step))} accessibilityLabel={`${label} 늘리기`} />
    </View>
  );
}

/** check(257:437) */
export function Check({ label, checked, onPress }: { label: string; checked: boolean; onPress: () => void }) {
  return (
    <Pressable style={st.check} onPress={onPress} accessibilityRole="checkbox" accessibilityState={{ checked }}>
      <View style={[st.checkBox, checked ? st.checkOn : st.checkOff]} />
      <Text style={st.checkLabel}>{label}</Text>
    </Pressable>
  );
}

/** toggle(257:442): 네이티브 Switch에 Figma 색만 */
export function Toggle({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
  return <Switch value={value} onValueChange={onChange} trackColor={{ true: C.primary, false: C.line }} />;
}

/** segmented(257:453): 둘 중 하나 */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
}) {
  return (
    <View style={st.seg}>
      {options.map(o => {
        const on = o.value === value;
        return (
          <Pressable
            key={o.value}
            style={[st.segItem, on && st.segOn]}
            onPress={() => onChange(o.value)}
            accessibilityRole="tab"
            accessibilityState={{ selected: on }}
          >
            <Text style={[st.segText, on && st.segTextOn]}>{o.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/** add button(257:454): 점선 */
export function AddButton({ title = '+ 추가', onPress }: { title?: string; onPress: () => void }) {
  return (
    <Pressable style={st.add} onPress={onPress} accessibilityRole="button">
      <Text style={st.addText}>{title}</Text>
    </Pressable>
  );
}

/** 남은 공간 밀어내기 (버튼을 바닥으로) */
export const Spacer = () => <View style={st.spacer} />;

/** 로딩/에러 공통 */
export function Status({ loading, error, onRetry }: { loading?: boolean; error?: { message: string } | null; onRetry?: () => void }) {
  if (loading) return <ActivityIndicator style={st.status} color={C.sub} />;
  if (error)
    return (
      <View style={st.status}>
        <Text style={t.error}>{error.message}</Text>
        {onRetry && <Pill title="다시 시도" kind="outline" onPress={onRetry} />}
      </View>
    );
  return null;
}

// 공통 텍스트 스타일
export const t = StyleSheet.create({
  h1: { fontSize: 22, fontWeight: '700', color: C.text },
  sub: { fontSize: 14, color: C.sub },
  body: { fontSize: 14, color: C.text },
  hint: { fontSize: 12, color: C.sub },
  step: { fontSize: 12, color: C.sub },
  error: { fontSize: 13, fontWeight: '700', color: C.error },
  label: { fontSize: 12, color: C.sub },
});

const st = StyleSheet.create({
  safe: { flex: 1, backgroundColor: C.bg },
  body: { flex: 1, padding: S.pad, gap: S.gap, backgroundColor: C.bg },
  header: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: S.pad, paddingVertical: 10 },
  backBtn: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: S.pill },
  backText: { fontSize: 12, color: C.sub },
  headerTitle: { flex: 1, fontSize: 19, fontWeight: '700', color: C.text },

  btn: { alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16, paddingVertical: 13, borderRadius: S.radius },
  btnOn: { backgroundColor: C.primary },
  btnOff: { backgroundColor: C.disabled },
  btnText: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },
  btnGhostText: { fontWeight: '400', color: C.sub },

  pill: { alignSelf: 'flex-start', paddingHorizontal: 12, paddingVertical: 6, borderRadius: S.pill, borderWidth: 1, borderColor: 'transparent' },
  pillOutline: { backgroundColor: '#FFFFFF', borderColor: C.line },
  pillSelected: { backgroundColor: C.primary, borderColor: C.primary },
  pillText: { fontSize: 12, color: C.sub },
  pillOutlineText: { color: C.text },
  pillSelectedText: { color: '#FFFFFF' },

  field: { gap: 6 },
  fieldLabel: { fontSize: 12, color: C.sub },
  input: { paddingHorizontal: 14, paddingVertical: 12, fontSize: 14, color: C.text, borderBottomWidth: 1, borderBottomColor: C.line },

  stepper: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  stepperLabel: { flex: 1, fontSize: 14, color: C.text },
  stepperValue: { width: 76, textAlign: 'center', fontSize: 15, fontWeight: '700', color: C.text },

  check: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 4 },
  checkBox: { width: 20, height: 20, borderRadius: 5 },
  checkOn: { backgroundColor: C.primary },
  checkOff: { borderWidth: 1, borderColor: C.sub },
  checkLabel: { flex: 1, fontSize: 14, color: C.text },

  seg: { flexDirection: 'row', borderWidth: 1, borderColor: C.line, borderRadius: S.pill, overflow: 'hidden' },
  segItem: { flex: 1, height: 28, alignItems: 'center', justifyContent: 'center', borderRadius: S.pill },
  segOn: { backgroundColor: C.primary },
  segText: { fontSize: 12, color: C.text },
  segTextOn: { color: '#FFFFFF', fontWeight: '700' },

  add: { alignItems: 'center', paddingHorizontal: 16, paddingVertical: 13, borderRadius: S.radius, borderWidth: 1, borderStyle: 'dashed', borderColor: C.sub },
  addText: { fontSize: 15, color: C.sub },

  spacer: { flex: 1 },
  status: { alignItems: 'center', gap: 8, paddingVertical: 24 },
});
