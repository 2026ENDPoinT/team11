import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { DayOfWeek, TimetableEntry } from '../api/types';
import { parseHM } from '../kst';
import { C } from '../theme';

// 시간표 격자(260:295): 왼쪽 시간 눈금, 요일 열, 수업 블록. 기본 월~금 9~17시, 주말 수업·늦은 수업 있으면 늘어남.
const DAYS: DayOfWeek[] = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
export const DAY_KO: Record<DayOfWeek, string> = { MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토', SUN: '일' };
const HOUR_H = 42;
const HEAD_H = 20;
const GUTTER = 26;

export function Timetable({
  entries,
  editable,
  onPress,
  onDelete,
}: {
  entries: TimetableEntry[];
  editable?: boolean;
  onPress?: (e: TimetableEntry) => void;
  onDelete?: (e: TimetableEntry) => void;
}) {
  const hasWeekend = entries.some(e => e.dayOfWeek === 'SAT' || e.dayOfWeek === 'SUN');
  const days = hasWeekend ? DAYS : DAYS.slice(0, 5);
  const mins = entries.map(e => parseHM(e.startTime) ?? 540);
  const maxs = entries.map(e => parseHM(e.endTime) ?? 1080);
  const startH = Math.min(9, ...mins.map(m => Math.floor(m / 60)));
  const endH = Math.max(18, ...maxs.map(m => Math.ceil(m / 60)));
  const hours = Array.from({ length: endH - startH }, (_, i) => startH + i);

  return (
    <View style={[st.box, { height: HEAD_H + hours.length * HOUR_H + 2 }]}>
      {hours.map((h, i) => (
        <View key={h} style={[st.line, { top: HEAD_H + i * HOUR_H }]}>
          <Text style={st.hour}>{h}</Text>
        </View>
      ))}
      <View style={[st.cols, { left: GUTTER }]}>
        {days.map(d => (
          <View key={d} style={st.col}>
            <Text style={st.dayLabel}>{DAY_KO[d]}</Text>
            {entries
              .filter(e => e.dayOfWeek === d)
              .map(e => {
                const s = parseHM(e.startTime) ?? 0;
                const en = parseHM(e.endTime) ?? s + 60;
                const top = HEAD_H + ((s - startH * 60) / 60) * HOUR_H + 2;
                const height = ((en - s) / 60) * HOUR_H - 4;
                return (
                  <Pressable
                    key={e.entryId}
                    style={[st.block, { top, height }]}
                    onPress={() => onPress?.(e)}
                    disabled={!onPress}
                    accessibilityRole="button"
                    accessibilityLabel={`${e.courseName} ${e.startTime.slice(0, 5)}~${e.endTime.slice(0, 5)}`}
                  >
                    <Text style={st.name} numberOfLines={1}>{e.courseName}</Text>
                    {!!e.room && <Text style={st.room} numberOfLines={1}>{e.room}</Text>}
                    {editable && (
                      <Pressable style={st.del} onPress={() => onDelete?.(e)} hitSlop={6} accessibilityRole="button" accessibilityLabel={`${e.courseName} 삭제`}>
                        <Text style={st.delText}>×</Text>
                      </Pressable>
                    )}
                  </Pressable>
                );
              })}
          </View>
        ))}
      </View>
    </View>
  );
}

const st = StyleSheet.create({
  box: { borderWidth: 1, borderColor: C.line, borderRadius: 8, overflow: 'hidden' },
  line: { position: 'absolute', left: 0, right: 0, height: 1, backgroundColor: '#F2F2F2' },
  hour: { position: 'absolute', left: 4, top: 2, fontSize: 10, color: C.sub },
  cols: { position: 'absolute', top: 0, bottom: 0, right: 0, flexDirection: 'row' },
  col: { flex: 1 },
  dayLabel: { height: HEAD_H, textAlign: 'center', fontSize: 11, lineHeight: HEAD_H, color: C.sub },
  block: { position: 'absolute', left: 2, right: 2, padding: 4, gap: 1, borderRadius: 6, borderWidth: 1, borderColor: C.line, backgroundColor: '#F2F2F2', overflow: 'hidden' },
  name: { fontSize: 10, fontWeight: '700', color: C.text },
  room: { fontSize: 8.5, color: C.sub },
  del: { position: 'absolute', top: 0, right: 0, width: 16, height: 16, borderRadius: 8, backgroundColor: C.primary, alignItems: 'center', justifyContent: 'center' },
  delText: { fontSize: 10, lineHeight: 12, fontWeight: '700', color: '#FFFFFF' },
});
