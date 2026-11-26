import type { TokenDTO } from "../types/api";
export interface Session extends TokenDTO {
  expiresAt: number;
}
const key = "urbanpulse.session.v1";
export function readSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(key);
    if (!raw) return null;
    const value = JSON.parse(raw) as Session;
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
export function saveSession(value: Session | null) {
  try {
    if (value) sessionStorage.setItem(key, JSON.stringify(value));
    else sessionStorage.removeItem(key);
  } catch {
    /* La sesión continúa solo en memoria si el navegador bloquea el almacenamiento. */
  }
}
