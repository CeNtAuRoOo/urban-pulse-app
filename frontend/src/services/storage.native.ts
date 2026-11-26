import * as SecureStore from "expo-secure-store";
import type { TokenDTO } from "../types/api";
export interface Session extends TokenDTO {
  expiresAt: number;
}
const key = "urbanpulse.session.v1";
let writes = Promise.resolve();
export async function readSession(): Promise<Session | null> {
  try {
    const raw = await SecureStore.getItemAsync(key);
    if (!raw) return null;
    const value = JSON.parse(raw) as Session;
    if (!value.accessToken || !value.user?.id || !(value.expiresAt > Date.now())) {
      await SecureStore.deleteItemAsync(key);
      return null;
    }
    return value;
  } catch {
    return null;
  }
}
export function saveSession(value: Session | null) {
  writes = writes
    .then(async () => {
      if (value) await SecureStore.setItemAsync(key, JSON.stringify(value));
      else await SecureStore.deleteItemAsync(key);
    })
    .catch(() => {
      /* Continúa en memoria si el almacén seguro no está disponible. */
    });
  return writes;
}
