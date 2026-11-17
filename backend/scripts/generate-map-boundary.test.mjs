import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, copyFileSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const backend = fileURLToPath(new URL('../', import.meta.url));
const migration = 'src/main/resources/db/migration/V1__initial_schema.sql';
function isolated(run) {
  const parent = resolve(tmpdir());
  const temporary = mkdtempSync(join(parent, 'urbanpulse-generator-'));
  try {
    for (const file of [migration, 'scripts/generate-map-boundary.mjs',
      'scripts/maps-functions.sql.template', 'src/main/resources/maps/malaga-ign.geojson']) {
      mkdirSync(dirname(join(temporary, file)), { recursive: true });
      copyFileSync(join(backend, file), join(temporary, file));
    }
    run(temporary, (...args) => spawnSync(process.execPath,
      [join(temporary, 'scripts/generate-map-boundary.mjs'), ...args], { encoding: 'utf8' }));
  } finally {
    assert.equal(dirname(resolve(temporary)), parent);
    assert(basename(temporary).startsWith('urbanpulse-generator-'));
    rmSync(temporary, { recursive: true, force: true });
  }
}
test('check verifica los 12339 vértices sin escribir y la generación conserva el resto de V1', () => isolated((root, run) => {
  const file = join(root, migration), original = readFileSync(file, 'utf8');
  assert.equal(run('--check').status, 0);
  assert.equal(readFileSync(file, 'utf8'), original);
  const changed = '-- contenido externo conservado\n' + original.replace(
    /(?<=-- BEGIN GENERATED MAP GEOGRAPHY\n)[\s\S]*?(?=-- END GENERATED MAP GEOGRAPHY)/, '-- alterado\n');
  writeFileSync(file, changed);
  assert.notEqual(run('--check').status, 0);
  assert.equal(readFileSync(file, 'utf8'), changed);
  assert.equal(run().status, 0);
  assert.equal(readFileSync(file, 'utf8'), '-- contenido externo conservado\n' + original);
  assert.match(run('--check').stdout, /12339 vertices/);
}));
test('rechaza marcadores ausentes, duplicados o invertidos sin modificar V1', () => isolated((root, run) => {
  const file = join(root, migration), original = readFileSync(file, 'utf8');
  for (const invalid of [original.replace('-- BEGIN GENERATED MAP GEOGRAPHY\n', ''),
    original + '\n-- END GENERATED MAP GEOGRAPHY',
    '-- END GENERATED MAP GEOGRAPHY\n-- BEGIN GENERATED MAP GEOGRAPHY\n']) {
    writeFileSync(file, invalid);
    assert.notEqual(run().status, 0);
    assert.equal(readFileSync(file, 'utf8'), invalid);
  }
}));
test('rechaza una fuente geográfica con checksum distinto', () => isolated((root, run) => {
  const file = join(root, 'src/main/resources/maps/malaga-ign.geojson');
  writeFileSync(file, readFileSync(file, 'utf8') + '\n');
  assert.match(run('--check').stderr, /Unexpected boundary checksum/);
}));
