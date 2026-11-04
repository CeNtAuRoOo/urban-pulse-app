# UrbanPulse — contrato de dominio y API unificados (dominio v1 / API 0.5.0)

> **Estado actual:** Autenticación, Usuarios, Invitaciones, Departamentos y Categorías están implementados en Java/JPA/Flyway; los demás tags son modelos de diseño revisables. Las migraciones previas se validaron en PostgreSQL efímero, **no** consta su aplicación sobre Neon `development`. La versión OpenAPI **vigente es 0.5.0**. Las fuentes gráficas y sus SVG regenerados con draw.io Desktop incluyen los módulos implementados; véase la [comprobación estructural](../diagrams/README.md#criterios-de-sincronización).

## 1. Referencia coherente y varias vistas

El proyecto aplica un flujo _design-first_ con fuentes complementarias:

- **Requisitos del caso de estudio y ADR**: determinan el alcance, prioridades, roles, invariantes y decisiones arquitectónicas; las funcionalidades opcionales siguen aplazadas.
- **Modelo relacional**: [`../diagrams/relacional.drawio`](../diagrams/relacional.drawio) (cuatro páginas: incidencias, tramitación/auditoría, identidad/organización, territorio y contexto). Es una combinación de **esquema V1 consolidada ya implementado** y diseño aún no persistido.
- **Clases simplificadas**: [`../diagrams/clases-simplificado.drawio`](../diagrams/clases-simplificado.drawio) (una página), con atributos Java/DTO/enums/embebidos; **sin líneas UML intencionadamente**, ya que las relaciones están en el ER.
- **Contrato OpenAPI fuente**: [`openapi/base-openapi.yaml`](openapi/base-openapi.yaml), rutas en `openapi/paths/` y esquemas en `openapi/components/`.
- **Salidas derivadas**: [`openapi.yaml`](openapi.yaml) y [`openapi.html`](openapi.html), regeneradas con Redocly y **nunca editadas a mano**.

DTO, entidad de dominio, entidad JPA y tabla SQL **no son equivalentes**. `x-roles` orienta al lector del contrato; el backend comprueba autorización efectiva, especialmente pertenencias por departamento, titularidad y estado cuando se implementen las operaciones. Un endpoint descrito en YAML tampoco es necesariamente un endpoint ejecutable.

## 2. Acuerdos vigentes del dominio y su traducción a la API

| Tema                      | Regla de negocio/almacenamiento                                                                  | Contrato HTTP y estado                                                                                                             |
| ------------------------- | ------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| Identificadores           | UUIDv7 desde Hibernate donde corresponda; algunas claves compuestas/naturales y externas         | `string` + `format: uuid` para IDs internos, sin imponer versión UUID en OpenAPI                                                   |
| Identidad                 | `User` único por email normalizado, `CITIZEN` siempre presente, preferencias y correo verificado | Registro público sin selección de roles; `UserDTO` no expone hash ni `tokenVersion`                                                |
| Roles globales            | `CITIZEN`, `OPERATOR`, `ADMIN` mediante `UserRole`, y se consultan vigentes en PostgreSQL        | JWT Bearer, `UserDTO.roles`; no hay permisos permanentes confiados a claims                                                        |
| Roles departamentales     | `TECHNICIAN` y `DEPARTMENT_COORDINATOR` mediante pertenencias con ámbito e historial             | Pertenencias activas e históricas en `UserDTO`; el canje devuelve DTO propio                                                       |
| Operador                  | Puede actuar transversalmente al revisar/derivar, sin pertenecer a departamento                  | Operaciones de incidencias futuras con `x-roles: OPERATOR` como metadato                                                           |
| Catálogo de departamentos | Nombre/código únicos, estado y fechas; entidad compartida con pertenencias/invitaciones          | GET colección/detalle, POST y PATCH implementados; [contrato de Departamentos](README.md#51-departamentos-implementados-catálogos) |
| Catálogo de categorías    | UUIDv7, nombre único de 2–100 puntos Unicode, descripción opcional y active; V1 sin semillas     | GET colección/detalle, POST y PATCH implementados; [contrato](README.md#contrato-de-categorías)                                    |
| Departamento responsable  | `Incident.assignedDepartment` opcional, diferente de asignar trabajo                             | `IncidentDTO.assignedDepartment`, futuro `PUT /incidents/{incidentId}/department`                                                  |
| Trabajo                   | `Assignment` temporal a **equipo XOR técnico**, con como máximo una activa                       | Futura petición `AssignmentRequest` con `teamId` o `technicianId`, no ambos                                                        |
| Aceptación                | `acceptedBy`/`acceptedAt` no inicia automáticamente el trabajo                                   | Respuesta futura `AssignmentDTO`                                                                                                   |
| Ciclo de vida             | `VALIDATED` puede existir sin departamento; derivación y trabajo son fases distintas             | `ASSIGNED_TO_DEPARTMENT` y `WORK_ASSIGNED`, no un único `ASSIGNED`                                                                 |
| Invitaciones              | Temporal, correo obligatorio, rol/ámbito fijados y canje sobre cuenta verificada                 | `InvitationRequest` `oneOf`; cinco rutas **implementadas**; contrato `0.5.0`                                                       |
| Duplicados                | Sugerencias calculadas; vínculo persistido solo por acción humana                                | DTO de sugerencias y enlaces **de diseño**                                                                                         |
| Contexto urbano           | Localización y fuentes con IDs externos separados de los internos                                | DTO de coordenadas y contexto, sin proveedores elegidos                                                                            |

### 2.1. Revisión histórica de Autenticación (0.3.0 y 0.4.0)

El contrato de autenticación evolucionó desde diseño de registro JWT (`0.3.0`) hasta confirmación/reenvío SMTP (`0.4.0`), ya implementados. Actualmente los cinco métodos HTTP bajo `/api/v1` son `POST /auth/register` (`201 TokenDTO` sin `Location`), `POST /auth/login` (`200 TokenDTO`), `POST /users/me/password-changes` (`204`), `POST /auth/email-verifications/confirm` (`204`) y `POST /auth/email-verifications/resend` (`202`/`204`/`410`).

- `User` inicia activo, `emailVerified=false`, `tokenVersion=0`, con preferencias `true` y rol global `CITIZEN`. Solo un flujo autenticado y controlado puede conceder privilegios adicionales.
- JWT HS256 por **7200 s** con `sub/iat/exp/iss/ver`, comprobación de cuenta, versión, roles globales vigentes y `EMAIL_VERIFIED` desde PostgreSQL por petición. El mismo JWT funciona tras verificar correo y puede reconocer un rol global recién adquirido por invitación.
- Cambio de contraseña: bloqueo y aumento de `tokenVersion` dentro de la transacción para invalidar tokens anteriores. BCrypt coste 12 para contraseñas.
- Verificación: HMAC propio del código de ocho dígitos, diez minutos como máximo, cuenta pendiente eliminable tras 24 h solo si carece de referencias históricas y correo `AFTER_COMMIT`; confirmación/reenvío y limpieza ya implementados. SMTP Mailpit local **no** garantiza entrega externa.
- Errores: `400` JSON ilegible/tipos/cuerpo excesivo, `401` credenciales/JWT/cuenta inválidos, `403 EMAIL_NOT_VERIFIED`, `409` duplicidad de correo, `422` validación, `429 Retry-After`, `503` fallo tipado PostgreSQL y `500` genérico. Límite de 16 KiB en los cinco POST de auth; cuotas en memoria por instancia.
- Recuperación de contraseña olvidada, refresh, primer ADMIN productivo y `GET fields` no están implementados.

### 2.2. Invitaciones implementadas (0.5.0)

El diseño descarta `/auth/staff-registrations`, `StaffRegistrationRequest` e `InvitationCreatedDTO`; **emitir una invitación no registra un usuario**. Se implementan cinco operaciones bajo `/api/v1`:

| Método y recurso                                     | Autorización              | Respuesta                       |
| ---------------------------------------------------- | ------------------------- | ------------------------------- |
| `POST /staff-invitations`                            | ADMIN activo/verificado   | `201 InvitationDTO`, `Location` |
| `GET /staff-invitations`                             | ADMIN activo/verificado   | `200 InvitationPageDTO`         |
| `GET /staff-invitations/{invitationId}`              | ADMIN activo/verificado   | `200 InvitationDTO`             |
| `POST /staff-invitations/{invitationId}/revocations` | ADMIN activo/verificado   | `204`                           |
| `POST /users/me/staff-invitation-redemptions`        | CITIZEN activo/verificado | `200 InvitationRedemptionDTO`   |

La variante `GlobalInvitationRequest` acepta `OPERATOR`/`ADMIN` y **no** acepta `departmentId` ni siquiera como `null`; `DepartmentInvitationRequest` acepta `TECHNICIAN`/`DEPARTMENT_COORDINATOR` con `departmentId` UUID activo. Email obligatorio normalizado, plazo siete días por defecto (1–30 configurable), código de **256 bits/43 caracteres Base64URL** protegido por HMAC-SHA256 de propósito propio. Ni administración ni auditoría reciben el código. La invitación puede preceder al registro del destinatario.

El canje verifica JWT, cuenta `CITIZEN`, email verificado coincidente, invitación activa y ausencia de permiso duplicado. Añade `UserRole` o `DepartmentMembership` en una transacción junto a estado/auditoría, sin cambiar usuario ni emitir JWT nuevo; protege con bloqueos **usuario → invitación → departamento**. Los estados `ACTIVE/REDEEMED/REVOKED/EXPIRED` se derivan de instantes, no de columna `status`. Una invitación revocada o canjeada no se reactiva. El SMTP ocurre **después del commit**, sin outbox ni reintentos.

Listado filtrado por estado/email/rol/departamento con paginación `page=1`, `pageSize=20` (máximo 100), `PageMetadata`, orden `createdAt DESC,id DESC`. Cuotas de canje 10/900 s por usuario y 30/900 s por IP; 16 KiB máximo de cuerpo en POST. `422 INVALID_INVITATION_CODE` genérico para códigos invalidables/ajenos, `422 INVALID_DEPARTMENT`, `409 PERMISSION_ALREADY_GRANTED` o `409 INVITATION_NOT_ACTIVE` según operación. [Decisiones](../adr/0007-invitaciones-personal.md) y [pruebas](../development.md).

## 3. Derivación frente a trabajo de incidencia

Como **diseño no implementado**, una evolución representativa es:

`REPORTED → VALIDATED → ASSIGNED_TO_DEPARTMENT → WORK_ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED`

El operador puede validar sin departamento; derivar cambia `Incident.assignedDepartment` y audita sin generar `Assignment`. El coordinador con adscripción departamental activa puede asignar trabajo a equipo **o** técnico. Una reasignación termina la anterior y crea otra, sin forzar un `StatusChange` si el estado no cambia. Aceptar registra `acceptedBy/acceptedAt` y no inicia automáticamente `IN_PROGRESS`. Los permisos futuros dependen de `DepartmentMembership`, `TeamMembership`, titularidad, asignación y recurso además de `x-roles`. La matriz exhaustiva de transiciones sigue pendiente.

## 4. Atributos y alcance que permanecen abiertos

`UrbanAssetDTO.id` es UUID interno; `externalId` pertenece al proveedor y `sourceId` referencia `ExternalSource` si existe. `AuditEvent.resourceId`/`requestId` son cadenas heterogéneas, no FKs obligatorias. Algunos campos `StatusChangeDTO` pueden omitirse en cambios automáticos o iniciales, pero ello no elimina el deber de auditar. `UserDTO.emailVerified` es la representación segura del estado, sin `passwordHash` ni `tokenVersion`; las pertenencias departamentales de usuarios se decidirán en el tag correspondiente.

**Pendiente de implementar:** equipos, API de retirada de permisos, operaciones de incidencias, fotografías, notificaciones de negocio, filtros avanzados por campo/titularidad, selección de mapas, fuentes de contexto, IA entrenada, RAG y analítica predictiva. Tampoco hay proveedor SMTP real, auditoría administrativa completa RF18 ni bootstrap del primer administrador. Autenticación, Usuarios, Invitaciones y la administración de Departamentos y Categorías sí están implementadas; ambos catálogos ofrecen consulta, alta y PATCH, sin DELETE.

## 5. Proceso dominio–API por tag

1. Revisar casos de uso, requisitos, prioridades y ADR acordados.
2. Contrastar juntos paths/DTO de OpenAPI, ER y catálogo de clases (tipos, cardinalidad, PK/FK, nulabilidad, estados, errores y permisos).
3. Acordar qué parte queda implementada y qué parte sigue como propuesta; evitar crear tablas/endpoints solo por estar dibujados.
4. Aplicar cambios en el mismo PR a fuentes OpenAPI, modelos `.drawio`, exportaciones SVG, texto/ADR y tests. Los diagramas de V1 consolidada ya están actualizados: actualizar solo cuando **cambie** de nuevo el dominio.
5. Implementar JPA/Flyway/servicios/HTTP de forma incremental, con tests de seguridad e integración aislados.
6. Regenerar bundle y HTML **desde el YAML modular**, revisar diff y registrar resultados efectivamente ejecutados.

## 6. Validación del contrato

Desde la raíz:

```bash
npx --yes @redocly/cli@2.16.0 lint docs/api/openapi/base-openapi.yaml
npx --yes @redocly/cli@2.16.0 bundle docs/api/openapi/base-openapi.yaml -o docs/api/openapi.yaml
npx --yes @redocly/cli@2.16.0 build-docs docs/api/openapi.yaml -o docs/api/openapi.html
```

**Validaciones completadas (30/10/2026):** contrato OpenAPI `0.5.0` sin errores de lint (dos avisos conocidos: licencia por decidir y servidor localhost), **90 pruebas de backend superadas** y un recorrido HTTP aislado con PostgreSQL/Mailpit satisfactorio. El historial Git sitúa la reorganización de paquetes antes de los commits de Invitaciones y pruebas. Resultados, alcance y comandos: [Guía de desarrollo](../development.md).

## 7. Referencias

- [Guía de API](README.md), [autenticación](../auth-design.md), [modelo de dominio](../domain-model.md) y [diagramas actualizados](../diagrams/README.md).
- [ADR-0005](../adr/0005-jwt-spring-security.md), [ADR-0007](../adr/0007-invitaciones-personal.md), [ADR-0009](../adr/0009-rbac-departamentos-equipos.md), [ADR-0010](../adr/0010-ciclo-vida-y-asignaciones.md) y [ADR-0011](../adr/0011-identificadores-uuidv7.md).
- [Desarrollo: pruebas](../development.md) y [CONTRIBUTING](../../CONTRIBUTING.md).

## Usuarios: contrato implementado

Las cinco operaciones cubren perfil propio y administración global con correo verificado. Los DTO admiten solo `displayName` o `active` según la ruta; la normalización de nombre se valida en puntos Unicode. `LAST_ACTIVE_ADMIN` protege la disponibilidad administrativa con estado 409. El listado pagina en SQL y carga roles y pertenencias en lote; el filtro departamental excluye historia, aunque el DTO la incluye. La baja y reentrada departamental son servicios internos sin nuevas rutas. Véanse [Usuarios](README.md#usuarios-implementados), [ADR-0012](../adr/0012-gestion-usuarios-perfiles-y-administracion.md) y [ADR-0013](../adr/0013-pertenencias-usuarios-departamentos.md).
