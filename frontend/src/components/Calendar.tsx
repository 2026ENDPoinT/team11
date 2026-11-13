import { StyleSheet, Pressable, Text, View } from 'react-native';
import { Calendar as RNCalendar, LocaleConfig, type DateData } from 'react-native-calendars';
import type { DayProps } from 'react-native-calendars/src/calendar/day';
import { useStore, ymd } from '../store';
import { C } from './ui';

const WEEK = ['일', '월', '화', '수', '목', '금', '토'];
LocaleConfig.locales.ko = {
  monthNames: Array.from({ length: 12 }, (_, i) => `${i + 1}월`),
  monthNamesShort: Array.from({ length: 12 }, (_, i) => `${i + 1}월`),
  dayNames: WEEK.map(w => `${w}요일`),
  dayNamesShort: WEEK,
  today: '오늘',
};
LocaleConfig.defaultLocale = 'ko';

// 3-2 일정 · 캘린더 (14:715) 월간 달력. react-native-calendars 사용 → 좌우 화살표/밀어서 달 이동
// selected가 있으면 그 날짜를 진하게 표시 (3-3)
export default function Calendar({
  month,
  selected,
  onSelect,
}: {
  month: Date; // 처음 보여줄 달의 아무 날짜
  selected?: string;
  onSelect: (date: string) => void;
}) {
  return (
    <RNCalendar
      initialDate={selected ?? ymd(month)}
      monthFormat="yyyy년 M월"
      hideExtraDays
      enableSwipeMonths
      markedDates={selected ? { [selected]: { selected: true } } : undefined}
      dayComponent={Day}
      onDayPress={d => onSelect(d.dateString)}
      style={styles.box}
      theme={{
        calendarBackground: 'transparent',
        monthTextColor: C.text,
        textMonthFontSize: 16,
        textMonthFontWeight: '700',
        arrowColor: C.sub,
        textSectionTitleColor: C.sub,
        textDayHeaderFontSize: 11,
        weekVerticalMargin: 3,
      }}
    />
  );
}

// 날짜 칸: 숫자 · 단일 일정 점 · 기간 일정 막대(시작일이나 일요일 칸에서 그 주 끝까지 그림)
function Day({ date, marking, onPress, accessibilityLabel }: DayProps & { date?: DateData }) {
  const { events } = useStore();
  if (!date) return null;
  const d = date.dateString;
  const dow = new Date(d + 'T00:00:00').getDay();
  const on = !!marking?.selected;
  const dot = events.some(e => !e.endDate && e.date === d);
  const bars = events.filter(e => e.endDate && (e.date === d || (dow === 0 && e.date < d && d <= e.endDate)));

  return (
    <Pressable style={styles.cell} onPress={() => onPress?.(date)} accessibilityLabel={accessibilityLabel}>
      <View style={on && styles.selected}>
        <Text style={[styles.num, on && styles.numOn]}>{date.day}</Text>
      </View>
      {dot && <View style={styles.dot} />}
      {bars.map(e => {
        // 이 칸부터 그 주 토요일 또는 일정 끝날까지 몇 칸인지
        const rest = Math.round((+new Date(e.endDate + 'T00:00:00') - +new Date(d + 'T00:00:00')) / 864e5);
        const n = Math.min(rest, 6 - dow) + 1;
        return (
          <View key={e.id} pointerEvents="none" style={[styles.bar, { width: `${n * 100 - 14}%` }]}>
            <Text style={styles.barText} numberOfLines={1}>{e.title}</Text>
          </View>
        );
      })}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  box: { borderWidth: 1, borderColor: C.line, borderRadius: 8, paddingHorizontal: 0, paddingBottom: 6 },
  cell: { alignSelf: 'stretch', height: 66, alignItems: 'center', gap: 2 },
  num: { fontSize: 12, color: C.text },
  selected: { backgroundColor: C.dark, borderRadius: 2, paddingHorizontal: 1 },
  numOn: { color: '#FFFFFF', fontWeight: '700' },
  dot: { width: 4, height: 4, borderRadius: 2, backgroundColor: C.text },
  bar: { position: 'absolute', top: 15, left: '7%', backgroundColor: C.dark, borderRadius: 4, padding: 2 },
  barText: { fontSize: 6, color: '#FFFFFF' },
});
