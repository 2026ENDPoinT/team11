import { useState } from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { C, MOOD_IMG, Pill, Screen, Segmented, WEEKDAY, fmtDate, fmtHm } from '../../components/ui';
import { fmtHM, sleepMin, summarize, useStore, ymd, type SleepRecord } from '../../store';

const MAX_H = 10; // 차트 최대 10시간

// 4 주간 리포트 (14:917) / 4-1 월간 리포트 (57:1315)
export default function ReportScreen() {
  const navigation = useNavigation();
  const { records } = useStore();
  const [mode, setMode] = useState<'주간' | '월간'>('주간');
  const [anchor, setAnchor] = useState(() => new Date());

  // 주간: anchor 포함 지난 7일 / 월간: anchor가 속한 달
  const days =
    mode === '주간'
      ? Array.from({ length: 7 }, (_, i) =>
          ymd(new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate() - 6 + i)),
        )
      : Array.from({ length: new Date(anchor.getFullYear(), anchor.getMonth() + 1, 0).getDate() }, (_, i) =>
          ymd(new Date(anchor.getFullYear(), anchor.getMonth(), i + 1)),
        );
  const byDate = new Map(records.map(r => [r.date, r]));
  const list = days.map(d => byDate.get(d)).filter((r): r is SleepRecord => !!r).reverse();
  const stats = summarize(list);

  const move = (dir: number) =>
    setAnchor(a =>
      mode === '주간'
        ? new Date(a.getFullYear(), a.getMonth(), a.getDate() + 7 * dir)
        : new Date(a.getFullYear(), a.getMonth() + dir, 1),
    );

  return (
    <Screen scroll>
      <Segmented options={['주간', '월간'] as const} value={mode} onChange={setMode} />
      <View style={styles.dateRow}>
        <Pill outline title="‹" onPress={() => move(-1)} />
        <Text style={styles.dateTitle}>
          {mode === '주간' ? fmtDate(ymd(anchor)) : `${anchor.getFullYear()}년 ${anchor.getMonth() + 1}월`}
        </Text>
        <Pill outline title="›" onPress={() => move(1)} />
      </View>

      {/* 피그마 'image 1' 차트 자리: 기록으로 직접 그림 */}
      <View style={styles.chart}>
        <Text style={styles.big}>{stats ? fmtHm(stats.avg) : '-'}</Text>
        <View style={styles.plot}>
          {[MAX_H, MAX_H / 2, 0].map(v => (
            <View key={v} style={[styles.grid, { bottom: `${(v / MAX_H) * 100}%` }]}>
              <Text style={styles.axis}>{v ? `${v}h` : '0'}</Text>
            </View>
          ))}
          <View style={styles.bars}>
            {days.map(d => {
              const r = byDate.get(d);
              const h = r ? Math.min(sleepMin(r) / 60, MAX_H) : 0;
              return (
                <View key={d} style={styles.barCol}>
                  <View style={[styles.bar, { height: `${(h / MAX_H) * 100}%` }]} />
                </View>
              );
            })}
          </View>
        </View>
        {mode === '주간' && (
          <View style={styles.labels}>
            {days.map(d => (
              <Text key={d} style={styles.label}>{WEEKDAY[new Date(d + 'T00:00:00').getDay()]}</Text>
            ))}
          </View>
        )}
      </View>

      <View style={styles.summary}>
        <Stat label="평균 수면" value={stats ? fmtHm(stats.avg) : '-'} />
        <Stat label="평균 취침" value={stats ? fmtHM(stats.bed) : '-'} />
        <Stat label="규칙성" value={stats ? `${stats.regular}%` : '-'} />
      </View>

      {list.map(r => (
        <Pressable
          key={r.date}
          style={styles.item}
          onPress={() => navigation.navigate('SleepRecord', { date: r.date })}
        >
          <Text style={styles.itemDate}>{fmtDate(r.date)}</Text>
          {r.mood && <Image source={MOOD_IMG[r.mood]} style={styles.mood} />}
          <Text style={styles.itemSub}>| {fmtHm(sleepMin(r))}</Text>
          <Text style={styles.chev}>›</Text>
        </Pressable>
      ))}
      {!list.length && <Text style={styles.empty}>이 기간에 수면 기록이 없어요.</Text>}
    </Screen>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.stat}>
      <Text style={styles.statLabel}>{label}</Text>
      <Text style={styles.statValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  dateRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  dateTitle: { flex: 1, textAlign: 'center', fontSize: 16, fontWeight: '700', color: C.text },
  chart: { backgroundColor: '#FAFAFA', borderTopLeftRadius: 12, borderTopRightRadius: 12, padding: 16, gap: 8 },
  big: { fontSize: 28, color: C.text },
  plot: { height: 100, marginRight: 28 },
  grid: { position: 'absolute', left: 0, right: -28, borderTopWidth: 1, borderColor: '#E5E5E5' },
  axis: { position: 'absolute', right: 0, top: -7, fontSize: 10, color: '#BDBDBD' },
  bars: { flex: 1, flexDirection: 'row', alignItems: 'flex-end', gap: 2 },
  barCol: { flex: 1, height: '100%', justifyContent: 'flex-end', alignItems: 'center' },
  bar: { width: '60%', backgroundColor: '#CFCDD6' },
  labels: { flexDirection: 'row', marginRight: 28 },
  label: { flex: 1, textAlign: 'center', fontSize: 10, color: '#BDBDBD' },
  summary: { flexDirection: 'row', gap: 8, borderWidth: 1, borderColor: '#D2D2D2', borderRadius: 8 },
  stat: { flex: 1, alignItems: 'center', gap: 2, padding: 14 },
  statLabel: { fontSize: 11, color: C.sub },
  statValue: { fontSize: 16, fontWeight: '700', color: C.text },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 4,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderColor: C.fill,
  },
  itemDate: { flex: 1, fontSize: 15, color: C.text },
  mood: { width: 24, height: 24, opacity: 0.6 },
  itemSub: { fontSize: 13, color: C.sub },
  chev: { fontSize: 16, color: C.sub },
  empty: { textAlign: 'center', fontSize: 13, color: C.sub, paddingVertical: 24 },
});
