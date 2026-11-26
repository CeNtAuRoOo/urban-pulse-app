import { useFormNavigation, useUnsavedChanges } from "../services/form-guard";
import { useState } from "react";
import { useLocalSearchParams, useRouter } from "expo-router";
import { View, useWindowDimensions } from "react-native";
import * as Location from "expo-location";
import {
  Badge,
  Body,
  Button,
  DataList,
  Details,
  Feedback,
  Field,
  NavLink,
  PageHeading,
  Pager,
  Panel,
  QueryState,
  Row,
  Select,
  date,
  options,
  priorities,
  statusNames,
  styles,
} from "../components/ui";
import { useAction, useResource } from "../services/hooks";
import { ApiError, type ApiResponse, type Query } from "../services/http";
import { useSession } from "../services/session";
import { permissions } from "../services/permissions";
import type { Category, HistoryEvent, Incident, IncidentSummary, Page } from "../types/api";
import { incidentBody, incidentFields } from "./incident-form";
import { Processing, ProcessingHistory } from "./Processing";
import { Attachments } from "./Attachments";
import { Duplicates } from "./Duplicates";
import { Tabs } from "../components/Tabs";
import { Disclosure } from "../components/Disclosure";
import { incidentHref, createIncidentHref, routeKey, returnTarget } from "../services/navigation";
import type { Href } from "expo-router";
import { GeoMap } from "../components/GeoMap";
export function IncidentForm({
  original: incoming,
  etag: incomingEtag,
  onSaved,
  onCancel,
}: {
  original?: Incident;
  etag?: string | null;
  onSaved: (r: ApiResponse<Incident>) => void;
  onCancel?: () => void;
}) {
  const [{ original, etag }] = useState(() => ({ original: incoming, etag: incomingEtag }));
  const versionChanged = incoming?.version !== original?.version || incomingEtag !== etag;
  const { api } = useSession(),
    action = useAction(),
    locationAction = useAction();
  const categories = useResource<Category[]>("/categories");
  const [fields, setFields] = useState(() => incidentFields(original)),
    [stale, setStale] = useState(false),
    [pick, setPick] = useState(false);
  const set = (key: keyof typeof fields, value: string) => setFields((v) => ({ ...v, [key]: value }));
  const [initial] = useState(() => JSON.stringify(incidentFields(original)));
  const guard = useUnsavedChanges(JSON.stringify(fields) !== initial);
  const active = categories.result?.data.filter((c) => c.active) || [];
  return (
    <View style={styles.stack}>
      <Feedback error={action.error} />
      <Field
        label="Título"
        hint="Resume el problema en 5–150 caracteres."
        value={fields.title}
        onChangeText={(v) => set("title", v)}
        editable={!action.busy}
      />
      <Field
        label="Descripción"
        hint="Explica qué sucede y dónde encontrarlo (10–5000 caracteres)."
        multiline
        value={fields.description}
        onChangeText={(v) => set("description", v)}
        editable={!action.busy}
      />
      <QueryState {...categories} />
      {original ? (
        <Select
          label="Categoría"
          value={fields.categoryMode}
          onChange={(v) => set("categoryMode", v)}
          options={[
            { value: "keep", label: "Conservar categoría" },
            { value: "change", label: "Cambiar categoría" },
            { value: "remove", label: "Quitar categoría" },
          ]}
        />
      ) : null}
      {!original || fields.categoryMode === "change" ? (
        <Select
          label="Categoría del aviso"
          value={fields.categoryId}
          onChange={(v) => set("categoryId", v)}
          options={[
            {
              value: "",
              label: original ? "Selecciona categoría" : "Sin categoría",
            },
            ...active.map((c) => ({ value: c.id, label: c.name })),
          ]}
        />
      ) : null}
      <Body>Ubicación</Body>
      <Body muted>
        Indica el lugar del problema. Puedes usar tu posición, marcarlo en el mapa o introducir las
        coordenadas.
      </Body>
      <Feedback error={locationAction.error} />
      <Row>
        <Button
          secondary
          disabled={locationAction.busy}
          icon="crosshair"
          onPress={() =>
            void locationAction.run(async () => {
              const permission = await Location.requestForegroundPermissionsAsync();
              if (permission.status !== "granted")
                throw new Error(
                  "No se ha autorizado la ubicación. Puedes introducirla manualmente o marcarla en el mapa."
                );
              const location = await Location.getCurrentPositionAsync({
                accuracy: Location.Accuracy.Balanced,
              });
              setFields((v) => ({
                ...v,
                latitude: String(location.coords.latitude),
                longitude: String(location.coords.longitude),
                accuracy: location.coords.accuracy === null ? "" : String(location.coords.accuracy),
              }));
            })
          }
        >
          {locationAction.busy ? "Localizando…" : "Usar mi ubicación"}
        </Button>
        <Button secondary icon="map-pin" onPress={() => setPick(!pick)}>
          {pick ? "Cerrar mapa" : "Seleccionar en mapa"}
        </Button>
      </Row>
      {pick ? (
        <GeoMap
          items={[]}
          onPick={(coords) => {
            setFields((v) => ({
              ...v,
              latitude: String(coords.latitude),
              longitude: String(coords.longitude),
              accuracy: "",
            }));
          }}
        />
      ) : null}
      <Field
        label="Latitud"
        keyboardType="numbers-and-punctuation"
        value={fields.latitude}
        onChangeText={(v) => set("latitude", v)}
        placeholder="36.72…"
      />
      <Field
        label="Longitud"
        keyboardType="numbers-and-punctuation"
        value={fields.longitude}
        onChangeText={(v) => set("longitude", v)}
        placeholder="−4.42…"
      />
      <Field
        label="Precisión en metros (opcional)"
        keyboardType="decimal-pad"
        value={fields.accuracy}
        onChangeText={(v) => set("accuracy", v)}
      />
      <Row>
        <Button
          disabled={action.busy || stale || versionChanged || (!!original && !etag)}
          onPress={() =>
            guard.beforeSave(() =>
              action.run(async () => {
                if (versionChanged) throw new Error("El expediente ha cambiado. Actualiza antes de guardar.");
                if (original && !etag) throw new ApiError(428, "IF_MATCH_REQUIRED");
                const body = incidentBody(fields, original);
                if (body.categoryId && !active.some((c) => c.id === body.categoryId))
                  throw new Error("Selecciona una categoría activa.");
                try {
                  const result = await api<Incident>(original ? `/incidents/${original.id}` : "/incidents", {
                    method: original ? "PATCH" : "POST",
                    body,
                    ...(original && etag ? { etag } : {}),
                  });
                  guard.saved();
                  onSaved(result);
                } catch (e) {
                  if (e instanceof ApiError && [409, 412, 428].includes(e.status)) setStale(true);
                  throw e;
                }
              })
            )
          }
        >
          {action.busy ? "Guardando…" : original ? "Guardar corrección" : "Registrar incidencia"}
        </Button>
        {onCancel ? (
          <Button secondary disabled={action.busy} onPress={() => guard.discard(onCancel)}>
            Cancelar
          </Button>
        ) : null}
      </Row>
      {stale || versionChanged ? (
        <Body>
          El expediente ha cambiado. Conserva tu texto y recarga el detalle para revisar la versión actual
          antes de volver a guardar.
        </Body>
      ) : null}
      {original && !etag ? (
        <Body>No se recibió la versión del expediente. Recarga el detalle antes de editar.</Body>
      ) : null}
    </View>
  );
}
export function CreateIncident() {
  const router = useRouter(),
    { from } = useLocalSearchParams<{ from?: string }>();
  const guard = useFormNavigation(),
    back = returnTarget(from);
  const cancel = () => router.dismissTo(back.href as Href);
  return (
    <>
      <Button secondary icon="arrow-left" onPress={() => guard.request(cancel)}>
        {back.label}
      </Button>
      <PageHeading
        title="Comunicar incidencia"
        description="Tu aviso ayuda a cuidar los espacios que compartimos."
      />
      <Panel>
        <IncidentForm
          onCancel={cancel}
          onSaved={(r) => router.replace(incidentHref(r.data.id, back.href) as Href)}
        />
      </Panel>
    </>
  );
}
export function IncidentList({ all = false }: { all?: boolean }) {
  const params = useLocalSearchParams<Record<string, string>>(),
    router = useRouter();
  const page = Math.max(1, Number(params.page) || 1);
  const filters: Query = all
    ? Object.fromEntries(
        ["status", "categoryId", "priority", "sort", "createdFrom", "createdTo"].map((k) => [
          k,
          params[k] || undefined,
        ])
      )
    : {};
  const list = useResource<Page<IncidentSummary>>(all ? "/incidents" : "/incidents/mine", {
    page,
    pageSize: 20,
    ...filters,
  });
  return (
    <>
      <PageHeading
        title={all ? "Gestión municipal" : "Mis incidencias"}
        description={
          all
            ? "Revisa y gestiona las incidencias de tu municipio."
            : "Cada aviso, su estado y los siguientes pasos."
        }
      >
        <NavLink href={createIncidentHref(routeKey(all ? "/incidents/all" : "/incidents", params))} button>
          Comunicar incidencia
        </NavLink>
      </PageHeading>
      <Panel>
        {all ? (
          <IncidentFilters initial={filters} onApply={(q) => router.setParams({ ...q, page: "1" })} />
        ) : null}
        <QueryState {...list} empty={list.result?.data.data.length === 0} />
        {list.result ? (
          <>
            <IncidentRows
              items={list.result.data.data}
              origin={routeKey(all ? "/incidents/all" : "/incidents", params)}
            />
            <Pager
              pagination={list.result.data.pagination}
              onPage={(p) => router.setParams({ page: String(p) })}
            />
          </>
        ) : null}
      </Panel>
    </>
  );
}
export function IncidentRows({
  items,
  origin = "/incidents",
}: {
  items: IncidentSummary[];
  origin?: string;
}) {
  return (
    <DataList
      items={items}
      columns={[
        {
          label: "Incidencia",
          grow: 2,
          render: (i) => <NavLink href={incidentHref(i.id, origin)}>{i.title}</NavLink>,
        },
        {
          label: "Categoría",
          render: (i) => <Body>{i.category?.name || "Sin categoría"}</Body>,
        },
        {
          label: "Estado",
          render: (i) => (
            <Badge
              tone={
                i.status === "REJECTED"
                  ? "danger"
                  : i.status === "AWAITING_INFORMATION"
                    ? "warning"
                    : "normal"
              }
            >
              {statusNames[i.status] || i.status}
            </Badge>
          ),
        },
        {
          label: "Prioridad",
          render: (i) => <Body>{i.priority ? priorities[i.priority] : "Sin decidir"}</Body>,
        },
        {
          label: "Actualización",
          render: (i) => <Body muted>{date(i.updatedAt)}</Body>,
        },
      ]}
    />
  );
}
function IncidentFilters({
  initial,
  onApply,
}: {
  initial: Query;
  onApply: (v: Record<string, string>) => void;
}) {
  const categories = useResource<Category[]>("/categories");
  const [v, set] = useState<Record<string, string>>(() =>
      Object.fromEntries(Object.entries(initial).map(([k, x]) => [k, String(x || "")]))
    ),
    [error, setError] = useState<unknown>();
  const update = (key: string, value: string) => set((x) => ({ ...x, [key]: value }));
  return (
    <View style={styles.stack}>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 16 }}>
        {[
          [
            "status",
            "Estado",
            options(
              Object.fromEntries(
                Object.entries(statusNames).filter(
                  ([k]) => !["ACTIVE", "REDEEMED", "REVOKED", "EXPIRED"].includes(k)
                )
              ),
              "Todos"
            ),
          ],
          [
            "categoryId",
            "Categoría",
            [
              { value: "", label: "Todas" },
              ...(categories.result?.data || []).map((c) => ({
                value: c.id,
                label: c.name,
              })),
            ],
          ],
          ["priority", "Prioridad", options(priorities, "Todas")],
          [
            "sort",
            "Orden",
            [
              { value: "", label: "Más recientes" },
              { value: "priority,desc", label: "Mayor prioridad" },
              { value: "priority,asc", label: "Menor prioridad" },
            ],
          ],
        ].map(([key, label, choices]) => (
          <View key={String(key)} style={{ flexGrow: 1, flexBasis: 180 }}>
            <Select
              label={String(label)}
              value={v[String(key)] || ""}
              onChange={(x) => update(String(key), x)}
              options={choices as { value: string; label: string }[]}
            />
          </View>
        ))}
      </View>
      <Row>
        <View style={{ flexGrow: 1, flexBasis: 220 }}>
          <Field
            label="Desde (fecha ISO con zona)"
            placeholder="2026-11-01T00:00:00+01:00…"
            value={v.createdFrom || ""}
            onChangeText={(x) => update("createdFrom", x)}
          />
        </View>
        <View style={{ flexGrow: 1, flexBasis: 220 }}>
          <Field
            label="Hasta (fecha ISO con zona)"
            placeholder="2026-11-30T23:59:59+01:00…"
            value={v.createdTo || ""}
            onChangeText={(x) => update("createdTo", x)}
          />
        </View>
      </Row>
      <Feedback error={error} />
      <Row>
        <Button
          secondary
          onPress={() => {
            try {
              const next = { ...v };
              for (const k of ["createdFrom", "createdTo"])
                if (next[k]) {
                  if (!/(Z|[+-]\d{2}:\d{2})$/.test(next[k]))
                    throw new Error("Incluye la zona horaria en las fechas.");
                  next[k] = new Date(next[k]).toISOString();
                }
              if (next.createdFrom && next.createdTo && next.createdFrom > next.createdTo)
                throw new Error("La fecha inicial debe ser anterior a la final.");
              setError(undefined);
              onApply(next);
            } catch (e) {
              setError(e);
            }
          }}
        >
          Aplicar filtros
        </Button>
        <Button
          secondary
          onPress={() => {
            const empty = Object.fromEntries(
              ["status", "categoryId", "priority", "sort", "createdFrom", "createdTo"].map((k) => [k, ""])
            );
            set(empty);
            setError(undefined);
            onApply(empty);
          }}
        >
          Limpiar
        </Button>
      </Row>
    </View>
  );
}
export function IncidentDetail() {
  const { id, from, tab: selectedTab } = useLocalSearchParams<{ id: string; from?: string; tab?: string }>();
  const guard = useFormNavigation();
  const router = useRouter(),
    wide = useWindowDimensions().width >= 1100;
  const resource = useResource<Incident>(`/incidents/${encodeURIComponent(id)}`),
    { session } = useSession();
  const [editing, setEditing] = useState(false),
    [revision, setRevision] = useState(0);
  const incident = resource.result?.data,
    p = permissions(session?.user, incident),
    back = returnTarget(from);
  const tab = ["summary", "history", "attachments", ...(p.staff ? ["duplicates"] : [])].includes(
    selectedTab || ""
  )
    ? selectedTab!
    : "summary";
  const reload = () => {
    setEditing(false);
    resource.reload();
    setRevision((v) => v + 1);
  };
  return (
    <>
      <Button
        secondary
        icon="arrow-left"
        onPress={() => guard.request(() => router.dismissTo(back.href as Href))}
      >
        {back.label}
      </Button>
      <PageHeading title={incident?.title || "Detalle de incidencia"}>
        <Button
          secondary
          icon="refresh-cw"
          onPress={() =>
            guard.request(() => {
              setEditing(false);
              reload();
            })
          }
        >
          Actualizar
        </Button>
      </PageHeading>
      <QueryState {...resource} />
      {incident ? (
        <>
          <Row>
            <Badge
              tone={
                incident.status === "REJECTED"
                  ? "danger"
                  : incident.status === "AWAITING_INFORMATION"
                    ? "warning"
                    : "normal"
              }
            >
              {statusNames[incident.status]}
            </Badge>
            <Body muted>Prioridad: {incident.priority ? priorities[incident.priority] : "sin decidir"}</Body>
            <Body muted>
              {incident.category?.name || "Sin categoría"} · {date(incident.updatedAt)}
            </Body>
          </Row>
          <Tabs
            value={tab}
            onChange={(value) =>
              guard.request(() => {
                setEditing(false);
                router.setParams({ tab: value });
              })
            }
            items={[
              { value: "summary", label: "Resumen" },
              { value: "history", label: "Historial" },
              { value: "attachments", label: "Evidencias" },
              ...(p.staff ? [{ value: "duplicates", label: "Duplicados" }] : []),
            ]}
          />
          {tab === "summary" ? (
            <View style={{ flexDirection: wide ? "row" : "column", gap: 24, alignItems: "flex-start" }}>
              <View style={{ flex: wide ? 1.2 : undefined, width: wide ? undefined : "100%", gap: 20 }}>
                <Panel title="Sobre esta incidencia">
                  <Body>{incident.description}</Body>
                  <Details
                    entries={[
                      [
                        "Ubicación",
                        `${incident.location.coordinates.latitude}, ${incident.location.coordinates.longitude}`,
                      ],
                      ["Informante", incident.reporter?.displayName],
                    ]}
                  />
                  <Disclosure title="Datos del expediente">
                    <Details
                      entries={[
                        ["Referencia", incident.id],
                        ["Registrada", date(incident.createdAt)],
                        ["Último cambio", date(incident.updatedAt)],
                        [
                          "Precisión",
                          incident.location.coordinates.accuracyMeters === undefined
                            ? "No indicada"
                            : `${incident.location.coordinates.accuracyMeters} m`,
                        ],
                        ...(incident.assignedDepartment
                          ? [["Departamento", incident.assignedDepartment.name] as [string, string]]
                          : []),
                        ...(incident.resolvedAt
                          ? [["Resuelta", date(incident.resolvedAt)] as [string, string]]
                          : []),
                        ...(incident.closedAt
                          ? [["Cerrada", date(incident.closedAt)] as [string, string]]
                          : []),
                      ]}
                    />
                  </Disclosure>
                  {p.edit ? (
                    <Button secondary icon="edit-2" onPress={() => guard.request(() => setEditing(!editing))}>
                      {editing ? "Cerrar edición" : "Corregir datos"}
                    </Button>
                  ) : null}
                  {editing && p.edit ? (
                    <IncidentForm
                      original={incident}
                      etag={resource.result?.etag}
                      onCancel={() => setEditing(false)}
                      onSaved={(r) => {
                        resource.setResult(r);
                        setEditing(false);
                        setRevision((v) => v + 1);
                      }}
                    />
                  ) : null}
                </Panel>
              </View>
              <View style={{ flex: wide ? 1 : undefined, width: wide ? undefined : "100%", gap: 20 }}>
                <Processing key={`processing-${revision}`} incident={incident} changed={reload} />
              </View>
            </View>
          ) : null}
          {tab === "history" ? (
            <ProcessingHistory key={`history-${revision}`} incident={incident}>
              <IncidentHistory id={id} />
            </ProcessingHistory>
          ) : null}
          {tab === "attachments" ? <Attachments incident={incident} /> : null}
          {tab === "duplicates" && p.staff ? <Duplicates incident={incident} /> : null}
        </>
      ) : null}
    </>
  );
}
function IncidentHistory({ id }: { id: string }) {
  const [page, setPage] = useState(1),
    resource = useResource<Page<HistoryEvent>>(`/incidents/${id}/history`, {
      page,
      pageSize: 20,
    });
  return (
    <Panel title="Historial">
      <QueryState {...resource} empty={resource.result?.data.data.length === 0} />
      {resource.result ? (
        <>
          {resource.result.data.data.map((e) => (
            <View style={styles.divider} key={e.id}>
              <Body>{e.summary}</Body>
              <Body muted>
                {date(e.occurredAt)}
                {e.actor ? ` · ${e.actor.displayName}` : ""}
              </Body>
            </View>
          ))}
          <Pager pagination={resource.result.data.pagination} onPage={setPage} />
        </>
      ) : null}
    </Panel>
  );
}
