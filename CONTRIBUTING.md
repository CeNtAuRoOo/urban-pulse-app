# Guía de contribución

UrbanPulse se desarrolla de forma incremental, revisando **el dominio y OpenAPI como una especificación conjunta** antes de implementar cada tag. La documentación distingue el modelo de diseño global del subconjunto ejecutable: Autenticación, Usuarios, Invitaciones, catálogos, Incidencias, Evidencias, Tramitación, Mapas y Duplicados se apoyan actualmente en una única migración Flyway V1 consolidada. La estructura Java actual se organiza por módulos de negocio, no por paquetes técnicos globales.

## Flujo de trabajo

1. Actualizar las referencias con `git fetch origin`, conservando los cambios locales existentes.
2. Crear desde `origin/main` una rama específica para la funcionalidad, corrección, refactor o documentación.
3. Revisar los requisitos, ADR, diagramas y contrato OpenAPI del ámbito afectado.
4. Implementar exclusivamente el alcance acordado (migración, modelo, DTO, servicio, seguridad, controladores y pruebas cuando correspondan).
5. Hacer commits pequeños y descriptivos.
6. Ejecutar las comprobaciones pertinentes y registrar **resultados reales, con fecha y entorno**; no extrapolar resultados anteriores a refactors posteriores.
7. Abrir Pull Request contra `main`, solicitar revisión y fusionar solo tras la aprobación.

## Ramas y commits

Prefijos de rama: `feature/`, `fix/`, `docs/`, `refactor/`, `test/`, `ci/`.

Usar **Conventional Commits** con tipo inglés y descripción en español: `tipo(scope): descripción del cambio`, con scope opcional. Ejemplos:

```text
feat(backend): añadir endpoint para crear incidencias
fix(backend): corregir validación de incidencias
refactor(backend): reorganizar paquetes por módulos
test(backend): ampliar pruebas de canje concurrente
docs(diagrams): sincronizar modelos y exportaciones
docs(adr): registrar las decisiones de permisos
ci: configurar comprobaciones automatizadas
```

## Dominio–OpenAPI: revisión conjunta por tag

La documentación del [modelo de dominio](docs/domain-model.md) es una referencia inicial **revisable**, no una afirmación de que todas las tablas o clases estén implementadas. Consultar [diagramas](docs/diagrams/README.md): `relacional.drawio` tiene cuatro páginas con entidades, PK/FK y relaciones; `clases-simplificado.drawio` presenta un catálogo Java de atributos y tipos sin líneas UML deliberadamente.

1. Analizar requisitos y casos de uso, incluidos los componentes aplazados del enunciado.
2. Leer `docs/api/openapi/paths/<tag>.yaml`, los esquemas de `components/schemas/` y la [guía de API](docs/api/README.md).
3. Contrastar **las dos vistas del dominio**: tipos, nulabilidad, restricciones, cardinalidades, roles, transiciones y persistencia; no dar por implementado lo dibujado.
4. Resolver divergencias con los [ADR](docs/adr/README.md) o registrar una nueva decisión cuando afecte a la arquitectura.
5. Actualizar en el mismo PR **las fuentes `.drawio` y sus SVG**, los YAML modulares, el [contrato dominio–API](docs/api/domain-openapi.md), el modelo de dominio, los ADR y las pruebas pertinentes. No alterar componentes ajenos sin coordinación.
6. Implementar solo las entidades necesarias en cada tag; no añadir todas las tablas previstas anticipadamente ni usar `ddl-auto=update`.
7. Describir con precisión qué se implementó, qué se probó y qué permanece como diseño o tarea futura.

## Verificaciones por ámbito

**Frontend**, desde `frontend/`:

```bash
npm ci
npm test
npx tsc --noEmit
npm run lint
npx expo export --platform web
```

Como mantenimiento opcional, `npx expo install --check` consulta las recomendaciones actuales de Expo; puede pedir parches nuevos y no forma parte de los checks obligatorios de CI.

**Backend**, desde `backend/`, con Java 25, Maven y Docker:

```bash
mvn -B -ntp test
mvn -B -ntp clean verify -Ppostgres-it
```

El perfil `postgres-it` ejecuta las catorce suites PostgreSQL documentadas en [Desarrollo](docs/development.md#backend-unitariasmvc-e-integración-postgresql), con Testcontainers y sin la base persistente de Compose. La guía incluye comandos Docker para repetir las verificaciones sin instalar Java/Maven en el host. Los comandos de herramientas anteriores son los del CI; no definen otro modo habitual de arrancar la aplicación.

`NeonConnectivityIT` es una prueba histórica explícita de JDBC/HeadBucket, excluida de CI y del desarrollo local ordinario. El perfil `neon-storage` utiliza S3 real y requiere un recurso de pruebas no productivo y autorización explícita; las credenciales locales no autorizan su ejecución automática.

No lanzar tests de escritura en Neon compartido ni migraciones experimentales sobre `production`. Los resultados de 90 pruebas y HTTP aislado figuran en un informe fechado el 30/10/2026; **el orden Git sitúa la reorganización antes de los commits de Invitaciones y pruebas**, por lo que no deben invertirse los hitos por la fecha del informe. Registrar una ejecución nueva solo cuando se realice efectivamente.

**API (design-first):**

```bash
npx --yes @redocly/cli@2.16.0 lint docs/api/openapi/base-openapi.yaml
npx --yes @redocly/cli@2.16.0 bundle docs/api/openapi/base-openapi.yaml -o docs/api/openapi.yaml
npx --yes @redocly/cli@2.16.0 build-docs docs/api/openapi.yaml -o docs/api/openapi.html --template docs/api/redoc-template.hbs
```

Los artefactos `docs/api/openapi.yaml` y `docs/api/openapi.html` son derivados y no se editan manualmente. Comprobar versión `0.8.0`, enlaces `$ref` y cambios esperados antes de incluirlos en el commit.

**Documentación:** comprobar vínculos relativos, presencia de todos los SVG que se enlazan, correspondencia `.drawio`/exportación, ausencia de referencias a informes temporales y coherencia con el árbol Java actual. Ejecutar `git diff --check` y comprobar que no se han incorporado secretos, datos personales reales, `.env`, `target`, `dist` o `node_modules`.

El informe de pruebas HTTP de Invitaciones utilizó `compose.test.yaml` y `backend/scripts/invitations_http_test.py` con PostgreSQL/Mailpit efímeros, **pero esos archivos no figuran en el repositorio**. No presentar ese flujo como reproducible sin restaurar y revisar el runner. Las suites Maven/Testcontainers configuradas en el proyecto se describen en [Desarrollo](docs/development.md); la política funcional y de seguridad, en [ADR-0007](docs/adr/0007-invitaciones-personal.md).

## Mantenimiento de k6

La [batería k6](k6/README.md) contiene scripts implementados y aún no ejecutados con k6 real; mantener ese estado separado de resultados reales posteriores. Al ampliar un escenario, contrastar ruta, método, roles, DTO, fixtures y errores con código, OpenAPI `implemented` y tests Java. Mantener nombres de métrica estables, evitar UUID/tokens en tags y aislar preparación, negativas y mutaciones. Actualizar matriz y guía de fixtures junto al script.

Para cambios exclusivamente k6/documentación, ejecutar desde la raíz `./k6/check-syntax.ps1` (PowerShell 7 y Node), ejecutar `node --experimental-vm-modules --test k6/tests/offline.test.mjs`, revisar imports/rutas y `git diff --check`; el análisis sintáctico no evalúa módulos y las regresiones semánticas usan respuestas sintéticas y dobles sin red. Ninguno acredita el runtime real ni rendimiento. Las cargas solo se ejecutan con entorno y datos autorizados; stress no es obligatorio por contribución ni se integra en CI. No versionar resultados, manifiestos privados ni secretos. No regenerar OpenAPI/HTML si no cambia el contrato.

## CI, bases locales y migraciones

El [workflow CI](.github/workflows/ci.yml) valida PR hacia main, push a main y ejecución manual: backend (Surefire y catorce suites PostgreSQL Testcontainers), frontend (npm ci, npm test, TypeScript, ESLint y exportación web), OpenAPI y Compose. No incluye `NeonConnectivityIT`, el perfil `neon-storage` ni el runner HTTP mutativo `frontend/scripts/integration.cjs`. No necesita secretos ni despliega; las ramas se comprueban por PR/manual para evitar duplicados.

Desarrollar mediante el único Compose: cada integrante usa su PostgreSQL local persistente. Cambiar de rama no separa el historial del volumen; coordinar versiones Flyway antes de integrar y validar la secuencia sobre una base nueva cuando sea incompatible. No reescribir migraciones compartidas ni usar `repair` o `down -v` como reparación rutinaria. La [guía](docs/development.md#3-ramas-y-migraciones-concurrentes) describe el procedimiento seguro.

## Pull Requests

Usar la [plantilla](.github/pull_request_template.md): descripción, tipo, alcance, pruebas, checklist e issue asociado cuando exista. Para un tag funcional, incluir operaciones y DTO revisados, entidades/relaciones comprobadas, decisiones abiertas, migraciones, pruebas ejecutadas y compatibilidad con otros tags.

Si el PR modifica **solo documentación**, dejarlo explícito y comprobar contenido, enlaces, fuente y exportaciones de diagramas sin atribuirle una ejecución de tests Java no realizada. Cuando se reorganizan paquetes, actualizar rutas de ejemplos, tests y arquitectura antes de completar el PR.

## Documentación

Mantener sincronizados OpenAPI, dominio, ADR y gráficos **cada vez que cambie una regla o entidad relevante**. Los cambios arquitectónicos importantes requieren ADR nuevo o revisión explícita del correspondiente. No reproducir informes temporales de Codex en enlaces de documentación permanente: su información verificable se consolida en los documentos estables del proyecto.

## Tramitación y migraciones

La primera fase vive en processing dentro del monolito. La integración del 16/11/2026 consolida excepcionalmente V1 antes de su primera aplicación; una V1 ya aplicada es inmutable. Registrar toda nueva integración PostgreSQL en las inclusiones explícitas de postgres-it. No ejecutar pruebas ordinarias contra Neon. Al cambiar el contrato, editar las fuentes OpenAPI y regenerar YAML/HTML con Redocly 2.16.0. Las fuentes drawio se editan preservando geometrías; sus SVG se exportan manualmente.
