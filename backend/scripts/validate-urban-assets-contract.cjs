const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const assert = require('node:assert/strict');
const bin = (process.env.PATH || process.env.Path).split(path.delimiter).find(p =>
  fs.existsSync(path.resolve(p, '../@redocly/cli/package.json')));
if (!bin) throw Error('Ejecutar mediante npx --package=@redocly/cli@2.16.0');
const dependency = createRequire(path.resolve(bin, '../../package.json'));
const yaml = dependency('yaml'), Ajv = dependency('ajv');
const root = path.resolve(__dirname, '../..');
const contract = yaml.parse(fs.readFileSync(path.join(root, 'docs/api/openapi.yaml'), 'utf8'));
const ajv = new Ajv({ strict: false, allErrors: true });
dependency('ajv-formats')(ajv);
const schema = contract.paths['/urban-assets'].get.responses['200'].content['application/json'].schema;
const validate = ajv.compile({ components: contract.components, ...schema });
const data = JSON.parse(fs.readFileSync(path.join(root, 'backend/target/urban-assets-examples/catalog.json'), 'utf8'));
assert(validate(data), JSON.stringify(validate.errors));
assert(validate([]), 'El catálogo vacío es contractual');
for (const change of [
  d => { d[0].email = 'private@example.test'; },
  d => { d[0].internalSecret = 'sintético'; },
  d => { delete d[0].geom; },
  d => { d[0].geom = null; },
  d => { d[0].id = 'uuid-no-contractual'; },
]) {
  const invalid = structuredClone(data); change(invalid);
  assert(!validate(invalid), 'El contrato acepta una mutación inválida');
}
assert(!validate(Array.from({ length: 501 }, () => data[0])), 'El contrato debe limitar 500 filas');
console.log('Respuesta HTTP real de activos conforme a OpenAPI; controles negativos y catálogo vacío correctos.');
