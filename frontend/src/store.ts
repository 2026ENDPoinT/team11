import { useSyncExternalStore } from 'react';

// ponytail: 앱 메모리에만 저장(재시작하면 초기화). 서버/AsyncStorage 정해지면 여기만 바꾸면 됨

export type Klass = {
  id: string;
  name: string;
  room: string;
  day: number; // 0=월 … 4=금
  start: number; // 분 (09:00 → 540)
  end: number;
};

export type EventType = '과제' | '시험' | '약속' | '기타';
export type Ev = {
  id: string;
  title: string;
  type: EventType;
  date: string; // YYYY-MM-DD
  endDate?: string; // 기간 일정(예: 수업철회기간)
  start?: number;
  end?: number;
};

export type Mood = 'happy' | 'neutral' | 'sad';
export type SleepRecord = {
  date: string; // 일어난 날 YYYY-MM-DD
  bed: number; // 분
  wake: number;
  mood?: Mood;
  memo?: string;
};
export const sleepMin = (r: SleepRecord) => (r.wake - r.bed + 1440) % 1440;

const h = (hh: number, mm = 0) => hh * 60 + mm;

let state = {
  user: { name: '마스터', id: 'master22' },
  classes: [
    { id: 'c1', name: '자료구조', room: '미래관 301', day: 0, start: h(9), end: h(10, 30) },
    { id: 'c2', name: '자료구조', room: '미래관 301', day: 2, start: h(9), end: h(10, 30) },
    { id: 'c3', name: '운영체제', room: '창학관 204', day: 1, start: h(10), end: h(11, 30) },
    { id: 'c4', name: '운영체제', room: '창학관 204', day: 3, start: h(10), end: h(11, 30) },
    { id: 'c5', name: '선형대수', room: '다산관 110', day: 3, start: h(12), end: h(13, 30) },
    { id: 'c6', name: '캡스톤', room: '미래관 520', day: 1, start: h(14), end: h(17) },
    { id: 'c7', name: '영어회화', room: '다솔관 105', day: 4, start: h(14), end: h(16) },
  ] as Klass[],
  events: [
    { id: 'e1', title: '수업철회기간', type: '기타', date: '2026-11-02', endDate: '2026-11-04' },
    { id: 'e2', title: '자료구조 과제', type: '과제', date: '2026-11-09' },
    { id: 'e3', title: '운영체제 퀴즈', type: '시험', date: '2026-11-11' },
    { id: 'e4', title: '팀 회의', type: '약속', date: '2026-11-13' },
    { id: 'e5', title: '선형대수 중간', type: '시험', date: '2026-11-16' },
    { id: 'e6', title: '캡스톤 발표', type: '과제', date: '2026-11-18' },
  ] as Ev[],
  records: [
    { date: '2026-11-09', bed: h(1, 30), wake: h(8), mood: 'happy' },
    { date: '2026-11-08', bed: h(0, 20), wake: h(8, 30), mood: 'sad' },
    { date: '2026-11-07', bed: h(0, 50), wake: h(9, 30), mood: 'neutral' },
    { date: '2026-11-06', bed: h(0), wake: h(7), mood: 'happy' },
    { date: '2026-11-05', bed: h(2, 40), wake: h(7, 30), mood: 'happy' },
    { date: '2026-11-04', bed: h(1), wake: h(7, 20), mood: 'happy' },
    { date: '2026-11-03', bed: h(2, 20), wake: h(7, 30), mood: 'sad' },
  ] as SleepRecord[],
};

const listeners = new Set<() => void>();
const set = (patch: Partial<typeof state>) => {
  state = { ...state, ...patch };
  listeners.forEach(l => l());
};

export const useStore = () =>
  useSyncExternalStore(
    l => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => state,
  );

const newId = () => Math.random().toString(36).slice(2);

export const saveClass = (c: Omit<Klass, 'id'> & { id?: string }) =>
  set({
    classes: c.id
      ? state.classes.map(x => (x.id === c.id ? { ...x, ...c } as Klass : x))
      : [...state.classes, { ...c, id: newId() }],
  });
export const deleteClass = (id: string) =>
  set({ classes: state.classes.filter(c => c.id !== id) });
export const addEvent = (e: Omit<Ev, 'id'>) =>
  set({ events: [...state.events, { ...e, id: newId() }] });
export const updateEvent = (id: string, patch: Partial<Ev>) =>
  set({ events: state.events.map(e => (e.id === id ? { ...e, ...patch } : e)) });
export const deleteEvent = (id: string) =>
  set({ events: state.events.filter(e => e.id !== id) });
export const setUser = (user: { name: string; id: string }) => set({ user });
export const saveRecord =(r: SleepRecord) =>
  set({ records: [...state.records.filter(x => x.date !== r.date), r] });

// 평균 수면, 평균 취침(자정 넘김 고려), 규칙성(평균 취침 ±1시간 안에 잔 날 비율)
export function summarize(list: SleepRecord[]) {
  if (!list.length) return null;
  const avg = list.reduce((s, r) => s + sleepMin(r), 0) / list.length;
  const beds = list.map(r => (r.bed < 12 * 60 ? r.bed + 1440 : r.bed)); // 새벽은 +24h
  const bedAvg = beds.reduce((s, b) => s + b, 0) / beds.length;
  const regular = Math.round((beds.filter(b => Math.abs(b - bedAvg) <= 60).length / beds.length) * 100);
  return { avg, bed: Math.round(bedAvg) % 1440, regular };
}

// "09:05" → 545, 형식이 틀리면 null
export const parseHM = (s: string) => {
  const m = /^(\d{1,2}):(\d{2})$/.exec(s.trim());
  if (!m || +m[1] > 23 || +m[2] > 59) return null;
  return +m[1] * 60 + +m[2];
};
export const fmtHM = (m: number) =>
  `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
export const ymd = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
