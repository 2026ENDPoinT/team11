import { useCallback, useEffect, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import { ApiError } from './api/client';

// 조회 훅 하나로 전 화면을 덮는다. 화면이 포커스될 때마다 다시 불러온다.
// ponytail: 캐시 없음. 같은 데이터를 여러 화면이 자주 쓰게 되면 React Query로.
export function useApi<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(fn, deps);
  const reload = useCallback(() => {
    let alive = true;
    setLoading(true);
    load()
      .then(d => alive && (setData(d), setError(null)))
      .catch(e => alive && setError(e))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [load]);

  useFocusEffect(reload);
  return { data, error, loading, reload, setData };
}

// 화면 밖(모달 등)에서 포커스 이펙트가 없을 때
export function useApiOnce<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let alive = true;
    setLoading(true);
    fn()
      .then(d => alive && (setData(d), setError(null)))
      .catch(e => alive && setError(e))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return { data, error, loading, setData };
}

export const errMsg = (e: unknown) => (e instanceof ApiError ? e.message : '문제가 생겼어요. 다시 시도해 주세요.');
