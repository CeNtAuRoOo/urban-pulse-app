import type { FormGuard } from "../services/form-guard";
/** Native screen removal is handled by Expo's beforeRemove event. */
export function installBrowserNavigationGuard(_guard: FormGuard): () => void {
  return () => {};
}
