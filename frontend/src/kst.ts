// 서버는 모든 시각을 +09:00 ISO로 주고받는다. 기기 타임존과 무관하게 KST 문자열을 직접 조립·파싱한다.
// toISOString()은 Z로 나가므로 쓰지 않는다.

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

const p2 = (n: number) => String(n).padStart(2, '0');

/** 분(0~1439) → "HH:mm" */
export const fmtHM = (min: number) => {
  const v = ((min % 1440) + 1440) % 1440;
  return `${p2(Math.floor(v / 60))}:${p2(v % 60)}`;
};

/** "HH:mm" 또는 "HH:mm:ss" → 분. 형식이 틀리면 null */
export const parseHM = (s: string): number | null => {
  const m = /^(\d{1,2}):(\d{2})(?::\d{2})?$/.exec(s.trim());
  if (!m || +m[1] > 23 || +m[2] > 59) return null;
  return +m[1] * 60 + +m[2];
};

/** 분 → "7시간 30분" / "45분" */
export const fmtDuration = (min: number) => {
  const h = Math.floor(min / 60);
  const m = min % 60;
  if (!h) return `${m}분`;
  return m ? `${h}시간 ${m}분` : `${h}시간`;
};

/** 오늘(KST) yyyy-MM-dd */
export const todayKst = () => ymdFromDate(new Date(Date.now() + KST_OFFSET_MS), true);

/** Date → yyyy-MM-dd. utc=true면 UTC 파트를 읽는다(KST 보정된 Date용) */
export const ymdFromDate = (d: Date, utc = false) =>
  utc
    ? `${d.getUTCFullYear()}-${p2(d.getUTCMonth() + 1)}-${p2(d.getUTCDate())}`
    : `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}`;

/** yyyy-MM-dd ± n일 (달력 계산은 UTC로 해서 DST 영향 없음) */
export const addDays = (ymd: string, n: number) => {
  const [y, m, d] = ymd.split('-').map(Number);
  return ymdFromDate(new Date(Date.UTC(y, m - 1, d + n)), true);
};

/** yyyy-MM-dd → 요일 0=일 … 6=토 */
export const weekday = (ymd: string) => {
  const [y, m, d] = ymd.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).getUTCDay();
};
export const WEEKDAY_KO = ['일', '월', '화', '수', '목', '금', '토'] as const;

/** (yyyy-MM-dd, 분) → "yyyy-MM-ddTHH:mm:00+09:00". 분이 1440을 넘으면 다음 날로 넘긴다 */
export const kstIso = (ymd: string, min: number) => {
  const day = Math.floor(min / 1440);
  const date = day ? addDays(ymd, day) : ymd;
  return `${date}T${fmtHM(min)}:00+09:00`;
};

/** 절대 시각(Date, epoch 기준) → "+09:00" ISO. HealthKit 등 외부 절대 시각을 보낼 때만 쓴다 */
export const kstIsoFromDate = (d: Date) => {
  const k = new Date(d.getTime() + KST_OFFSET_MS);
  return `${ymdFromDate(k, true)}T${p2(k.getUTCHours())}:${p2(k.getUTCMinutes())}:${p2(k.getUTCSeconds())}+09:00`;
};

/** "+09:00" ISO → { ymd, min }. 오프셋 파트는 무시하고 로컬 파트만 읽는다(서버가 항상 KST로 주므로) */
export const parseIso = (iso: string) => {
  const m = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})/.exec(iso);
  if (!m) throw new Error(`KST ISO 형식 아님: ${iso}`);
  return { ymd: m[1], min: +m[2] * 60 + +m[3] };
};

/** "11월 14일 (토)" */
export const fmtDateKo = (ymd: string, withWeekday = true) => {
  const [, m, d] = ymd.split('-').map(Number);
  return withWeekday ? `${m}월 ${d}일 (${WEEKDAY_KO[weekday(ymd)]})` : `${m}월 ${d}일`;
};
