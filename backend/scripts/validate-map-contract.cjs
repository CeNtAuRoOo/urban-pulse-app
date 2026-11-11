// Uses the dependencies bundled with the repository's official Redocly CLI.
// npx --yes --package=@redocly/cli@2.16.0 -c "node backend/scripts/validate-map-contract.cjs"
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const dependencyBin = (process.env.PATH || process.env.Path).split(path.delimiter).find(p =>
  fs.existsSync(path.resolve(p, '../@redocly/cli/package.json')));
if (!dependencyBin) throw new Error('Run through npx with @redocly/cli@2.16.0; see script header');
const fromTool = createRequire(path.resolve(dependencyBin, '../../package.json'));
const yaml = fromTool('yaml');
const Ajv = fromTool('ajv');
const formats = fromTool('ajv-formats');
const root = path.resolve(__dirname, '../..');
const contract = yaml.parse(fs.readFileSync(path.join(root, 'docs/api/openapi.yaml'), 'utf8'));
const ajv = new Ajv({ strict: false, allErrors: true });
formats(ajv);
const validate = ajv.compile({ components: contract.components, $ref: '#/components/schemas/MapIncidentsDTO' });
for (const name of ['incident', 'cluster']) {
  const file = path.join(root, 'backend/target/maps-examples', name + '.json');
  const response = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (!validate(response)) throw new Error(JSON.stringify(validate.errors));
  const invalid = structuredClone(response);
  invalid.items[0].description = 'Private data must be rejected by the map schema';
  if (validate(invalid)) throw new Error('Schema unexpectedly accepts private fields');
  console.log('PostgreSQL HTTP response conforms to OpenAPI: ' + name);
}
