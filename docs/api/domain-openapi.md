# UrbanPulse: contrato de dominio y API unificados (dominio v1 / API 0.3.0 de diseño)

> **Estado: referencia de diseño compartida, no implementación funcional ni validación integral de entidades.** La revisión del 25/10/2026 desarrolla la autenticación, sin sustituir la revisión obligatoria por tag. El contrato anterior `0.2.0` y el dominio v1 constituían una base coherente; `0.3.0` documenta la revisión de autenticación antes del código. Consultar [auth-design.md](../auth-design.md).

## 1. Una referencia coherente, varias vistas

El conjunto de diseño se compone de:

- **Requisitos del proyecto y ADR aceptados:** reglas de negocio y decisiones arquitectónicas, con matices pendientes.
- **Modelo relacional**: `docs/diagrams/relacional.drawio` (cuatro páginas: incidencias, tramitación, identidad/organización, territorio); relaciones, claves y cardinalidades candidatas.
- **Catálogo de clases**: `docs/diagrams/clases-simplificado.drawio` (una página): entidades Java previstas, enums, objetos embebidos y DTO auxiliares; sin líneas de asociación por diseño.
- **Contrato editable**: `docs/api/openapi/base-openapi.yaml` con referencias a `paths/` y `components/`. Define recursos y mensajes HTTP, **no** la forma exacta de las tablas.
- **Salidas generadas**: `docs/api/openapi.yaml` y `docs/api/openapi.html`, reconstruidas desde el modular; **no editar manualmente**.

Las vistas deben conservar la misma semántica: un DTO no es una entidad JPA, una propiedad de entidad puede no aparecer en ningún JSON y la existencia del YAML no implica una ruta disponible. La revisión de seguridad agrega un documento funcional específico, [auth-design.md](../auth-design.md), y revisa ADR-0005/0007/0009/0011.

## 2. Acuerdos ya reflejados en el diseño base

| Tema                     | Regla del dominio                                                                    | Representación en API                                                                                           |
| ------------------------ | ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------- |
| Identificadores          | UUIDv7 para entidades con PK, salvo claves naturales/candidatas compuestas           | `string` con `format: uuid` para identificadores; rol por código estable. Versión de UUID no la impone OpenAPI. |
| Roles globales           | `CITIZEN`, `OPERATOR`, `ADMIN` a través de `UserRole`; toda cuenta incluye `CITIZEN` | `UserDTO.roles` con `GlobalRoleCode`. El cliente no selecciona permisos en el registro.                         |
| Roles departamentales    | `DEPARTMENT_COORDINATOR`, `TECHNICIAN` vía `DepartmentMembership`                    | `UserDTO.departmentMemberships` visible según autorización.                                                     |
| Operador neutral         | Valida y deriva incidencias sin pertenecer a departamento                            | Operaciones de revisión/derivación con `x-roles: OPERATOR`; seguridad real en servidor.                         |
| Coordinador              | Distribuye trabajo dentro de departamentos autorizados                               | `POST /incidents/{incidentId}/assignments` con adscripción activa.                                              |
| Departamento responsable | `Incident.assignedDepartment` opcional                                               | `IncidentDTO.assignedDepartment` y `PUT /incidents/{incidentId}/department`. No crea `Assignment`.              |
| Trabajo                  | Asignación temporal a equipo **o** técnico (XOR)                                     | `AssignmentRequest` con exactamente uno de `teamId` o `technicianId`; historial de reasignaciones.              |
| Aceptación               | `acceptedBy`/`acceptedAt` sin comienzo automático del trabajo                        | Operación de aceptación y campos en `AssignmentDTO`.                                                            |
| Estados                  | `VALIDATED` puede existir sin departamento, dos etapas de asignación                 | `ASSIGNED_TO_DEPARTMENT` y `WORK_ASSIGNED`.                                                                     |
| Invitaciones             | Canje para cuenta existente o nueva; rol/departamento autorizado                     | `InvitationRequest` con `oneOf`; nuevo `StaffGlobalRoleCode` restringido a OPERATOR/ADMIN, nunca CITIZEN.       |
| Duplicados               | Sugerencias calculadas, vínculos humanos persistidos                                 | `DuplicateSuggestionDTO` y `DuplicateLinkDTO`.                                                                  |
| Ubicación                | Coordenadas `Location`/`Coordinates` embebidas                                       | DTO de localización, proveedores no elegidos.                                                                   |

### Revisión de identidad/autenticación (25/10/2026)

| Tema                               | Regla de diseño acordada                                                                                     | OpenAPI `0.3.0`                                                                               |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------- |
| Registro                           | User activo, `emailVerified=false`, `tokenVersion=0`, preferencias `true`, rol CITIZEN; se inicia sesión     | `POST /auth/register`, `201 TokenDTO`; no se elige rol ni se valida email por correo todavía. |
| Login                              | BCrypt, cuenta activa, JWT firmado de 7200 s                                                                 | `POST /auth/login`, `200 TokenDTO`, `401` genérico si falla; sin `404`/`409` normales.        |
| Revocación tras cambiar contraseña | Incremento transaccional de `User.tokenVersion`                                                              | `POST /users/me/password-changes`, tag Autenticación, `204` sin cuerpo.                       |
| Autenticación protegida            | Token `sub`, `iat`, `exp`, `iss`, `ver` y comprobación de usuario/roles actuales en PostgreSQL cada petición | Esquema Bearer JWT; `x-roles` no es garantía de autorización ejecutable.                      |
| Protección frente a abuso          | Limitador local por instancia con reposición y memoria acotada                                               | Respuesta `429` y cabecera `Retry-After` cuando corresponda.                                  |
| Validaciones                       | `400` JSON ilegible, `422` datos inválidos, `409` email duplicado                                            | `ProblemDetail` uniforme en MVC, filtros y seguridad.                                         |
| Identificadores JPA                | UUIDv7 desde Hibernate `UuidGenerator.Style.VERSION_7`                                                       | `format: uuid`, prueba de persistencia pendiente.                                             |
| Mejoras aplazadas                  | Email confirmado obligatoriamente, recuperación por correo, refresh y `fields`                               | Sin operaciones nuevas ni `fields` para estas capacidades futuras.                            |

## 3. Diferencia esencial: derivación frente a trabajo

Recorrido representativo, no matriz completa:

`REPORTED → VALIDATED → ASSIGNED_TO_DEPARTMENT → WORK_ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED`.

- **Revisión:** operador valida y puede conservar la incidencia en VALIDATED sin departamento.
- **Derivación/transferencia:** operador elige departamento, modifica `Incident.assignedDepartment` y audita, aunque no cambie el estado.
- **Distribución:** coordinador asigna equipo estable o técnico individual mediante `Assignment` y puede transitar a WORK_ASSIGNED.
- **Reasignación:** finaliza la asignación anterior y crea otra, sin forzar `StatusChange` si el estado se mantiene.
- **Aceptación:** registra `acceptedBy`/`acceptedAt` sin forzar IN_PROGRESS.

La autorización en estos casos no se decide únicamente por `x-roles`: depende de `DepartmentMembership`, `TeamMembership`, asignación vigente, titularidad y estado del recurso. El núcleo auth prepara la resolución de autoridades actuales, no implementa las tablas departamentales.

## 4. Qué no está cerrado o queda fuera del primer tag

La revisión del contrato **no** implica aprobar todas las reglas de transiciones, transferencias, reasignaciones, alta/revocación de permisos departamentales, CRUD de equipos, política de invitaciones, proveedor de mapas, fotografías, almacenamiento analítico ni resultados de fuentes externas.

Quedan expresamente como mejoras de autenticación **futuras**: refresh tokens, sistema de correo con confirmación obligatoria de email antes de avanzar, recuperación de contraseña olvidada por email, y primer administrador manual como tarea posterior. La propuesta `GET fields` con permisos por atributo (`OWNER`, `ADMIN`, `NONE`, etc.) no está aprobada; requiere investigar relación de titularidad, proyecciones SQL y exposición segura. No se implementan tablas de permisos dinámicos por anticipado.

Los componentes opcionales marcados en rojo en el enunciado (entrenamiento de modelos, RAG, hotspots predictivos), así como evaluaciones IA con motor y ejecución asíncrona no decididos, no pertenecen a las rutas activas. La asistencia determinista no exige entrenar IA.

No inventar atributos, límites, endpoints ni permisos para cerrar huecos sin revisión del tag correspondiente.

### Precisión sobre DTO y auditoría

- `UrbanAssetDTO.id` representa UUID interno; `externalId` identifica al proveedor; `sourceId` es opcional y referencia `ExternalSource`. Nunca equiparar IDs externos a UUID.
- Los recursos persistentes del contrato usan `format: uuid` según corresponda; la política de generación se define en JPA y ADR-0011.
- `AuditEvent.resourceId` y `requestId` son cadenas heterogéneas, no FKs UUID obligatorias.
- `StatusChangeDTO.fromStatus` y `changedBy` pueden faltar al crear incidencia o actuar el sistema; esto no elimina el deber de auditar.
- El `oneOf` de invitaciones y enums por ámbito permiten validar rol/departamento; las invitaciones para personal global no deben aceptar CITIZEN.
- `UserDTO.emailVerified` identifica estado de correo pero **no significa que exista todavía un proceso de verificación**. No mostrar `passwordHash` ni `tokenVersion` en las respuestas API. `departmentMemberships` queda sujeto a visibilidad autorizada.

## 5. Flujo de tareas: una unidad dominio–API

1. Escoger tag/caso de uso y consultar requisitos y ADR.
2. Estudiar **juntos** paths/DTO y ambas vistas del dominio; la versión base anterior no era una validación exhaustiva.
3. Revisar campos, nulabilidad, invariantes, ámbitos de autorización, errores y alcance real del caso de uso.
4. Documentar cambios de contrato, diagramas editables, exportaciones, ADR y dominio en el mismo PR y coordinar impacto sobre otros tags.
5. Implementar solo entidades/repositorios, Flyway, servicios y endpoints necesarios, con tests de contrato, seguridad y persistencia aislada. No crear todas las tablas dibujadas.
6. Validar el YAML modular, regenerar `openapi.yaml` y `openapi.html`, revisar diff, y detallar qué queda pendiente y qué se ejecutó realmente.

## 6. Comandos de validación/generación

```bash
npx @redocly/cli lint docs/api/openapi/base-openapi.yaml
npx @redocly/cli bundle docs/api/openapi/base-openapi.yaml --output docs/api/openapi.yaml
npx @redocly/cli build-docs docs/api/openapi.yaml --output docs/api/openapi.html
```

Son comandos pendientes de ejecución para esta revisión documental. Los ficheros derivados no se mantienen manualmente: tras regenerar, verificar que `info.version` es `0.3.0` y que no hay `$ref` rotos.

## 7. Referencias

- [Diseño de autenticación](../auth-design.md).
- [Modelo de dominio](../domain-model.md) y [diagramas](../diagrams/README.md).
- [ADR-0005](../adr/0005-jwt-spring-security.md), [ADR-0007](../adr/0007-invitaciones-personal.md), [ADR-0009](../adr/0009-rbac-departamentos-equipos.md), [ADR-0010](../adr/0010-ciclo-vida-y-asignaciones.md), [ADR-0011](../adr/0011-identificadores-uuidv7.md).
- [Guía de API](README.md) y [Contribución](../../CONTRIBUTING.md).
