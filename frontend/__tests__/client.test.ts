import { ApiError, request, setTokens } from '../src/api/client';

const ok = (data: unknown, status = 200) =>
  ({ ok: status < 400, status, json: async () => ({ data, error: null }) }) as Response;
const fail = (status: number, code: string) =>
  ({ ok: false, status, json: async () => ({ data: null, error: { code, message: code } }) }) as Response;

let calls: { url: string; body?: any; auth?: string }[];
beforeEach(() => {
  calls = [];
  (globalThis as any).fetch = jest.fn(async (url: string, init: RequestInit) => {
    const body = init.body ? JSON.parse(init.body as string) : undefined;
    const auth = (init.headers as any)?.Authorization;
    calls.push({ url, body, auth });
    return (globalThis as any).__respond(url, body, auth);
  }) as any;
});

test('envelope 해체 + 실패는 ApiError', async () => {
  await setTokens(null);
  (globalThis as any).__respond = () => ok({ x: 1 });
  expect(await request('/a', { auth: false })).toEqual({ x: 1 });
  (globalThis as any).__respond = () => fail(409, 'USER_DUPLICATE_LOGIN_ID');
  await expect(request('/b', { auth: false })).rejects.toMatchObject({ code: 'USER_DUPLICATE_LOGIN_ID', status: 409 });
});

test('401 만료 → refresh 한 번(single-flight) → 새 토큰으로 재시도, refreshToken 회전 저장', async () => {
  await setTokens({ accessToken: 'old', refreshToken: 'r1' });
  let refreshCount = 0;
  (globalThis as any).__respond = (url: string, body: any, auth: string) => {
    if (url.endsWith('/auth/refresh')) {
      refreshCount++;
      expect(body.refreshToken).toBe('r1');
      return ok({ accessToken: 'new', refreshToken: 'r2', expiresIn: 1800 });
    }
    return auth === 'Bearer new' ? ok({ me: true }) : fail(401, 'AUTH_TOKEN_EXPIRED');
  };
  // 동시 요청 3개가 모두 만료 → refresh는 정확히 1회
  const results = await Promise.all([request('/me'), request('/me'), request('/me')]);
  expect(results).toEqual([{ me: true }, { me: true }, { me: true }]);
  expect(refreshCount).toBe(1);
  const { getTokens } = require('../src/api/client');
  expect(getTokens()).toEqual({ accessToken: 'new', refreshToken: 'r2' });
});

test('refresh 실패 → 토큰 삭제 + 세션 종료 콜백', async () => {
  const { setOnSessionLost, getTokens } = require('../src/api/client');
  const lost = jest.fn();
  setOnSessionLost(lost);
  await setTokens({ accessToken: 'old', refreshToken: 'used' });
  (globalThis as any).__respond = (url: string) =>
    url.endsWith('/auth/refresh') ? fail(401, 'AUTH_TOKEN_INVALID') : fail(401, 'AUTH_TOKEN_EXPIRED');
  await expect(request('/me')).rejects.toBeInstanceOf(ApiError);
  expect(lost).toHaveBeenCalled();
  expect(getTokens()).toBeNull();
});
