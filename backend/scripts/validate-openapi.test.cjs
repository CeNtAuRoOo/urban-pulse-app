const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const { verify, readModular, yaml, root } = require("./validate-openapi.cjs");

const readBundle = () => yaml.parse(fs.readFileSync(path.join(root, "docs/api/openapi.yaml"), "utf8"));

test("contrato conjunto válido y recuentos coherentes", () => {
  const result = verify(readModular(), readBundle());

  assert.equal(result.implemented + result.future, result.operations);

  assert.ok(result.operations > 0);
});

test("rechaza bundle futuro con fuente implementada incluso conservando conteos", () => {
  const bundle = readBundle();

  bundle.paths["/incidents/{incidentId}/duplicate-candidates"].get["x-implementation"] = "future";

  bundle.paths["/notifications"].get["x-implementation"] = "implemented";

  assert.throws(() => verify(readModular(), bundle));
});

test("rechaza referencias rotas", () => {
  const bundle = readBundle();

  bundle.components.schemas.Broken = {
    $ref: "#/components/schemas/DoesNotExist",
  };

  assert.throws(() => verify(readModular(), bundle));
});

test("rechaza permisos divergentes", () => {
  const bundle = readBundle();

  bundle.paths["/incidents/{incidentId}/duplicate-links"].post["x-roles"] = ["CITIZEN"];

  assert.throws(() => verify(readModular(), bundle));
});

test("rechaza operaciones sin operationId", () => {
  const bundle = readBundle();

  const firstPath = Object.keys(bundle.paths)[0];
  const firstMethod = Object.keys(bundle.paths[firstPath]).find((method) =>
    ["get", "put", "post", "delete", "patch", "head", "options", "trace"].includes(method)
  );

  delete bundle.paths[firstPath][firstMethod].operationId;

  assert.throws(() => verify(readModular(), bundle));
});

test("rechaza clasificación x-implementation inválida", () => {
  const bundle = readBundle();

  bundle.paths["/incidents/{incidentId}/duplicate-links"].post["x-implementation"] = "pending";

  assert.throws(() => verify(readModular(), bundle));
});
