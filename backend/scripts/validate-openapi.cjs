const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { createRequire } = require("node:module");

const bin = (process.env.PATH || process.env.Path || "")
  .split(path.delimiter)
  .find((p) => fs.existsSync(path.resolve(p, "../@redocly/cli/package.json")));

if (!bin) {
  throw Error(
    'Ejecutar con npx --package=@redocly/cli@2.16.0 -c "node backend/scripts/validate-openapi.cjs"'
  );
}

const yaml = createRequire(path.resolve(bin, "../../package.json"))("yaml");

const methods = new Set(["get", "put", "post", "delete", "patch", "head", "options", "trace"]);

const root = path.resolve(__dirname, "../..");

const pointer = (value, ref) =>
  ref
    .slice(1)
    .split("/")
    .slice(1)
    .map((p) => decodeURIComponent(p).replaceAll("~1", "/").replaceAll("~0", "~"))
    .reduce((o, p) => o?.[p], value);

function readModular() {
  const file = path.join(root, "docs/api/openapi/base-openapi.yaml");
  const source = yaml.parse(fs.readFileSync(file, "utf8"));

  assert(source && source.paths, "El contrato OpenAPI no contiene paths");

  for (const [route, item] of Object.entries(source.paths)) {
    if (!item.$ref) continue;

    const [relative, fragment] = item.$ref.split("#");
    const referencedFile = path.resolve(path.dirname(file), relative);

    assert(fs.existsSync(referencedFile), "Archivo referenciado inexistente: " + relative);

    const document = yaml.parse(fs.readFileSync(referencedFile, "utf8"));
    source.paths[route] = pointer(document, "#" + fragment);

    assert(source.paths[route], "Referencia modular inexistente: " + item.$ref);
  }

  return source;
}

function operations(document) {
  return Object.entries(document.paths).flatMap(([route, item]) =>
    Object.entries(item)
      .filter(([method]) => methods.has(method))
      .map(([method, operation]) => ({
        key: method + " " + route,
        ...operation,
      }))
  );
}

function implementationCounts(list) {
  return {
    implemented: list.filter((o) => o["x-implementation"] === "implemented").length,
    future: list.filter((o) => o["x-implementation"] === "future").length,
  };
}

function verify(source, bundle) {
  const expected = operations(source);
  const actual = operations(bundle);

  assert(expected.length > 0, "El contrato fuente no contiene operaciones");
  assert(actual.length > 0, "El bundle no contiene operaciones");

  // Cada operación debe tener una clasificación válida.
  for (const operation of expected) {
    assert(
      ["implemented", "future"].includes(operation["x-implementation"]),
      "Clasificación x-implementation ausente o inválida: " + operation.key
    );
  }

  // El bundle debe contener exactamente las operaciones de la fuente.
  assert.deepEqual(
    actual.map((o) => o.key).sort(),
    expected.map((o) => o.key).sort(),
    "Las operaciones del bundle no coinciden con el contrato modular"
  );

  // No se permiten operationId duplicados.
  const operationIds = actual.map((o) => o.operationId);
  assert(operationIds.every(Boolean), "Hay operaciones sin operationId");
  assert.equal(new Set(operationIds).size, actual.length, "Hay operationId duplicados");

  const actualByKey = new Map(actual.map((o) => [o.key, o]));

  for (const operation of expected) {
    const bundled = actualByKey.get(operation.key);

    assert(bundled, "Operación ausente en bundle: " + operation.key);

    for (const key of ["operationId", "x-implementation", "x-roles", "security"]) {
      assert.deepEqual(bundled[key], operation[key], operation.key + ": discrepancia en " + key);
    }

    assert.deepEqual(
      Object.keys(bundled.responses || {}).sort(),
      Object.keys(operation.responses || {}).sort(),
      operation.key + ": discrepancia en responses"
    );
  }

  // Operaciones críticas que deben continuar implementadas.
  for (const id of [
    "getMapIncidents",
    "getIncidentsIncidentIdDuplicateCandidates",
    "postIncidentsIncidentIdDuplicateLinks",
  ]) {
    const operation = actual.find((o) => o.operationId === id);

    assert(operation, "Falta la operación crítica: " + id);
    assert.equal(operation["x-implementation"], "implemented", id);
  }

  // Todas las referencias del bundle deben ser internas y resolubles.
  function references(value) {
    if (!value || typeof value !== "object") return;

    if (value.$ref) {
      assert(value.$ref.startsWith("#/"), "Referencia externa en bundle: " + value.$ref);

      assert(pointer(bundle, value.$ref) !== undefined, "Referencia no resuelta: " + value.$ref);
    }

    Object.values(value).forEach(references);
  }

  references(bundle);

  assert.equal(bundle.info.version, source.info.version, "La versión del bundle no coincide con la fuente");

  const counts = implementationCounts(expected);

  assert.equal(
    counts.implemented + counts.future,
    expected.length,
    "Hay operaciones sin clasificación válida"
  );

  return {
    paths: Object.keys(bundle.paths).length,
    operations: actual.length,
    implemented: counts.implemented,
    future: counts.future,
  };
}

module.exports = {
  verify,
  readModular,
  yaml,
  root,
};

if (require.main === module) {
  const source = readModular();
  const bundleFile = path.join(root, "docs/api/openapi.yaml");

  assert(fs.existsSync(bundleFile), "No existe docs/api/openapi.yaml. Ejecuta primero Redocly bundle.");

  const bundle = yaml.parse(fs.readFileSync(bundleFile, "utf8"));

  console.log(JSON.stringify(verify(source, bundle), null, 2));
}
