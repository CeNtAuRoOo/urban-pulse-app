import { useCallback, useEffect, useRef, useState } from "react";
import { useSession } from "./session";
import type { ApiResponse, Query } from "./http";

export function useResource<T>(path: string, query?: Query) {
  const { api } = useSession();
  const [revision, setRevision] = useState(0);
  const serialized = JSON.stringify(query || {});
  const key = `${path}:${serialized}:${revision}`;
  const [state, setState] = useState<{ key: string; result?: ApiResponse<T>; error?: unknown }>();
  useEffect(() => {
    const controller = new AbortController();
    api<T>(path, { query: JSON.parse(serialized), signal: controller.signal })
      .then((result) => {
        if (!controller.signal.aborted) setState({ key, result });
      })
      .catch((error) => {
        if (!controller.signal.aborted) setState({ key, error });
      });
    return () => controller.abort();
  }, [api, path, serialized, key]);
  const reload = useCallback(() => setRevision((value) => value + 1), []);
  return {
    result: state?.key === key ? state.result : undefined,
    error: state?.key === key ? state.error : undefined,
    loading: state?.key !== key,
    reload,
    setResult: (result: ApiResponse<T>) => setState({ key, result }),
  };
}
export function useAction() {
  const lock = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const [success, setSuccess] = useState("");
  async function run(action: () => Promise<void>) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError(undefined);
    setSuccess("");
    try {
      await action();
    } catch (reason) {
      setError(reason);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  return { busy, error, success, setSuccess, run };
}
