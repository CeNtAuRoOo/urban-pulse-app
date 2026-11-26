require("./load-source.cjs");
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { incidentBody, incidentFields } = require("../src/features/incident-form.ts");
const { request, ApiError } = require("../src/services/http.ts");
const { textLength, password } = require("../src/services/validation.ts");
const original = {
  title: "Farola averiada",
  description: "La farola no funciona desde ayer.",
  category: { id: "category-1" },
  location: { coordinates: { latitude: 36.7, longitude: -4.4, accuracyMeters: 2 } },
};
test("PATCH omite categoría conservada y solo transmite cambios", () => {
  assert.deepEqual(
    incidentBody({ ...incidentFields(original), title: "Farola reparada parcialmente" }, original),
    { title: "Farola reparada parcialmente" }
  );
  assert.deepEqual(incidentBody({ ...incidentFields(original), categoryMode: "remove" }, original), {
    categoryId: null,
  });
  assert.deepEqual(
    incidentBody({ ...incidentFields(original), categoryMode: "change", categoryId: "category-2" }, original),
    { categoryId: "category-2" }
  );
  assert.throws(() => incidentBody(incidentFields(original), original), /No hay cambios/);
});
test("Coordenadas completas, números y precisión opcional", () => {
  assert.deepEqual(incidentBody({ ...incidentFields(original), latitude: "37", accuracy: "" }, original), {
    coordinates: { latitude: 37, longitude: -4.4 },
  });
  for (const fields of [{ latitude: "" }, { latitude: "91" }, { longitude: "Infinity" }, { accuracy: "-1" }])
    assert.throws(() => incidentBody({ ...incidentFields(original), ...fields }, original));
});
test("Límites Unicode y contraseña BCrypt en bytes", () => {
  assert.equal(textLength("😀".repeat(150), 5, 150, "Título").length, 300);
  assert.throws(() => textLength("😀".repeat(151), 5, 150, "Título"));
  assert.throws(() => textLength("ab\ud800cd", 5, 150, "Título"));
  assert.throws(() => password("á".repeat(37)));
});
test("Cliente conserva ETag literal, query segura y cabeceras de respuesta", async (t) => {
  t.mock.method(global, "fetch", async (url, options) => {
    assert.equal(url.searchParams.get("email"), "name+tag@example.test");
    assert.equal(options.headers.get("If-Match"), '"sha256-opaque"');
    assert.equal(options.headers.get("Authorization"), "Bearer test-only-token");
    return new Response('{"id":"1"}', {
      status: 200,
      headers: { ETag: '"sha256-next"', Location: "/api/v1/incidents/1" },
    });
  });
  const result = await request(
    "/incidents/1",
    {
      method: "PATCH",
      body: { title: "Corregido" },
      etag: '"sha256-opaque"',
      query: { email: "name+tag@example.test" },
    },
    "test-only-token"
  );
  assert.equal(result.etag, '"sha256-next"');
  assert.equal(result.location, "/api/v1/incidents/1");
});
test("204 y 202 vacíos, POST sin cuerpo, 412 sin reintento y Retry-After", async (t) => {
  let calls = 0;
  t.mock.method(global, "fetch", async (_url, options) => {
    calls++;
    assert.equal(options.body, undefined);
    assert.equal(options.headers.has("Content-Type"), false);
    if (calls <= 2) return new Response(null, { status: calls === 1 ? 204 : 202 });
    return new Response(JSON.stringify({ code: calls === 3 ? "PRECONDITION_FAILED" : "RATE_LIMITED" }), {
      status: calls === 3 ? 412 : 429,
      headers: { "Retry-After": "60" },
    });
  });
  assert.equal((await request("/example", { method: "POST" })).data, undefined);
  assert.equal((await request("/example", { method: "POST" })).status, 202);
  await assert.rejects(request("/example"), (error) => error instanceof ApiError && error.status === 412);
  assert.equal(calls, 3);
  await assert.rejects(request("/example"), (error) => error.status === 429 && error.retryAfter === "60");
});

const { returnTarget, routeKey, incidentHref } = require("../src/services/navigation.ts");
const { permissions } = require("../src/services/permissions.ts");
const { normalizeViewport } = require("../src/maps/types.ts");
test("Retorno contextual conserva filtros y rechaza destinos externos o desconocidos", () => {
  const source = routeKey("/incidents/all", { page: "3", status: "REPORTED", categoryId: "category-1" });
  const url = new URL(incidentHref("incident-1", source), "https://urbanpulse.test");
  assert.equal(returnTarget(url.searchParams.get("from")).href, source);
  assert.equal(returnTarget("/map?selected=incident-1&zoom=14").label, "Volver al mapa");
  assert.equal(returnTarget("/").label, "Volver al inicio");
  for (const path of ["https://example.org", "//example.org", "/profile", "/map\\evil"])
    assert.equal(returnTarget(path).href, "/incidents");
});
test("Permisos globales, titularidad y estados se mantienen separados", () => {
  const citizen = {
    id: "a",
    active: true,
    emailVerified: true,
    roles: ["CITIZEN"],
    departmentMemberships: [{ role: "TECHNICIAN", leftAt: null }],
  };
  const reported = { reporter: { id: "a" }, status: "REPORTED" };
  assert.equal(permissions(citizen, reported).staff, false);
  assert.equal(permissions(citizen, reported).edit, true);
  assert.equal(permissions({ ...citizen, roles: ["CITIZEN", "ADMIN"] }, reported).review, false);
  assert.equal(
    permissions({ ...citizen, roles: ["CITIZEN", "OPERATOR"] }, { ...reported, reporter: { id: "b" } })
      .review,
    true
  );
  assert.equal(permissions(citizen, { ...reported, status: "AWAITING_INFORMATION" }).respond, true);
  assert.equal(permissions(citizen, { ...reported, status: "VALIDATED" }).attachments, false);
  assert.equal(permissions({ ...citizen, active: false }, reported).edit, false);
});
test("Viewport ajusta el zoom al límite de amplitud real del backend", () => {
  const v = normalizeViewport({ minLat: 36, minLon: -5, maxLat: 37, maxLon: -4, zoom: 18 });
  assert.ok(v.maxLon - v.minLon <= 1440 / 2 ** v.zoom);
  assert.ok(v.zoom <= 18);
});
test("Evidencias utilizan multipart sin Content-Type manual y descarga autenticada", async (t) => {
  const form = new FormData();
  form.append("file", new Blob(["test"], { type: "image/png" }), "qa.png");
  t.mock.method(global, "fetch", async (_url, options) => {
    assert.equal(options.headers.get("Authorization"), "Bearer test-token");
    if (options.method === "POST") {
      assert.equal(options.body, form);
      assert.equal(options.headers.has("Content-Type"), false);
      return new Response("{}", { status: 201 });
    }
    assert.match(options.headers.get("Accept"), /image\/png/);
    return new Response(new Blob(["image"], { type: "image/png" }), { status: 200 });
  });
  await request("/incidents/1/attachments", { method: "POST", body: form }, "test-token");
  const result = await request("/incidents/1/attachments/1/content", { responseType: "blob" }, "test-token");
  assert.equal(await result.data.text(), "image");
});

const { UnsavedChanges } = require("../src/services/unsaved-changes.ts");
const { createIncidentHref, returnTarget: formReturn } = require("../src/services/navigation.ts");
test("Cancelación de un formulario conserva los otros borradores de la pantalla", () => {
  const changes = new UnsavedChanges();
  assert.equal(changes.has(), false);
  changes.update("profile", true);
  changes.update("password", true);
  changes.clear("profile");
  assert.equal(changes.has("profile"), false);
  assert.equal(changes.has("password"), true);
  changes.update("password", false);
  assert.equal(changes.has(), false);
  changes.update("incident", true);
  changes.clear();
  assert.equal(changes.has(), false);
});
test("Crear incidencia conserva origen, filtros y paginación y rechaza destinos externos", () => {
  const source = "/incidents/all?page=3&status=REPORTED";
  const link = new URL(createIncidentHref(source), "https://urbanpulse.test");
  assert.equal(link.pathname, "/incidents/new");
  assert.equal(formReturn(link.searchParams.get("from")).href, source);
  assert.equal(formReturn(undefined).href, "/incidents");
  assert.equal(
    new URL(createIncidentHref("https://external.test"), "https://urbanpulse.test").searchParams.get("from"),
    "/incidents"
  );
});

test("Atrás y Adelante conservan el historial y permiten continuar editando", (t) => {
  const { installBrowserNavigationGuard } = require("../src/platform/navigation-guard.web.ts");
  const listeners = new Map();
  let index = 0,
    pending,
    delivered = 0;
  const entries = [{ id: "initial" }];
  const history = {
    get state() {
      return entries[index];
    },
    pushState(state) {
      entries.splice(++index, entries.length, state);
    },
    replaceState(state) {
      entries[index] = state;
    },
    go(delta) {
      index += delta;
      assert.ok(index >= 0 && index < entries.length);
      let stopped = false;
      listeners.get("popstate")?.({
        state: entries[index],
        stopImmediatePropagation() {
          stopped = true;
        },
      });
      if (!stopped) delivered++;
    },
  };
  global.window = undefined;
  t.after(() => {
    delete global.window;
  });
  t.mock.property(global, "window", {
    history,
    addEventListener(name, fn) {
      listeners.set(name, fn);
    },
    removeEventListener(name) {
      listeners.delete(name);
    },
  });
  const changes = new UnsavedChanges();
  const cleanup = installBrowserNavigationGuard({
    changes,
    request(action) {
      pending = action;
    },
  });
  history.pushState({ id: "form" }, "", "/incidents/new");
  changes.update("form", true);
  history.go(-1);
  assert.equal(index, 1);
  assert.equal(delivered, 0);
  assert.equal(history.state.id, "form");
  assert.equal(entries.length, 2);
  // Staying means no action is dispatched; a second Back can still be confirmed.
  pending = undefined;
  history.go(-1);
  changes.clear();
  pending();
  assert.equal(index, 0);
  assert.equal(delivered, 1);
  history.go(1);
  assert.equal(index, 1);
  assert.equal(delivered, 2);
  cleanup();
  assert.equal(listeners.size, 0);
});

const { TemporaryFile, isPickerCacheCopy } = require("../src/services/temporary-file.ts");
test("Fotografía: un fallo conserva la copia para reintentar; éxito/cancelación la liberan una vez", async () => {
  let deleted = 0;
  const file = new TemporaryFile(async () => {
    deleted++;
  });
  await assert.rejects(
    file.use(async () => {
      throw new Error("red");
    }),
    /red/
  );
  assert.equal(deleted, 0);
  await file.use(async () => "subida correcta");
  await file.dispose();
  await file.dispose();
  assert.equal(deleted, 1);
  await assert.rejects(
    file.use(async () => {}),
    /no está seleccionada/
  );
});
test("Abandono durante subida: no borrar mientras el transporte lee la fotografía", async () => {
  let finish,
    deleted = 0;
  const file = new TemporaryFile(async () => {
    deleted++;
  });
  const upload = file.use(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      })
  );
  await file.dispose();
  assert.equal(deleted, 0);
  finish();
  await upload;
  assert.equal(deleted, 1);
});
test("Solo se reconocen copias UUID de DocumentPicker dentro de la caché privada", () => {
  const root = "file:///private/app/cache/",
    uuid = "11111111-2222-4333-8444-555555555555.png";
  assert.equal(isPickerCacheCopy(root + "DocumentPicker/" + uuid, root), true);
  for (const uri of [
    "content://photos/" + uuid,
    "file:///photos/" + uuid,
    root + "DocumentPicker/original.png",
    root + "DocumentPicker/../" + uuid,
    root + "DocumentPicker/" + uuid + "/child",
    root + "DocumentPicker/" + uuid + "%2fchild",
    root + "DocumentPicker/" + uuid + "?original=yes",
  ])
    assert.equal(isPickerCacheCopy(uri, root), false, uri);
  assert.equal(isPickerCacheCopy(root + "DocumentPicker/" + uuid, null), false);
});
test("Guardar un formulario detecta borradores vecinos sin desregistrarlos antes del resultado", () => {
  const forms = new UnsavedChanges();
  forms.update("correction", true);
  forms.update("review", true);
  assert.equal(forms.hasOther("correction"), true);
  assert.equal(forms.hasOther("review"), true);
  forms.clear("correction");
  assert.equal(forms.has("review"), true);
  assert.equal(forms.hasOther("review"), false);
});

test("Limpieza nativa: únicamente borra el archivo temporal adoptado; no directorios ni originales", async () => {
  const source = require("node:fs").readFileSync(require.resolve("../src/platform/files.native.ts"), "utf8");
  const compiled = require("typescript").transpileModule(source, {
    compilerOptions: { module: require("typescript").ModuleKind.CommonJS },
  }).outputText;
  const deleted = [],
    inspected = [];
  const cache = "file:///private/app/cache/";
  const uri = cache + "DocumentPicker/11111111-2222-4333-8444-555555555555.png";
  const fakeFs = {
    cacheDirectory: cache,
    getInfoAsync: async (path) => {
      inspected.push(path);
      return { exists: true, isDirectory: false };
    },
    deleteAsync: async (path) => {
      deleted.push(path);
    },
  };
  const module = { exports: {} };
  new Function("require", "module", "exports", compiled)(
    (name) =>
      name === "expo-file-system/legacy"
        ? fakeFs
        : name === "expo-sharing"
          ? {}
          : require("../src/services/temporary-file.ts"),
    module,
    module.exports
  );
  await module.exports.pickerTemporaryFile("file:///photos/original.png").dispose();
  await module.exports.pickerTemporaryFile("content://photos/original").dispose();
  assert.deepEqual(inspected, []);
  const owned = module.exports.pickerTemporaryFile(uri);
  await owned.dispose();
  await owned.dispose();
  assert.deepEqual(deleted, [uri]);
  fakeFs.getInfoAsync = async () => ({ exists: true, isDirectory: true });
  await module.exports.pickerTemporaryFile(uri).dispose();
  assert.deepEqual(deleted, [uri]);
});
