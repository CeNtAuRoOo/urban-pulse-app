import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { offline, response, syntheticToken } from "./harness.mjs";

const { api: checks } = await offline("lib/checks.js");
const id = "42126143-821b-4dce-81ce-636b9752678e";
const owner = "11111111-1111-4111-8111-111111111111";
const staff = "22222222-2222-4222-8222-222222222222";
const date = "2026-11-10T19:10:40.166261Z";
const etag = `"sha256-${"a".repeat(64)}"`;
const newEtag = `"sha256-${"b".repeat(64)}"`;
const clone = structuredClone;
const fixture = (name) =>
  JSON.parse(readFileSync(new URL(`../../docs/api/examples/${name}`, import.meta.url), "utf8"));
const marker = fixture("maps-incident.json");
const cluster = fixture("maps-cluster.json");
const person = { id: owner, displayName: "Informante sintético" };
const category = { id, name: "Alumbrado", description: null, active: true };
const department = { ...category, code: "TEST" };
const profile = {
  ...person,
  email: "fixture@example.test",
  emailVerified: true,
  active: true,
  createdAt: date,
  roles: ["CITIZEN"],
  departmentMemberships: [],
};
const summary = {
  id,
  title: "Aviso sintético",
  location: { coordinates: { latitude: 36.72, longitude: -4.42 } },
  status: "REPORTED",
  version: 0,
  createdAt: date,
  updatedAt: date,
};
const detail = { ...summary, description: "Descripción sintética del aviso", reporter: person };
const event = {
  id,
  eventType: "CREATED",
  occurredAt: date,
  summary: "Incidencia registrada.",
  actor: person,
  details: { version: "0" },
};
const review = {
  decision: "VALIDATE",
  reviewedAt: date,
  resultingStatus: "VALIDATED",
  reason: null,
  responsible: "Ayuntamiento",
};
const outcome = { incidentId: id, status: "VALIDATED", informationPending: false, latestReview: review };
const page = (data) => ({
  data,
  pagination: { page: 1, pageSize: 20, totalItems: data.length, totalPages: data.length ? 1 : 0 },
});

test("Mapas: ambos ejemplos versionados, vacío, mezcla, categoría/prioridad y marcador propio", () => {
  for (const data of [
    marker,
    cluster,
    { items: [], count: 0, totalIncidents: 0 },
    { items: [...marker.items, ...cluster.items], count: 2, totalIncidents: 3 },
  ])
    assert.equal(checks.map(data), true);
  const full = clone(marker);
  Object.assign(full.items[0], {
    category: { id, name: "Alumbrado" },
    priority: "HIGH",
    coordinatePrecision: "EXACT",
    canAccessPrivateDetail: true,
  });
  assert.equal(checks.map(full, id), true);
  assert.equal(checks.map(full, owner), false);
  assert.equal(checks.map(cluster, id), false);
});

const mapMutations = {
  "categoría filtra secreto": (d) => {
    d.items[0].category = { id, name: "Alumbrado", internalSecret: "sintético" };
  },
  "categoría sin nombre": (d) => {
    d.items[0].category = { id };
  },
  "categoría con nombre no textual": (d) => {
    d.items[0].category = { id, name: {} };
  },
  "categoría sin UUID": (d) => {
    d.items[0].category = { id: "incorrecto", name: "Alumbrado" };
  },
  "categoría null explícita": (d) => {
    d.items[0].category = null;
  },
  "coordenadas con campo privado": (d) => {
    d.items[0].coordinates.privatePosition = "sintético";
  },
  "precisión privada no contractual": (d) => {
    d.items[0].coordinates.accuracyMeters = 1;
  },
  "latitud fuera de WGS84": (d) => {
    d.items[0].coordinates.latitude = 91;
  },
  "longitud no finita": (d) => {
    d.items[0].coordinates.longitude = Infinity;
  },
  "coordenadas ausentes": (d) => {
    delete d.items[0].coordinates;
  },
  "marcador filtra correo": (d) => {
    d.items[0].email = "private@example.test";
  },
  "raíz desconocida": (d) => {
    d.internal = {};
  },
  "título inseguro": (d) => {
    d.items[0].title = "Nombre real";
  },
  "estado futuro inventado": (d) => {
    d.items[0].status = "REOPENED";
  },
  "prioridad inválida": (d) => {
    d.items[0].priority = "URGENT";
  },
  "prioridad null explícita": (d) => {
    d.items[0].priority = null;
  },
  "fecha inválida": (d) => {
    d.items[0].createdAt = "2026-02-30T12:00:00Z";
  },
  "fecha no temporal": (d) => {
    d.items[0].createdAt = "mañana";
  },
  "precisión incoherente": (d) => {
    d.items[0].coordinatePrecision = "EXACT";
  },
  "permiso no booleano": (d) => {
    d.items[0].canAccessPrivateDetail = "false";
  },
  "count incorrecto": (d) => {
    d.count = 2;
  },
  "total incorrecto": (d) => {
    d.totalIncidents = 0;
  },
  "total no entero": (d) => {
    d.totalIncidents = 1.5;
  },
};
for (const [label, mutate] of Object.entries(mapMutations))
  test(`Mapas rechaza ${label}`, () => {
    const data = clone(marker);
    mutate(data);
    assert.equal(checks.map(data), false);
  });
for (const [label, mutate] of Object.entries({
  "identidad en cluster": (d) => {
    d.items[0].incidentId = id;
  },
  "coordenadas anidadas privadas": (d) => {
    d.items[0].coordinates.email = "private@example.test";
  },
  "recuento menor de dos": (d) => {
    d.items[0].incidentCount = 1;
    d.totalIncidents = 1;
  },
  "recuento fraccionario": (d) => {
    d.items[0].incidentCount = 2.5;
    d.totalIncidents = 2.5;
  },
  "ID de grupo inválido": (d) => {
    d.items[0].clusterId = "incorrecto";
  },
  "campo obligatorio ausente": (d) => {
    delete d.items[0].clusterId;
  },
}))
  test(`Cluster rechaza ${label}`, () => {
    const data = clone(cluster);
    mutate(data);
    assert.equal(checks.map(data), false);
  });
test("Mapas: límite de 1000 representaciones y entradas malformadas", () => {
  assert.equal(
    checks.map({ items: Array(1000).fill(marker.items[0]), count: 1000, totalIncidents: 1000 }),
    true
  );
  assert.equal(
    checks.map({ items: Array(1001).fill(marker.items[0]), count: 1001, totalIncidents: 1001 }),
    false
  );
  for (const value of [null, [], {}, { items: [null], count: 1, totalIncidents: 1 }])
    assert.equal(checks.map(value), false);
  for (const key of Object.keys(marker.items[0])) {
    const data = clone(marker);
    delete data.items[0][key];
    assert.equal(checks.map(data), false, key);
  }
});

test("Historial: cinco eventos, detalles legítimos, actor omitido y mapas históricos vacíos", () => {
  for (const [eventType, details] of [
    ["CREATED", { version: "0" }],
    ["UPDATED", { version: "123" }],
    ["REVIEWED", { decision: "REJECT", reason: "Motivo público" }],
    ["REVIEWED", { decision: "VALIDATE" }],
    ["PRIORITY_CHANGED", { priority: "HIGH" }],
    ["INFORMATION_RESPONDED", {}],
  ]) {
    const item = { ...clone(event), eventType, details };
    delete item.actor;
    assert.equal(checks.historyItem(item, owner), true);
    assert.equal(checks.historyItem({ ...item, details: {} }, owner), true);
    if (["CREATED", "UPDATED", "INFORMATION_RESPONDED"].includes(eventType)) {
      assert.equal(checks.historyItem({ ...item, actor: person }, owner), true);
    }
  }
});
for (const [label, mutate] of Object.entries({
  "email en actor": (d) => {
    d.actor.email = "private@example.test";
  },
  "roles en actor": (d) => {
    d.actor.roles = ["ADMIN"];
  },
  "pertenencias en actor": (d) => {
    d.actor.departmentMemberships = [];
  },
  "actor sin displayName": (d) => {
    delete d.actor.displayName;
  },
  "actor municipal": (d) => {
    d.actor.id = staff;
  },
  "actor null explícito": (d) => {
    d.actor = null;
  },
  "actor en REVIEWED": (d) => {
    d.eventType = "REVIEWED";
    d.details = {};
  },
  "actor en PRIORITY_CHANGED": (d) => {
    d.eventType = "PRIORITY_CHANGED";
    d.details = {};
  },
  "details privados": (d) => {
    d.details.email = "private@example.test";
  },
  "details ausentes": (d) => {
    delete d.details;
  },
  "versión numérica": (d) => {
    d.details.version = 1;
  },
  "clave de otro evento": (d) => {
    d.details.priority = "HIGH";
  },
  "motivo interno VALIDATE": (d) => {
    delete d.actor;
    d.eventType = "REVIEWED";
    d.details = { decision: "VALIDATE", reason: "privado" };
  },
  "campo raíz privado": (d) => {
    d.internal = "privado";
  },
}))
  test(`Historial rechaza ${label}`, () => {
    const data = clone(event);
    mutate(data);
    assert.equal(checks.historyItem(data, owner), false);
  });
test("Historial con actor exige informante conocido también al leer como personal", () => {
  assert.equal(checks.historyItem(event), false);
  assert.equal(checks.historyItem(event, staff), false);
  assert.equal(checks.historyItem(event, owner), true);
});

test("Resultado público: null legítimos, tres decisiones y snapshot histórico", () => {
  assert.equal(checks.outcome({ ...outcome, latestReview: null }, id), true);
  for (const [decision, resultingStatus, reason] of [
    ["VALIDATE", "VALIDATED", null],
    ["REJECT", "REJECTED", "Motivo público"],
    ["REQUEST_INFORMATION", "AWAITING_INFORMATION", "Faltan datos"],
  ]) {
    assert.equal(
      checks.outcome({ ...outcome, latestReview: { ...review, decision, resultingStatus, reason } }, id),
      true
    );
    assert.equal(
      checks.outcome(
        { ...outcome, latestReview: { ...review, decision, resultingStatus: null, reason: null } },
        id
      ),
      true
    );
  }
});
for (const [label, mutate] of Object.entries({
  "email en revisión": (d) => {
    d.latestReview.email = "private@example.test";
  },
  "identidad municipal": (d) => {
    d.latestReview.reviewedBy = person;
  },
  "motivo privado VALIDATE": (d) => {
    d.latestReview.reason = "privado";
  },
  "snapshot fuera del enum": (d) => {
    d.latestReview.resultingStatus = "CLOSED";
  },
  "responsable personal": (d) => {
    d.latestReview.responsible = "Nombre municipal";
  },
  "fecha inválida": (d) => {
    d.latestReview.reviewedAt = "ayer";
  },
  "decisión desconocida": (d) => {
    d.latestReview.decision = "ASSIGN";
  },
  "pendencia incoherente": (d) => {
    d.informationPending = true;
  },
  "ID distinto": (d) => {
    d.incidentId = owner;
  },
  "correo raíz": (d) => {
    d.email = "private@example.test";
  },
}))
  test(`Resultado público rechaza ${label}`, () => {
    const data = clone(outcome);
    mutate(data);
    assert.equal(checks.outcome(data, id), false);
  });
test("Resultado público exige todas las claves ALWAYS, incluidas las nulas", () => {
  for (const key of Object.keys(review)) {
    const data = clone(outcome);
    delete data.latestReview[key];
    assert.equal(checks.outcome(data, id), false, key);
  }
  for (const key of Object.keys(outcome)) {
    const data = clone(outcome);
    delete data[key];
    assert.equal(checks.outcome(data, id), false, key);
  }
});

test("ETag: formato SHA-256 minúsculo, cabecera sin distinguir mayúsculas", () => {
  assert.equal(checks.strongEtag(response({}, 200, { ETag: etag })), true);
  assert.equal(checks.strongEtag({ headers: { etag } }), true);
  for (const value of [
    '"cualquier-cosa"',
    '"0"',
    `W/${etag}`,
    etag.toUpperCase(),
    ` ${etag}`,
    `${etag}, ${etag}`,
    `"sha256-${"a".repeat(63)}"`,
    `"sha256-${"a".repeat(65)}"`,
    "",
    [etag],
  ]) {
    assert.equal(checks.strongEtag({ headers: { ETag: value } }), false);
  }
  for (const value of [null, {}, { headers: {} }, { headers: { ETag: etag, etag } }])
    assert.equal(checks.strongEtag(value), false);
});
test("Incidencia: opcionales legítimos y proyecciones anidadas estrictas", () => {
  assert.equal(checks.incident(detail, id, owner), true);
  const full = {
    ...clone(detail),
    category,
    assignedDepartment: department,
    priority: "HIGH",
    resolvedAt: date,
    closedAt: date,
  };
  full.location.coordinates.accuracyMeters = 0;
  assert.equal(checks.incident(full, id, owner), true);
  for (const path of [
    ["reporter"],
    ["location"],
    ["location", "coordinates"],
    ["category"],
    ["assignedDepartment"],
  ]) {
    const data = clone(full);
    let target = data;
    for (const key of path) target = target[key];
    target.email = "private@example.test";
    assert.equal(checks.incident(data, id, owner), false, path.join("."));
  }
  assert.equal(checks.incident({ ...detail, reporter: { id: owner } }, id), false);
  assert.equal(checks.incident({ ...detail, category: null }, id), false);
  assert.equal(checks.incident(detail, id, staff), false);
  assert.equal(checks.summary(summary), true);
  assert.equal(checks.summary(detail), false);
});
test("Páginas y catálogos: vacío legítimo, null contractual y rechazo de extras", () => {
  assert.equal(checks.pageOf(page([]), 1, 20, checks.summary), true);
  assert.equal(checks.pageOf(page([summary]), 1, 20, checks.summary), true);
  assert.equal(
    checks.pageOf(
      { data: [], pagination: { page: 2, pageSize: 20, totalItems: 1, totalPages: 1 } },
      2,
      20,
      checks.summary
    ),
    true
  );
  assert.equal(checks.pageOf({ ...page([]), internal: true }, 1, 20, checks.summary), false);
  assert.equal(checks.catalog([category]), true);
  assert.equal(checks.catalog([department], "department"), true);
  assert.equal(checks.catalog([]), true);
  assert.equal(checks.catalog([{ ...category, email: "private@example.test" }]), false);
});
test("Tramitación interna: DTO completo, nullable histórico y ASSISTED legítimo en lectura", () => {
  const rows = {
    reviews: {
      id,
      incidentId: id,
      decision: "VALIDATE",
      reason: "Motivo histórico interno",
      reviewedBy: person,
      reviewedAt: date,
      resultingStatus: null,
    },
    "priority-decisions": {
      id,
      incidentId: id,
      priority: "HIGH",
      source: "ASSISTED",
      justification: "Justificación",
      decidedBy: person,
      decidedAt: date,
    },
    "information-responses": {
      id,
      incidentId: id,
      reviewId: id,
      message: "Respuesta",
      respondedBy: person,
      respondedAt: date,
    },
  };
  for (const [resource, row] of Object.entries(rows)) {
    assert.equal(checks.processingItem(resource, row), true);
    assert.equal(checks.processingItem(resource, { ...row, extra: true }), false);
    for (const key of Object.keys(row)) {
      const data = clone(row);
      delete data[key];
      assert.equal(checks.processingItem(resource, data), false, key);
    }
  }
});
test("Duplicados: vacío, candidato exigido y reasons tipados", () => {
  const data = [{ candidateIncidentId: owner, score: 0.5, reasons: ["Proximidad"] }];
  assert.equal(checks.duplicateCandidates(data, id, owner), true);
  assert.equal(checks.duplicateCandidates([], id), true);
  assert.equal(checks.duplicateCandidates([], id, owner), false);
  assert.equal(checks.duplicateCandidates([{ ...data[0], reasons: [{}] }], id), false);
  assert.equal(checks.duplicateCandidates([...data, ...data], id), false);
});

test("Configuración: defaults solo en ausencia; vacío se rechaza también en opcionales", async () => {
  const { api, context } = await offline("lib/config.js");
  assert.equal(api.choice("ACTOR", "CITIZEN", ["CITIZEN"]), "CITIZEN");
  assert.equal(api.uuid("CATEGORY_ID", false), undefined);
  for (const empty of ["", " "]) {
    context.__ENV.ACTOR = empty;
    assert.throws(() => api.choice("ACTOR", "CITIZEN", ["CITIZEN"]), /vacío/);
    context.__ENV.CATEGORY_ID = empty;
    assert.throws(() => api.uuid("CATEGORY_ID", false), /UUID/);
    context.__ENV.INCIDENT_PRIORITY = empty;
    assert.throws(() => api.staffQuery(), /vacío/);
  }
  for (const BASE_URL of [
    "",
    "http://localhost:8080/path",
    "https://example.test",
    "http://localhost:8080@other.test",
  ]) {
    await assert.rejects(offline("lib/config.js", { BASE_URL }));
  }
  await assert.rejects(offline("lib/config.js", { K6_ENVIRONMENT: "" }));
  assert.equal(
    (
      await offline("lib/config.js", {
        BASE_URL: "https://example.test",
        K6_ALLOW_REMOTE: "1",
        K6_REMOTE_ORIGIN: "https://example.test",
      })
    ).api.baseUrl,
    "https://example.test"
  );
});
test("Perfiles: límites, flags, pesos y defaults sin opcionales", async () => {
  const { api, context } = await offline("lib/profiles.js");
  assert.equal(api.readProfile().scenario.executor, "constant-vus");
  assert.equal(
    api.readWeights("CITIZEN").reduce((sum, weight) => sum + weight, 0),
    100
  );
  context.__ENV.READ_WEIGHTS = "";
  assert.throws(() => api.readWeights("CITIZEN"), /READ_WEIGHTS/);
  delete context.__ENV.READ_WEIGHTS;
  context.__ENV.VUS = "51";
  assert.throws(() => api.readProfile(), /VUS/);
  delete context.__ENV.VUS;
  for (const kind of ["stress", "spike", "soak"]) {
    context.__ENV.READ_PROFILE = kind;
    delete context.__ENV.K6_ALLOW_HEAVY;
    assert.throws(() => api.readProfile(), /K6_ALLOW_HEAVY/);
    context.__ENV.K6_ALLOW_HEAVY = "1";
    const profile = api.readProfile();
    if (kind !== "soak") assert.equal(profile.scenario.stages.at(-1).target, 0);
    else assert.equal(profile.scenario.duration, "900s");
  }
});
test("JWT: validación local de caducidad; no se verifica firma en el doble", async () => {
  const { api, context } = await offline("lib/auth.js", { CITIZEN_TOKEN: syntheticToken(owner) });
  assert.equal(api.actor(60).subject, owner);
  context.__ENV.CITIZEN_TOKEN = syntheticToken(owner, { exp: Math.floor(Date.now() / 1000) + 100 });
  assert.throws(() => api.actor(60), /vigencia/);
  context.__ENV.CITIZEN_TOKEN = "malformado";
  assert.throws(() => api.actor(60), /malformado/);
});

test("HTTP offline: estado exacto, semántica, JSON inválido, diagnósticos y preflight separados", async () => {
  let next = response({ valid: true });
  const run = await offline("lib/http.js", {}, () => next);
  const request = (options = {}, validate = (data) => data.valid === true) =>
    run.api.request({ token: "sintético" }, "GET", "/users/me", "/users/me", "profile", validate, options);
  assert.equal(request().ok, true);
  assert.equal(run.calls[0].params.responseCallback.statuses[0], 200);
  assert.equal(run.calls[0].params.redirects, 0);
  next = response({ valid: false });
  assert.equal(request().ok, false);
  next = response({ valid: true });
  assert.equal(
    request({
      extraChecks: {
        throws: () => {
          throw new Error("sintético");
        },
      },
    }).ok,
    false
  );
  next = {
    ...response(null),
    json() {
      throw new Error("JSON inválido");
    },
  };
  assert.equal(request().ok, false);
  next = response({ status: 429, title: "Demasiadas peticiones" }, 429, {
    "Content-Type": "application/problem+json",
    "Retry-After": "5",
  });
  assert.equal(request().ok, false);
  assert.equal(run.checks.at(-1).tags.traffic, "diagnostic");
  assert.equal(run.checks.at(-1).results.every(Boolean), true);
  assert.equal(run.samples.find((s) => s.name === "unexpected_responses").tags.kind, "rate_limit");
  const nominalCount = run.samples.length;
  assert.equal(request({ traffic: "preflight" }).ok, false);
  assert.equal(run.samples.length, nominalCount);
  assert.deepEqual(
    run.samples.filter((s) => s.name === "semantic_failures").map((s) => s.value),
    [false, true, true, true, true]
  );
  assert.equal(
    run.calls.every((c) => c.params.tags.name === "GET /api/v1/users/me"),
    true
  );
});
test("Thresholds: smoke funcional estricto y load/arrival conservan p95/errores/drops", async () => {
  const { api } = await offline("lib/metrics.js");
  const smoke = api.optionsFor({}, ["detail"], {}, { smoke: true });
  assert.equal(smoke.thresholds["checks{traffic:nominal}"][0], "rate==1");
  assert.equal(smoke.thresholds.semantic_failures[0], "rate==0");
  assert.equal(
    Object.keys(smoke.thresholds).some((key) => key.includes("duration")),
    false
  );
  const load = api.optionsFor({}, ["mine"], { dropped_iterations: ["count==0"] });
  assert.equal(load.thresholds["http_req_duration{traffic:nominal,operation:mine}"][0], "p(95)<500");
  assert.equal(load.thresholds["http_req_failed{traffic:nominal}"][0], "rate<0.01");
  assert.equal(load.thresholds.dropped_iterations[0], "count==0");
  assert.equal(load.systemTags.includes("url"), false);
});

test("Privacidad HTTP: no-store debe ser una directiva real, no un substring", async () => {
  let cacheControl;
  const run = await offline("lib/http.js", {}, () => response({}, 200, { "Cache-Control": cacheControl }));
  for (const value of ["no-cache, no-store, max-age=0", "NO-STORE", "private, no-store"]) {
    cacheControl = value;
    assert.equal(
      run.api.request({ token: "sintético" }, "GET", "/users/me", "/users/me", "profile", () => true).ok,
      true
    );
  }
  for (const value of ["", "x-no-store", "no-store=false", "private", "public, max-age=60"]) {
    cacheControl = value;
    assert.equal(
      run.api.request({ token: "sintético" }, "GET", "/users/me", "/users/me", "profile", () => true).ok,
      false
    );
  }
});
test("Arrival: una petición por iteración y ninguna pausa en el doble", async () => {
  const run = await offline("scenarios/arrival-rate-read.js", { CITIZEN_TOKEN: syntheticToken(owner) }, () =>
    response(page([]))
  );
  run.api.default();
  assert.equal(run.calls.length, 1);
  assert.equal(run.sleeps.length, 0);
  assert.equal(run.api.options.scenarios.arrival.maxVUs, 5);
  assert.equal(run.api.options.thresholds.dropped_iterations[0], "count==0");
});
test("Mapas: un VU, pausas mínimas y viewport validado antes del doble HTTP", async () => {
  const run = await offline("scenarios/map-read.js", { CITIZEN_TOKEN: syntheticToken(owner) }, () =>
    response(marker)
  );
  run.api.default();
  assert.equal(run.api.options.scenarios.map.vus, 1);
  assert.equal(run.sleeps[0], 5);
  await assert.rejects(
    offline("scenarios/map-read.js", { CITIZEN_TOKEN: syntheticToken(owner), MAP_INTERVAL_SECONDS: "1" })
  );
  await assert.rejects(
    offline("scenarios/map-read.js", { CITIZEN_TOKEN: syntheticToken(owner), MAP_ZOOM: "18" }),
    /Viewport/
  );
});
test("Smoke personal: setup propaga informante y el historial detecta actor municipal", async () => {
  let exposedActor = person;
  const run = await offline(
    "scenarios/smoke-read.js",
    { ACTOR: "OPERATOR", OPERATOR_TOKEN: syntheticToken(staff), OPERATOR_INCIDENT_ID: id },
    (call) => {
      const op = call.params.tags.operation;
      if (op === "profile") return response({ ...profile, id: staff, roles: ["CITIZEN", "OPERATOR"] });
      if (op === "detail") return response(detail, 200, { ETag: etag });
      if (op === "history") return response(page([{ ...event, actor: exposedActor }]));
      if (op === "map") return response(marker);
      if (op === "review_outcome") return response(outcome);
      if (op === "categories" || op === "departments") return response([]);
      return response(page([]));
    }
  );
  const data = run.api.setup();
  assert.equal(data.reporterId, owner);
  run.api.default(data);
  assert.equal(
    run.samples.filter((s) => s.name === "semantic_failures").every((s) => s.value === false),
    true
  );
  exposedActor = { id: staff, displayName: "Personal sintético" };
  run.api.default(data);
  assert.equal(
    run.samples.some((s) => s.name === "semantic_failures" && s.tags.operation === "history" && s.value),
    true
  );
});
test("Escritura offline: un POST, If-Match literal del GET, historial y aborto sin retry en 412", async () => {
  const env = { CITIZEN_TOKEN: syntheticToken(owner), K6_ENABLE_WRITES: "1", RUN_ID: "offline-123" };
  // La ETag del GET difiere de la del POST y de la respuesta PATCH.
  const getEtag = `"sha256-${"c".repeat(64)}"`;
  const run = await offline("scenarios/smoke-incidents-write.js", env, (call) => {
    if (call.method === "POST")
      return response({ ...detail, title: JSON.parse(call.body).title }, 201, {
        ETag: etag,
        Location: `/api/v1/incidents/${id}`,
      });
    if (call.method === "PATCH")
      return response({ ...detail, description: JSON.parse(call.body).description, version: 1 }, 200, {
        ETag: newEtag,
      });
    if (call.url.includes("/history"))
      return response(page([event, { ...event, eventType: "UPDATED", details: { version: "1" } }]));
    return response(detail, 200, { ETag: getEtag });
  });
  run.api.default();
  assert.equal(run.calls.length, 4);
  assert.equal(run.calls.filter((c) => c.method === "POST").length, 1);
  assert.equal(run.calls.find((c) => c.method === "PATCH").params.headers["If-Match"], getEtag);
  assert.equal(
    run.samples.filter((s) => s.name === "semantic_failures").every((s) => !s.value),
    true
  );
  const conflict = await offline("scenarios/smoke-incidents-write.js", env, (call) => {
    if (call.method === "POST")
      return response({ ...detail, title: JSON.parse(call.body).title }, 201, {
        ETag: etag,
        Location: `/api/v1/incidents/${id}`,
      });
    if (call.method === "PATCH")
      return response({ status: 412, title: "Conflicto" }, 412, {
        "Content-Type": "application/problem+json",
      });
    return response(detail, 200, { ETag: getEtag });
  });
  assert.throws(() => conflict.api.default(), /ABORT: PATCH/);
  assert.equal(conflict.calls.length, 3);
  assert.equal(conflict.calls.filter((c) => c.method === "POST").length, 1);
  assert.equal(conflict.calls[2].params.headers["If-Match"], getEtag);
  conflict.context.__ITER = 1;
  assert.throws(() => conflict.api.default(), /ABORT: Smoke/);
  assert.equal(conflict.calls.length, 3);
});
test("Escritura rechaza flags, RUN_ID vacío/inválido y acepta UUID o identificador 8..64", async () => {
  const env = { CITIZEN_TOKEN: syntheticToken(owner), K6_ENABLE_WRITES: "1", RUN_ID: "offline-123" };
  await assert.rejects(
    offline("scenarios/smoke-incidents-write.js", { ...env, K6_ENABLE_WRITES: "" }),
    /K6_ENABLE_WRITES/
  );
  for (const RUN_ID of ["", "corto", "con espacios", "x".repeat(65)])
    await assert.rejects(offline("scenarios/smoke-incidents-write.js", { ...env, RUN_ID }), /RUN_ID/);
  for (const RUN_ID of [id, "offline-123", "x".repeat(64)])
    await offline("scenarios/smoke-incidents-write.js", { ...env, RUN_ID });
});

test("Perfil: identidad propia completa, pertenencias históricas y rechazo de campos privados", () => {
  const actor = { subject: owner, role: "CITIZEN" };
  assert.equal(checks.userProfile(profile, actor), true);
  const membership = { department, role: "TECHNICIAN", joinedAt: date, leftAt: null };
  const full = { ...profile, departmentMemberships: [membership, { ...membership, leftAt: date }] };
  assert.equal(checks.userProfile(full, actor), true);
  for (const mutate of [
    (d) => {
      d.passwordHash = "sintético";
    },
    (d) => {
      d.tokenVersion = 0;
    },
    (d) => {
      delete d.email;
    },
    (d) => {
      d.roles.push("TECHNICIAN");
    },
    (d) => {
      d.roles.push("CITIZEN");
    },
    (d) => {
      d.roles.push("ADMIN");
    },
    (d) => {
      d.departmentMemberships[0].email = "private@example.test";
    },
    (d) => {
      d.departmentMemberships[0].department.secret = "sintético";
    },
    (d) => {
      d.departmentMemberships[0].role = "ADMIN";
    },
    (d) => {
      delete d.departmentMemberships[0].leftAt;
    },
  ]) {
    const invalid = clone(full);
    mutate(invalid);
    assert.equal(checks.userProfile(invalid, actor), false);
  }
});

test("Todos los perfiles de lectura enlazan imports; setup conserva el informante y no carga Mapas", async () => {
  for (const READ_PROFILE of ["load", "stress", "spike", "soak"]) {
    const run = await offline(
      "scenarios/read.js",
      {
        CITIZEN_TOKEN: syntheticToken(owner),
        CITIZEN_INCIDENT_ID: id,
        READ_PROFILE,
        K6_ALLOW_HEAVY: "1",
        READ_WEIGHTS: "0,0,100,0,0,0",
      },
      (call) => {
        if (call.params.tags.operation === "profile") return response(profile);
        if (call.params.tags.operation === "detail") return response(detail, 200, { ETag: etag });
        return response(page([event]));
      }
    );
    run.api.default(run.api.setup());
    assert.equal(run.calls.length, 3);
    assert.equal(run.calls[2].params.tags.operation, "history");
    assert.equal(
      run.samples.filter((s) => s.name === "semantic_failures").every((s) => !s.value),
      true
    );
    assert.equal(run.sleeps[0], 1);
  }
});

test("Lecturas internas offline: colecciones vacías y duplicados sin nuevas operaciones", async () => {
  for (const STAFF_FLOW of ["processing", "duplicates"]) {
    const run = await offline(
      "scenarios/staff-read.js",
      { OPERATOR_TOKEN: syntheticToken(staff), OPERATOR_INCIDENT_ID: id, STAFF_FLOW },
      (call) => {
        if (call.params.tags.operation === "profile")
          return response({ ...profile, id: staff, roles: ["CITIZEN", "OPERATOR"] });
        if (call.params.tags.operation === "detail") return response(detail, 200, { ETag: etag });
        if (call.params.tags.operation === "review_outcome") return response(outcome);
        return response(STAFF_FLOW === "duplicates" ? [] : page([]));
      }
    );
    run.api.setup();
    run.api.default();
    assert.equal(run.calls.length, STAFF_FLOW === "processing" ? 6 : 3);
    assert.equal(
      run.calls.every((call) => call.method === "GET"),
      true
    );
    assert.equal(
      run.samples.filter((s) => s.name === "semantic_failures").every((s) => !s.value),
      true
    );
  }
});

test("Escritura: preflight de categoría y aborto tras alta inválida sin reintentar", async () => {
  const env = {
    CITIZEN_TOKEN: syntheticToken(owner),
    K6_ENABLE_WRITES: "1",
    RUN_ID: "offline-123",
    CATEGORY_ID: id,
  };
  let active = false;
  const run = await offline("scenarios/smoke-incidents-write.js", env, (call) => {
    if (call.params.tags.operation === "profile") return response(profile);
    if (call.params.tags.operation === "category_fixture") return response({ ...category, active });
    return response({ ...detail, title: JSON.parse(call.body).title }, 201, {
      ETag: '"obsoleta"',
      Location: `/api/v1/incidents/${id}`,
    });
  });
  assert.throws(() => run.api.setup(), /ABORT: CATEGORY_ID/);
  assert.equal(
    run.calls.every((call) => call.method === "GET"),
    true
  );
  active = true;
  run.api.setup();
  assert.throws(() => run.api.default(), /ABORT: Alta/);
  assert.equal(run.calls.filter((call) => call.method === "POST").length, 1);
  assert.equal(
    run.calls.some((call) => call.method === "PATCH"),
    false
  );
});
