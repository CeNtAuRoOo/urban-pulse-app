# Documentación de la API de UrbanPulse

## Estado: dominio y API alineados como versión base

UrbanPulse sigue un enfoque **design-first**: revisar casos de uso, modelo de dominio y contrato antes de desarrollar controladores, persistencia y frontend. El OpenAPI **3.0.3** (versión del contrato `0.2.0`) está sincronizado en sus decisiones base con el [dominio v1](../domain-model.md) y los ADR. Su catálogo de rutas y DTO sigue siendo **diseño**, no código ejecutado ni validación exhaustiva de cada caso de uso.

Desde el 18/10/2026 se dispone además de un [**modelo de dominio lógico v1**](../domain-model.md), con [diagrama relacional y catálogo de clases](../diagrams/README.md). **Los dos artefactos son versiones base revisables.** En particular, la aparición de una clase en UML no demuestra que sea una entidad Java existente, ni la aparición de un endpoint demuestra que haya un controlador funcional. La aprobación de un PR de tag valida **el alcance de ese tag** y sus entidades involucradas, no todo el modelo.

**Ajustes de consistencia antes de implementar:** los códigos de rol se restringen según su ámbito (`GlobalRoleCode` / `DepartmentRoleCode`), los identificadores de entidades del contexto urbano se distinguen de los IDs externos, y `StatusChangeDTO` admite que `fromStatus` o `changedBy` estén ausentes. Se han desactivado los dos endpoints de evaluación IA que presuponían un motor y una ejecución asíncrona no acordados: sus archivos YAML quedan solo como exploración no incluida en el contrato activo.

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

## Alineación entre OpenAPI y dominio

Desde esta revisión, **los dos diagramas y el contrato modular representan una única versión base de diseño**. Se han armonizado roles globales/departamentales, estados de la incidencia, derivación versus trabajo, invitaciones, duplicados e identificadores UUID. Consulta el [documento de integración](domain-openapi.md), que mantiene la tabla de correspondencia entre decisiones y operaciones.

El proceso de implementación sigue siendo incremental: al elegir un tag **no se buscan discrepancias conocidas entre dos diseños desactualizados**, sino que se verifica y concreta conjuntamente el alcance del dominio y API. Toda decisión nueva o corrección posterior modifica ambas vistas, y se revisa su efecto sobre otros tags en el mismo PR.

Los módulos opcionales de IA entrenada, _hotspots_ predictivos y RAG permanecen como material exploratorio, fuera de las rutas de la especificación base activa. El tag Catálogos implementa exclusivamente `Department`, incluido el código corto único del DTO, su migración y sus endpoints; el resto continúa como diseño pendiente.

## Validación y visualización

Desde la raíz del repositorio, si se dispone de Node/npm y acceso a Redocly CLI:

```bash
npx @redocly/cli lint docs/api/openapi/base-openapi.yaml
npx @redocly/cli bundle docs/api/openapi/base-openapi.yaml -o docs/api/openapi.yaml
npx @redocly/cli build-docs docs/api/openapi.yaml -o docs/api/openapi.html
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

**Referencia unificada:** [Integración entre dominio y OpenAPI](domain-openapi.md).
