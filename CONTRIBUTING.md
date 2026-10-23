# Guía de contribución

Este documento establece las normas para contribuir a UrbanPulse. El repositorio se desarrolla de forma incremental, con un **dominio–OpenAPI sincronizado** cuyas vistas se revisan como una sola especificación al implementar cada funcionalidad.

## Flujo de trabajo

1. Actualizar `main` antes de empezar.
2. Crear una rama para el cambio.
3. Revisar requisitos, diseño y decisiones del ámbito correspondiente.
4. Implementar exclusivamente el alcance aprobado, incluidos contratos, persistencia y pruebas cuando corresponda.
5. Realizar commits pequeños, descriptivos y coherentes.
6. Ejecutar las verificaciones pertinentes y documentar resultados reales.
7. Abrir Pull Request hacia `main`, solicitar revisión y fusionar únicamente cuando esté aprobado.

## Ramas

Prefijos admitidos:

- `feature/`: funcionalidad nueva.
- `fix/`: corrección.
- `docs/`: documentación y diagramas.
- `refactor/`: refactorización sin cambios funcionales.
- `test/`: pruebas.
- `ci/`: integración continua.

## Commits

Usar **Conventional Commits**: prefijos estándar en inglés y descripción en español:

`tipo(scope): descripción del cambio`

El `scope` es opcional para cambios generales. Ejemplos:

- `feat(backend): añadir endpoint para crear incidencias`
- `feat(frontend): implementar formulario de incidencias`
- `fix(backend): corregir validación de incidencias`
- `refactor(backend): simplificar servicios de tramitación`
- `test(backend): añadir pruebas de asignaciones`
- `docs(diagrams): incorporar modelos relacional y de clases`
- `docs(domain): documentar la revisión incremental del dominio`
- `docs(adr): registrar decisiones de roles y asignaciones`
- `ci: configurar comprobaciones automatizadas`

## Dominio–OpenAPI: revisión conjunta obligatoria por tag

La documentación [del modelo de dominio v1](docs/domain-model.md) contiene las decisiones y entidades previstas; **no es código implementado ni una validación integral de todas las entidades**. En [diagramas](docs/diagrams/README.md) hay:

- `relacional.drawio`: cuatro páginas de tablas candidatas, PK/FK, relaciones y cardinalidades.
- `clases-simplificado.drawio`: una sola página de campos/tipos Java, enumeraciones, objetos embebidos y DTO de apoyo **sin conexiones UML**, ya que las relaciones están en el ER.

**No se añadirán todas las entidades al backend de forma anticipada.** Cada compañero que trabaje en un tag realizará la revisión de las clases y tablas que necesita, además del contrato API, y **comunicará cambios relevantes sobre clases compartidas**.

Antes de implementar una funcionalidad:

1. Comprobar los casos de uso del enunciado y su alcance, incluidos los apartados opcionales aplazados.
2. Leer `docs/api/openapi/paths/<tag>.yaml`, los DTO de `components/schemas/` y la guía [`docs/api/README.md`](docs/api/README.md).
3. Examinar **ambos diagramas**; verificar campos, tipos, cardinalidades, nulabilidad, relaciones, persistencia, permisos y reglas de negocio de las entidades afectadas.
4. Resolver nuevas decisiones o divergencias detectadas durante la implementación a partir de los requisitos y los [ADR](docs/adr/README.md), sin tratar automáticamente los datos de un dibujo como decisiones cerradas.
5. Actualizar en el mismo PR los esquemas OpenAPI modulares, diagramas `.drawio` y sus exportaciones, el [modelo de dominio](docs/domain-model.md), los ADR y las pruebas que correspondan. No alterar recursos ajenos sin coordinación.
6. Implementar **solo el subconjunto requerido** de entidades JPA, migraciones Flyway, servicios, controladores y frontend. No usar `ddl-auto=update`; probar migraciones en entorno aislado/`development`, nunca de forma experimental en `production`.
7. Explicar qué se validó, qué se implementó y qué quedó pendiente. Aprobar un tag **no valida automáticamente todo el modelo**.

Para decisiones con impacto arquitectónico no resueltas por los ADR vigentes, documentar un nuevo ADR. Las modificaciones menores de una entidad pueden registrarse en el PR y en `docs/domain-model.md` sin crear ADR por cada campo.

## Verificaciones por ámbito

- **Frontend:** en `frontend/`, ejecutar `npx expo install --check`, `npx tsc --noEmit` y `npm run lint`; cuando aplique, `npx expo export --platform web`. Documentar advertencias relevantes de dependencias.
- **Backend:** ejecutar `mvn test`/`mvn verify` para tests aislados. Los tests externos `*IT` se invocan expresamente, por ejemplo `docker compose run --rm backend mvn -B -Dtest=NeonConnectivityIT test`, confirmando que se apunta a Neon `development`. **No** ejecutar tests externos o migraciones contra `production`.
- **Contrato API:** tras revisar el tag, ejecutar (si se dispone de la herramienta) `npx @redocly/cli lint docs/api/openapi/base-openapi.yaml` y regenerar `docs/api/openapi.yaml` desde el modular y `docs/api/openapi.html` desde el bundle; revisar ambos diffs. Consultar [la guía unificada](docs/api/domain-openapi.md).
- **Diagramas y documentación:** abrir los `.drawio` en diagrams.net; comprobar las cuatro páginas ER y la página de clases, exportaciones y vínculos Markdown. Ejecutar `git diff --check`, verificar que no se incluyen secretos, datos personales reales ni ficheros generados ajenos al cambio.

La [guía de desarrollo](docs/development.md) distingue qué comandos se han probado realmente. No afirmar que se ejecutaron nuevas comprobaciones si no fue así.

## Pull Requests

Utilizar [la plantilla](.github/pull_request_template.md): descripción, ámbito, tipo, pruebas, checklist e issue asociado cuando exista. Para PR funcional de un tag añadir dentro de la descripción:

- Tag y operaciones/DTO revisados.
- Entidades, relaciones y restricciones revisadas en los dos diagramas.
- Divergencias de diseño resueltas y decisiones aún abiertas.
- Componentes implementados y pruebas ejecutadas (o motivo por el que no corresponden).
- Impacto sobre otros tags o entidades compartidas y documentación actualizada.

Para PR **solo documental**, señalar expresamente que **no hay cambios de lógica, API desplegada ni migraciones**; basta verificar la documentación, los diagramas y los vínculos relativos (no inventar tests de backend).

## Documentación

Mantener sincronizados el contrato API, el modelo del dominio, los ADR y las exportaciones del diagrama **cuando su área de negocio evolucione**. Los cambios arquitectónicos importantes requieren un ADR o la revisión explícita de uno existente.
