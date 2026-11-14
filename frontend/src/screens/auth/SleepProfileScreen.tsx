import { useState } from 'react';
import { Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Button, Screen, Spacer, Stepper, t } from '../../components/ui';
import { Slider } from '../../components/Slider';
import { fmtDuration } from '../../kst';
import { draft } from './onboarding';

// 0-4 온보딩 2 · 수면 프로필(258:112). 하루 필요 수면 30분 단위(서버 300~600), 통학·준비·입면 10분 단위(0~240, 입면 0~60)
export default function SleepProfileScreen() {
  const nav = useNavigation();
  const [sleep, setSleep] = useState(draft.sleepNeedMin);
  const [commute, setCommute] = useState(draft.commuteMin);
  const [prep, setPrep] = useState(draft.prepMin);
  const [latency, setLatency] = useState(draft.sleepLatencyMin ?? 10);

  const next = () => {
    Object.assign(draft, { sleepNeedMin: sleep, commuteMin: commute, prepMin: prep, sleepLatencyMin: latency });
    nav.navigate('ExamProfile');
  };

  return (
    <Screen>
      <Text style={t.step}>2 / 3</Text>
      <Text style={t.h1}>나에게 맞는 수면 시간</Text>
      <Text style={t.hint}>하루 필요 수면 시간 - {fmtDuration(sleep)}</Text>
      <Slider value={sleep} min={5 * 60} max={10 * 60} step={30} onChange={setSleep} />
      <Text style={t.hint}>준비</Text>
      <Stepper label="통학 시간" value={commute} onChange={setCommute} max={180} />
      <Stepper label="준비 시간" value={prep} onChange={setPrep} max={180} />
      <Stepper label="잠들기까지" value={latency} onChange={setLatency} max={60} />
      <Spacer />
      <Button title="다음" onPress={next} />
    </Screen>
  );
}

// 0-4 온보딩 2 · 시험 기간(258:147). examPrepDays 서버 범위 1~14.
// 목표 최소 수면은 온보딩 요청에 필드가 없어 → 온보딩 완료 후 PUT /users/me/settings { minSleepMin }로 보낸다.
export function ExamProfileScreen() {
  const nav = useNavigation();
  const [minSleep, setMinSleep] = useState(4 * 60 + 30);
  const [days, setDays] = useState(draft.examPrepDays ?? 2);

  const next = () => {
    draft.examPrepDays = days;
    examDraft.minSleepMin = minSleep;
    nav.navigate('NotificationPermission');
  };

  return (
    <Screen>
      <Text style={t.step}>2 / 3</Text>
      <Text style={t.h1}>시험 기간</Text>
      <Stepper label="목표 최소 수면" value={minSleep} onChange={setMinSleep} step={30} min={4 * 60} max={8 * 60} fmt={fmtDuration} />
      <Stepper label="시험 기간" value={days} onChange={setDays} step={1} min={1} max={14} fmt={d => `${d}일`} />
      <Text style={t.hint}>첫 시험 며칠 전부터 준비할 예정인가요?</Text>
      <Spacer />
      <Button title="다음" onPress={next} />
    </Screen>
  );
}

export const examDraft = { minSleepMin: 4 * 60 + 30 };
