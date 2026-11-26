/** Isolated component regressions. No request reaches the real API.
 * First export the web app, then run this script from frontend/ with an existing Playwright install.
 * UI_EXPORT_DIR=dist; PLAYWRIGHT_MODULE=playwright; CHROME_EXECUTABLE is optional.
 */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const origin = "http://localhost:8888";
const api = "http://localhost:8080/api/v1";
const exported = path.resolve(process.env.UI_EXPORT_DIR || "dist");
const id = "00000000-0000-4000-8000-000000000001";
const category = {
  id: "00000000-0000-4000-8000-000000000002",
  name: "Categoría aislada",
  active: true,
  description: null,
};
const user = {
  id: "admin-test",
  displayName: "Administración de prueba",
  email: "admin@example.test",
  active: true,
  emailVerified: true,
  roles: ["CITIZEN", "ADMIN"],
  departmentMemberships: [],
  createdAt: "2026-01-01T00:00:00Z",
};
const baseIncident = () => ({
  id,
  title: "Incidencia de prueba aislada",
  description: "Descripción del escenario de regresión.",
  status: "REPORTED",
  category,
  version: 1,
  reporter: { id: "another-user", displayName: "Informante" },
  location: { coordinates: { latitude: 36.72, longitude: -4.42 } },
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
});
let browser;
async function fixture() {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  await ctx.grantPermissions(["local-network-access"], { origin });
  await ctx.addInitScript(
    (session) => {
      sessionStorage.setItem("urbanpulse.session.v1", JSON.stringify(session));
      window.pickerUrls = { created: [], revoked: [] };
      const create = URL.createObjectURL.bind(URL),
        revoke = URL.revokeObjectURL.bind(URL);
      URL.createObjectURL = (file) => {
        const uri = create(file);
        window.pickerUrls.created.push(uri);
        return uri;
      };
      URL.revokeObjectURL = (uri) => {
        window.pickerUrls.revoked.push(uri);
        revoke(uri);
      };
    },
    { user, accessToken: "isolated-test-token", expiresAt: Date.now() + 3600000 }
  );
  await ctx.route(origin + "/**", async (route) => {
    const file = path.resolve(exported, "." + decodeURIComponent(new URL(route.request().url()).pathname));
    assert.ok(file.startsWith(exported + path.sep) || file === exported);
    const target =
      fs.existsSync(file) && fs.statSync(file).isFile() ? file : path.join(exported, "index.html");
    await route.fulfill({
      path: target,
      contentType:
        {
          ".html": "text/html",
          ".js": "application/javascript",
          ".css": "text/css",
          ".ttf": "font/ttf",
          ".png": "image/png",
        }[path.extname(target)] || "application/octet-stream",
    });
  });
  await ctx.route("https://tile.openstreetmap.org/**", (route) => route.abort());
  const state = {
    incident: baseIncident(),
    writes: [],
    reads: 0,
    failPatch: false,
    failReview: false,
    failUpload: false,
  };
  await ctx.route(api + "/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      endpoint = url.pathname.slice("/api/v1".length);
    const headers = {
      "Access-Control-Allow-Origin": origin,
      "Access-Control-Allow-Headers": "*",
      "Access-Control-Allow-Methods": "*",
      "Access-Control-Expose-Headers": "ETag",
      "Content-Type": "application/json",
    };
    const reply = (body, status = 200, more = {}) =>
      route.fulfill({ status, headers: { ...headers, ...more }, body: JSON.stringify(body) });
    if (request.method() === "OPTIONS") return route.fulfill({ status: 204, headers });
    if (request.method() !== "GET") {
      const body = endpoint.endsWith("/attachments") ? null : request.postDataJSON();
      state.writes.push({ endpoint, method: request.method(), body, etag: request.headers()["if-match"] });
      if (endpoint === `/incidents/${id}`) {
        if (state.failPatch)
          return reply({ code: "PRECONDITION_FAILED", message: "Conflicto aislado de edición" }, 412);
        assert.equal(request.headers()["if-match"], `"rev-${state.incident.version}"`);
        state.incident = { ...state.incident, ...body, version: state.incident.version + 1 };
        return reply(state.incident, 200, { ETag: `"rev-${state.incident.version}"` });
      }
      if (endpoint.endsWith("/reviews")) {
        if (state.failReview)
          return reply({ code: "INCIDENT_VERSION_CONFLICT", message: "Conflicto aislado de revisión" }, 409);
        assert.equal(body.expectedIncidentVersion, state.incident.version);
        state.incident = {
          ...state.incident,
          status: body.decision === "VALIDATE" ? "VALIDATED" : "REJECTED",
          version: state.incident.version + 1,
        };
        return reply({ id: "review-test" }, 201);
      }
      if (endpoint.endsWith("/attachments"))
        return state.failUpload
          ? reply({ message: "Fallo aislado de subida" }, 503)
          : reply({ id: "attachment-test" }, 201);
      throw new Error("Unexpected write: " + endpoint);
    }
    state.reads++;
    if (endpoint === "/users/me") return reply(user);
    if (endpoint === "/categories") return reply([category]);
    if (endpoint === `/incidents/${id}`)
      return reply(state.incident, 200, { ETag: `"rev-${state.incident.version}"` });
    if (endpoint.endsWith("/review-outcome"))
      return reply({
        incidentId: id,
        latestReview: null,
        status: state.incident.status,
        informationPending: false,
      });
    if (endpoint.endsWith("/attachments") || endpoint.endsWith("/duplicate-candidates")) return reply([]);
    if (endpoint.endsWith("/history"))
      return reply({ data: [], pagination: { page: 1, totalPages: 1, pageSize: 20, totalItems: 0 } });
    throw new Error("Unexpected read: " + endpoint);
  });
  const page = await ctx.newPage(),
    errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(`${origin}/incidents/${id}`);
  await page.getByRole("button", { name: "Corregir datos", exact: true }).waitFor();
  return { ctx, page, state, errors };
}
const button = (p, name) => p.getByRole("button", { name, exact: true });
async function drafts(p) {
  await button(p, "Corregir datos").click();
  await p.getByLabel("Título", { exact: true }).fill("Corrección sin guardar");
  await p.getByRole("button", { name: "Decisión: Validar", exact: true }).click();
  await p.getByRole("radio", { name: "Rechazar", exact: true }).click();
  await p.getByLabel("Motivo comunicable al ciudadano").fill("Motivo pendiente de revisión");
}
async function kept(p) {
  assert.equal(await p.getByLabel("Título", { exact: true }).inputValue(), "Corrección sin guardar");
  assert.equal(
    await p.getByLabel("Motivo comunicable al ciudadano").inputValue(),
    "Motivo pendiente de revisión"
  );
}
async function saveReview(p) {
  await button(p, "Guardar decisión").click();
  await button(p, "Confirmar").click();
}
async function close(f) {
  assert.deepEqual(f.errors, []);
  await f.ctx.close();
}
async function run() {
  browser = await chromium.launch({
    headless: true,
    ...(process.env.CHROME_EXECUTABLE ? { executablePath: process.env.CHROME_EXECUTABLE } : {}),
  });
  let f = await fixture(),
    p = f.page;
  await drafts(p);
  await button(p, "Guardar corrección").click();
  await button(p, "Seguir editando").click();
  await kept(p);
  assert.equal(f.state.writes.length, 0);
  await button(p, "Actualizar").click();
  await button(p, "Seguir editando").click();
  await kept(p);
  await button(p, "Guardar corrección").click();
  await button(p, "Guardar y descartar").click();
  await button(p, "Corregir datos").waitFor();
  assert.equal(f.state.writes.length, 1);
  assert.equal(f.state.writes[0].etag, '"rev-1"');
  assert.equal(await p.getByLabel("Motivo comunicable al ciudadano").count(), 0);
  await saveReview(p);
  await p.getByRole("heading", { name: "Decidir prioridad", exact: true }).waitFor();
  assert.equal(f.state.writes[1].body.expectedIncidentVersion, 2);
  console.log(
    "PASS corrección: cancelar no escribe; confirmar remonta y la siguiente decisión usa versión 2"
  );
  await close(f);
  f = await fixture();
  p = f.page;
  await drafts(p);
  await saveReview(p);
  await button(p, "Seguir editando").click();
  await kept(p);
  assert.equal(f.state.writes.length, 0);
  await saveReview(p);
  await button(p, "Guardar y descartar").click();
  await p.getByText("Rechazada", { exact: true }).waitFor();
  assert.equal(await p.getByLabel("Título", { exact: true }).count(), 0);
  assert.equal(f.state.writes.length, 1);
  assert.equal(f.state.writes[0].body.expectedIncidentVersion, 1);
  console.log("PASS decisión: protege corrección vecina y respeta los permisos del estado resultante");
  await close(f);
  f = await fixture();
  p = f.page;
  await drafts(p);
  f.state.failPatch = true;
  await button(p, "Guardar corrección").click();
  await button(p, "Guardar y descartar").click();
  await p.getByRole("alert").waitFor();
  await kept(p);
  assert.equal(await button(p, "Guardar corrección").getAttribute("aria-disabled"), "true");
  f.state.failReview = true;
  f.state.incident.version = 2;
  await saveReview(p);
  await button(p, "Guardar y descartar").click();
  await p
    .getByText(
      "El expediente ha cambiado. Conserva tu texto y pulsa Actualizar antes de realizar otra operación."
    )
    .waitFor();
  await kept(p);
  assert.equal(f.state.writes[1].body.expectedIncidentVersion, 1);
  assert.equal(await button(p, "Guardar decisión").getAttribute("aria-disabled"), "true");
  await p.getByRole("tab", { name: "Historial", exact: true }).click();
  await button(p, "Seguir editando").click();
  await kept(p);
  await button(p, "Actualizar").click();
  await button(p, "Descartar cambios").click();
  await button(p, "Corregir datos").waitFor();
  assert.equal(await p.getByLabel("Título", { exact: true }).count(), 0);
  console.log(
    "PASS conflictos 412/409: todos los borradores se conservan, sin reenviar contra versión nueva; Actualizar requiere consentimiento"
  );
  await close(f);
  f = await fixture();
  p = f.page;
  await p.getByRole("tab", { name: "Evidencias", exact: true }).click();
  const png = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=",
    "base64"
  );
  const pick = async (name, mimeType = "image/png", buffer = png) => {
    const chooser = p.waitForEvent("filechooser");
    await button(p, "Seleccionar fotografía").click();
    await (await chooser).setFiles({ name, mimeType, buffer });
  };
  await pick("primera.png");
  await button(p, "Subir fotografía").waitFor();
  f.state.failUpload = true;
  await button(p, "Subir fotografía").click();
  await p.getByRole("alert").waitFor();
  assert.equal(await p.evaluate(() => pickerUrls.revoked.length), 0);
  await pick("segunda.png");
  await p.getByText(/segunda.png/).waitFor();
  assert.equal(await p.evaluate(() => pickerUrls.revoked.length), 1);
  await pick("rechazada.txt", "text/plain", Buffer.from("invalid"));
  await p.getByText("Selecciona un JPEG o PNG.", { exact: true }).waitFor();
  assert.equal(await p.evaluate(() => pickerUrls.revoked.length), 2);
  await button(p, "Cancelar evidencia").click();
  await button(p, "Seguir editando").click();
  assert.equal(await p.evaluate(() => pickerUrls.revoked.length), 2);
  await button(p, "Cancelar evidencia").click();
  await button(p, "Descartar cambios").click();
  assert.equal(await p.evaluate(() => pickerUrls.revoked.length), 3);
  await pick("abandono.png");
  await button(p, "Subir fotografía").waitFor();
  await p.getByRole("tab", { name: "Resumen", exact: true }).click();
  await button(p, "Descartar cambios").click();
  assert.equal(await p.evaluate(() => pickerUrls.revoked.length), 4);
  await p.getByRole("tab", { name: "Evidencias", exact: true }).click();
  await pick("subida.png");
  await button(p, "Subir fotografía").waitFor();
  f.state.failUpload = false;
  await button(p, "Subir fotografía").click();
  await p.getByText("Evidencia incorporada.", { exact: true }).waitFor();
  assert.equal(await p.evaluate(() => pickerUrls.revoked.length), 5);
  console.log(
    "PASS fotografías web: fallo/reintento, sustitución, rechazo, cancelar, desmontaje y subida correcta"
  );
  await close(f);
  await browser.close();
}
run().catch(async (error) => {
  console.error(error);
  await browser?.close();
  process.exitCode = 1;
});
