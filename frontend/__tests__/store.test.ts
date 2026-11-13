import { parseHM, sleepMin, summarize } from '../src/store';

test('parseHM', () => {
  expect(parseHM('09:05')).toBe(545);
  expect(parseHM('9:05')).toBe(545);
  expect(parseHM('24:00')).toBeNull();
  expect(parseHM('9시')).toBeNull();
});

test('자정 넘긴 수면 시간', () => {
  expect(sleepMin({ date: 'x', bed: 23 * 60 + 30, wake: 7 * 60 })).toBe(450);
});

test('summarize: 평균 취침은 자정 전후를 이어서 계산', () => {
  const s = summarize([
    { date: 'a', bed: 23 * 60 + 30, wake: 7 * 60 }, // 23:30
    { date: 'b', bed: 30, wake: 8 * 60 }, // 00:30
  ])!;
  expect(s.bed).toBe(0); // 00:00 (12:00이 아님)
  expect(s.avg).toBe(450);
  expect(s.regular).toBe(100);
  expect(summarize([])).toBeNull();
});
