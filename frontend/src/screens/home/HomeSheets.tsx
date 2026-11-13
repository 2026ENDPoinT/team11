import { useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { Picker } from '@react-native-picker/picker';
import type { ExamNature, PlanMode, PreparationState, Settings } from '../../api/types';
import { Stepper, t } from '../../components/ui';
import { RangeSlider } from '../../components/Slider';
import { fmtDuration, fmtHM } from '../../kst';
import { C } from '../../theme';
import { MODES } from './modes';

// 홈 바텀시트 3종 + 목표 수면 휠.
// 1-A-1 일상: 목표 수면(휠)·준비·통학 / 1-B-1 시험: 목표 최소 수면(휠)·준비·통학 / 1-C-1 집중: 침대 체류 범위
// 1-B-3 공부 모드: 공부량 5단계 + 시험 성격 → POST /plans/today?mode=EXAM 바디

const GOALS = Array.from({ length: 19 }, (_, i) => 3 * 60 + i * 30); // 3h ~ 12h, 30분

function Sheet({
  mode,
  title,
  visible,
  onClose,
  children,
}: {
  mode: PlanMode;
  title: string;
  visible: boolean;
  onClose: () => void;
  children: React.ReactNode;
}) {
  const m = MODES[mode];
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={[st.dim, { backgroundColor: m.dim }]} onPress={onClose} accessibilityLabel="닫기" />
      <View style={[st.sheet, { backgroundColor: m.sheet }]}>
        <View style={[st.handle, { backgroundColor: m.track }]} />
        <Text style={st.sheetTitle}>{title}</Text>
        <View style={st.body}>{children}</View>
      </View>
    </Modal>
  );
}

function DoneButton({ mode, onPress, busy }: { mode: PlanMode; onPress: () => void; busy?: boolean }) {
  return (
    <Pressable
      style={[st.done, { backgroundColor: MODES[mode].done, opacity: busy ? 0.6 : 1 }]}
      onPress={onPress}
      disabled={busy}
      accessibilityRole="button"
    >
      <Text style={st.doneText}>{busy ? '저장 중…' : '완료'}</Text>
    </Pressable>
  );
}

/** 1-A-1 / 1-B-1: 수면 설정 → PUT /users/me/settings 일부 */
export function SleepSettingsSheet({
  mode,
  visible,
  settings,
  onClose,
  onDone,
}: {
  mode: 'NORMAL' | 'EXAM';
  visible: boolean;
  settings: Settings;
  onClose: () => void;
  onDone: (patch: Partial<Settings>) => Promise<void>;
}) {
  // 일상은 목표 수면(sleepNeedMin), 시험은 목표 최소 수면(minSleepMin)
  const goalKey = mode === 'NORMAL' ? 'sleepNeedMin' : 'minSleepMin';
  const [goal, setGoal] = useState(settings[goalKey]);
  const [prep, setPrep] = useState(settings.prepMin);
  const [commute, setCommute] = useState(settings.commuteMin);
  const [picking, setPicking] = useState(false);
  const [busy, setBusy] = useState(false);
  const m = MODES[mode];

  const done = async () => {
    setBusy(true);
    try {
      await onDone({ [goalKey]: goal, prepMin: prep, commuteMin: commute });
    } finally {
      setBusy(false);
    }
  };
  const goals = mode === 'NORMAL' ? GOALS.filter(g => g >= 300 && g <= 600) : GOALS.filter(g => g >= 240 && g <= 480);

  return (
    <Sheet mode={mode} title="수면 설정" visible={visible} onClose={onClose}>
      <View style={st.row}>
        <Text style={st.label}>{mode === 'NORMAL' ? '목표 수면 시간' : '목표 최소 수면'}</Text>
        <Pressable onPress={() => setPicking(true)} accessibilityRole="button" accessibilityHint="눌러서 시간 고르기">
          <Text style={st.goal}>{fmtDuration(goal)}</Text>
        </Pressable>
      </View>
      <Stepper label="기상 후 준비 시간" value={prep} onChange={setPrep} max={240} />
      <Stepper label="통학 시간" value={commute} onChange={setCommute} max={240} />
      <DoneButton mode={mode} onPress={done} busy={busy} />

      {/* 1-A-2 / 1-B-2 목표 수면 시간 휠: 바깥을 누르면 닫힘, 굴린 값이 바로 반영 */}
      <Modal visible={picking} transparent animationType="fade" onRequestClose={() => setPicking(false)}>
        <View style={[st.pickerWrap, { backgroundColor: m.dim }]}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setPicking(false)} accessibilityLabel="닫기" />
          <View style={[st.picker, { backgroundColor: m.sheet }]}>
            <Text style={st.pickerTitle}>{mode === 'NORMAL' ? '목표 수면 시간' : '목표 최소 수면'}</Text>
            <Picker selectedValue={goal} onValueChange={v => setGoal(Number(v))} style={st.wheel} itemStyle={st.wheelItem}>
              {goals.map(g => (
                <Picker.Item key={g} label={fmtDuration(g)} value={g} color={g === goal ? m.accent : C.sub} />
              ))}
            </Picker>
          </View>
        </View>
      </Modal>
    </Sheet>
  );
}

/** 1-C-1: 집중 모드 침대 체류 범위 → focusMinInBedMin / focusMaxInBedMin */
export function FocusSettingsSheet({
  visible,
  settings,
  onClose,
  onDone,
}: {
  visible: boolean;
  settings: Settings;
  onClose: () => void;
  onDone: (patch: Partial<Settings>) => Promise<void>;
}) {
  const [low, setLow] = useState(settings.focusMinInBedMin);
  const [high, setHigh] = useState(settings.focusMaxInBedMin);
  const [busy, setBusy] = useState(false);
  const done = async () => {
    setBusy(true);
    try {
      await onDone({ focusMinInBedMin: low, focusMaxInBedMin: high });
    } finally {
      setBusy(false);
    }
  };
  return (
    <Sheet mode="FOCUS" title="수면 설정" visible={visible} onClose={onClose}>
      <Text style={t.hint}>
        하루 필요 수면 시간 {fmtHM(low).replace(/^0/, '')} - {fmtHM(high).replace(/^0/, '')}
      </Text>
      <RangeSlider
        low={low}
        high={high}
        min={3 * 60}
        max={10 * 60}
        step={30}
        onChange={(l, h) => {
          setLow(l);
          setHigh(h);
        }}
      />
      <DoneButton mode="FOCUS" onPress={done} busy={busy} />
    </Sheet>
  );
}

const PREP_STATES: PreparationState[] = ['STARTING', 'STARTING_HEAVY', 'SEEN_NOT_RECALLABLE', 'PATCHING_GAPS', 'FINAL_REVIEW'];
const NATURES: { value: ExamNature; label: string }[] = [
  { value: 'RECALL', label: '암기' },
  { value: 'MIXED', label: '혼합' },
  { value: 'PROBLEM_SOLVING', label: '문제풀이' },
];

/** 1-B-3: 공부 모드 → POST /plans/today?mode=EXAM { preparationState, examNature } */
export function StudyModeSheet({
  visible,
  initial,
  onClose,
  onDone,
}: {
  visible: boolean;
  initial?: { preparationState: PreparationState; examNature: ExamNature };
  onClose: () => void;
  onDone: (req: { preparationState: PreparationState; examNature: ExamNature }) => Promise<void>;
}) {
  // 공부량: easy(이제 시작) ~ hard(마무리 복습) 5단계. 서버 enum 순서 그대로.
  const [level, setLevel] = useState(initial ? PREP_STATES.indexOf(initial.preparationState) : 2);
  const [nature, setNature] = useState<ExamNature>(initial?.examNature ?? 'MIXED');
  const [busy, setBusy] = useState(false);
  const m = MODES.EXAM;
  const done = async () => {
    setBusy(true);
    try {
      await onDone({ preparationState: PREP_STATES[level], examNature: nature });
    } finally {
      setBusy(false);
    }
  };
  return (
    <Sheet mode="EXAM" title="공부 모드" visible={visible} onClose={onClose}>
      <Text style={st.label}>공부량</Text>
      <View style={st.levelLabels}>
        <Text style={st.levelLabel}>easy</Text>
        <Text style={st.levelLabel}>hard</Text>
      </View>
      <View style={st.levels}>
        {PREP_STATES.map((s, i) => (
          <Pressable
            key={s}
            style={[st.level, { backgroundColor: i <= level ? m.accent : 'rgba(184,86,90,0.18)' }]}
            onPress={() => setLevel(i)}
            accessibilityRole="radio"
            accessibilityState={{ selected: i === level }}
            accessibilityLabel={`공부량 ${i + 1}단계`}
            hitSlop={8}
          />
        ))}
      </View>
      <View style={st.natures}>
        {NATURES.map(n => {
          const on = n.value === nature;
          return (
            <Pressable
              key={n.value}
              style={[st.modePill, on ? { backgroundColor: m.accent } : st.modePillOff]}
              onPress={() => setNature(n.value)}
              accessibilityRole="radio"
              accessibilityState={{ selected: on }}
            >
              <Text style={[st.modePillText, on && st.modePillTextOn]}>{n.label}</Text>
            </Pressable>
          );
        })}
      </View>
      <DoneButton mode="EXAM" onPress={done} busy={busy} />
    </Sheet>
  );
}

// 모드 선택 pill(홈 상단·시험 성격 공용): 선택 = accent 채움 흰 글씨 bold, 아니면 반투명 흰 + 테두리
export const st = StyleSheet.create({
  dim: { flex: 1 },
  sheet: { borderTopLeftRadius: 24, borderTopRightRadius: 24, paddingTop: 10, paddingBottom: 24 },
  handle: { alignSelf: 'center', width: 36, height: 4, borderRadius: 2 },
  sheetTitle: { marginTop: 16, marginHorizontal: 20, fontSize: 20, lineHeight: 28, fontWeight: '700', color: C.ink },
  body: { marginTop: 8, backgroundColor: '#FFFFFF', padding: 16, gap: 16 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  label: { flex: 1, fontSize: 14, color: '#2A2B3D' },
  goal: { fontSize: 16, lineHeight: 19, fontWeight: '700', color: C.text },
  done: { height: 48, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  doneText: { fontSize: 16, fontWeight: '700', color: '#FBF7F1' },

  pickerWrap: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  picker: { width: 260, alignItems: 'center', gap: 8, paddingVertical: 20, borderRadius: 16 },
  pickerTitle: { fontSize: 16, lineHeight: 19, fontWeight: '700', color: C.ink },
  wheel: { width: 200, height: 180 },
  wheelItem: { fontSize: 15, height: 180 },

  levelLabels: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: -10 },
  levelLabel: { fontSize: 12, color: '#8A8B99' },
  levels: { flexDirection: 'row', gap: 4 },
  level: { flex: 1, height: 8, borderRadius: 4 },
  natures: { flexDirection: 'row', justifyContent: 'center', gap: 6 },
  modePill: { paddingHorizontal: 14, paddingVertical: 7, borderRadius: 16, borderWidth: 1, borderColor: 'transparent' },
  modePillOff: { backgroundColor: 'rgba(255,255,255,0.6)', borderColor: 'rgba(42,44,62,0.14)' },
  modePillText: { fontSize: 12, color: '#8A8B99' },
  modePillTextOn: { color: '#FFFFFF', fontWeight: '700' },
});
