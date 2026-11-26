import { useUnsavedChanges } from "../services/form-guard";
import { Tabs } from "../components/Tabs";
import { useState, type ReactNode } from "react";
import { View } from "react-native";
import {
  Body,
  Button,
  Confirm,
  Details,
  Feedback,
  Field,
  Pager,
  Panel,
  QueryState,
  Select,
  date,
  options,
  priorities,
  styles,
} from "../components/ui";
import { useAction, useResource } from "../services/hooks";
import { useSession } from "../services/session";
import { permissions } from "../services/permissions";
import { textLength } from "../services/validation";
import { ApiError } from "../services/http";
import type { Category, Incident, Page } from "../types/api";
import type { InformationResponse, PriorityDecision, Review, ReviewOutcome } from "../types/operations";
const decisions: Record<string, string> = {
  VALIDATE: "Validar",
  REJECT: "Rechazar",
  REQUEST_INFORMATION: "Solicitar información",
};
export function Processing({ incident, changed }: { incident: Incident; changed: () => void }) {
  const { session } = useSession(),
    p = permissions(session?.user, incident),
    outcome = useResource<ReviewOutcome>(`/incidents/${incident.id}/review-outcome`);
  return (
    <>
      <Panel title="Resultado de revisión">
        <QueryState {...outcome} />
        {outcome.result ? (
          <>
            {outcome.result.data.latestReview ? (
              <>
                <Body>{decisions[outcome.result.data.latestReview.decision]} · Ayuntamiento</Body>
                <Body muted>{date(outcome.result.data.latestReview.reviewedAt)}</Body>
                {outcome.result.data.latestReview.reason ? (
                  <Body>{outcome.result.data.latestReview.reason}</Body>
                ) : null}
              </>
            ) : (
              <Body muted>El Ayuntamiento todavía no ha registrado una revisión.</Body>
            )}
          </>
        ) : null}
      </Panel>
      {p.review ? <DecisionForm incident={incident} kind="review" changed={changed} /> : null}
      {p.respond ? <DecisionForm incident={incident} kind="response" changed={changed} /> : null}
      {p.prioritize ? <DecisionForm incident={incident} kind="priority" changed={changed} /> : null}
    </>
  );
}
export function ProcessingHistory({ incident, children }: { incident: Incident; children: ReactNode }) {
  const { session } = useSession(),
    staff = permissions(session?.user, incident).staff;
  const [section, setSection] = useState("activity");
  return (
    <>
      <Tabs
        value={section}
        onChange={setSection}
        items={[
          { value: "activity", label: "Actividad" },
          ...(staff
            ? [
                { value: "reviews", label: "Revisiones" },
                { value: "priority-decisions", label: "Prioridad" },
              ]
            : []),
          { value: "information-responses", label: "Respuestas" },
        ]}
      />
      {section === "activity" ? (
        children
      ) : (
        <Records
          key={section}
          incidentId={incident.id}
          kind={section}
          title={
            section === "reviews"
              ? "Revisiones anteriores"
              : section === "priority-decisions"
                ? "Decisiones de prioridad"
                : "Respuestas de información"
          }
        />
      )}
    </>
  );
}
function DecisionForm({
  incident: incoming,
  kind,
  changed,
}: {
  incident: Incident;
  kind: "review" | "response" | "priority";
  changed: () => void;
}) {
  const [incident] = useState(incoming);
  const versionChanged = incoming.version !== incident.version;
  const { api } = useSession(),
    action = useAction();
  const categories = useResource<Category[]>(kind === "review" ? "/categories" : null);
  const [decision, setDecision] = useState(
      incident.status === "AWAITING_INFORMATION" ? "REJECT" : "VALIDATE"
    ),
    [category, setCategory] = useState(incident.category?.id || ""),
    [message, setMessage] = useState(""),
    [priority, setPriority] = useState("MEDIUM"),
    [stale, setStale] = useState(false);
  const initialDecision = incident.status === "AWAITING_INFORMATION" ? "REJECT" : "VALIDATE";
  const guard = useUnsavedChanges(
    !!message ||
      decision !== initialDecision ||
      category !== (incident.category?.id || "") ||
      priority !== "MEDIUM"
  );
  const reset = () => {
    setMessage("");
    setDecision(initialDecision);
    setCategory(incident.category?.id || "");
    setPriority("MEDIUM");
  };
  const save = () =>
    guard.beforeSave(() =>
      action.run(async () => {
        if (versionChanged) throw new Error("El expediente ha cambiado. Actualiza antes de guardar.");
        let body: Record<string, unknown> = {
          expectedIncidentVersion: incident.version,
        };
        let route = "";
        if (kind === "review") {
          route = "reviews";
          body.decision = decision;
          if (decision === "VALIDATE") {
            if (!categories.result?.data.some((c) => c.id === category && c.active))
              throw new Error("Selecciona una categoría activa.");
            body.categoryId = category;
          } else body.reason = textLength(message, 3, 2000, "Motivo");
        } else if (kind === "priority") {
          route = "priority-decisions";
          if (priority === incident.priority)
            throw new Error("Selecciona una prioridad distinta de la actual.");
          body = {
            ...body,
            priority,
            justification: textLength(message, 3, 2000, "Justificación"),
          };
        } else {
          route = "information-responses";
          body.message = textLength(message, 3, 2000, "Respuesta");
        }
        try {
          await api(`/incidents/${incident.id}/${route}`, {
            method: "POST",
            body,
          });
          guard.saved();
          changed();
        } catch (e) {
          if (e instanceof ApiError && e.status === 409) setStale(true);
          throw e;
        }
      })
    );
  return (
    <Panel
      title={
        kind === "review"
          ? "Revisión municipal"
          : kind === "priority"
            ? "Decidir prioridad"
            : "Responder al Ayuntamiento"
      }
    >
      <Feedback error={action.error} />
      {kind === "review" ? (
        <>
          <Select
            label="Decisión"
            value={decision}
            onChange={setDecision}
            options={options(incident.status === "AWAITING_INFORMATION" ? { REJECT: "Rechazar" } : decisions)}
          />
          {decision === "VALIDATE" ? (
            <>
              <QueryState {...categories} />
              <Select
                label="Categoría validada"
                value={category}
                onChange={setCategory}
                options={[
                  { value: "", label: "Selecciona una categoría" },
                  ...(categories.result?.data || [])
                    .filter((c) => c.active)
                    .map((c) => ({ value: c.id, label: c.name })),
                ]}
              />
            </>
          ) : null}
        </>
      ) : null}
      {kind === "priority" ? (
        <Select label="Prioridad" value={priority} onChange={setPriority} options={options(priorities)} />
      ) : null}
      {kind !== "review" || decision !== "VALIDATE" ? (
        <Field
          label={
            kind === "response"
              ? "Información adicional"
              : kind === "priority"
                ? "Justificación interna"
                : "Motivo comunicable al ciudadano"
          }
          multiline
          value={message}
          onChangeText={setMessage}
          hint="Entre 3 y 2000 caracteres."
        />
      ) : null}
      <Confirm
        title="Confirmar decisión"
        message={
          kind === "response"
            ? "Tu respuesta quedará registrada y el expediente volverá a revisión."
            : "Esta decisión se incorporará al expediente y no puede rectificarse desde esta operación."
        }
        disabled={action.busy || stale || versionChanged}
        onConfirm={save}
      >
        Guardar {kind === "response" ? "respuesta" : "decisión"}
      </Confirm>
      <Button secondary disabled={action.busy} onPress={() => guard.discard(reset)}>
        Cancelar {kind === "response" ? "respuesta" : "decisión"}
      </Button>
      {stale || versionChanged ? (
        <Body>
          El expediente ha cambiado. Conserva tu texto y pulsa Actualizar antes de realizar otra operación.
        </Body>
      ) : null}
    </Panel>
  );
}
type RecordDTO = Review | PriorityDecision | InformationResponse;
function recordEntries(r: RecordDTO): [string, string | null][] {
  if ("decision" in r)
    return [
      ["Decisión", decisions[r.decision]],
      ["Motivo", r.reason],
      ["Fecha", date(r.reviewedAt)],
      ["Revisor", r.reviewedBy.displayName],
      ["Estado resultante", r.resultingStatus],
    ];
  if ("priority" in r)
    return [
      ["Prioridad", priorities[r.priority]],
      ["Justificación", r.justification],
      ["Fecha", date(r.decidedAt)],
      ["Responsable", r.decidedBy.displayName],
      ["Origen", r.source],
    ];
  return [
    ["Respuesta", r.message],
    ["Fecha", date(r.respondedAt)],
    ["Informante", r.respondedBy.displayName],
  ];
}
function Records({ incidentId, kind, title }: { incidentId: string; kind: string; title: string }) {
  const [page, setPage] = useState(1),
    [selected, setSelected] = useState(""),
    resource = useResource<Page<RecordDTO>>(`/incidents/${incidentId}/${kind}`, { page, pageSize: 10 });
  return (
    <Panel title={title}>
      <QueryState {...resource} empty={resource.result?.data.data.length === 0} />
      {resource.result ? (
        <>
          {resource.result.data.data.map((r) => (
            <View key={r.id} style={styles.divider}>
              <Details entries={recordEntries(r)} />
              <Button secondary onPress={() => setSelected(selected === r.id ? "" : r.id)}>
                Consultar registro
              </Button>
            </View>
          ))}
          <Pager pagination={resource.result.data.pagination} onPage={setPage} />
        </>
      ) : null}
      {selected ? <RecordDetail path={`/incidents/${incidentId}/${kind}/${selected}`} /> : null}
    </Panel>
  );
}
function RecordDetail({ path }: { path: string }) {
  const resource = useResource<RecordDTO>(path);
  return (
    <View style={styles.panel}>
      <QueryState {...resource} />
      {resource.result ? <Details entries={recordEntries(resource.result.data)} /> : null}
    </View>
  );
}
