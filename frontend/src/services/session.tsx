import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import { ApiError, request, type ApiResponse, type RequestOptions } from "./http";
import { readSession, saveSession, type Session } from "./storage";
import type { TokenDTO, User } from "../types/api";

interface SessionContextValue {
  session: Session | null;
  ready: boolean;
  notice: string;
  accept: (token: TokenDTO) => void;
  logout: (notice?: string) => void;
  refresh: () => Promise<void>;
  api: <T>(path: string, options?: RequestOptions) => Promise<ApiResponse<T>>;
}
const Context = createContext<SessionContextValue | null>(null);
const subscribeReady = () => () => {};
export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(readSession);
  const current = useRef<Session | null>(session);
  const ready = useSyncExternalStore(
    subscribeReady,
    () => true,
    () => false
  );
  const [notice, setNotice] = useState("");
  const update = useCallback((next: Session | null) => {
    current.current = next;
    saveSession(next);
    setSession(next);
  }, []);
  const logout = useCallback(
    (message = "Has cerrado la sesión.") => {
      update(null);
      setNotice(message);
    },
    [update]
  );
  useEffect(() => {
    if (!session) return;
    const expire = () => {
      if (current.current && current.current.expiresAt <= Date.now())
        logout("Tu sesión ha caducado. Inicia sesión de nuevo.");
    };
    const timer = setTimeout(expire, Math.min(Math.max(0, session.expiresAt - Date.now()), 2147483647));
    window.addEventListener("focus", expire);
    return () => {
      clearTimeout(timer);
      window.removeEventListener("focus", expire);
    };
  }, [session, logout]);
  const api = useCallback(
    async <T,>(path: string, options?: RequestOptions) => {
      const snapshot = current.current;
      if (!snapshot || snapshot.expiresAt <= Date.now()) {
        logout("Tu sesión ha caducado. Inicia sesión de nuevo.");
        throw new ApiError(401, "SESSION_EXPIRED");
      }
      try {
        return await request<T>(path, options, snapshot.accessToken);
      } catch (error) {
        if (
          error instanceof ApiError &&
          error.status === 401 &&
          current.current?.accessToken === snapshot.accessToken
        )
          logout("La sesión ya no es válida. Inicia sesión de nuevo.");
        throw error;
      }
    },
    [logout]
  );
  const refresh = useCallback(async () => {
    const snapshot = current.current;
    const response = await api<User>("/users/me");
    if (snapshot && current.current === snapshot) update({ ...snapshot, user: response.data });
  }, [api, update]);
  const accept = useCallback(
    (token: TokenDTO) => {
      setNotice("");
      update({ ...token, expiresAt: Date.now() + token.expiresIn * 1000 });
    },
    [update]
  );
  return (
    <Context.Provider value={{ session, ready, notice, accept, logout, refresh, api }}>
      {children}
    </Context.Provider>
  );
}
export function useSession() {
  const value = useContext(Context);
  if (!value) throw new Error("SessionProvider requerido");
  return value;
}
