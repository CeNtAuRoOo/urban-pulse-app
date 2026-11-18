import exec from "k6/execution";
import { group } from "k6";
import { actor } from "../lib/auth.js";
import { isUuid, number, requireFlag, uuid } from "../lib/config.js";
import { catalog, historyItem, incident, pageOf, strongEtag } from "../lib/checks.js";
import { header, request } from "../lib/http.js";
import { optionsFor } from "../lib/metrics.js";
import { preflight } from "../lib/reads.js";

requireFlag("K6_ENABLE_WRITES");
const identity = actor(300);
if (identity.role !== "CITIZEN")
  throw new Error("El smoke de escritura exige ACTOR=CITIZEN sin roles de personal.");
const runId = __ENV.RUN_ID;
if (!runId || !/^[a-zA-Z0-9-]{8,64}$/.test(runId))
  throw new Error("RUN_ID: identificador único de ejecución de 8..64 caracteres ASCII alfanuméricos/guion.");
const categoryId = uuid("CATEGORY_ID", false);
const latitude = number("WRITE_LATITUDE", 36.7213, -90, 90, false);
const longitude = number("WRITE_LONGITUDE", -4.4214, -180, 180, false);
export const options = optionsFor(
  { write_smoke: { executor: "shared-iterations", vus: 1, iterations: 1, maxDuration: "3m" } },
  ["create", "detail", "patch", "history"],
  {},
  { smoke: true }
);
export function setup() {
  preflight(identity);
  if (
    categoryId &&
    !request(
      identity,
      "GET",
      `/categories/${categoryId}`,
      "/categories/{categoryId}",
      "category_fixture",
      (data) => catalog([data]) && data.id === categoryId && data.active === true,
      { traffic: "preflight" }
    ).ok
  ) {
    exec.test.abort("CATEGORY_ID no corresponde a una categoría activa.");
  }
}

function required(result, message) {
  if (!result.ok) exec.test.abort(message);
  return result;
}

export default function () {
  // También limita POST ante un override accidental de VUs/iteraciones en la CLI local.
  if (__VU !== 1 || __ITER !== 0)
    exec.test.abort("Smoke acotado a un VU y una iteración. No ampliar con opciones CLI.");
  group("alta, corrección e historial propios", () => {
    const title = `Prueba k6 ${runId}-${__VU}-${__ITER}`;
    const created = required(
      request(
        identity,
        "POST",
        "/incidents",
        "/incidents",
        "create",
        (data) =>
          isUuid(data.id) &&
          incident(data, data.id, identity.subject) &&
          data.title === title &&
          data.status === "REPORTED",
        {
          status: 201,
          body: {
            title,
            description: "Aviso sintético para comprobar el recorrido controlado de k6.",
            coordinates: { latitude, longitude },
            ...(categoryId ? { categoryId } : {}),
          },
          extraChecks: {
            "ETag fuerte opaca": strongEtag,
            "Location del recurso creado": (response, data) =>
              header(response, "Location") === `/api/v1/incidents/${data.id}`,
          },
        }
      ),
      "Alta fallida; no se repite el POST. Comprobar posibles residuos en la BD de pruebas."
    );
    const id = created.data.id;
    const path = `/incidents/${id}`;
    const current = required(
      request(
        identity,
        "GET",
        path,
        "/incidents/{incidentId}",
        "detail",
        (data) => incident(data, id, identity.subject) && data.status === "REPORTED",
        { extraChecks: { "ETag fuerte opaca": strongEtag } }
      ),
      "Detalle no disponible; se conserva la incidencia creada."
    );
    const description = `Corrección sintética ${runId}-${__VU}-${__ITER}`;
    required(
      request(
        identity,
        "PATCH",
        path,
        "/incidents/{incidentId}",
        "patch",
        (data) =>
          incident(data, id, identity.subject) &&
          data.description === description &&
          data.status === "REPORTED" &&
          data.version > current.data.version,
        {
          body: { description },
          headers: { "If-Match": header(current.response, "ETag") },
          extraChecks: {
            "ETag fuerte opaca": strongEtag,
            "ETag actualizada": (response) => header(response, "ETag") !== header(current.response, "ETag"),
          },
        }
      ),
      "PATCH fallido (412 indica carrera); no se reintenta ni se oculta el conflicto."
    );
    required(
      request(
        identity,
        "GET",
        `${path}/history?page=1&pageSize=20`,
        "/incidents/{incidentId}/history",
        "history",
        (data) =>
          pageOf(data, 1, 20, (item) => historyItem(item, identity.subject)) &&
          ["CREATED", "UPDATED"].every((type) => data.data.some((item) => item.eventType === type))
      ),
      "Historial sin los eventos esperados."
    );
  });
}
