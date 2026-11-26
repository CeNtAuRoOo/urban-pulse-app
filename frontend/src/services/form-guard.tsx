import { createContext, useContext, useEffect, useId } from "react";
import { useNavigation } from "expo-router";
import type { UnsavedChanges } from "./unsaved-changes";

export type FormGuard = {
  changes: UnsavedChanges;
  request: (action: () => void, id?: string) => void;
  save: (action: () => Promise<void>, id: string) => void;
};
export const FormGuardContext = createContext<FormGuard | null>(null);
export function useFormNavigation() {
  const guard = useContext(FormGuardContext);
  if (!guard) throw new Error("FormGuardProvider is required");
  return guard;
}
export function useUnsavedChanges(dirty: boolean) {
  const guard = useFormNavigation(),
    id = useId(),
    navigation = useNavigation();
  useEffect(() => {
    guard.changes.update(id, dirty);
    return () => guard.changes.clear(id);
  }, [guard, id, dirty]);
  useEffect(
    () =>
      navigation.addListener("beforeRemove", (event) => {
        if (!guard.changes.has()) return;
        event.preventDefault();
        guard.request(() => navigation.dispatch(event.data.action));
      }),
    [navigation, guard]
  );
  return {
    discard: (action: () => void) => guard.request(action, id),
    saved: () => guard.changes.clear(id),
    beforeSave: (action: () => Promise<void>) => guard.save(action, id),
  };
}
