import { request, setTokens } from './client';
import type * as T from './types';

const q = (params: Record<string, string | number | undefined>) => {
  const s = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&');
  return s ? `?${s}` : '';
};

// App config — 앱 시작 시 1회. 콜드 스타트 워밍업 겸해서 타임아웃 길게.
export const getAppConfig = (timeoutMs = 120_000) =>
  request<T.AppConfig>('/app/config', { auth: false, timeoutMs });

// Auth
export const signup = (body: { name: string; loginId: string; password: string }) =>
  request<{ userId: number; loginId: string; name: string }>('/auth/signup', { method: 'POST', body, auth: false });
export const login = async (body: { loginId: string; password: string }) => {
  const d = await request<T.LoginRes>('/auth/login', { method: 'POST', body, auth: false });
  await setTokens({ accessToken: d.accessToken, refreshToken: d.refreshToken });
  return d;
};
export const logout = async (refreshToken: string) => {
  try {
    await request<void>('/auth/logout', { method: 'POST', body: { refreshToken } });
  } finally {
    await setTokens(null);
  }
};
export const changePassword = (body: { currentPassword: string; newPassword: string; refreshToken?: string }) =>
  request<void>('/auth/password', { method: 'PUT', body });

// Users
export const getMe = () => request<T.Me>('/users/me');
export const updateName = (name: string) => request<T.Me>('/users/me', { method: 'PUT', body: { name } });
export const submitOnboarding = (body: T.OnboardingReq) =>
  request<T.Me>('/users/me/onboarding', { method: 'POST', body });
export const getSettings = () => request<T.Settings>('/users/me/settings');
export const updateSettings = (body: Partial<T.Settings>) =>
  request<T.Settings>('/users/me/settings', { method: 'PUT', body });
export const getConsents = () => request<{ consents: T.Consent[] }>('/users/me/consents');
export const updateConsents = (consents: { type: T.ConsentType; agreed: boolean }[]) =>
  request<{ consents: T.Consent[] }>('/users/me/consents', { method: 'PUT', body: { consents } });
export const getNotificationSettings = () => request<T.NotificationSettings>('/users/me/notification-settings');
export const updateNotificationSettings = (body: T.NotificationSettings) =>
  request<T.NotificationSettings>('/users/me/notification-settings', { method: 'PUT', body });
export const deleteMe = async (password: string) => {
  await request<void>('/users/me', { method: 'DELETE', body: { password } });
  await setTokens(null);
};

// Timetable
export const getSemesters = () => request<{ semesters: T.Semester[] }>('/semesters');
export const getTimetable = (semester?: string) =>
  request<{ semester: string; entries: T.TimetableEntry[] }>(`/timetable${q({ semester })}`);
export const createEntry = (body: T.TimetableEntryReq) =>
  request<T.TimetableEntry>('/timetable/entries', { method: 'POST', body });
export const updateEntry = (id: number, body: T.TimetableEntryReq) =>
  request<T.TimetableEntry>(`/timetable/entries/${id}`, { method: 'PUT', body });
export const deleteEntry = (id: number) => request<void>(`/timetable/entries/${id}`, { method: 'DELETE' });
export const importEverytime = (url: string, semester?: string) =>
  request<{ semester: string; entries: T.TimetableEntry[] }>(`/timetable/import/everytime${q({ semester })}`, {
    method: 'POST',
    body: { url },
    timeoutMs: 60_000,
  });

// Events
export const getEvents = (from: string, to: string) => request<{ events: T.Event[] }>(`/events${q({ from, to })}`);
export const getEvent = (id: number) => request<T.Event>(`/events/${id}`);
export const createEvent = (body: T.EventReq) => request<T.Event>('/events', { method: 'POST', body });
export const updateEvent = (id: number, body: T.EventReq) =>
  request<T.Event>(`/events/${id}`, { method: 'PUT', body });
export const deleteEvent = (id: number) => request<void>(`/events/${id}`, { method: 'DELETE' });

// Sleep logs
export const getSleepLogs = (from: string, to: string) =>
  request<{ logs: T.SleepLog[] }>(`/sleep-logs${q({ from, to })}`);
export const getSleepLog = (id: number) => request<T.SleepLog>(`/sleep-logs/${id}`);
export const createSleepLog = (body: T.SleepLogReq) => request<T.SleepLog>('/sleep-logs', { method: 'POST', body });
export const updateSleepLog = (id: number, body: T.SleepLogReq) =>
  request<T.SleepLog>(`/sleep-logs/${id}`, { method: 'PUT', body });
export const deleteSleepLog = (id: number) => request<void>(`/sleep-logs/${id}`, { method: 'DELETE' });
export const importHealthKitSleep = (nights: { bedAt: string; wakeAt: string }[]) =>
  request<T.SleepLogImportResult>('/sleep-logs/import', { method: 'POST', body: { nights } });

// Reports
export const getReport = (period: T.ReportPeriod, date?: string) =>
  request<T.Report>(`/reports${q({ period, date })}`);

// Plans — FOCUS/EXAM은 AI 대기라 타임아웃 넉넉히
export const getPlanToday = (mode: T.PlanMode) =>
  request<T.Plan>(`/plans/today${q({ mode })}`, { timeoutMs: 30_000 });
export const regeneratePlan = (mode: T.PlanMode, body?: T.ExamPlanReq) =>
  request<T.Plan>(`/plans/today${q({ mode })}`, { method: 'POST', body, timeoutMs: 30_000 });
