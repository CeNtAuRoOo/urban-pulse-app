# Documentación de la API de UrbanPulse

## Estado: especificación inicial pendiente de revisión por tag

UrbanPulse sigue un enfoque **design-first**: revisar casos de uso, modelo de dominio y contrato antes de desarrollar controladores, persistencia y frontend. La primera versión de OpenAPI **3.0.3** (versión del contrato `0.1.0`) propone **16 tags, 53 rutas, 72 operaciones y 80 esquemas/DTO reutilizables**. Estas cifras describen el borrador existente, **no funcionalidades implementadas** ni aprobadas individualmente.

Desde el 18/10/2026 se dispone además de un [**modelo de dominio lógico v1**](../domain-model.md), con [diagrama relacional y catálogo de clases](../diagrams/README.md). **Los dos artefactos son versiones base revisables.** En particular, la aparición de una clase en UML no demuestra que sea una entidad Java existente, ni la aparición de un endpoint demuestra que haya un controlador funcional. La aprobación de un PR de tag valida **el alcance de ese tag** y sus entidades involucradas, no todo el modelo.

## Fuente editable y organización del contrato

```text
docs/api/
├── README.md
├── openapi.yaml                 # Bundle completo actual: NO editar a mano
└── openapi/
    ├── base-openapi.yaml        # Entrada modular; fuente de verdad
    ├── paths/                  # Operaciones por tag (16 archivos)
    │   ├── autenticacion.yaml
    │   ├── usuarios.yaml
    │   ├── invitaciones.yaml
    │   ├── catalogos.yaml
    │   ├── incidencias.yaml
    │   ├── evidencias.yaml
    │   ├── tramitacion.yaml
    │   ├── duplicados.yaml
    │   ├── contexto-urbano.yaml
    │   ├── mapas.yaml
    │   ├── notificaciones.yaml
    │   ├── analitica.yaml
    │   ├── informes.yaml
    │   ├── asistencia-inteligente.yaml
    │   ├── conocimiento.yaml
    │   └── administracion.yaml
    └── components/
        ├── schemas/            # DTOs por ámbito y comunes.yaml
        ├── parameters.yaml
        ├── responses.yaml
        └── security-schemes.yaml
```

La fuente de verdad editable es [`openapi/base-openapi.yaml`](openapi/base-openapi.yaml) junto con sus `$ref` a `paths/` y `components/`. `openapi.yaml` es una **copia monolítica que no se actualiza automáticamente** al editar otro archivo; si se versiona, hay que regenerarla y comprobarla.

### Convenciones

- La organización se hace por funcionalidad (`tag`), no siempre por prefijo de URL. Ejemplo: `/incidents/{incidentId}/attachments` se documenta en `paths/evidencias.yaml`.
- Las claves de primer nivel en cada `paths/*.yaml` son rutas completas; las referencias desde `base-openapi.yaml` utilizan punteros JSON con `/` escapado como `~1`.
- Se reutilizan esquemas, parámetros, seguridad y respuestas; un cambio común puede afectar varios tags.
- Orden editorial de una operación: `tags`, `summary`, `description`, `operationId`, `security` (si aplica), `x-roles`, `parameters`/`requestBody` (si aplica) y `responses`.
- `x-roles` es **solo documental**. El backend deberá comprobar rol, pertenencia departamental, titularidad y estado de recursos según las decisiones aceptadas. El esquema `BearerAuth` define JWT Bearer por defecto; las operaciones públicas lo anulan con `security: []`.
- La primera especificación no utiliza `x-phase`, `x-requirements` ni anclas/alias YAML.

## Flujo obligatorio: un tag y el dominio que utiliza

Cada tag es una **unidad de revisión e implementación**, no el compromiso de implementar todo OpenAPI o todas las entidades del modelo simultáneamente.

1. **Requisitos:** identificar casos de uso y límites del enunciado original; no incorporar por inercia partes avanzadas opcionales en rojo (IA, entrenamiento de modelos, RAG) que no se hayan aprobado para el alcance.
2. **Contrato:** abrir `openapi/paths/<tag>.yaml` y los esquemas/DTO correspondientes; revisar rutas, HTTP, reglas funcionales, errores, filtros, paginación, campos y visibilidad.
3. **Dominio:** abrir **ambos** [`relacional.drawio`](../diagrams/relacional.drawio) (PK/FK, cardinalidades) y [`clases-simplificado.drawio`](../diagrams/clases-simplificado.drawio) (campos Java, enums, `@Embeddable` y DTO). Verificar entidades compartidas aunque aparezcan en otros tags.
4. **Decisiones:** contrastar [modelo de dominio](../domain-model.md) y [ADR](../adr/README.md), establecer límites reales y nulabilidad, roles globales/departamentales, autorizaciones, transiciones, invariantes, historia y almacenamiento. **No copiar sin revisar los límites del borrador OpenAPI ni los campos del UML.**
5. **Acordar el cambio:** si los diagramas y OpenAPI divergen, resolverlo en el PR del tag, revisar impacto en otros tags y modificar coordinadamente la fuente modular, los dos diagramas y los ADR que corresponda. Una clase dibujada no obliga a materializar una tabla o endpoint si el caso de uso no lo requiere.
6. **Validar y después implementar:** ejecutar lint del contrato; regenerar y revisar `openapi.yaml` si se mantiene. Añadir controladores, DTO, servicios, entidades/repositorios y migraciones **solo del ámbito implementado**, con pruebas de contrato, validaciones, autorización, lógica y persistencia aislada. No ejecutar migraciones experimentales sobre `production`.
7. **Documentar en el PR:** especificar qué tag, endpoints, DTO y entidades se **revisaron**, qué se **implementó**, qué queda **pendiente** y qué pruebas se ejecutaron. El modelo completo seguirá en revisión por otros compañeros.

Un tag se considera revisado cuando su PR se aprueba con sus cambios coherentes. Coordinar los cambios en entidades compartidas antes de fusionar PR concurrentes.

## Diferencias conocidas entre el OpenAPI inicial y el dominio v1

Estas diferencias se documentan **sin modificar todavía los YAML de contrato** en el presente PR, precisamente porque se revisarán por funcionalidad:

| Tema             | Situación del contrato actual                                                 | Diseño del dominio v1 / acción cuando se implemente                                                                                                         |
| ---------------- | ----------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ciclo de vida    | Enum `IncidentStatus` con `ASSIGNED`                                          | Sustituido conceptualmente por `ASSIGNED_TO_DEPARTMENT` y `WORK_ASSIGNED`; revisar rutas, filtros, DTO y transiciones en `Incidencias`/`Tramitación`.       |
| Roles            | `UserDTO`/`UserSummaryDTO` con un `role`, `UserRole` sin ámbito departamental | `Role`, `UserRole` (roles globales), `DepartmentMembership` (roles departamentales); acordar exposición en DTO y política de permisos.                      |
| Asignación       | `AssignmentRequest` contiene `departmentId`, `teamId`, `technicianId`         | Derivación a departamento mediante `Incident.assignedDepartment`, distribución concreta a `Team` o técnico en `Assignment`; revisar recursos y operaciones. |
| Invitaciones     | Invitación inicial ligada a un rol, con reglas incompletas                    | ADR-0007 **aceptado**: rol con ámbito, posible departamento y canje por una cuenta ya existente.                                                            |
| Identificadores  | IDs ilustrativos de cadena (p. ej. `usr_1`, `inc_001`)                        | Convención propuesta UUIDv7 (ADR-0011); adecuar ejemplos y esquemas `format: uuid` al revisar.                                                              |
| Duplicados       | Sugerencias y enlaces como contratos distintos                                | `DuplicateSuggestionDTO` calculado (no persiste); `DuplicateLink` solo con confirmación humana. Adaptar representación final por tag.                       |
| Contexto externo | DTOs y operaciones en borrador                                                | Dominio **provisional**; no seleccionar proveedores ni materializar estructuras definitivas hasta estudiarlo.                                               |
| IA/RAG           | Tags presentes en borrador                                                    | Funcionalidades avanzadas marcadas opcionales en el enunciado se posponen, no se convierten automáticamente en backlog de la primera fase.                  |

No debe confundirse un ADR de diseño aceptado con la revisión del **detalle de un endpoint**: formatos y reglas operativas deben validarse en el tag.

## Validación y visualización

Desde la raíz del repositorio, si se dispone de Node/npm y acceso a Redocly CLI:

```bash
npx @redocly/cli lint docs/api/openapi/base-openapi.yaml
npx @redocly/cli bundle docs/api/openapi/base-openapi.yaml -o docs/api/openapi.yaml
```

Son **comandos propuestos, no pruebas ejecutadas para este PR**. El bundle sobrescribe `docs/api/openapi.yaml`; revisar `git diff` y que no haya divergencia del modular. Para Swagger Editor u otros visores sin soporte de `$ref` externos, abrir la especificación unificada regenerada.

## Cuestiones aún abiertas

- Matriz exacta de transiciones de estado y permisos por operación/ámbito.
- Definición detallada de pertenencias, transferencias y reasignaciones, contraseñas/JWT e invitaciones.
- Límites de fotografías, políticas EXIF/privacidad y consistencia SQL/Object Storage.
- Alcance real de integración con fuentes externas, caducidad y modelo de datos geográficos.
- Semántica de DTO compartidos y su compatibilidad con las clases previstas.
- La selección dinámica de campos `fields` **no está aprobada ni forma parte del contrato actual**; evaluarla separadamente si surge una necesidad medida.

## Referencias

- [Modelo de dominio y revisión por tag](../domain-model.md)
- [Diagramas](../diagrams/README.md)
- [Contribución](../../CONTRIBUTING.md)
- [Arquitectura](../architecture.md)
- [ADR-0005: JWT](../adr/0005-jwt-spring-security.md)
- [ADR-0007: Invitaciones](../adr/0007-invitaciones-personal.md)
- [ADR-0009: RBAC y departamentos](../adr/0009-rbac-departamentos-equipos.md)
- [ADR-0010: Tramitación](../adr/0010-ciclo-vida-y-asignaciones.md)
