import exec from "k6/execution";
import { group, sleep } from "k6";
import { request } from "./http.js";
import {
  catalog,
  historyItem,
  incident,
  map,
  outcome,
  pageOf,
  strongEtag,
  summary,
  userProfile,
} from "./checks.js";
import { isUuid, page, pageSize, pagination, pauseSeconds, staffQuery } from "./config.js";

export function profile(actor, traffic = "nominal") {
  return request(actor, "GET", "/users/me", "/users/me", "profile", (data) => userProfile(data, actor), {
    traffic,
  });
}

export function detail(actor, traffic = "nominal", id = actor.incidentId) {
  return request(
    actor,
    "GET",
    `/incidents/${id}`,
    "/incidents/{incidentId}",
    "detail",
    (data) => incident(data, id, actor.role === "CITIZEN" ? actor.subject : undefined),
    { traffic, extraChecks: { "ETag fuerte opaca": strongEtag } }
  );
}

export function preflight(actor, needsIncident = false) {
  if (!profile(actor, "preflight").ok) exec.test.abort("Preflight: cuenta, rol o verificación incorrectos.");
  if (!needsIncident) return {};
  const fixture = detail(actor, "preflight");
  if (!fixture.ok) exec.test.abort("Preflight: fixture inexistente, ajeno o detalle inválido.");
  return { reporterId: fixture.data.reporter.id };
}

export function categories(actor) {
  return request(actor, "GET", "/categories", "/categories", "categories", catalog);
}

export function departments(actor) {
  return request(actor, "GET", "/departments", "/departments", "departments", (data) =>
    catalog(data, "department")
  );
}

export function mine(actor) {
  return request(actor, "GET", `/incidents/mine?${pagination}`, "/incidents/mine", "mine", (data) =>
    pageOf(data, page, pageSize, summary)
  );
}

export function staffList(actor, search = staffQuery()) {
  const filters = Object.fromEntries(
    search.split("&").map((pair) => pair.split("=").map(decodeURIComponent))
  );
  return request(actor, "GET", `/incidents?${search}`, "/incidents", "staff_list", (data) =>
    pageOf(
      data,
      page,
      pageSize,
      (item) =>
        summary(item) &&
        item.status === filters.status &&
        (!filters.priority || item.priority === filters.priority) &&
        (!filters.categoryId || (item.category && item.category.id === filters.categoryId))
    )
  );
}

export function history(actor, id = actor.incidentId, traffic = "nominal") {
  return request(
    actor,
    "GET",
    `/incidents/${id}/history?${pagination}`,
    "/incidents/{incidentId}/history",
    "history",
    (data) =>
      pageOf(data, page, pageSize, (item) =>
        historyItem(item, actor.role === "CITIZEN" ? actor.subject : actor.reporterId)
      ),
    { traffic }
  );
}

export function reviewOutcome(actor) {
  return request(
    actor,
    "GET",
    `/incidents/${actor.incidentId}/review-outcome`,
    "/incidents/{incidentId}/review-outcome",
    "review_outcome",
    (data) => outcome(data, actor.incidentId)
  );
}

export function mapRead(actor, viewport) {
  return request(actor, "GET", `/map/incidents?${viewport.search}`, "/map/incidents", "map", (data) =>
    map(data, viewport.expectedId)
  );
}

export function smoke(actor, viewport, search) {
  group("lectura por rol", () => {
    profile(actor);
    categories(actor);
    departments(actor);
    mine(actor);
    detail(actor);
    history(actor);
    reviewOutcome(actor);
    if (actor.role !== "CITIZEN") staffList(actor, search);
  });
  group("mapa: una consulta", () => mapRead(actor, viewport));
}

export function weightedRead(actor, weights, search) {
  const operations = [
    () => mine(actor),
    () => detail(actor),
    () => history(actor),
    () => categories(actor),
    () => departments(actor),
    () => staffList(actor, search),
  ];
  // Ciclo determinista de 100 iteraciones, desfasado entre VUs; no crea etiquetas por identidad.
  const slot = (__ITER * 37 + (__VU - 1) * 17) % 100;
  let cumulative = 0;
  for (let i = 0; i < weights.length; i += 1) {
    cumulative += weights[i];
    if (slot < cumulative) {
      group("consulta representativa", operations[i]);
      break;
    }
  }
  sleep(pauseSeconds);
}

export function internalPage(actor, resource, itemCheck) {
  return request(
    actor,
    "GET",
    `/incidents/${actor.incidentId}/${resource}?${pagination}`,
    `/incidents/{incidentId}/${resource}`,
    resource.replace(/-/g, "_"),
    (data) =>
      pageOf(
        data,
        page,
        pageSize,
        (item) => isUuid(item.id) && item.incidentId === actor.incidentId && itemCheck(item)
      )
  );
}
