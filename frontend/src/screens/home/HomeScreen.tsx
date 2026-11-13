import { useCallback, useState } from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import BellIcon from '../../assets/icons/bell.svg';
import { ApiError } from '../../api/client';
import * as api from '../../api/endpoints';
import type { ExamNature, Plan, PlanMode, PreparationState, Settings } from '../../api/types';
import { Gradient } from '../../components/Gradient';
import { Pill } from '../../components/ui';
import { fmtHM, parseIso, todayKst } from '../../kst';
import { useSession } from '../../session';
import { C } from '../../theme';
import { appConfig } from '../auth/SplashScreen';
import { FocusSettingsSheet, SleepSettingsSheet, StudyModeSheet, st as sheetSt } from './HomeSheets';
import { MODES, PENGUIN } from './modes';

// 수면 막대 범위 21:00 ~ 다음날 10:00
const BAR_START = 21 * 60;
const BAR_LEN = 13 * 60;
const onBar = (min: number) => (((min - BAR_START + 1440) % 1440) / BAR_LEN) * 100;

// 1-A 일상(415:2269) · 1-B 시험(415:2296) · 1-C 집중(415:2322). 취침/기상은 서버 플랜 값을 그대로 보여준다.
export default function HomeScreen() {
  const nav = useNavigation();
  const { me, setMe } = useSession();
  // ponytail: 마지막 모드는 메모리에만. 앱 재시작하면 일상으로.
  const [mode, setMode] = useState<PlanMode>('NORMAL');
  const [plan, setPlan] = useState<Plan | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [sheet, setSheet] = useState<'sleep' | 'focus' | 'study' | null>(null);
  // 시험 모드 POST 바디(공부량·성격). 처음엔 없음 → 공부 모드 시트부터.
  const [examReq, setExamReq] = useState<{ preparationState: PreparationState; examNature: ExamNature } | null>(null);
  const m = MODES[mode];

  const load = useCallback(
    async (target: PlanMode, regenerate = false, body?: typeof examReq) => {
      setLoading(true);
      setError(null);
      try {
        const p = regenerate
          ? await api.regeneratePlan(target, target === 'EXAM' ? body ?? examReq ?? undefined : undefined)
          : await api.getPlanToday(target);
        setPlan(p);
      } catch (e) {
        const err = e instanceof ApiError ? e : new ApiError('UNKNOWN', '플랜을 불러오지 못했어요.', 0);
        // 시험 모드 첫 생성은 바디가 있어야 함 → 공부 모드 시트로 유도
        if (target === 'EXAM' && err.code === 'VALIDATION_FAILED' && !examReq) setSheet('study');
        setPlan(null);
        setError(err);
      } finally {
        setLoading(false);
      }
    },
    [examReq],
  );

  useFocusEffect(
    useCallback(() => {
      load(mode);
    }, [mode, load]),
  );

  const switchMode = (next: PlanMode) => {
    if (next === mode) return;
    setMode(next);
  };

  const saveSettings = async (patch: Partial<Settings>) => {
    try {
      const s = await api.updateSettings(patch);
      if (me) setMe({ ...me, settings: s });
      setSheet(null);
      await load(mode, true);
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError('UNKNOWN', '저장에 실패했어요.', 0));
      setSheet(null);
    }
  };

  const saveStudy = async (req: { preparationState: PreparationState; examNature: ExamNature }) => {
    setExamReq(req);
    setSheet(null);
    await load('EXAM', true, req);
  };

  const openCardSheet = () => {
    if (mode === 'FOCUS') setSheet('focus');
    else setSheet('sleep');
  };

  const bed = plan?.bedAt ? parseIso(plan.bedAt).min : null;
  const wake = plan?.wakeAt ? parseIso(plan.wakeAt).min : null;
  const modes: PlanMode[] = ['NORMAL', ...(appConfig.focusMode ? (['FOCUS'] as PlanMode[]) : []), ...(appConfig.examMode ? (['EXAM'] as PlanMode[]) : [])];

  return (
    <View style={st.screen}>
      <Gradient from={m.from} to={m.to} />
      <SafeAreaView style={st.safe} edges={['top']}>
        <View style={st.topRow}>
          <View style={st.modeButtons}>
            {modes.map(k => {
              const on = k === mode;
              return (
                <Pressable
                  key={k}
                  style={[sheetSt.modePill, on ? { backgroundColor: MODES[k].accent } : sheetSt.modePillOff]}
                  onPress={() => switchMode(k)}
                  accessibilityRole="tab"
                  accessibilityState={{ selected: on }}
                >
                  <Text style={[sheetSt.modePillText, on && sheetSt.modePillTextOn]}>{MODES[k].label}</Text>
                </Pressable>
              );
            })}
          </View>
          <Pressable style={st.bell} onPress={() => nav.navigate('NotificationSettings')} accessibilityRole="button" accessibilityLabel="알림 설정">
            <BellIcon width={20} height={20} color={C.ink} />
          </Pressable>
        </View>

        <View style={st.center}>
          <Image source={PENGUIN[mode]} style={st.penguin} resizeMode="contain" />
        </View>

        {/* card/오늘 수면 플랜 */}
        <Pressable style={st.card} onPress={openCardSheet} accessibilityRole="button" accessibilityLabel="수면 설정 열기">
          {loading ? (
            <ActivityIndicator color={m.accent} style={st.cardStatus} />
          ) : error ? (
            <View style={st.cardStatus}>
              <Text style={st.warn}>{error.message}</Text>
              {error.code === 'SLEEP_HISTORY_INSUFFICIENT' && (
                <Pill title="수면 기록하기" kind="outline" onPress={() => nav.navigate('SleepRecord', { date: todayKst() })} />
              )}
              {mode === 'EXAM' && error.code === 'VALIDATION_FAILED' && (
                <Pill title="공부 모드 설정" kind="outline" onPress={() => setSheet('study')} />
              )}
              {mode === 'EXAM' && error.code === 'EVENT_NOT_FOUND' && (
                <Pill title="시험 일정 추가" kind="outline" onPress={() => nav.navigate('EventEdit', {})} />
              )}
              {!['SLEEP_HISTORY_INSUFFICIENT', 'VALIDATION_FAILED', 'EVENT_NOT_FOUND'].includes(error.code) && (
                <Pill title="다시 시도" kind="outline" onPress={() => load(mode)} />
              )}
            </View>
          ) : plan && (plan.status === 'NO_FEASIBLE_PLAN' || bed === null || wake === null) ? (
            <View style={st.cardStatus}>
              <Text style={st.warn}>{plan.warnings[0] ?? '오늘은 맞는 플랜을 못 찾았어요.'}</Text>
              <Pill title="다시 만들기" kind="outline" onPress={() => load(mode, true)} />
            </View>
          ) : (
            <>
              <View style={st.times}>
                <View>
                  <Text style={st.timeLabel}>권장 취침</Text>
                  <Text style={st.time}>{fmtHM(bed!)}</Text>
                </View>
                <View style={st.alignEnd}>
                  <Text style={st.timeLabel}>권장 기상</Text>
                  <Text style={st.time}>{fmtHM(wake!)}</Text>
                </View>
              </View>
              <View style={[st.track, { backgroundColor: m.track }]}>
                <View
                  style={[
                    st.range,
                    { backgroundColor: m.accent, left: `${onBar(bed!)}%`, width: `${(((wake! - bed! + 1440) % 1440) / BAR_LEN) * 100}%` },
                  ]}
                />
              </View>
              <Text style={st.basis}>{plan!.basisText ?? '내일 일정 없음 · 기본 기상 09:00'}</Text>
            </>
          )}
        </Pressable>
      </SafeAreaView>

      {me && sheet === 'sleep' && mode !== 'FOCUS' && (
        <SleepSettingsSheet mode={mode} visible settings={me.settings} onClose={() => setSheet(null)} onDone={saveSettings} />
      )}
      {me && sheet === 'focus' && (
        <FocusSettingsSheet visible settings={me.settings} onClose={() => setSheet(null)} onDone={saveSettings} />
      )}
      {sheet === 'study' && <StudyModeSheet visible initial={examReq ?? undefined} onClose={() => setSheet(null)} onDone={saveStudy} />}
    </View>
  );
}

const st = StyleSheet.create({
  screen: { flex: 1 },
  safe: { flex: 1, paddingHorizontal: 20, paddingBottom: 24 },
  topRow: { height: 46, marginTop: 6, justifyContent: 'center' },
  modeButtons: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', gap: 6, top: 30 },
  bell: {
    alignSelf: 'flex-end',
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: C.hairline,
    alignItems: 'center',
    justifyContent: 'center',
  },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', marginTop: 24 },
  penguin: { width: 362, height: 362, maxWidth: '100%' },
  card: {
    backgroundColor: 'rgba(255,255,255,0.55)',
    borderWidth: 1,
    borderColor: C.hairline,
    borderRadius: 12,
    padding: 16,
    gap: 12,
    minHeight: 132,
    justifyContent: 'center',
  },
  cardStatus: { alignItems: 'center', gap: 10, paddingVertical: 8 },
  warn: { fontSize: 13, color: C.inkSoft, textAlign: 'center' },
  times: { flexDirection: 'row', justifyContent: 'space-between' },
  alignEnd: { alignItems: 'flex-end' },
  timeLabel: { fontSize: 12, lineHeight: 16, color: C.inkSoft },
  time: { fontSize: 28, lineHeight: 36, fontWeight: '500', color: C.ink },
  track: { height: 8, borderRadius: 4, overflow: 'hidden' },
  range: { position: 'absolute', top: 0, bottom: 0, borderRadius: 4 },
  basis: { fontSize: 12, lineHeight: 16, color: C.inkFaint },
});
