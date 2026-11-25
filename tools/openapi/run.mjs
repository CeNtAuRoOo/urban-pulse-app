import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const toolsDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(toolsDirectory, '../..');
const manifest = JSON.parse(readFileSync(resolve(toolsDirectory, 'package.json'), 'utf8'));
const nodeVersion = readFileSync(resolve(toolsDirectory, '.node-version'), 'utf8').trim();
const npmVersion = process.env.npm_config_user_agent?.match(/^npm\/([^ ]+)/)?.[1];
const command = process.argv[2];

function fail(message) {
  console.error(message);
  process.exit(1);
}

if (nodeVersion !== manifest.engines.node) {
  fail('Las versiones de Node en .node-version y package.json deben coincidir.');
}
if (process.versions.node !== nodeVersion || npmVersion !== manifest.engines.npm) {
  fail(`OpenAPI requiere Node ${nodeVersion} y npm ${manifest.engines.npm}. ` +
    `Versiones detectadas: Node ${process.versions.node}, npm ${npmVersion ?? 'desconocido'}. ` +
    'Ejecuta los scripts mediante npm con las versiones fijadas.');
}

function run(executable, args) {
  const result = spawnSync(executable, args, {
    cwd: repositoryRoot,
    stdio: 'inherit',
    shell: false,
  });
  if (result.error) fail(`No se pudo ejecutar ${executable}: ${result.error.message}`);
  if (result.signal) fail(`${executable} terminó por la señal ${result.signal}.`);
  if (result.status !== 0) process.exit(result.status ?? 1);
}

const source = 'docs/api/openapi/base-openapi.yaml';
const yaml = 'docs/api/openapi.yaml';
const html = 'docs/api/openapi.html';
const redocly = (...args) => run(process.execPath, [
  resolve(toolsDirectory, 'node_modules/@redocly/cli/bin/cli.js'), ...args,
]);

function generate() {
  redocly('bundle', source, '-o', yaml);
  redocly('build-docs', yaml, '-o', html, '--template', 'docs/api/redoc-template.hbs');
}

switch (command) {
  case 'runtime':
    console.log(`OpenAPI: Node ${nodeVersion}, npm ${npmVersion}`);
    break;
  case 'validate':
    redocly('lint', source);
    redocly('lint', yaml);
    break;
  case 'generate':
    generate();
    break;
  case 'check':
    redocly('lint', source);
    generate();
    redocly('lint', yaml);
    run('git', ['diff', '--exit-code', '--', yaml, html]);
    break;
  default:
    fail('Comando desconocido. Usa npm run validate, generate o check.');
}
