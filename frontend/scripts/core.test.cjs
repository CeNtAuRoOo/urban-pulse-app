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
