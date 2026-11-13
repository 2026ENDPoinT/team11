import { useState } from 'react';
import { Image, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import BellIcon from '../../assets/icons/home/bell.svg';
import { Button, C, Stepper, Wheel, fmtMin } from '../../components/ui';

// 목표 수면 시간 휠: 3시간 ~ 12시간, 30분 단위
const GOALS = Array.from({ length: 19 }, (_, i) => 3 * 60 + i * 30);

const IMG = {
  penguin: require('../../assets/images/penguin.png'),
  penguinExam: require('../../assets/images/penguin_exam.png'),
};

// 수면 막대가 보여주는 시간 범위: 21:00 ~ 다음날 10:00
const BAR_START = 21 * 60;
const BAR_LEN = 13 * 60;

const hhmm = (m: number) => {
  const v = ((m % 1440) + 1440) % 1440;
  return `${String(Math.floor(v / 60)).padStart(2, '0')}:${String(v % 60).padStart(2, '0')}`;
};
const onBar = (m: number) => (((m - BAR_START + 1440) % 1440) / BAR_LEN) * 100;

// 1 홈 (17:2) · 1-1 수면설정 시트 (97:436) · 1-2 시험기간 (40:161) · 1-3 시험모드 시트 (97:672)
export default function HomeScreen() {
  const navigation = useNavigation();
  // 카드 위 '일상 / 시험' 버튼으로 전환
  // ponytail: 시험 기간이 되면 일정의 '시험' 일정으로 자동 전환하는 건 아직 없음
  const [exam, setExam] = useState(false);
  const [sheet, setSheet] = useState(false);
  const [picking, setPicking] = useState(false); // 목표 수면 시간 휠 열림
  const closeSheet = () => {
    setSheet(false);
    setPicking(false);
  };
  // 메모(Frame 13): 일상이랑 시험 기간의 수면설정(목표 수면·준비·통학)은 서로 연동되지 않게 → 모드별로 따로 보관
  // ponytail: 앱 메모리에만 있음. 저장소 정해지면 store로 옮기기
  const [plans, setPlans] = useState({
    daily: { goal: 7 * 60 + 30, ready: 40, commute: 50 },
    exam: { goal: 7 * 60 + 30, ready: 40, commute: 50 },
  });
  const mode = exam ? 'exam' : 'daily';
  const { goal, ready, commute } = plans[mode];
  const setPlan = (patch: Partial<typeof plans.daily>) =>
    setPlans(p => ({ ...p, [mode]: { ...p[mode], ...patch } }));
  const setGoal = (v: number) => setPlan({ goal: v });
  const setReady = (v: number) => setPlan({ ready: v });
  const setCommute = (v: number) => setPlan({ commute: v });

  // ponytail: 첫 수업은 시간표 데이터 연결 전까지 고정값
  const firstClass = { name: '1교시 오픈소스소프트웨어', start: 9 * 60 };
  const wake = firstClass.start - ready - commute;
  const bed = wake - goal;

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      {/* unslop에는 알림 목록 화면이 없어서 5-2 알림 설정으로 연결 */}
      <Pressable
        style={styles.bell}
        accessibilityRole="button"
        accessibilityLabel="알림 설정"
        onPress={() => navigation.navigate('NotificationSettings')}
      >
        <BellIcon width={20} height={20} />
      </Pressable>

      <View style={styles.center}>
        <Image
          source={exam ? IMG.penguinExam : IMG.penguin}
          style={styles.penguin}
          resizeMode="contain"
        />
      </View>

      {/* 피그마 'card 일상/시험 모드': 카드 위 왼쪽. 지금 모드 글자만 진하게 */}
      <Pressable
        style={styles.mode}
        onPress={() => setExam(e => !e)}
        accessibilityRole="switch"
        accessibilityLabel="시험 모드"
        accessibilityState={{ checked: exam }}
      >
        <Text style={styles.modeText}>
          <Text style={!exam && styles.modeOn}>일상</Text> / <Text style={exam && styles.modeOn}>시험</Text>
        </Text>
      </Pressable>

      <Pressable style={styles.card} onPress={() => setSheet(true)} accessibilityRole="button">
        <View style={styles.times}>
          <View>
            <Text style={styles.timeLabel}>권장 취침</Text>
            <Text style={styles.time}>{hhmm(bed)}</Text>
          </View>
          <View style={styles.alignEnd}>
            <Text style={styles.timeLabel}>권장 기상</Text>
            <Text style={styles.time}>{hhmm(wake)}</Text>
          </View>
        </View>
        <View style={styles.sleepTrack}>
          <View
            style={[
              styles.sleepRange,
              { left: `${onBar(bed)}%`, width: `${(goal / BAR_LEN) * 100}%` },
            ]}
          />
        </View>
        <Text style={styles.classText}>
          {firstClass.name}({hhmm(firstClass.start)})
        </Text>
      </Pressable>

      <Modal visible={sheet} transparent animationType="slide" onRequestClose={closeSheet}>
        <Pressable style={styles.dim} onPress={closeSheet} />
        <View style={styles.sheet}>
          <View style={styles.handle} />
          <Text style={styles.sheetTitle}>수면 설정</Text>
          {/* 메모: 목표 수면 시간은 평소엔 값만, 숫자 누르면 휠이 모달로 나오고 숫자를 고르면 사라짐
              / 준비·통학은 10분 단위, 3시간 이하, −,+ 위치 고정 */}
          <View style={styles.sheetBody}>
            <View style={styles.row}>
              <Text style={styles.label}>목표 수면 시간</Text>
              <Pressable
                onPress={() => setPicking(true)}
                accessibilityRole="button"
                accessibilityHint="눌러서 시간 고르기"
              >
                <Text style={styles.goal}>{fmtMin(goal)}</Text>
              </Pressable>
            </View>
            <Stepper label="기상 후 준비 시간" value={ready} onChange={setReady} max={180} />
            <Stepper label="통학 시간" value={commute} onChange={setCommute} max={180} />
            <Button title="완료" onPress={closeSheet} style={styles.done} />
          </View>
        </View>

        {/* 목표 수면 시간 휠 모달: 굴려서 맞추고 숫자를 누르면 확정되고 닫힘. 바깥을 눌러도 닫힘 */}
        <Modal visible={picking} transparent animationType="fade" onRequestClose={() => setPicking(false)}>
          <View style={styles.pickerWrap}>
            <Pressable style={StyleSheet.absoluteFill} onPress={() => setPicking(false)} accessibilityLabel="닫기" />
            <View style={styles.picker}>
              <Text style={styles.pickerTitle}>목표 수면 시간</Text>
              <Wheel values={GOALS} value={goal} onChange={setGoal} onPick={() => setPicking(false)} />
            </View>
          </View>
        </Modal>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#FFFFFF', paddingHorizontal: 20, paddingBottom: 24 },
  alignEnd: { alignItems: 'flex-end' },
  bell: {
    alignSelf: 'flex-end',
    marginTop: 6,
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(42,44,62,0.08)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  // 피그마: 펭귄 262~590, 일상/시험 버튼 593~627, 카드 630 → 사이 간격 3px씩
  center: { flex: 1, alignItems: 'center', justifyContent: 'flex-end', paddingBottom: 3 },
  mode: {
    alignSelf: 'flex-start',
    height: 34,
    justifyContent: 'center',
    paddingHorizontal: 18,
    marginBottom: 3,
    borderWidth: 1,
    borderColor: 'rgba(42,44,62,0.08)',
    borderRadius: 12,
  },
  modeText: { fontSize: 12, lineHeight: 16, color: '#9C9C9C' },
  modeOn: { color: '#2A2C3E', fontWeight: '700' },
  penguin: { width: 307, height: 328 },
  card: {
    borderWidth: 1,
    borderColor: 'rgba(42,44,62,0.08)',
    borderRadius: 12,
    padding: 16,
    gap: 12,
  },
  times: { flexDirection: 'row', justifyContent: 'space-between' },
  timeLabel: { fontSize: 12, lineHeight: 16, color: '#5A5D75' },
  time: { fontSize: 28, lineHeight: 36, fontWeight: '500', color: '#2A2C3E' },
  sleepTrack: { height: 8, borderRadius: 4, backgroundColor: '#EFE8E3', overflow: 'hidden' },
  sleepRange: { position: 'absolute', top: 0, bottom: 0, borderRadius: 4, backgroundColor: '#5C5F86' },
  classText: { fontSize: 12, lineHeight: 16, color: '#727489' },
  dim: { flex: 1, backgroundColor: 'rgba(31,33,48,0.38)' },
  sheet: {
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    paddingTop: 10,
    paddingBottom: 24,
  },
  handle: { alignSelf: 'center', width: 36, height: 4, borderRadius: 2, backgroundColor: '#CCCCD4' },
  sheetTitle: {
    marginTop: 16,
    marginHorizontal: 20,
    fontSize: 20,
    lineHeight: 28,
    fontWeight: '700',
    color: '#2A2C3E',
  },
  sheetBody: { padding: 16, gap: 16 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  label: { flex: 1, fontSize: 14, color: C.text },
  goal: { fontSize: 16, lineHeight: 19, fontWeight: '700', color: C.text },
  pickerWrap: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(31,33,48,0.38)' },
  picker: { width: 260, alignItems: 'center', gap: 8, paddingVertical: 20, borderRadius: 16, backgroundColor: '#FFFFFF' },
  pickerTitle: { fontSize: 16, lineHeight: 19, fontWeight: '700', color: '#2A2C3E' },
  done: { height: 48, borderRadius: 12, paddingVertical: 0 },
});
