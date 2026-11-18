const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const { verify, readModular, yaml, root } = require("./validate-openapi.cjs");

const readBundle = () => yaml.parse(fs.readFileSync(path.join(root, "docs/api/openapi.yaml"), "utf8"));

test("contrato conjunto válido y recuentos coherentes", () => {
  const result = verify(readModular(), readBundle());

  assert.deepEqual(result, { paths: 57, operations: 77, implemented: 48, future: 29 });
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

test('motivo: el contrato admite 2000 puntos tras trim y rechaza Unicode inválido', () => {
  const schema = yaml.parse(fs.readFileSync(path.join(root, 'docs/api/openapi/components/schemas/duplicados.yaml'), 'utf8')).DuplicateLinkRequest.properties.reason;
  const pattern = new RegExp(schema.pattern, 'u');
  for (const value of ['  ' + '🚦'.repeat(2000) + '  ', '', '\t', 'Motivo']) assert(pattern.test(value));
  for (const value of ['🚦'.repeat(2001), 'x'.repeat(2001), '\u0000', '\ud800']) assert(!pattern.test(value));
});

test("rechaza eliminación simultánea de una operación en fuente y bundle", () => {
  const source = readModular(), bundle = readBundle();
  delete source.paths["/notifications"].get;
  delete bundle.paths["/notifications"].get;
  assert.throws(() => verify(source, bundle));
});

test("rechaza cambio simultáneo de permisos conservando los recuentos", () => {
  const source = readModular(), bundle = readBundle();
  source.paths["/urban-assets"].get["x-roles"] = ["ADMIN"];
  bundle.paths["/urban-assets"].get["x-roles"] = ["ADMIN"];
  assert.throws(() => verify(source, bundle));
});

test("conserva identificadores opacos y componentes conceptuales", () => {
  const bundle = readBundle();
  assert.equal(bundle.components.parameters.DocumentId.schema.type, "string");
  for (const name of ["UrbanAssetDTO", "UrbanAssetCandidateDTO", "UrbanAssetAssociationDTO",
      "UrbanAssetAssociationRequest", "ExternalObservationDTO", "UrbanContextDTO",
      "AIAssessmentDTO", "AIRecommendationDTO", "TrainingJobDTO"])
    assert.ok(bundle.components.schemas[name], name);
});
