import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Klass } from '../store';
import { C } from './ui';

const DAYS = ['월', '화', '수', '목', '금'];
const FIRST = 9; // 9시
const HOURS = 9; // 9 ~ 17시
const HEAD = 21; // 요일 줄 높이
const ROW = 42.11; // 1시간 높이 (피그마 timetable grid 14:520)
const TIME_COL = 27;

// 3 일정 · 시간표 그리드. onDelete가 있으면 × 버튼 표시 (3-1 수업 추가·수정)
export default function Timetable({
  classes,
  onPress,
  onDelete,
}: {
  classes: Klass[];
  onPress?: (c: Klass) => void;
  onDelete?: (c: Klass) => void;
}) {
  const [w, setW] = useState(0);
  const col = (w - TIME_COL) / 5;
  const y = (m: number) => HEAD + ((m - FIRST * 60) / 60) * ROW + 2;

  return (
    <View style={styles.grid} onLayout={e => setW(e.nativeEvent.layout.width)}>
      {DAYS.map((d, i) => (
        <Text key={d} style={[styles.day, { left: TIME_COL + col * i, width: col }]}>
          {d}
        </Text>
      ))}
      {Array.from({ length: HOURS }, (_, i) => (
        <View key={i} style={[styles.line, { top: HEAD + ROW * i }]}>
          <Text style={styles.hour}>{FIRST + i}</Text>
        </View>
      ))}
      {w > 0 &&
        classes.map(c => (
          <Pressable
            key={c.id}
            disabled={!onPress}
            onPress={() => onPress?.(c)}
            style={[
              styles.block,
              { left: TIME_COL + col * c.day, width: col - 4, top: y(c.start), height: y(c.end) - y(c.start) },
            ]}
          >
            <Text style={styles.name} numberOfLines={1}>{c.name}</Text>
            <Text style={styles.room} numberOfLines={1}>{c.room}</Text>
            {onDelete && (
              <Pressable
                style={styles.del}
                hitSlop={8}
                accessibilityLabel={`${c.name} 삭제`}
                onPress={() => onDelete(c)}
              >
                <Text style={styles.delText}>×</Text>
              </Pressable>
            )}
          </Pressable>
        ))}
    </View>
  );
}

const styles = StyleSheet.create({
  grid: {
    height: HEAD + ROW * HOURS + 2,
    borderWidth: 1,
    borderColor: C.line,
    borderRadius: 8,
    overflow: 'hidden',
  },
  day: { position: 'absolute', top: 3, textAlign: 'center', fontSize: 11, color: C.sub },
  line: { position: 'absolute', left: 0, right: 0, height: ROW, borderTopWidth: 1, borderColor: C.fill },
  hour: { marginLeft: 5, marginTop: 2, fontSize: 10, color: C.sub },
  block: {
    position: 'absolute',
    padding: 4,
    gap: 1,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: C.line,
    backgroundColor: C.fill,
  },
  name: { fontSize: 10, fontWeight: '700', color: C.text },
  room: { fontSize: 8.5, color: C.sub },
  del: {
    position: 'absolute',
    top: 1,
    right: 2,
    width: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: C.dark,
    alignItems: 'center',
    justifyContent: 'center',
  },
  delText: { fontSize: 10, fontWeight: '700', color: '#FFFFFF', lineHeight: 12 },
});
