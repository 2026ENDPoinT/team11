import { addDays, fmtHM, kstIso, parseHM, parseIso, weekday } from '../src/kst';

test('KST ISO 왕복', () => {
  expect(kstIso('2026-11-12', 23 * 60 + 30)).toBe('2026-11-12T23:30:00+09:00');
  expect(parseIso('2026-11-13T07:00:00+09:00')).toEqual({ ymd: '2026-11-13', min: 7 * 60 });
  // 1440 넘으면 다음 날
  expect(kstIso('2026-11-12', 1440 + 90)).toBe('2026-11-13T01:30:00+09:00');
  // 서버가 Z로 준 값도 로컬 파트만 읽는다
  expect(parseIso('2026-11-10T09:00:00Z').min).toBe(540);
});

test('날짜 산술', () => {
  expect(addDays('2026-11-30', 1)).toBe('2026-12-01');
  expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  expect(weekday('2026-11-14')).toBe(6); // 토
  expect(fmtHM(545)).toBe('09:05');
  expect(parseHM('9:05')).toBe(545);
  expect(parseHM('09:05:00')).toBe(545);
  expect(parseHM('24:00')).toBeNull();
});
