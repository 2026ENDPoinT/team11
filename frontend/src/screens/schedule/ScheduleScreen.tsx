import { useState } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import Calendar from '../../components/Calendar';
import Timetable from '../../components/Timetable';
import DayEvents from './DayEvents';
import { C, Screen, Segmented } from '../../components/ui';
import { useStore } from '../../store';

// 3 일정 · 시간표 (14:504) / 3-2 일정 · 캘린더 (14:715) / 3-2-1 일정 확인 (YS_github: 탭 안에서 바로 보임)
export default function ScheduleScreen() {
  const navigation = useNavigation();
  const { classes } = useStore();
  const [view, setView] = useState<'시간표' | '캘린더'>('시간표');
  const [day, setDay] = useState<string>(); // 캘린더에서 고른 날짜

  return (
    <Screen scroll>
      <Segmented options={['시간표', '캘린더'] as const} value={view} onChange={setView} />
      {view === '시간표' ? (
        <>
          <Timetable
            classes={classes}
            onPress={c => navigation.navigate('ClassEdit', { id: c.id })}
          />
          <Pressable style={styles.add} onPress={() => navigation.navigate('ClassEdit', {})}>
            <Text style={styles.addText}>+ 수업 추가</Text>
          </Pressable>
        </>
      ) : (
        <>
          <Calendar month={new Date()} selected={day} onSelect={setDay} />
          {/* 날짜를 고르면 달력 아래에 그날 일정 (3-2-1). 날짜가 바뀌면 수정 중이던 줄은 닫힘 */}
          {day && <DayEvents key={day} date={day} />}
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  add: {
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: C.sub,
    borderRadius: 10,
    paddingVertical: 13,
    alignItems: 'center',
  },
  addText: { fontSize: 15, color: C.sub },
});
