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

La documentación del [modelo de dominio](docs/domain-model.md) es una referencia inicial **revisable**, no una afirmación de que todas las tablas o clases estén implementadas. Consultar [diagramas](docs/diagrams/README.md): `relacional.drawio` tiene cinco páginas con entidades, PK/FK y relaciones; `clases-simplificado.drawio` presenta un catálogo Java de atributos y tipos sin líneas UML deliberadamente.

1. Analizar requisitos y casos de uso, incluidos los componentes aplazados del enunciado.
2. Leer `docs/api/openapi/paths/<tag>.yaml`, los esquemas de `components/schemas/` y la [guía de API](docs/api/README.md).
3. Contrastar **las dos vistas del dominio**: tipos, nulabilidad, restricciones, cardinalidades, roles, transiciones y persistencia; no dar por implementado lo dibujado.
4. Resolver divergencias con los [ADR](docs/adr/README.md) o registrar una nueva decisión cuando afecte a la arquitectura.
5. Actualizar en el mismo PR **las fuentes `.drawio` y sus SVG**, los YAML modulares, el modelo de dominio, los ADR y las pruebas pertinentes. No alterar componentes ajenos sin coordinación.
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

**OpenAPI**, desde la raíz, con Node 24.22.3 y npm 11.19.1:

```bash
npm --prefix tools/openapi ci
npm --prefix tools/openapi run check
```

El paquete de [herramientas OpenAPI](tools/openapi/README.md) tiene su propio lockfile.
Usar siempre `npm ci` para reproducir artefactos; las actualizaciones de dependencias
requieren revisión explícita del lockfile, auditoría y comparación de los derivados.

**Backend**, desde `backend/`, con Java 25, Maven y Docker:

```bash
mvn -B -ntp test
mvn -B -ntp clean verify -Ppostgres-it
```

Los [procedimientos de desarrollo](docs/development.md) describen el entorno local, Maven y las suites PostgreSQL. Para un cambio de contrato, editar las fuentes modulares y reproducir las [comprobaciones Redocly](docs/api/README.md#validación-y-visualización). Para k6, mantener las regresiones de Node y comprobar el [lanzador local](k6/README.md).

Las migraciones instaladas en bases conservables son inmutables. Coordinar las nuevas versiones entre ramas y ensayarlas en una BD vacía. El SQL sintético pertenece exclusivamente al entorno Compose. Los tests ordinarios utilizan PostgreSQL efímero y dobles S3.

## Pull Requests

Usar la [plantilla](.github/pull_request_template.md): descripción, tipo, alcance, pruebas, checklist e issue asociado cuando exista. Para un tag funcional, incluir operaciones y DTO revisados, entidades/relaciones comprobadas, decisiones abiertas, migraciones, pruebas ejecutadas y compatibilidad con otros tags.

Si el PR modifica **solo documentación**, dejarlo explícito y comprobar contenido, enlaces, fuente y exportaciones de diagramas sin atribuirle una ejecución de tests Java no realizada. Cuando se reorganizan paquetes, actualizar rutas de ejemplos, tests y arquitectura antes de completar el PR.

## Documentación

Mantener sincronizados OpenAPI, dominio, ADR y gráficos **cada vez que cambie una regla o entidad relevante**. Los cambios arquitectónicos importantes requieren ADR nuevo o revisión explícita del correspondiente. No reproducir informes temporales de Codex en enlaces de documentación permanente: su información verificable se consolida en los documentos estables del proyecto.
