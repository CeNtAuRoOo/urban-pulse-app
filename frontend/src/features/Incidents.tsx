import { useState } from "react";
import { useLocalSearchParams } from "expo-router";
import {
  Badge,
  Button,
  date,
  Details,
  Feedback,
  Field,
  NavLink,
  Pager,
  Panel,
  QueryState,
  Select,
  statusNames,
  Textarea,
} from "../components/ui";
import { useAction, useResource } from "../services/hooks";
import { ApiError, type ApiResponse, type Query } from "../services/http";
import { useSession } from "../services/session";
import type { Category, HistoryEvent, Incident, IncidentSummary, Page } from "../types/api";
import { incidentBody, incidentFields } from "./incident-form";

function IncidentForm({
  original,
  etag,
  onSaved,
  onCancel,
}: {
  original?: Incident;
  etag?: string | null;
  onSaved: (response: ApiResponse<Incident>) => void;
  onCancel?: () => void;
}) {
  const { api } = useSession();
  const action = useAction();
  const categories = useResource<Category[]>("/categories");
  const [fields, setFields] = useState(() => incidentFields(original));
  const [stale, setStale] = useState(false);
  const set = (key: keyof typeof fields, value: string) =>
    setFields((previous) => ({ ...previous, [key]: value }));
  const active = categories.result?.data.filter((category) => category.active) || [];
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        void action.run(async () => {
          if (original && !etag) throw new ApiError(428, "IF_MATCH_REQUIRED");
          const body = incidentBody(fields, original);
          if (body.categoryId && !active.some((category) => category.id === body.categoryId))
            throw new Error("Selecciona una categoría activa del catálogo actualizado.");
          try {
            const response = await api<Incident>(original ? `/incidents/${original.id}` : "/incidents", {
              method: original ? "PATCH" : "POST",
              body,
              ...(original && etag ? { etag } : {}),
            });
            onSaved(response);
          } catch (error) {
            if (error instanceof ApiError && [409, 412, 428].includes(error.status)) setStale(true);
            throw error;
          }
        });
      }}
    >
      <Feedback error={action.error} />
      <QueryState {...categories} empty={false} />
      <fieldset disabled={action.busy || stale}>
        <Field
          label="Título (5–150 caracteres)"
          required
          value={fields.title}
          onChange={(e) => set("title", e.target.value)}
        />
        <Textarea
          label="Descripción (10–5000 caracteres)"
          required
          value={fields.description}
          onChange={(e) => set("description", e.target.value)}
        />
        <div className="grid">
          <Field
            label="Latitud WGS84"
            type="number"
            step="any"
            min={-90}
            max={90}
            required
            value={fields.latitude}
            onChange={(e) => set("latitude", e.target.value)}
          />
          <Field
            label="Longitud WGS84"
            type="number"
            step="any"
            min={-180}
            max={180}
            required
            value={fields.longitude}
            onChange={(e) => set("longitude", e.target.value)}
          />
          <Field
            label="Precisión en metros (opcional)"
            type="number"
            step="any"
            min={0}
            value={fields.accuracy}
            onChange={(e) => set("accuracy", e.target.value)}
          />
        </div>
        <Button
          secondary
          onClick={() =>
            setFields((previous) => ({ ...previous, latitude: "36.7213", longitude: "-4.4214" }))
          }
        >
          Rellenar ejemplo editable de Málaga
        </Button>
        <p className="hint">
          Coordenadas introducidas manualmente; comprueba que corresponden al lugar del aviso.
        </p>
        {original && (
          <Select
            label="Acción sobre la categoría"
            value={fields.categoryMode}
            onChange={(e) => {
              set("categoryMode", e.target.value);
              if (e.target.value === "change") set("categoryId", "");
            }}
          >
            <option value="keep">Conservar categoría actual</option>
            <option value="change">Cambiar categoría</option>
            <option value="remove">Quitar categoría</option>
          </Select>
        )}
        {(!original || fields.categoryMode === "change") && (
          <Select
            label={original ? "Nueva categoría activa" : "Categoría (opcional)"}
            required={!!original}
            disabled={categories.loading || !!categories.error}
            value={fields.categoryId}
            onChange={(e) => set("categoryId", e.target.value)}
          >
            <option value="">{original ? "Selecciona una categoría" : "Sin categoría"}</option>
            {active.map((category) => (
              <option value={category.id} key={category.id}>
                {category.name}
              </option>
            ))}
          </Select>
        )}
        <Button type="submit" disabled={!!original && !etag}>
          {action.busy ? "Guardando…" : original ? "Guardar corrección" : "Registrar incidencia"}
        </Button>
        {onCancel && (
          <Button secondary onClick={onCancel}>
            Cancelar
          </Button>
        )}
      </fieldset>
      {stale && (
        <p role="alert">
          La corrección se ha detenido. Recarga el detalle con el botón superior para revisar los datos
          actuales; no se reenviará automáticamente.
        </p>
      )}
      {original && !etag && (
        <Feedback
          error={
            new Error(
              "No se recibió la cabecera ETag. Recarga el detalle y comprueba la exposición CORS de esta cabecera."
            )
          }
        />
      )}
    </form>
  );
}
export function CreateIncident() {
  const [created, setCreated] = useState<ApiResponse<Incident>>();
  return (
    <>
      <h1>Crear incidencia</h1>
      <p className="muted">El reporte se registra a tu nombre en estado inicial.</p>
      <Panel>
        {created ? (
          <>
            <Feedback success={`Incidencia registrada · HTTP ${created.status}`} />
            <Details
              entries={[
                ["Identificador", created.data.id],
                ["Título", created.data.title],
                ["Estado", statusNames[created.data.status]],
              ]}
            />
            <p>
              <NavLink href={`/incidents/${created.data.id}`} className="button">
                Abrir detalle e historial
              </NavLink>
              <Button secondary onClick={() => setCreated(undefined)}>
                Crear otra incidencia
              </Button>
            </p>
          </>
        ) : (
          <IncidentForm onSaved={setCreated} />
        )}
      </Panel>
    </>
  );
}
export function IncidentList({ all = false }: { all?: boolean }) {
  const [page, setPage] = useState(1);
  const [filters, setFilters] = useState<Query>({});
  const list = useResource<Page<IncidentSummary>>(all ? "/incidents" : "/incidents/mine", {
    page,
    pageSize: 20,
    ...filters,
  });
  return (
    <>
      <div className="toolbar">
        <h1>{all ? "Todas las incidencias" : "Mis incidencias"}</h1>
        <NavLink href="/incidents/new" className="button">
          Crear incidencia
        </NavLink>
        <Button secondary disabled={list.loading} onClick={list.reload}>
          Actualizar
        </Button>
      </div>
      <Panel>
        {all && (
          <IncidentFilters
            onApply={(query) => {
              setPage(1);
              setFilters(query);
            }}
          />
        )}
        <QueryState {...list} empty={list.result?.data.data.length === 0} />
        {list.result && (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Título</th>
                    <th>Categoría</th>
                    <th>Estado</th>
                    <th>Fecha de creación</th>
                    <th>Consulta</th>
                  </tr>
                </thead>
                <tbody>
                  {list.result.data.data.map((item) => (
                    <tr key={item.id}>
                      <td>{item.title}</td>
                      <td>{item.category?.name || "Sin categoría"}</td>
                      <td>
                        <Badge>{statusNames[item.status] || item.status}</Badge>
                      </td>
                      <td>{date(item.createdAt)}</td>
                      <td>
                        <NavLink href={`/incidents/${item.id}`}>Ver detalle →</NavLink>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager pagination={list.result.data.pagination} onPage={setPage} disabled={list.loading} />
          </>
        )}
      </Panel>
    </>
  );
}
function IncidentFilters({ onApply }: { onApply: (query: Query) => void }) {
  const categories = useResource<Category[]>("/categories");
  const [error, setError] = useState<unknown>();
  return (
    <>
      <form
        className="filters"
        onSubmit={(e) => {
          e.preventDefault();
          setError(undefined);
          const form = new FormData(e.currentTarget);
          const from = String(form.get("createdFrom"));
          const to = String(form.get("createdTo"));
          if (from && to && from > to) {
            setError(new Error("La fecha inicial no puede ser posterior a la final."));
            return;
          }
          onApply({
            status: String(form.get("status")),
            categoryId: String(form.get("categoryId") || ""),
            createdFrom: from ? new Date(from).toISOString() : undefined,
            createdTo: to ? new Date(to).toISOString() : undefined,
          });
        }}
      >
        <Select label="Estado" name="status">
          <option value="">Todos</option>
          {[
            "REPORTED",
            "VALIDATED",
            "REJECTED",
            "ASSIGNED_TO_DEPARTMENT",
            "WORK_ASSIGNED",
            "IN_PROGRESS",
            "RESOLVED",
            "CLOSED",
          ].map((status) => (
            <option key={status} value={status}>
              {statusNames[status]}
            </option>
          ))}
        </Select>
        <Select label="Categoría" name="categoryId" disabled={!categories.result}>
          <option value="">Todas</option>
          {categories.result?.data.map((category) => (
            <option value={category.id} key={category.id}>
              {category.name}
              {category.active ? "" : " (inactiva)"}
            </option>
          ))}
        </Select>
        <Field label="Desde (hora local)" name="createdFrom" type="datetime-local" />
        <Field label="Hasta (hora local)" name="createdTo" type="datetime-local" />
        <Button type="submit">Aplicar filtros</Button>
      </form>
      <Feedback error={error} />
      <QueryState {...categories} />
    </>
  );
}
export function IncidentDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const resource = useResource<Incident>(`/incidents/${encodeURIComponent(id)}`);
  const [editing, setEditing] = useState(false);
  const [historyRevision, setHistoryRevision] = useState(0);
  const [success, setSuccess] = useState("");
  const incident = resource.result?.data;
  function reload() {
    setEditing(false);
    setSuccess("");
    resource.reload();
    setHistoryRevision((value) => value + 1);
  }
  return (
    <>
      <div className="toolbar">
        <h1>Detalle de incidencia</h1>
        <Button secondary onClick={reload} disabled={resource.loading}>
          Recargar detalle
        </Button>
      </div>
      <NavLink href="/incidents">← Mis incidencias</NavLink>
      <QueryState {...resource} />
      <Feedback success={success} />
      {incident && (
        <>
          <Panel title={incident.title}>
            <Details
              entries={[
                ["Identificador", incident.id],
                ["Estado", <Badge key="status">{statusNames[incident.status] || incident.status}</Badge>],
                ["Descripción", incident.description],
                [
                  "Categoría",
                  incident.category
                    ? `${incident.category.name}${incident.category.active ? "" : " (inactiva)"}`
                    : "Sin categoría",
                ],
                ["Informante", incident.reporter?.displayName],
                ["ID del informante", incident.reporter?.id],
                [
                  "Latitud / longitud",
                  `${incident.location.coordinates.latitude}, ${incident.location.coordinates.longitude}`,
                ],
                ["Precisión (m)", incident.location.coordinates.accuracyMeters ?? "No indicada"],
                ["Creación", date(incident.createdAt)],
                ["Actualización", date(incident.updatedAt)],
                ...(incident.priority ? [["Prioridad", incident.priority] as [string, string]] : []),
                ...(incident.assignedDepartment
                  ? [["Departamento", incident.assignedDepartment.name] as [string, string]]
                  : []),
                ...(incident.resolvedAt
                  ? [["Resolución", date(incident.resolvedAt)] as [string, string]]
                  : []),
                ...(incident.closedAt ? [["Cierre", date(incident.closedAt)] as [string, string]] : []),
              ]}
            />
            {incident.status === "REPORTED" && !editing && (
              <p>
                <Button onClick={() => setEditing(true)}>Corregir datos</Button>
              </p>
            )}
          </Panel>
          {editing && incident.status === "REPORTED" && (
            <Panel title="Corregir reporte inicial">
              <IncidentForm
                key={resource.result?.etag || incident.id}
                original={incident}
                etag={resource.result?.etag}
                onCancel={() => setEditing(false)}
                onSaved={(response) => {
                  resource.setResult(response);
                  setEditing(false);
                  setSuccess(`Corrección guardada · HTTP ${response.status}`);
                  setHistoryRevision((value) => value + 1);
                }}
              />
            </Panel>
          )}
          <IncidentHistory key={`${id}-${historyRevision}`} id={id} />
        </>
      )}
    </>
  );
}
function IncidentHistory({ id }: { id: string }) {
  const [page, setPage] = useState(1);
  const resource = useResource<Page<HistoryEvent>>(`/incidents/${encodeURIComponent(id)}/history`, {
    page,
    pageSize: 20,
  });
  return (
    <Panel title="Historial público">
      <QueryState {...resource} empty={resource.result?.data.data.length === 0} />
      {resource.result && (
        <>
          {resource.result.data.data.map((event) => (
            <article className="history" key={event.id}>
              <Badge>{statusNames[event.eventType] || event.eventType}</Badge>
              <p>{event.summary}</p>
              <small className="muted">
                {date(event.occurredAt)} · {event.actor?.displayName || "Actor no informado"}
              </small>
              {event.actor && <small className="muted">{event.actor.id}</small>}
            </article>
          ))}
          <Pager pagination={resource.result.data.pagination} onPage={setPage} />
        </>
      )}
    </Panel>
  );
}
