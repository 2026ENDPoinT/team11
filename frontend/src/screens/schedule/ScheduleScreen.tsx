import { useState } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import Calendar from '../../components/Calendar';
import Timetable from '../../components/Timetable';
import { C, Screen, Segmented } from '../../components/ui';
import { useStore } from '../../store';

// 3 일정 · 시간표 (14:504) / 3-2 일정 · 캘린더 (14:715)
export default function ScheduleScreen() {
  const navigation = useNavigation();
  const { classes } = useStore();
  const [view, setView] = useState<'시간표' | '캘린더'>('시간표');

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
        <Calendar
          month={new Date()}
         
          onSelect={date =>
            navigation.navigate('Main', { screen: 'Schedule', params: { screen: 'EventDay', params: { date } } })
          }
        />
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
