# UrbanPulse: contrato de dominio y API unificados (versión base v1 / API 0.2.0)

> **Estado:** referencia de diseño compartida, no implementación funcional ni aprobación de cada restricción. Esta actualización alinea las decisiones aceptadas del dominio con el contrato OpenAPI modular; la revisión detallada se efectúa **por tag y caso de uso**. Las entidades no tienen por qué existir todavía en JPA.

## 1. Una referencia coherente, varias vistas

El conjunto de diseño se compone de:

- **Requisitos del proyecto y ADR aceptados:** reglas y decisiones de arquitectura/negocio.
- **Modelo relacional**: `docs/diagrams/relacional.drawio` (cuatro páginas); relaciones, claves y cardinalidades previstas.
- **Catálogo de clases**: `docs/diagrams/clases-simplificado.drawio` (una página); entidades Java propuestas, tipos, enum, `@Embeddable` y DTO. Deliberadamente no duplica las líneas de relación del ER.
- **Contrato editable**: `docs/api/openapi/base-openapi.yaml` y los archivos `paths/` y `components/` a los que referencia. Establece **la forma de los recursos expuestos**, no la estructura directa de las tablas.
- **Salidas generadas**: `docs/api/openapi.yaml` (bundle para distribución) y `docs/api/openapi.html` (documentación visual). Se **regeneran**; no son fuentes independientes.

Las representaciones deben conservar la misma semántica. **Un DTO no es necesariamente una entidad JPA** y los campos internos que no se exponen en la API pueden aparecer solo en los diagramas.

## 2. Acuerdos ya reflejados en el contrato

| Tema                     | Regla del dominio                                                   | Representación pública                                                                                                                                                                                               |
| ------------------------ | ------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Identificadores          | UUIDv7 como criterio para entidades con PK; excepciones de catálogo | `string`, `format: uuid` para IDs de entidad; `RoleCode` es código estable y no UUID. `format` no determina cómo se genera el UUID.                                                                                  |
| Roles globales           | `CITIZEN`, `OPERATOR`, `ADMIN` vía `UserRole`                       | `UserDTO.roles`: enum `GlobalRoleCode`, solo roles globales de la cuenta.                                                                                                                                            |
| Roles departamentales    | `DEPARTMENT_COORDINATOR`, `TECHNICIAN` vía `DepartmentMembership`   | `UserDTO.departmentMemberships`: departamento, enum `DepartmentRoleCode` y fechas. Su visibilidad requiere autorización.                                                                                             |
| Operador neutral         | Valida, prioriza y deriva sin tener que pertenecer a departamento   | Operaciones de revisión y derivación con `x-roles: OPERATOR`.                                                                                                                                                        |
| Coordinador              | Distribuye trabajo dentro de sus departamentos                      | `POST /incidents/{incidentId}/assignments`, con comprobación obligatoria de adscripción activa.                                                                                                                      |
| Departamento responsable | `Incident.assignedDepartment` opcional                              | `IncidentDTO.assignedDepartment` y `PUT /incidents/{incidentId}/department`. **No** crea una `Assignment`.                                                                                                           |
| Trabajo                  | `Assignment` temporal a **equipo o técnico** (XOR)                  | `AssignmentRequest` admite exactamente uno de `teamId` o `technicianId`; se conservan reasignaciones.                                                                                                                |
| Aceptación               | `acceptedBy`/`acceptedAt`, distinta del comienzo del trabajo        | Operación de aceptación y datos correspondientes en `AssignmentDTO`.                                                                                                                                                 |
| Estados                  | `VALIDATED` puede existir sin departamento; dos fases distintas     | `ASSIGNED_TO_DEPARTMENT` y `WORK_ASSIGNED` reemplazan al `ASSIGNED` genérico.                                                                                                                                        |
| Invitaciones             | ADR-0007 aceptado; canje por cuenta nueva o existente               | Registro con invitación para cuenta nueva y canje autenticado que reutiliza `User` existente. La petición emplea `oneOf`: para roles `DEPARTMENT`, `departmentId` es obligatorio; para roles `GLOBAL`, no se admite. |
| Duplicados               | Sugerencias calculadas; solo se persiste una relación confirmada    | `DuplicateSuggestionDTO` contiene candidato y similitud; `DuplicateLinkDTO` es confirmación humana.                                                                                                                  |
| Localización             | Coordenadas en `Location`/`Coordinates` embebidos                   | DTO de coordenadas y localización; el sistema externo no está elegido.                                                                                                                                               |

## 3. Diferencia esencial: derivación frente a trabajo

Un recorrido representativo (no equivale a una matriz completa de transiciones) es:

`REPORTED → VALIDATED → ASSIGNED_TO_DEPARTMENT → WORK_ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED`.

- **Revisión:** el operador valida y la incidencia puede permanecer en `VALIDATED` sin unidad responsable.
- **Derivación/transferencia:** el operador elige un departamento; se actualiza `Incident.assignedDepartment` y se audita también si una transferencia no produce nueva transición de estado.
- **Distribución del trabajo:** el coordinador del departamento elige un equipo reutilizable o un técnico individual, crea una `Assignment` temporal y, cuando corresponda, pasa a `WORK_ASSIGNED`.
- **Reasignación:** finaliza la `Assignment` anterior y crea otra sin forzar un `StatusChange` si el estado de negocio no ha cambiado.
- **Aceptación:** registra `acceptedBy`/`acceptedAt` sin transitar automáticamente a `IN_PROGRESS`.

La autorización departamental **no puede decidirse solo por el contenido de `x-roles`**: el backend deberá verificar `DepartmentMembership`, `TeamMembership`, la asignación activa, la propiedad de la incidencia y su estado.

## 4. Qué no se ha cerrado

La alineación de la **versión base** no implica que se hayan aprobado: matriz completa de transiciones, política de caducidad de invitaciones, gestión de revocación de permisos, contrato CRUD de equipos y sus miembros, validaciones de fotografías, detalles de geocodificación, diseño de activos urbanos, proveedores externos ni persistencia de resultados analíticos.

Los casos opcionales marcados en rojo en el enunciado (entrenamiento de modelos, RAG y _hotspots_ predictivos), así como los endpoints de evaluaciones IA que presuponen `modelId` y ejecución asíncrona todavía no decididos, no forman parte de las rutas activas del OpenAPI base. Sus archivos YAML exploratorios pueden conservarse como material de diseño sin obligación de implementación. La asistencia determinista no requiere entrenar un modelo.

No añadir entidades, campos, endpoints, tamaños máximos o permisos **por suposición** para cerrar estos huecos. Abrir una decisión durante el tag correspondiente, consensuarla y actualizar el conjunto de diseño en el mismo PR.

## Precisión sobre los DTO de territorio y auditoría

- `UrbanAssetDTO.id` es el UUID interno; `externalId` es el identificador del proveedor y `sourceId` es la referencia opcional a `ExternalSource`. No deben utilizarse códigos externos como si fueran UUID internos.
- Los identificadores de entidad conocidos (`Incident`, `Category`, `ExternalSource`, `District`, `Notification` y los correspondientes recursos) se expresan como `string` con `format: uuid` tanto en DTO como en los filtros. UUIDv7 sigue siendo una decisión de generación pendiente de implementar.
- `AuditEvent.resourceId` y `requestId` son identificadores heterogéneos y permanecen como cadenas genéricas, porque la auditoría no referencia obligatoriamente una PK UUID.
- En `StatusChangeDTO`, `fromStatus` y `changedBy` pueden omitirse si no existen (creación inicial o acción interna del sistema); la ausencia no elimina los requisitos de auditoría.
- Las variantes de `InvitationRequest` y los enum de roles por ámbito permiten detectar errores de ámbito **en el contrato**, sin delegarlo exclusivamente en una descripción textual.

## 5. Flujo de tareas: una sola unidad dominio–API

1. Escoger el **caso de uso/tag** y consultar el requisito y los ADR relacionados.
2. Estudiar **juntos** el `paths/<tag>.yaml`, sus DTO y las dos vistas del dominio. No existe una fase separada de reconciliación: ambos ya comparten decisiones base.
3. Confirmar campos, nulabilidad, reglas, asociaciones, scopes, autorización, errores y transición de estados **para ese alcance**.
4. Si la implementación revela un ajuste, modificar **en el mismo PR** las fuentes YAML modulares, los diagramas editables y sus SVG, los ADR pertinentes y el documento de dominio. Evaluar el impacto sobre otros tags y coordinarlo antes de fusionar.
5. Implementar solo las entidades/repositorios, Flyway, servicios y endpoints necesarios, con pruebas de contrato, seguridad y persistencia. No crear todas las tablas por anticipado.
6. Validar el YAML de entrada, regenerar `openapi.yaml` y `openapi.html`, revisar el diff y describir los artefactos revisados y las decisiones aún abiertas.

**Fuente editable única:** `docs/api/openapi/base-openapi.yaml` + módulos. El HTML y el YAML agrupado son artefactos derivables, no copias mantenidas manualmente.

## 6. Comandos de validación y generación

Desde la raíz del repositorio, con Node.js/npm disponible:

```bash
npx @redocly/cli lint docs/api/openapi/base-openapi.yaml
npx @redocly/cli bundle docs/api/openapi/base-openapi.yaml --output docs/api/openapi.yaml
npx @redocly/cli build-docs docs/api/openapi.yaml --output docs/api/openapi.html
```

`build-docs` crea la vista HTML a partir del bundle. Este flujo requiere acceso a Redocly CLI; no se ejecuta automáticamente al editar los módulos. Revisar que el bundle/HTML reflejan el `info.version` vigente y que no queden referencias rotas.

## 7. Fuentes de decisiones

- [Modelo de dominio](../domain-model.md).
- [ADR-0007, invitaciones](../adr/0007-invitaciones-personal.md).
- [ADR-0009, roles y equipos](../adr/0009-rbac-departamentos-equipos.md).
- [ADR-0010, estados y trabajo](../adr/0010-ciclo-vida-y-asignaciones.md).
- [ADR-0011, UUIDv7](../adr/0011-identificadores-uuidv7.md).
- [Guía de API](README.md) y [Contribución](../../CONTRIBUTING.md).
