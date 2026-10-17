# Documentación de la API

## Estado del contrato

UrbanPulse adopta un enfoque **design-first**: se define y revisa el contrato REST antes de implementar sus controladores, servicios y clientes. Existe una **primera versión de diseño**, en **OpenAPI 3.0.3** (versión del contrato `0.1.0`), que propone **16 tags, 53 rutas y 72 operaciones HTTP**, con **80 esquemas/DTOs** reutilizables. Estas cifras describen la especificación adjunta, **no funcionalidades implementadas**.

**Estado:** borrador inicial **pendiente de revisión funcional y técnica por cada tag**. Las rutas, los permisos, los campos obligatorios, los códigos de respuesta y las reglas de negocio pueden cambiar durante dicha revisión. La presencia de una operación en OpenAPI **no implica** que exista en el backend ni que se haya acordado definitivamente.

## Archivos y fuente de verdad

```text
docs/api/
├── README.md
├── openapi.yaml                 # Copia completa actual; NO editar a mano
└── openapi/
    ├── base-openapi.yaml        # Punto de entrada de la especificación modular
    ├── paths/                  # 16 archivos; operaciones agrupadas por tag
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
        ├── schemas/            # DTOs y tipos por ámbito; incluye comunes.yaml
        ├── parameters.yaml     # Parámetros reutilizables
        ├── responses.yaml      # Respuestas de error reutilizables
        └── security-schemes.yaml
```

La **fuente de verdad editable** es [`openapi/base-openapi.yaml`](openapi/base-openapi.yaml) junto con sus dependencias en `openapi/paths/` y `openapi/components/`. El archivo `base-openapi.yaml` reúne las rutas mediante `$ref` externos. Cada fichero de `paths/` agrupa las rutas asociadas a un tag, y los DTOs se mantienen en `components/schemas/`.

**Importante:** [`openapi.yaml`](openapi.yaml) es, en el estado adjunto, una **copia monolítica completa** que convive con los archivos modulares. No se actualiza automáticamente al editar un tag: para evitar dos versiones divergentes, **no se debe editar directamente**. Si se decide conservarla en Git, habrá que **regenerarla y verificarla en cada cambio del contrato**; si no es necesario versionar el resultado unificado, se podrá retirarla en un PR posterior. No se ha decidido todavía automatizar esta regeneración en CI.

### Referencias y convenciones de edición

- Las operaciones se organizan por funcionalidad (`tag`), no necesariamente por el prefijo de la URL: por ejemplo, los endpoints `/incidents/{incidentId}/attachments` se mantienen en `paths/evidencias.yaml`.
- En los `paths/*.yaml`, las claves de primer nivel son **rutas completas** (por ejemplo, `/auth/login`); sus referencias desde `base-openapi.yaml` utilizan punteros JSON escapados (`~1` para `/`).
- Para parámetros, respuestas y DTOs compartidos se usan referencias `$ref` relativas al archivo que las contiene. Los cambios en un componente común pueden afectar a varios tags.
- El orden de documentación de una operación será: `tags`, `summary`, `description`, `operationId`, `security` (si corresponde), `x-roles`, `parameters` / `requestBody` (si corresponde) y `responses`.
- `x-roles` es una **extensión documental**: **no implementa seguridad**. El esquema `BearerAuth` define JWT Bearer por defecto; las operaciones públicas lo anulan con `security: []`. El backend deberá aplicar los controles efectivos, incluida la titularidad del recurso.
- La primera versión **no utiliza** `x-phase`, `x-requirements` ni anclas/alias YAML.

## Flujo de trabajo: una tarea por tag

Cada tag es una **unidad de revisión y desarrollo**, no una promesa de implementación simultánea de toda la API. Antes de comenzar una tarea, la persona responsable deberá:

1. **Abrir el contrato del tag** en `openapi/paths/<tag>.yaml` y los DTOs correspondientes en `openapi/components/schemas/` (y consultar `base-openapi.yaml` para el contexto general).
2. **Revisar** casos de uso, rutas, métodos, semántica HTTP, parámetros, campos obligatorios/opcionales, validaciones, respuestas, errores y visibilidad de datos. Confirmar los roles y las reglas por titularidad/estado; identificar decisiones aún propuestas.
3. **Acordar los cambios** con el equipo antes de implementar. Actualizar las referencias y esquemas comunes si procede, revisando el impacto sobre **otros tags**. Un tag no se considera revisado solo por existir en el borrador inicial.
4. **Validar la especificación** y dejar constancia de qué comprobaciones se han ejecutado. Si se conserva la copia monolítica, regenerarla a partir de la estructura modular, nunca a la inversa.
5. **Implementar después** el backend/frontend y añadir pruebas de contrato, autorización, validaciones y respuestas para las operaciones abordadas. El PR deberá indicar qué partes se han revisado, cuáles están implementadas y cuáles siguen pendientes.

La revisión de un tag se considerará acordada **cuando el equipo apruebe el PR correspondiente**; esta guía no introduce nuevos marcadores de estado en OpenAPI. Para cambios contractuales entre tags, coordinarse antes de fusionar PR concurrentes.

## Validación y visualización

Desde la **raíz del repositorio** (requiere Node.js/npm y acceso a Redocly CLI, si no está instalado):

```bash
npx @redocly/cli lint docs/api/openapi/base-openapi.yaml
npx @redocly/cli bundle docs/api/openapi/base-openapi.yaml -o docs/api/openapi.yaml
```

El primer comando permite **comprobar** la especificación modular; el segundo **genera** una versión de un solo archivo para herramientas que no resuelvan referencias externas. Son **comandos propuestos**, no pruebas ya ejecutadas en este repositorio; sus resultados deberán verificarse antes de dar por válido el PR. El bundle sustituye el contenido de `docs/api/openapi.yaml`, por lo que conviene revisar `git diff` después de generarlo.

Para visualizarlo en Swagger Editor u otra herramienta que solo admita un archivo, utilizar **la copia unificada regenerada**, no abrir de forma aislada un `paths/*.yaml`, pues esos archivos no constituyen especificaciones OpenAPI completas.

## Decisiones todavía abiertas

Esta primera versión contempla JWT y autorizaciones por rol (ADR-0005, **aceptado**), así como un flujo de invitaciones municipales (ADR-0007, **propuesto**). Permanecen pendientes de confirmación, entre otras cuestiones, el detalle de las transiciones de estado, permisos por operación, límites de fotografías, expiración y renovación de tokens, procesamiento asíncrono y algunos DTOs. No dar estas reglas por aprobadas solo porque aparezcan en los archivos YAML.

### Solicitudes `fields`

La selección dinámica de campos **no está aprobada ni forma parte del contrato actual**. Antes de introducirla deberán definirse listas blancas, autorización por campo, significado de `null` y compatibilidad con DTOs de lectura/escritura. La optimización de consultas al repositorio se medirá antes de incorporarla.

## Referencias

- [Guía de contribución](../../CONTRIBUTING.md)
- [Guía de desarrollo](../development.md)
- [Arquitectura](../architecture.md)
- [ADR de JWT](../adr/0005-jwt-spring-security.md)
- [ADR de invitaciones — propuesto](../adr/0007-invitaciones-personal.md)
