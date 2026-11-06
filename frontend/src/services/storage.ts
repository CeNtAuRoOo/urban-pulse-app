import type { TokenDTO } from "../types/api";
export interface Session extends TokenDTO {
  expiresAt: number;
}
const key = "urbanpulse.demo.session";
// This desktop demo persists credentials only for the current browser tab.
export function readSession(): Session | null {
  try {
    const stored = sessionStorage.getItem(key);
    if (!stored) return null;
    const value = JSON.parse(stored) as Session;
    if (
      !value.accessToken ||
      !value.user?.id ||
      !Array.isArray(value.user.roles) ||
      !(value.expiresAt > Date.now())
    ) {
      sessionStorage.removeItem(key);
      return null;
    }
    return value;
  } catch {
    return null;
  }
}
export function saveSession(session: Session | null) {
  try {
    if (session) sessionStorage.setItem(key, JSON.stringify(session));
    else sessionStorage.removeItem(key);
  } catch {
    /* Memory-only session when storage is unavailable. */
  }
}
