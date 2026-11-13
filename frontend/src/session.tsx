import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { getTokens, loadTokens, setOnSessionLost } from './api/client';
import * as api from './api/endpoints';
import type { Me } from './api/types';

// 전역 상태는 이것뿐: 토큰(client.ts) + 로그인한 사용자.
type Session = {
  me: Me | null;
  setMe: (m: Me | null) => void;
  /** 저장된 토큰으로 /users/me 복원. 없거나 실패하면 null */
  restore: () => Promise<Me | null>;
  logout: () => Promise<void>;
};

const Ctx = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);

  useEffect(() => {
    // refresh까지 실패(세션 만료·탈취 감지)하면 로그아웃 상태로. 화면 전환은 navigation이 me===null을 보고 한다.
    setOnSessionLost(() => setMe(null));
  }, []);

  const value = useMemo<Session>(
    () => ({
      me,
      setMe,
      restore: async () => {
        const t = await loadTokens();
        if (!t) return null;
        try {
          const m = await api.getMe();
          setMe(m);
          return m;
        } catch {
          return null;
        }
      },
      logout: async () => {
        const rt = getTokens()?.refreshToken;
        if (rt) await api.logout(rt).catch(() => {});
        setMe(null);
      },
    }),
    [me],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSession() {
  const s = useContext(Ctx);
  if (!s) throw new Error('SessionProvider 안에서만 사용');
  return s;
}
