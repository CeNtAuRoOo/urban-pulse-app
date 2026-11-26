import { useUnsavedChanges } from "../services/form-guard";
import { useState } from "react";
import { View } from "react-native";
import { Body, Button, Confirm, Feedback, Field, NavLink, Panel, QueryState, styles } from "../components/ui";
import { useAction, useResource } from "../services/hooks";
import { useSession } from "../services/session";
import { textLength } from "../services/validation";
import type { Incident } from "../types/api";
import type { Duplicate } from "../types/operations";
export function Duplicates({ incident }: { incident: Incident }) {
  const list = useResource<Duplicate[]>(`/incidents/${incident.id}/duplicate-candidates`);
  return (
    <Panel title="Posibles duplicados">
      <Body muted>
        Coincidencias por proximidad, fecha y categoría. La puntuación es orientativa; revisa ambos avisos
        antes de vincularlos.
      </Body>
      <QueryState {...list} empty={list.result?.data.length === 0} />
      {list.result?.data.map((d) => (
        <Candidate key={d.candidateIncidentId} item={d} incident={incident} />
      ))}
    </Panel>
  );
}
function Candidate({ item, incident }: { item: Duplicate; incident: Incident }) {
  const [expanded, setExpanded] = useState(false),
    [reason, setReason] = useState("");
  const guard = useUnsavedChanges(!!reason);
  const candidate = useResource<Incident>(expanded ? `/incidents/${item.candidateIncidentId}` : null),
    { api } = useSession(),
    action = useAction();
  return (
    <View style={styles.divider}>
      <Body>
        Coincidencia:{" "}
        {new Intl.NumberFormat("es-ES", {
          style: "percent",
          maximumFractionDigits: 0,
        }).format(item.score)}
      </Body>
      <Body muted>{item.reasons.join(" · ")}</Body>
      <Button
        secondary
        onPress={() =>
          guard.discard(() => {
            setExpanded(!expanded);
            setReason("");
          })
        }
      >
        {expanded ? "Ocultar candidato" : "Examinar candidato"}
      </Button>
      {expanded ? (
        <>
          <QueryState {...candidate} />
          {candidate.result ? (
            <>
              <NavLink href={`/incidents/${item.candidateIncidentId}`}>{candidate.result.data.title}</NavLink>
              <Body>{candidate.result.data.description}</Body>
            </>
          ) : null}
          <Feedback error={action.error} success={action.success} />
          <Field label="Motivo de vinculación (opcional)" multiline value={reason} onChangeText={setReason} />
          <Confirm
            title="Vincular incidencias"
            message="Se conservarán ambos expedientes y su historial. El backend no ofrece desvinculación."
            disabled={action.busy || !!action.success}
            onConfirm={() =>
              void action.run(async () => {
                await api(`/incidents/${incident.id}/duplicate-links`, {
                  method: "POST",
                  body: {
                    candidateIncidentId: item.candidateIncidentId,
                    ...(reason.trim() ? { reason: textLength(reason, 0, 2000, "Motivo") } : {}),
                  },
                });
                guard.saved();
                setReason("");
                action.setSuccess("Incidencias vinculadas.");
              })
            }
          >
            Confirmar duplicado
          </Confirm>
        </>
      ) : null}
    </View>
  );
}
