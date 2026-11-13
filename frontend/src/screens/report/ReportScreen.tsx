import { useState } from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import * as api from '../../api/endpoints';
import type { ReportPeriod } from '../../api/types';
import { AddButton, Pill, Screen, Segmented, Status } from '../../components/ui';
import { addDays, fmtDateKo, todayKst, WEEKDAY_KO, weekday } from '../../kst';
import { C } from '../../theme';
import { useApi } from '../../useApi';

export const MOOD: Record<1 | 2 | 3, number> = {
  3: require('../../assets/images/mood_happy.png'),
  2: require('../../assets/images/mood_neutral.png'),
  1: require('../../assets/images/mood_sad.png'),
};
export const hm = (min: number) => `${Math.floor(min / 60)}h ${String(min % 60).padStart(2, '0')}m`;
const PLOT_H = 100;
const MAX_MIN = 10 * 60;

// 4 주간(264:359) / 4-1 월간(264:443) 리포트. 집계는 서버 값 그대로(avgSleepMin·avgBedTime·regularityPct).
export default function ReportScreen() {
  const nav = useNavigation();
  const [period, setPeriod] = useState<ReportPeriod>('WEEK');
  const [date, setDate] = useState(todayKst());
  const r = useApi(() => api.getReport(period, date), [period, date]);

  // ‹ › : 주간은 ±7일, 월간은 ±1달(1일 기준)
  const shift = (dir: 1 | -1) => {
    if (period === 'WEEK') setDate(addDays(date, 7 * dir));
    else {
      const [y, m] = date.split('-').map(Number);
      const nm = m + dir;
      setDate(`${nm < 1 ? y - 1 : nm > 12 ? y + 1 : y}-${String(((nm + 11) % 12) + 1).padStart(2, '0')}-01`);
    }
  };
  const label = period === 'WEEK' ? fmtDateKo(date) : `${date.slice(0, 4)}년 ${Number(date.slice(5, 7))}월`;
  const daily = r.data?.daily ?? [];
  const logged = [...daily].filter(d => d.sleepLogId).reverse();

  return (
    <Screen scroll>
      <Segmented options={[{ value: 'WEEK', label: '주간' }, { value: 'MONTH', label: '월간' }]} value={period} onChange={setPeriod} />
      <View style={st.range}>
        <Pill title="‹" kind="outline" onPress={() => shift(-1)} accessibilityLabel="이전" />
        <Text style={st.rangeLabel}>{label}</Text>
        <Pill title="›" kind="outline" onPress={() => shift(1)} accessibilityLabel="다음" />
      </View>

      {r.error ? (
        <Status error={r.error} onRetry={r.reload} />
      ) : (
        <>
          <View style={st.chart}>
            <Text style={st.big}>{r.data?.avgSleepMin != null ? hm(r.data.avgSleepMin) : '—'}</Text>
            <View style={st.plot}>
              {[10, 5, 0].map(h => (
                <View key={h} style={[st.grid, { top: PLOT_H - (h / 10) * PLOT_H }]}>
                  <Text style={st.gridLabel}>{h ? `${h}h` : '0'}</Text>
                </View>
              ))}
              <View style={st.bars}>
                {daily.map(d => (
                  <View key={d.date} style={st.barSlot}>
                    {d.sleepMin != null && <View style={[st.bar, { height: (Math.min(d.sleepMin, MAX_MIN) / MAX_MIN) * PLOT_H }]} />}
                  </View>
                ))}
              </View>
            </View>
            {period === 'WEEK' && (
              <View style={st.labels}>
                {daily.map(d => (
                  <Text key={d.date} style={st.label}>{WEEKDAY_KO[weekday(d.date)]}</Text>
                ))}
              </View>
            )}
          </View>
          <View style={st.summary}>
            <Stat label="평균 수면" value={r.data?.avgSleepMin != null ? hm(r.data.avgSleepMin) : '—'} />
            <Stat label="평균 취침" value={r.data?.avgBedTime ?? '—'} />
            <Stat label="규칙성" value={r.data?.regularityPct != null ? `${r.data.regularityPct}%` : '—'} />
          </View>
          {logged.map(d => (
            <Pressable key={d.date} style={st.row} onPress={() => nav.navigate('SleepRecord', { sleepLogId: d.sleepLogId!, date: d.date })} accessibilityRole="button">
              <Text style={st.rowDate}>{fmtDateKo(d.date)}</Text>
              {d.quality && <Image source={MOOD[d.quality]} style={st.mood} />}
              <Text style={st.rowDur}>| {hm(d.sleepMin ?? 0)}</Text>
              <Text style={st.chev}>›</Text>
            </Pressable>
          ))}
          {!r.loading && !logged.length && <Text style={st.empty}>이 기간엔 수면 기록이 없어요.</Text>}
          <AddButton title="+ 수면 기록" onPress={() => nav.navigate('SleepRecord', { date: todayKst() })} />
        </>
      )}
    </Screen>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View style={st.stat}>
      <Text style={st.statLabel}>{label}</Text>
      <Text style={st.statValue}>{value}</Text>
    </View>
  );
}

const st = StyleSheet.create({
  range: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  rangeLabel: { flex: 1, textAlign: 'center', fontSize: 16, fontWeight: '700', color: C.text },
  chart: { backgroundColor: '#FAFAFA', borderTopLeftRadius: 12, borderTopRightRadius: 12, padding: 16, gap: 8 },
  big: { fontSize: 28, color: C.text },
  plot: { height: PLOT_H, marginTop: 8, marginRight: 28 },
  grid: { position: 'absolute', left: 0, right: 0, height: 1, backgroundColor: '#E5E5E5' },
  gridLabel: { position: 'absolute', right: -28, top: -7, width: 26, textAlign: 'right', fontSize: 10, color: '#BDBDBD' },
  bars: { position: 'absolute', left: 0, right: 0, bottom: 0, top: 0, flexDirection: 'row' },
  barSlot: { flex: 1, justifyContent: 'flex-end', alignItems: 'center' },
  bar: { width: '55%', backgroundColor: '#CFCDD6' },
  labels: { flexDirection: 'row', paddingRight: 28 },
  label: { flex: 1, textAlign: 'center', fontSize: 10, color: '#BDBDBD' },
  summary: { flexDirection: 'row', gap: 8, borderWidth: 1, borderColor: '#D2D2D2', borderRadius: 8 },
  stat: { flex: 1, alignItems: 'center', gap: 2, padding: 14 },
  statLabel: { fontSize: 11, color: C.sub },
  statValue: { fontSize: 16, fontWeight: '700', color: C.text },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 4, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: '#F2F2F2' },
  rowDate: { flex: 1, fontSize: 15, color: C.text },
  mood: { width: 24, height: 24, opacity: 0.6 },
  rowDur: { fontSize: 13, color: C.sub },
  chev: { fontSize: 16, color: C.sub },
  empty: { textAlign: 'center', fontSize: 13, color: C.sub, paddingVertical: 12 },
});
