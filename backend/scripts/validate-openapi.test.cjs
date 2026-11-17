const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { verify, readModular, yaml, root } = require('./validate-openapi.cjs');
const readBundle = () => yaml.parse(fs.readFileSync(path.join(root, 'docs/api/openapi.yaml'), 'utf8'));
test('contrato conjunto válido', () => assert.equal(verify(readModular(), readBundle()).implemented, 47));
test('rechaza bundle futuro con fuente implementada incluso conservando conteos', () => {
  const bundle = readBundle();
  bundle.paths['/incidents/{incidentId}/duplicate-candidates'].get['x-implementation'] = 'future';
  bundle.paths['/notifications'].get['x-implementation'] = 'implemented';
  assert.throws(() => verify(readModular(), bundle));
});
test('rechaza referencias rotas', () => {
  const bundle = readBundle();
  bundle.components.schemas.Broken = { $ref: '#/components/schemas/DoesNotExist' };
  assert.throws(() => verify(readModular(), bundle));
});
test('rechaza permisos divergentes', () => {
  const bundle = readBundle();
  bundle.paths['/incidents/{incidentId}/duplicate-links'].post['x-roles'] = ['CITIZEN'];
  assert.throws(() => verify(readModular(), bundle));
});

test('motivo: el contrato admite 2000 puntos tras trim y rechaza Unicode inválido', () => {
  const schema = yaml.parse(fs.readFileSync(path.join(root, 'docs/api/openapi/components/schemas/duplicados.yaml'), 'utf8')).DuplicateLinkRequest.properties.reason;
  const pattern = new RegExp(schema.pattern, 'u');
  for (const value of ['  ' + '🚦'.repeat(2000) + '  ', '', '\t', 'Motivo']) assert(pattern.test(value));
  for (const value of ['🚦'.repeat(2001), 'x'.repeat(2001), '\u0000', '\ud800']) assert(!pattern.test(value));
});
