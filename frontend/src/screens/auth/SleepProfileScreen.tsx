import { useState } from 'react';
import { Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Button, Screen, Slider, Spacer, Stepper, fmtMin, t } from '../../components/ui';

// ponytail: 두 화면 값 모두 아직 저장 안 함. 저장소(API/AsyncStorage) 정해지면 '다음'에서 저장

// 0-4 온보딩 2 · 수면 프로필 (150:1006)
// 메모: 하루 필요 시간 30분 단위 / 통학·준비·잠들기까지 10분 단위, 3시간 이하 / −,+ 위치 고정
export default function SleepProfileScreen() {
  const navigation = useNavigation();
  const [sleep, setSleep] = useState(6 * 60);
  const [commute, setCommute] = useState(50);
  const [ready, setReady] = useState(40);
  const [fallAsleep, setFallAsleep] = useState(10);

  return (
    <Screen scroll>
      <Text style={t.step}>2 / 3</Text>
      <Text style={t.h1}>나에게 맞는 수면 시간</Text>
      <Text style={t.sub}>하루 필요 수면 시간 - {fmtMin(sleep)}</Text>
      <Slider value={sleep} min={5 * 60} max={10 * 60} step={30} onChange={setSleep} />
      <Text style={t.sub}>준비</Text>
      <Stepper label="통학 시간" value={commute} onChange={setCommute} max={180} />
      <Stepper label="준비 시간" value={ready} onChange={setReady} max={180} />
      <Stepper label="잠들기까지" value={fallAsleep} onChange={setFallAsleep} max={180} />
      <Spacer />
      <Button title="다음" onPress={() => navigation.navigate('ExamProfile')} />
    </Screen>
  );
}

// 0-4 온보딩 2 · 시험 기간 (150:1050)
// 메모: 목표 최소 수면 30분 단위 / 시험 기간 최대 30일, 이틀 단위 / −,+ 위치 고정
export function ExamProfileScreen() {
  const navigation = useNavigation();
  const [examMin, setExamMin] = useState(4 * 60 + 30);
  // 이틀 단위로 30일까지 맞추려고 2일부터 시작 (피그마 예시는 1일)
  const [examDays, setExamDays] = useState(2);

  return (
    <Screen scroll>
      <Text style={t.step}>2 / 3</Text>
      <Text style={t.h1}>시험 기간</Text>
      <Stepper label="목표 최소 수면" value={examMin} onChange={setExamMin} step={30} min={3 * 60} max={10 * 60} />
      <Stepper label="시험 기간" value={examDays} onChange={setExamDays} step={2} min={2} max={30} fmt={d => `${d}일`} />
      <Text style={t.hint}>첫 시험 며칠 전부터 준비할 예정인가요?</Text>
      <Spacer />
      <Button title="다음" onPress={() => navigation.navigate('NotificationPermission')} />
    </Screen>
  );
}
