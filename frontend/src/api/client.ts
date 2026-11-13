import AsyncStorage from '@react-native-async-storage/async-storage';
import type { ApiErrorBody, RefreshRes } from './types';

// 로컬 백엔드로 바꿀 땐 이 한 줄만. (Render 무료 티어: 콜드 스타트 ~100초)
export const BASE_URL = 'https://team11-h10w.onrender.com/api/v1';
const DEFAULT_TIMEOUT_MS = 20_000;

export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public status: number,
    public details?: any,
  ) {
    super(message);
  }
}

// ---- 토큰 ----
type Tokens = { accessToken: string; refreshToken: string };
const KEY = 'sleepsync.tokens';
let tokens: Tokens | null = null;

export const getTokens = () => tokens;
export async function setTokens(t: Tokens | null) {
  tokens = t;
  if (t) await AsyncStorage.setItem(KEY, JSON.stringify(t));
  else await AsyncStorage.removeItem(KEY);
}
export async function loadTokens() {
  const raw = await AsyncStorage.getItem(KEY);
  tokens = raw ? (JSON.parse(raw) as Tokens) : null;
  return tokens;
}

// refresh까지 실패하면(세션 끝) 앱이 로그인 화면으로 보내도록 훅
let onSessionLost: () => void = () => {};
export const setOnSessionLost = (fn: () => void) => (onSessionLost = fn);

// ---- 요청 ----
type Opts = {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  auth?: boolean;
  timeoutMs?: number;
};

export async function request<T>(path: string, opts: Opts = {}): Promise<T> {
  const { method = 'GET', body, auth = true, timeoutMs = DEFAULT_TIMEOUT_MS } = opts;
  const res = await send(path, method, body, auth ? tokens?.accessToken : undefined, timeoutMs);
  if (res.status === 204) return undefined as T;
  const json = await res.json();
  if (!res.ok) {
    const err = json?.error as ApiErrorBody | undefined;
    // accessToken 만료 → refresh 한 번 → 같은 요청 재시도
    if (res.status === 401 && err?.code === 'AUTH_TOKEN_EXPIRED' && auth && tokens) {
      const ok = await refresh();
      if (ok) return request<T>(path, opts);
    }
    throw new ApiError(err?.code ?? 'UNKNOWN', err?.message ?? `HTTP ${res.status}`, res.status, err?.details);
  }
  return json.data as T;
}

async function send(path: string, method: string, body: unknown, bearer: string | undefined, timeoutMs: number) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    return await fetch(`${BASE_URL}${path}`, {
      method,
      headers: {
        ...(body !== undefined && { 'Content-Type': 'application/json' }),
        ...(bearer && { Authorization: `Bearer ${bearer}` }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: ctrl.signal,
    });
  } catch (e: any) {
    throw new ApiError(
      e?.name === 'AbortError' ? 'TIMEOUT' : 'NETWORK',
      e?.name === 'AbortError' ? '서버 응답이 없어요. 잠시 후 다시 시도해 주세요.' : '네트워크 연결을 확인해 주세요.',
      0,
    );
  } finally {
    clearTimeout(timer);
  }
}

// refreshToken은 회전 방식: 쓸 때마다 새 걸로 교체되고 옛 건 즉시 무효.
// 동시에 401이 여러 개 와도 refresh는 한 번만 — 두 번 쏘면 두 번째가 '이미 쓴 토큰'이라 전 세션이 끊긴다.
let refreshing: Promise<boolean> | null = null;
export function refresh(): Promise<boolean> {
  if (refreshing) return refreshing;
  refreshing = (async () => {
    const rt = tokens?.refreshToken;
    if (!rt) return false;
    try {
      const res = await send('/auth/refresh', 'POST', { refreshToken: rt }, undefined, DEFAULT_TIMEOUT_MS);
      const json = await res.json();
      if (!res.ok) throw new Error(json?.error?.code);
      const d = json.data as RefreshRes;
      await setTokens({ accessToken: d.accessToken, refreshToken: d.refreshToken });
      return true;
    } catch {
      await setTokens(null);
      onSessionLost();
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}
