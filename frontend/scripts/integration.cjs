// Explicitly mutates only the isolated local demo API using real HTTP and SMTP.
require("./load-source.cjs");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
process.env.EXPO_PUBLIC_API_URL = "http://127.0.0.1:8888";
const { request, ApiError } = require("../src/services/http.ts");
const { incidentBody, incidentFields } = require("../src/features/incident-form.ts");
const secret =
  process.env.DEMO_TEST_PASSWORD ||
  fs
    .readFileSync(path.join(__dirname, "../.env.demo-test"), "utf8")
    .match(/^DEMO_TEST_PASSWORD=(.+)$/m)?.[1]
    .trim();
if (!secret) throw new Error("Falta DEMO_TEST_PASSWORD para las cuentas del SQL local.");
const stamp = Date.now();
let checks = 0;
const ok = (label) => {
  checks++;
  console.log(`OK ${checks}: ${label}`);
};
const login = async (email) =>
  (await request("/auth/login", { method: "POST", body: { email, password: secret } })).data;
const client = (token) => (route, options) => request(route, options, token.accessToken);
const fails = async (work, status) => {
  await assert.rejects(work, (error) => error instanceof ApiError && error.status === status);
};
async function emailCode(email, pattern) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const inbox = await (await fetch("http://127.0.0.1:8025/api/v1/messages")).json();
    for (const entry of inbox.messages || []) {
      if (!entry.To?.some((to) => to.Address === email)) continue;
      const message = await (await fetch(`http://127.0.0.1:8025/api/v1/message/${entry.ID}`)).json();
      const match = message.Text?.match(pattern);
      if (match) return match[0];
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error("No llegó el correo esperado a Mailpit.");
}
async function main() {
  const citizen = await login("demo.citizen@urbanpulse.test");
  const admin = await login("demo.admin@urbanpulse.test");
  const operator = await login("demo.operator@urbanpulse.test");
  const technician = await login("demo.technician@urbanpulse.test");
  const other = await login("demo.other@urbanpulse.test");
  const c = client(citizen),
    a = client(admin),
    o = client(operator),
    t = client(technician),
    stranger = client(other);
  assert(citizen.user.emailVerified);
  assert(!technician.user.roles.includes("OPERATOR"));
  assert.equal(technician.user.departmentMemberships.length, 1);
  await fails(c("/users"), 403);
  await fails(c("/incidents"), 403);
  await fails(t("/incidents"), 403);
  assert.equal((await o("/incidents")).status, 200);
  ok("Login y permisos globales frente a adscripciones departamentales");
  const preflight = await fetch(
    "http://127.0.0.1:8888/api/v1/incidents/019a0000-0000-7000-8000-000000000001",
    {
      method: "OPTIONS",
      headers: {
        Origin: "http://localhost:7777",
        "Access-Control-Request-Method": "PATCH",
        "Access-Control-Request-Headers": "authorization,content-type,if-match",
      },
    }
  );
  assert.equal(preflight.status, 200);
  assert.equal(preflight.headers.get("Access-Control-Allow-Origin"), "http://localhost:7777");
  assert.match(preflight.headers.get("Access-Control-Allow-Headers"), /if-match/i);
  ok("Preflight CORS de PATCH con If-Match");
  const category = await a("/categories", {
    method: "POST",
    body: { name: `Prueba ${stamp}`, description: "Creada por integración", active: true },
  });
  assert.equal(category.status, 201);
  assert.equal((await c(`/categories/${category.data.id}`)).status, 200);
  assert.equal(
    (await a(`/categories/${category.data.id}`, { method: "PATCH", body: { description: "" } })).data
      .description,
    ""
  );
  await fails(c("/categories", { method: "POST", body: { name: "Sin permiso" } }), 403);
  ok("Alta, consulta y PATCH de categorías");
  const department = await a("/departments", {
    method: "POST",
    body: { name: `Departamento ${stamp}`, code: `T${stamp}`, active: true },
  });
  assert.equal(department.status, 201);
  assert.equal((await c(`/departments/${department.data.id}`)).status, 200);
  assert.equal(
    (await a(`/departments/${department.data.id}`, { method: "PATCH", body: { description: "Actualizado" } }))
      .status,
    200
  );
  ok("Alta, consulta y PATCH de departamentos");
  const fields = {
    ...incidentFields(),
    title: "Farola de prueba 😀",
    description: "Aviso real de integración local.",
    latitude: "36.7213",
    longitude: "-4.4214",
    accuracy: "3",
    categoryId: category.data.id,
  };
  const created = await c("/incidents", { method: "POST", body: incidentBody(fields) });
  assert.equal(created.status, 201);
  assert.match(created.etag, /^"sha256-[a-f0-9]+"$/);
  assert(created.location);
  const route = `/incidents/${created.data.id}`;
  assert(
    (await c("/incidents/mine", { query: { page: 1, pageSize: 100 } })).data.data.some(
      (item) => item.id === created.data.id
    )
  );
  assert(
    (
      await o("/incidents", {
        query: {
          status: "REPORTED",
          categoryId: category.data.id,
          createdFrom: new Date(stamp - 60000).toISOString(),
          createdTo: new Date().toISOString(),
          page: 1,
          pageSize: 100,
        },
      })
    ).data.data.some((item) => item.id === created.data.id)
  );
  const detail = await c(route);
  assert.equal(detail.etag, created.etag);
  await fails(stranger(route), 404);
  await fails(c(route, { method: "PATCH", body: { title: "Sin cabecera" } }), 428);
  ok("Alta, listados, filtros, detalle autorizado, ETag y 428");
  const updated = await c(route, {
    method: "PATCH",
    etag: detail.etag,
    body: incidentBody({ ...incidentFields(detail.data), title: "Farola corregida 😀" }, detail.data),
  });
  assert.equal(updated.data.category.id, category.data.id);
  assert.notEqual(updated.etag, detail.etag);
  await fails(c(route, { method: "PATCH", etag: detail.etag, body: { title: "No sobrescribir" } }), 412);
  const afterConflict = await c(route);
  assert.equal(afterConflict.data.title, "Farola corregida 😀");
  assert.equal(afterConflict.etag, updated.etag);
  ok("PATCH real con ETag opaca y 412 sin sobrescritura");
  const removed = await c(route, { method: "PATCH", etag: updated.etag, body: { categoryId: null } });
  assert.equal(removed.data.category, undefined);
  const history = await c(`${route}/history`, { query: { page: 1, pageSize: 1 } });
  assert.equal(history.data.pagination.page, 1);
  assert.equal(history.data.pagination.totalItems, 3);
  assert.equal(history.data.data[0].eventType, "CREATED");
  assert.equal(
    (await c(`${route}/history`, { query: { page: 2, pageSize: 1 } })).data.data[0].eventType,
    "UPDATED"
  );
  ok("Desclasificación explícita e historial paginado");
  await a(`/categories/${category.data.id}`, { method: "PATCH", body: { active: false } });
  await fails(c(route, { method: "PATCH", etag: removed.etag, body: { categoryId: category.data.id } }), 422);
  ok("Rechazo de reclasificación a categoría inactiva");
  const profile = await c("/users/me");
  assert.equal(
    (await c("/users/me", { method: "PATCH", body: { displayName: profile.data.displayName } })).status,
    200
  );
  assert.equal(
    (await a("/users", { query: { page: 1, pageSize: 2, role: "CITIZEN", active: true } })).data.data.length,
    2
  );
  assert.equal((await a(`/users/${other.user.id}`)).status, 200);
  await a(`/users/${other.user.id}`, { method: "PATCH", body: { active: false } });
  await fails(stranger("/users/me"), 401);
  await a(`/users/${other.user.id}`, { method: "PATCH", body: { active: true } });
  ok("Perfil y consulta/activación administrativa de usuarios");
  const invitedEmail = `flow.${stamp}@urbanpulse.test`;
  const registered = (
    await request("/auth/register", {
      method: "POST",
      body: { displayName: "Registro de integración", email: invitedEmail, password: secret },
    })
  ).data;
  const pending = client(registered);
  assert.equal(registered.user.emailVerified, false);
  await fails(pending("/users/me"), 403);
  await fails(pending("/incidents/mine"), 403);
  const verificationCode = await emailCode(invitedEmail, /\b[0-9]{8}\b/);
  const confirmed = await pending("/auth/email-verifications/confirm", {
    method: "POST",
    body: { code: verificationCode },
  });
  assert.equal(confirmed.status, 204);
  assert.equal(confirmed.data, undefined);
  assert.equal((await pending("/users/me")).data.emailVerified, true);
  ok("Registro y verificación real por Mailpit con el mismo JWT");
  const invite = await a("/staff-invitations", {
    method: "POST",
    body: { email: invitedEmail, role: "OPERATOR" },
  });
  assert.equal(invite.status, 201);
  assert.equal(invite.data.invitationCode, undefined);
  assert.equal((await a(`/staff-invitations/${invite.data.id}`)).data.status, "ACTIVE");
  assert.equal(
    (
      await a("/staff-invitations", {
        query: { status: "ACTIVE", role: "OPERATOR", email: invitedEmail, page: 1, pageSize: 20 },
      })
    ).data.data.length,
    1
  );
  const invitationCode = await emailCode(invitedEmail, /\b[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]\b/);
  assert.equal(
    (await pending("/users/me/staff-invitation-redemptions", { method: "POST", body: { invitationCode } }))
      .status,
    200
  );
  assert((await pending("/users/me")).data.roles.includes("OPERATOR"));
  ok("Invitación global, metadatos, filtros, correo y canje real");
  const departmental = await a("/staff-invitations", {
    method: "POST",
    body: { email: invitedEmail, role: "TECHNICIAN", departmentId: department.data.id },
  });
  assert.equal(
    (await a("/staff-invitations", { query: { departmentId: department.data.id } })).data.data.length,
    1
  );
  assert.equal(
    (await a(`/staff-invitations/${departmental.data.id}/revocations`, { method: "POST" })).status,
    204
  );
  await fails(a(`/staff-invitations/${departmental.data.id}/revocations`, { method: "POST" }), 409);
  await a(`/departments/${department.data.id}`, { method: "PATCH", body: { active: false } });
  await fails(
    a("/staff-invitations", {
      method: "POST",
      body: { email: invitedEmail, role: "TECHNICIAN", departmentId: department.data.id },
    }),
    422
  );
  ok("Invitación departamental, revocación, 409 y departamento inactivo");
  assert.equal(
    (
      await pending("/users/me/password-changes", {
        method: "POST",
        body: { currentPassword: secret, newPassword: `${secret}x` },
      })
    ).status,
    204
  );
  await fails(pending("/users/me"), 401);
  ok("Cambio de contraseña invalida JWT anterior");
  console.log(
    `Integración completada: ${checks} grupos de comprobaciones reales. Sin tokens ni códigos en salida.`
  );
}
main().catch((error) => {
  console.error(
    error instanceof ApiError ? `HTTP ${error.status} ${error.code}: ${error.message}` : error.message
  );
  process.exitCode = 1;
});
