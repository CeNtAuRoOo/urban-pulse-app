const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { createRequire } = require('node:module');
const bin = (process.env.PATH || process.env.Path).split(path.delimiter).find(p =>
  fs.existsSync(path.resolve(p, '../@redocly/cli/package.json')));
if (!bin) throw Error('Ejecutar con npx --package=@redocly/cli@2.16.0 -c "node backend/scripts/validate-openapi.cjs"');
const yaml = createRequire(path.resolve(bin, '../../package.json'))('yaml');
const methods = new Set(['get', 'put', 'post', 'delete', 'patch', 'head', 'options', 'trace']);
const root = path.resolve(__dirname, '../..');
const pointer = (value, ref) => ref.slice(1).split('/').slice(1)
  .map(p => decodeURIComponent(p).replaceAll('~1', '/').replaceAll('~0', '~'))
  .reduce((o, p) => o?.[p], value);

function readModular() {
  const file = path.join(root, 'docs/api/openapi/base-openapi.yaml');
  const source = yaml.parse(fs.readFileSync(file, 'utf8'));
  for (const [route, item] of Object.entries(source.paths)) {
    if (!item.$ref) continue;
    const [relative, fragment] = item.$ref.split('#');
    const document = yaml.parse(fs.readFileSync(path.resolve(path.dirname(file), relative), 'utf8'));
    source.paths[route] = pointer(document, '#' + fragment);
    assert(source.paths[route], 'Referencia modular inexistente: ' + item.$ref);
  }
  return source;
}

function operations(document) {
  return Object.entries(document.paths).flatMap(([route, item]) =>
    Object.entries(item).filter(([method]) => methods.has(method))
      .map(([method, operation]) => ({ key: method + ' ' + route, ...operation })));
}

function verify(source, bundle) {
  const actual = operations(bundle), expected = operations(source);
  assert.equal(actual.length, 77);
  assert.equal(actual.filter(o => o['x-implementation'] === 'implemented').length, 47);
  assert.equal(actual.filter(o => o['x-implementation'] === 'future').length, 30);
  assert.equal(new Set(actual.map(o => o.operationId)).size, actual.length);
  assert.deepEqual(actual.map(o => o.key).sort(), expected.map(o => o.key).sort());
  for (const operation of expected) {
    const bundled = actual.find(o => o.key === operation.key);
    for (const key of ['operationId', 'x-implementation', 'x-roles', 'security'])
      assert.deepEqual(bundled[key], operation[key], operation.key + ': ' + key);
    assert.deepEqual(Object.keys(bundled.responses).sort(), Object.keys(operation.responses).sort());
  }
  for (const id of ['getMapIncidents', 'getIncidentsIncidentIdDuplicateCandidates', 'postIncidentsIncidentIdDuplicateLinks'])
    assert.equal(actual.find(o => o.operationId === id)?.['x-implementation'], 'implemented', id);
  function references(value) {
    if (!value || typeof value !== 'object') return;
    if (value.$ref) {
      assert(value.$ref.startsWith('#/'), 'Referencia externa en bundle: ' + value.$ref);
      assert(pointer(bundle, value.$ref) !== undefined, 'Referencia no resuelta: ' + value.$ref);
    }
    Object.values(value).forEach(references);
  }
  references(bundle);
  assert.equal(bundle.info.version, source.info.version);
  return { paths: Object.keys(bundle.paths).length, operations: actual.length, implemented: 47, future: 30 };
}
module.exports = { verify, readModular, yaml, root };
if (require.main === module) {
  console.log(JSON.stringify(verify(readModular(), yaml.parse(fs.readFileSync(path.join(root, 'docs/api/openapi.yaml'), 'utf8')))));
}
