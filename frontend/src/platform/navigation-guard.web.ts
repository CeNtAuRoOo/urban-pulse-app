import type { FormGuard } from "../services/form-guard";

/** Expo restores web history with a root reset, which can bypass screen beforeRemove.
 * Keep Expo's history entries and IDs intact; cancel traversal before its popstate listener.
 * A cancelled traversal is reversed, not replaced/pushed, so Back/Forward keep their meaning.
 */
export function installBrowserNavigationGuard(guard: FormGuard): () => void {
  const key = "urbanpulseHistoryIndex", history = window.history;
  const push = history.pushState, replace = history.replaceState;
  let current = Number(history.state?.[key]) || 0;
  let restoring: { destination: number } | null = null;
  replace.call(history, { ...history.state, [key]: current }, "");
  history.pushState = function (state, unused, url) {
    push.call(this, { ...state, [key]: current + 1 }, unused, url);
    current++;
  };
  history.replaceState = function (state, unused, url) {
    replace.call(this, { ...state, [key]: current }, unused, url);
  };
  const pop = (event: PopStateEvent) => {
    const next = event.state?.[key];
    if (typeof next !== "number") return;
    if (restoring) {
      event.stopImmediatePropagation();
      if (next !== current) { history.go(current - next); return; }
      const { destination } = restoring;
      restoring = null;
      guard.request(() => history.go(destination - current));
      return;
    }
    if (next !== current && guard.changes.has()) {
      event.stopImmediatePropagation();
      restoring = { destination: next };
      history.go(current - next);
    } else current = next;
  };
  const beforeUnload = (event: BeforeUnloadEvent) => {
    if (!guard.changes.has()) return;
    event.preventDefault();
    event.returnValue = "";
  };
  window.addEventListener("popstate", pop, true);
  window.addEventListener("beforeunload", beforeUnload);
  return () => {
    history.pushState = push;
    history.replaceState = replace;
    window.removeEventListener("popstate", pop, true);
    window.removeEventListener("beforeunload", beforeUnload);
  };
}
