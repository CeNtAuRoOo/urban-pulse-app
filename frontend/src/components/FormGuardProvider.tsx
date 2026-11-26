import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { installBrowserNavigationGuard } from "../platform/navigation-guard";
import { FormGuardContext } from "../services/form-guard";
import { UnsavedChanges } from "../services/unsaved-changes";
import { ConfirmationDialog } from "./ui";
import { Modal, View, ActivityIndicator, Text } from "react-native";

export function FormGuardProvider({ children }: { children: ReactNode }) {
  const [changes] = useState(() => new UnsavedChanges());
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savePrompt, setSavePrompt] = useState(false);
  const savingLock = useRef(false);
  const pending = useRef<{ action: () => void; id?: string; save?: boolean } | null>(null);
  const guard = useMemo(
    () => ({
      changes,
      request: (action: () => void, id?: string) => {
        if (savingLock.current) return;
        if (!changes.has(id)) {
          action();
          return;
        }
        if (pending.current) return;
        pending.current = { action, id };
        setSavePrompt(false);
        setOpen(true);
      },
      save: (action: () => Promise<void>, id: string) => {
        if (savingLock.current || pending.current) return;
        const execute = async () => {
          savingLock.current = true;
          setSaving(true);
          try {
            await action();
          } finally {
            savingLock.current = false;
            setSaving(false);
          }
        };
        if (!changes.hasOther(id)) {
          void execute();
          return;
        }
        // Keep every dirty registration until the write succeeds and its owner resets the forms.
        pending.current = {
          action: () => {
            void execute();
          },
          save: true,
        };
        setSavePrompt(true);
        setOpen(true);
      },
    }),
    [changes]
  );
  useEffect(() => installBrowserNavigationGuard(guard), [guard]);
  return (
    <FormGuardContext.Provider value={guard}>
      {children}
      <ConfirmationDialog
        visible={open}
        title={savePrompt ? "¿Guardar y descartar otros borradores?" : "¿Descartar los cambios?"}
        message={
          savePrompt
            ? "Guardar actualizará el expediente y descartará los cambios de los otros formularios. Si cancelas, se conservará todo y no se enviará nada."
            : "Hay cambios sin guardar. Si continúas, perderás lo que has escrito."
        }
        confirmLabel={savePrompt ? "Guardar y descartar" : "Descartar cambios"}
        cancelLabel="Seguir editando"
        danger
        onCancel={() => {
          pending.current = null;
          setOpen(false);
        }}
        onConfirm={() => {
          const next = pending.current;
          pending.current = null;
          setOpen(false);
          if (next) {
            if (!next.save) changes.clear(next.id);
            next.action();
          }
        }}
      />
      <Modal visible={saving} transparent onRequestClose={() => {}}>
        <View
          accessibilityViewIsModal
          style={{ flex: 1, backgroundColor: "#102C3A88", alignItems: "center", justifyContent: "center" }}
        >
          <View style={{ padding: 24, gap: 16, borderRadius: 20, backgroundColor: "white" }}>
            <ActivityIndicator />
            <Text role="status" accessibilityLiveRegion="polite">
              Guardando el expediente…
            </Text>
          </View>
        </View>
      </Modal>
    </FormGuardContext.Provider>
  );
}
