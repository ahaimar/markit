"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiFailure } from "@/lib/types";

interface ApiState<T> {
  data: T | null;
  error: ApiFailure | null;
  loading: boolean;
  reload: () => void;
}

export function useApi<T>(fetcher: () => Promise<T>, deps: readonly unknown[] = []): ApiState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiFailure | null>(null);
  const [attempt, setAttempt] = useState(0);
  const fetcherRef = useRef(fetcher);
  const depsKey = JSON.stringify(deps);

  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  useEffect(() => {
    let cancelled = false;
    fetcherRef
      .current()
      .then((result) => {
        if (!cancelled) {
          setError(null);
          setData(result);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setData(null);
          setError(err instanceof ApiFailure ? err : new ApiFailure(0, undefined, String(err)));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [attempt, depsKey]);

  const reload = useCallback(() => setAttempt((a) => a + 1), []);

  return { data, error, loading: data === null && error === null, reload };
}