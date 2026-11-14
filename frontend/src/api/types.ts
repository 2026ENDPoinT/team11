// SleepSync API 레퍼런스(백엔드 v2, 2026-11-14)의 응답/요청 모양 그대로.

export type ConsentType = 'MEDICAL_DISCLAIMER' | 'TERMS' | 'PRIVACY' | 'NOTIFICATION';
export type EventType = 'ASSIGNMENT' | 'EXAM' | 'PERSONAL' | 'ETC';
export type DayOfWeek = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN';
export type PlanMode = 'NORMAL' | 'FOCUS' | 'EXAM';
export type PlanStatus = 'READY' | 'BASELINE' | 'TRADEOFF' | 'NO_FEASIBLE_PLAN';
export type PreparationState =
  | 'STARTING'
  | 'STARTING_HEAVY'
  | 'SEEN_NOT_RECALLABLE'
  | 'PATCHING_GAPS'
  | 'FINAL_REVIEW';
export type ExamNature = 'RECALL' | 'MIXED' | 'PROBLEM_SOLVING';
export type ReportPeriod = 'WEEK' | 'MONTH';

export type ApiErrorBody = { code: string; message: string; details?: any };

// Auth
export type LoginRes = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: { userId: number; name: string; onboardingDone: boolean };
};
export type RefreshRes = { accessToken: string; refreshToken: string; expiresIn: number };

// Users
export type Settings = {
  sleepNeedMin: number;
  sleepLatencyMin: number;
  commuteMin: number;
  prepMin: number;
  bufferMin: number;
  minSleepMin: number;
  examPrepDays: number;
  focusMinInBedMin: number;
  focusMaxInBedMin: number;
};
export type Me = {
  userId: number;
  loginId: string;
  name: string;
  onboardingDone: boolean;
  settings: Settings;
};
export type OnboardingReq = {
  consents: { type: ConsentType; agreed: boolean }[];
  sleepNeedMin: number;
  sleepLatencyMin?: number;
  commuteMin: number;
  prepMin: number;
  examPrepDays?: number;
  notificationAllowed?: boolean;
};
export type Consent = { type: ConsentType; required: boolean; agreed: boolean; agreedAt: string | null };
export type NotificationSettings = {
  wakeAlarmEnabled: boolean;
  bedtimeAlarmEnabled: boolean;
  bedtimeLeadMin: 15 | 30 | 60;
};

// Timetable
export type Semester = { semester: string; startDate: string; endDate: string; current: boolean };
export type TimetableEntry = {
  entryId: number;
  semester: string;
  dayOfWeek: DayOfWeek;
  startTime: string; // "HH:mm:ss"
  endTime: string;
  courseName: string;
  room: string | null;
  source: 'MANUAL' | 'EVERYTIME';
};
export type TimetableEntryReq = {
  semester: string;
  dayOfWeek: DayOfWeek;
  startTime: string; // "HH:mm"
  endTime: string;
  courseName: string;
  room?: string;
};

// Events
export type Event = {
  eventId: number;
  title: string;
  type: EventType;
  startAt: string;
  endAt: string | null;
  allDay: boolean;
};
export type EventReq = Omit<Event, 'eventId' | 'endAt' | 'allDay'> & { endAt?: string; allDay?: boolean };

// Sleep logs
export type SleepLog = {
  sleepLogId: number;
  date: string; // KST 기상일
  bedAt: string;
  wakeAt: string;
  durationMin: number;
  quality: 1 | 2 | 3 | null;
  memo: string | null;
};
export type SleepLogReq = { bedAt: string; wakeAt: string; quality?: 1 | 2 | 3; memo?: string };
export type SleepLogImportResult = { imported: number; skipped: number; invalid: number[] };

// Reports
export type Report = {
  period: ReportPeriod;
  from: string;
  to: string;
  loggedDays: number;
  avgSleepMin: number | null;
  avgBedTime: string | null; // "HH:mm"
  regularityPct: number | null;
  daily: { date: string; sleepLogId: number | null; sleepMin: number | null; quality: 1 | 2 | 3 | null }[];
};

// Plans
export type Plan = {
  planId: number;
  date: string;
  mode: PlanMode;
  status: PlanStatus;
  bedAt: string | null;
  wakeAt: string | null;
  sleepMin: number | null;
  basisText: string | null;
  generatedBy: 'AI' | 'RULE';
  modelVersion: string | null;
  reasons: string[];
  warnings: string[];
};
export type ExamPlanReq = { eventId?: number; preparationState: PreparationState; examNature: ExamNature };

// App config
export type AppConfig = {
  apiVersion: string;
  featureFlags: { focusMode: boolean; examMode: boolean; everytimeImport: boolean };
};
