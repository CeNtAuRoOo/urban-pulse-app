# Guía de contribución

UrbanPulse se desarrolla de forma incremental, revisando **el dominio y OpenAPI como una especificación conjunta** antes de implementar cada tag. La documentación distingue el modelo de diseño global del subconjunto ejecutable: Autenticación, Usuarios, Invitaciones, catálogos, Incidencias y Evidencias se apoyan actualmente en una única migración Flyway V1 consolidada. La estructura Java actual se organiza por módulos de negocio, no por paquetes técnicos globales.

## Flujo de trabajo

1. Actualizar `main` antes de comenzar el desarrollo.
2. Crear una rama específica para la funcionalidad, corrección, refactor o documentación.
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
npx expo install --check
npx tsc --noEmit
npm run lint
npx expo export --platform web
```

**Backend**, desde `backend/`, con Java 25, Maven y Docker:

```bash
mvn -B -ntp test
mvn -B -ntp clean verify -Ppostgres-it
```

El perfil `postgres-it` añade `AuthenticationPostgresIT`, `EmailVerificationPostgresIT`, `StaffInvitationPostgresIT`, `UserRepositoryPostgresIT`, `DepartmentPostgresIT`, `CategoryPostgresIT`, `DepartmentMembershipPostgresIT`, `IncidentPostgresIT` y `AttachmentServicePostgresIT` con PostgreSQL 18 Testcontainers. `NeonConnectivityIT` está en `shared/storage/integration` y se ejecuta **solo** de manera explícita con destino Neon `development` autorizado:

```bash
docker compose run --rm backend mvn -B -Dtest=NeonConnectivityIT test
```

No lanzar tests de escritura en Neon compartido ni migraciones experimentales sobre `production`. Los resultados de 90 pruebas y HTTP aislado figuran en un informe fechado el 30/10/2026; **el orden Git sitúa la reorganización antes de los commits de Invitaciones y pruebas**, por lo que no deben invertirse los hitos por la fecha del informe. Registrar una ejecución nueva solo cuando se realice efectivamente.

**API (design-first):**

```bash
npx --yes @redocly/cli@2.16.0 lint docs/api/openapi/base-openapi.yaml
npx --yes @redocly/cli@2.16.0 bundle docs/api/openapi/base-openapi.yaml -o docs/api/openapi.yaml
npx --yes @redocly/cli@2.16.0 build-docs docs/api/openapi.yaml -o docs/api/openapi.html
```

Los artefactos `docs/api/openapi.yaml` y `docs/api/openapi.html` son derivados y no se editan manualmente. Comprobar versión `0.7.0`, enlaces `$ref` y cambios esperados antes de incluirlos en el commit.

**Documentación:** comprobar vínculos relativos, presencia de todos los SVG que se enlazan, correspondencia `.drawio`/exportación, ausencia de referencias a informes temporales y coherencia con el árbol Java actual. Ejecutar `git diff --check` y comprobar que no se han incorporado secretos, datos personales reales, `.env`, `target`, `dist` o `node_modules`.

El informe de pruebas HTTP de Invitaciones utilizó `compose.test.yaml` y `backend/scripts/invitations_http_test.py` con PostgreSQL/Mailpit efímeros, **pero esos archivos no figuran en el repositorio**. No presentar ese flujo como reproducible sin restaurar y revisar el runner. Las suites Maven/Testcontainers configuradas en el proyecto se describen en [Desarrollo](docs/development.md); la política funcional y de seguridad, en [ADR-0007](docs/adr/0007-invitaciones-personal.md).

## Pull Requests

Usar la [plantilla](.github/pull_request_template.md): descripción, tipo, alcance, pruebas, checklist e issue asociado cuando exista. Para un tag funcional, incluir operaciones y DTO revisados, entidades/relaciones comprobadas, decisiones abiertas, migraciones, pruebas ejecutadas y compatibilidad con otros tags.

Si el PR modifica **solo documentación**, dejarlo explícito y comprobar contenido, enlaces, fuente y exportaciones de diagramas sin atribuirle una ejecución de tests Java no realizada. Cuando se reorganizan paquetes, actualizar rutas de ejemplos, tests y arquitectura antes de completar el PR.

## Documentación

Mantener sincronizados OpenAPI, dominio, ADR y gráficos **cada vez que cambie una regla o entidad relevante**. Los cambios arquitectónicos importantes requieren ADR nuevo o revisión explícita del correspondiente. No reproducir informes temporales de Codex en enlaces de documentación permanente: su información verificable se consolida en los documentos estables del proyecto.
