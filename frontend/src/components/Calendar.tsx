import { useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Event } from '../api/types';
import { addDays, parseIso, weekday } from '../kst';
import { C } from '../theme';

// 월 캘린더(262:275): ‹ 2026년 11월 › · 일~토 · 주 행 66px · 일정 있는 날 점 · 종일·다일 일정은 밴드.
// react-native-calendars 대신 직접: Figma 모양(밴드·점)이 커스텀 dayComponent로도 맞추기 어려워 80줄이 더 싸다.
const ROW_H = 66;
const BAND_H = 12;

export const monthOf = (ymd: string) => ymd.slice(0, 7); // yyyy-MM
export const firstOfMonth = (ym: string) => `${ym}-01`;
export const lastOfMonth = (ym: string) => addDays(firstOfMonth(nextMonth(ym)), -1);
export const nextMonth = (ym: string) => {
  const [y, m] = ym.split('-').map(Number);
  return m === 12 ? `${y + 1}-01` : `${y}-${String(m + 1).padStart(2, '0')}`;
};
export const prevMonth = (ym: string) => {
  const [y, m] = ym.split('-').map(Number);
  return m === 1 ? `${y - 1}-12` : `${y}-${String(m - 1).padStart(2, '0')}`;
};

/** 일정이 걸치는 날짜 범위(yyyy-MM-dd). 종일·다일은 endAt 날짜까지, 시간 일정은 시작일 하루 */
export const eventDays = (e: Event) => {
  const s = parseIso(e.startAt).ymd;
  const en = e.endAt ? parseIso(e.endAt).ymd : s;
  return { from: s, to: e.allDay && en > s ? en : s };
};

export function Calendar({
  month,
  selected,
  events,
  onChangeMonth,
  onSelect,
}: {
  month: string; // yyyy-MM
  selected: string | null;
  events: Event[];
  onChangeMonth: (ym: string) => void;
  onSelect: (ymd: string) => void;
}) {
  const [y, m] = month.split('-').map(Number);
  const first = firstOfMonth(month);
  const lead = weekday(first);
  const last = lastOfMonth(month);
  const dayCount = Number(last.slice(8));
  const cells: (string | null)[] = [...Array(lead).fill(null), ...Array.from({ length: dayCount }, (_, i) => addDays(first, i))];
  while (cells.length % 7) cells.push(null);
  const weeks = Array.from({ length: cells.length / 7 }, (_, i) => cells.slice(i * 7, i * 7 + 7));

  // 점: 그 날에 걸치는 시간 일정. 밴드: 다일 종일 일정(주 단위로 잘라 그림)
  const { dots, bands } = useMemo(() => {
    const d = new Set<string>();
    const b: { title: string; from: string; to: string }[] = [];
    for (const e of events) {
      const { from, to } = eventDays(e);
      if (to > from) b.push({ title: e.title, from, to });
      else d.add(from);
    }
    return { dots: d, bands: b };
  }, [events]);

  return (
    <View style={st.box}>
      <View style={st.header}>
        <Pressable style={st.nav} onPress={() => onChangeMonth(prevMonth(month))} accessibilityRole="button" accessibilityLabel="이전 달">
          <Text style={st.navText}>‹</Text>
        </Pressable>
        <Text style={st.month}>{y}년 {m}월</Text>
        <Pressable style={st.nav} onPress={() => onChangeMonth(nextMonth(month))} accessibilityRole="button" accessibilityLabel="다음 달">
          <Text style={st.navText}>›</Text>
        </Pressable>
      </View>
      <View style={st.weekdays}>
        {['일', '월', '화', '수', '목', '금', '토'].map(d => (
          <Text key={d} style={st.weekday}>{d}</Text>
        ))}
      </View>
      {weeks.map((w, wi) => {
        const wFrom = w.find(Boolean) ?? '';
        const wTo = [...w].reverse().find(Boolean) ?? '';
        const firstIdx = w.findIndex(Boolean);
        const lastIdx = 6 - [...w].reverse().findIndex(Boolean);
        const wBands = bands
          .filter(b => b.from <= wTo && b.to >= wFrom)
          .map(b => ({ ...b, s: b.from < wFrom ? firstIdx : w.indexOf(b.from), e: b.to > wTo ? lastIdx : w.indexOf(b.to) }));
        return (
          <View key={wi} style={st.week}>
            {w.map((d, di) => {
              const on = d === selected;
              return (
                <Pressable key={di} style={st.day} onPress={() => d && onSelect(d)} disabled={!d} accessibilityRole="button" accessibilityLabel={d ?? undefined}>
                  {d && (
                    <>
                      <View style={[st.num, on && st.numOn]}>
                        <Text style={[st.numText, on && st.numTextOn]}>{Number(d.slice(8))}</Text>
                      </View>
                      {dots.has(d) && <View style={st.dot} />}
                    </>
                  )}
                </Pressable>
              );
            })}
            {wBands.map((b, bi) => (
              <View
                key={bi}
                pointerEvents="none"
                style={[st.band, { left: `${(b.s / 7) * 100}%`, width: `${((b.e - b.s + 1) / 7) * 100}%`, top: 18 + bi * (BAND_H + 2) }]}
              >
                <Text style={st.bandText} numberOfLines={1}>{b.title}</Text>
              </View>
            ))}
          </View>
        );
      })}
    </View>
  );
}

const st = StyleSheet.create({
  box: { borderWidth: 1, borderColor: C.line, borderRadius: 8, paddingBottom: 6, overflow: 'hidden' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingTop: 6, paddingHorizontal: 10 },
  nav: { padding: 10 },
  navText: { fontSize: 20, lineHeight: 20, color: C.text },
  month: { fontSize: 16, fontWeight: '700', color: C.text },
  weekdays: { flexDirection: 'row', paddingTop: 2, paddingBottom: 7 },
  weekday: { flex: 1, textAlign: 'center', fontSize: 11, color: C.sub },
  week: { flexDirection: 'row', paddingVertical: 3 },
  day: { flex: 1, height: ROW_H, alignItems: 'center', gap: 2 },
  num: { paddingHorizontal: 1, borderRadius: 2 },
  numOn: { backgroundColor: C.primary },
  numText: { fontSize: 12, color: C.text },
  numTextOn: { color: '#FFFFFF', fontWeight: '700' },
  dot: { width: 4, height: 4, borderRadius: 2, backgroundColor: C.primary },
  band: { position: 'absolute', height: BAND_H, paddingHorizontal: 3, paddingVertical: 1, borderRadius: 4, backgroundColor: C.primary, justifyContent: 'center' },
  bandText: { fontSize: 6, lineHeight: 9, color: '#FFFFFF' },
});
