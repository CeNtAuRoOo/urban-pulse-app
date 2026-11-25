# Herramientas OpenAPI

Paquete privado de desarrollo, independiente del frontend y del backend. Instala
el generador mediante su propio `package-lock.json`; no usa una CLI global ni
descarga herramientas durante la generación.

## Versiones y motivo

| Herramienta | Versión | Decisión |
| --- | --- | --- |
| Node.js | 24.22.3 | Versión exacta del CI que detectó la deriva; compatible con el HTML original, sin volver a 24.22.2. |
| npm | 11.19.1 | Versión incluida en la distribución oficial de Node 24.22.3 y utilizada por ambos jobs investigados. |
| Redocly CLI | 2.16.0 | Mantiene el generador y las reglas de validación existentes. |
| styled-components | 6.6.0 | Override que reproduce byte por byte el HTML versionado. |

Redocly 2.16.0 admite `styled-components: ^6.3.8`. Una instalación nueva mediante
`npx` empezó a resolver 6.7.0, publicada el 24 de noviembre de 2026. La versión de
styled-components interviene en los hashes de sus clases CSS. Fijar solo Redocly
o Node no evita esa deriva: el lockfile fija **todo** el árbol y sus integridades.

`.node-version` es la fuente de versión para `actions/setup-node`. `engines`,
`packageManager`, `.npmrc` y la comprobación de runtime mantienen explícita la
versión de npm. Los scripts y `preinstall` rechazan versiones distintas; no se
cambia la instalación global de Node/npm. Al actualizar Node, sincroniza
`.node-version`, `engines`, el lockfile y esta documentación; comprueba también
el npm incluido en la nueva distribución.

## Uso en Windows y Linux

Activa Node 24.22.3 y npm 11.19.1 mediante tu gestor habitual. Desde la raíz:

```sh
node --version
npm --version
npm --prefix tools/openapi ci
npm --prefix tools/openapi run check
```

También puedes ejecutar `npm ci` y `npm run check` desde `tools/openapi/`.
`run.mjs` resuelve la raíz a partir de su propia ubicación y lanza Node/Git sin
shell, con argumentos separados, por lo que admite rutas con espacios y no
depende de Bash ni PowerShell. Git debe estar disponible en PATH.

| Script | Operaciones |
| --- | --- |
| `validate` | Lint de la fuente modular y del YAML generado existente, sin escribir artefactos. |
| `generate` | Bundle de la fuente a `docs/api/openapi.yaml` y HTML con `docs/api/redoc-template.hbs`. |
| `check` | Lint de la fuente, generación de ambos artefactos, lint del bundle y `git diff --exit-code -- docs/api/openapi.yaml docs/api/openapi.html`. |

Cada fallo detiene el proceso y propaga un código de salida no nulo. `check`
regenera los archivos: revisa primero cualquier cambio local. Tras un cambio
intencionado del contrato, genera y revisa ambos derivados antes de incorporarlos
al mismo commit. El diff de CI compara los derivados regenerados con el índice
del checkout; no oculta diferencias de CSS ni elimina la comprobación del HTML.
La validación de Compose permanece en el siguiente paso del job.

## Mantenimiento y reproducibilidad

- Usa `npm ci`, no `npm install`, para trabajo habitual y CI. La instalación
  requiere las dependencias de desarrollo de este paquete (no uses `--omit=dev`).
- Cambia el manifiesto/override y regenera el lockfile con `npm install
  --package-lock-only` únicamente durante una actualización deliberada, usando
  las versiones de Node/npm fijadas. Revisa las versiones, URLs e integridades.
- Ejecuta `npm audit` incluyendo dependencias de desarrollo. No ejecutes
  `npm audit fix` automáticamente: puede cambiar el generador o su salida.
- Comprueba dos instalaciones limpias con `npm ci`, regenerando después de cada
  una y comparando los SHA-256 del YAML y HTML con los artefactos revisados.
  `npm ci` elimina el `node_modules` anterior y no debe cambiar el lockfile.
- Una actualización del generador puede requerir un cambio revisado del HTML;
  nunca ignores el diff para hacer pasar CI. Mantén los finales LF definidos en
  `.gitattributes`.
- `node_modules`, cachés, logs y temporales están excluidos por el `.gitignore`
  general. No incluyas auditorías en bruto ni salidas temporales en el repositorio.

## Auditoría del árbol fijado (25 de noviembre de 2026)

`npm --prefix tools/openapi audit --json` devuelve código 1: **14 entradas
afectadas (3 altas y 11 moderadas; ninguna crítica)**. Incluye dependientes
afectados por propagación, no 14 avisos independientes. Los cinco avisos son:

| Paquete instalado | Severidad del aviso | Aviso y alcance |
| --- | --- | --- |
| `@faker-js/faker` 7.6.0, vía `@redocly/respect-core` | Alta | [GHSA-qxc2-j82w-r537](https://github.com/advisories/GHSA-qxc2-j82w-r537): ejecución de código mediante `helpers.fake`. |
| `@opentelemetry/core` 2.0.1 y dependientes | Moderada | [GHSA-8988-4f7v-96qf](https://github.com/advisories/GHSA-8988-4f7v-96qf): consumo de memoria sin límite en propagación W3C Baggage. |
| `@redocly/cli` 2.16.0 | Moderada | [GHSA-657c-g7qc-r9j2](https://github.com/advisories/GHSA-657c-g7qc-r9j2): traversal de rutas en `split`. |
| `glob` 11.1.0 | Moderada | [GHSA-3g3m-2fjr-6pw4](https://github.com/advisories/GHSA-3g3m-2fjr-6pw4): recorrido del sistema de archivos después de cancelar por timeout. |
| `minimatch` 10.2.6 | Moderada | [GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249): posible denegación de servicio mediante patrones glob. |

Este paquete solo ejecuta `lint`, `bundle` y `build-docs` sobre los archivos del
repositorio; no ejecuta `split` ni las pruebas de Respect. No forma parte de las
dependencias de ejecución del frontend o backend. Estas restricciones acotan el
uso, pero **no equivalen a demostrar que los avisos sean inexplotables**.

La propuesta automática de npm requiere actualizar Redocly a 2.83.0, fuera de la
decisión de mantener 2.16.0 en esta corrección. No se han aplicado actualizaciones
ni overrides de seguridad adicionales sin comprobar su compatibilidad. Queda
pendiente una actualización revisada del generador y la reevaluación del árbol;
esta corrección garantiza reproducibilidad, no una auditoría sin vulnerabilidades.
Repite la auditoría en futuras actualizaciones: sus resultados dependen de la
base de avisos vigente.
