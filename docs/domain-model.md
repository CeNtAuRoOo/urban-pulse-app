# Modelo de dominio UrbanPulse: diseño v1 y módulos implementados

> **Estado a 05/11/2026:** los módulos anteriores conservan V1; Incidencias añade V2 con núcleo operativo y tres estructuras de tramitación sin operaciones; V3 refuerza sus invariantes de texto. El [roadmap](incidents-implementation-roadmap.md) conserva el modelo objetivo, sus dependencias y la trazabilidad RF01–RF26 del enunciado.

## 1. Fuentes y criterio de interpretación

El dominio de UrbanPulse parte del [enunciado](../README.md), los [ADR](adr/README.md), el [diseño OpenAPI modular](api/README.md), las entidades JPA realmente presentes y sus migraciones SQL. El contrato actual de la API es `0.6.0`; `0.2.0` fue una versión inicial de diseño, no la versión vigente. Los modelos se mantienen de forma incremental, con un alcance deliberadamente reducido en cada tag.

Una **entidad de dominio** expresa identidad y reglas de negocio; una **entidad JPA** representa persistencia efectivamente implementada y un **DTO** representa datos intercambiables de la API. No se deben confundir. Una clase puede figurar en el diagrama sin código Java ni tabla; el YAML puede definir rutas de diseño que todavía no responden en la aplicación.

## 2. Los diagramas y sus funciones

- [Modelo relacional, cuatro páginas](diagrams/relacional.drawio): **ER01** Incidencias, **ER02** tramitación y auditoría, **ER03** identidad, invitaciones, departamentos y organización, **ER04** territorio/contexto. Las exportaciones [`relacional-01.svg`](diagrams/relacional-01.svg) a [`relacional-04.svg`](diagrams/relacional-04.svg) se encuentran en el mismo directorio.
- [Catálogo de clases simplificadas](diagrams/clases-simplificado.drawio): una página de clases, enums, embebidos y DTO, **sin líneas de relación UML por decisión de diseño**. Relaciones y claves se estudian en el ER; [SVG exportado](diagrams/clases-simplificado.svg).
- [C4 contexto y contenedores](diagrams/README.md): arquitectura exterior y almacenamiento. Reflejan correo SMTP/Mailpit y solo fotografías en el alcance inicial.

Las fuentes editables distinguen V1, el núcleo V2 y las entidades conceptuales. El [inventario](diagrams/README.md) identifica las exportaciones históricas y la validación de fuentes. Siguen siendo futuros los diagramas de despliegue y de la matriz completa de tramitación.

## 3. Conceptos principales y estado

### 3.1. Incidencias y ubicación — núcleo implementado

`Incident` es el reporte central. V2 implementa informante, texto, coordenadas, categoría, estado REPORTED, fechas y versión; prioridad y departamento quedan opcionales para tramitación. El objetivo completo conserva: informante, título y descripción, categoría opcional, coordenadas, dirección/barrio/distrito cuando se conozcan, estado, prioridad, departamento responsable e historial. Los datos geográficos/contextuales enriquecen el reporte sin impedir su creación si un proveedor falla. `Attachment` representa metadatos de fotografías, cuyo binario se almacenará fuera de PostgreSQL en el bucket S3 privado `incident-evidence`; todavía no se implementa el flujo de autorización/subida/descarga.

Las sugerencias de duplicados se calculan y no deben fusionar ni rechazar automáticamente incidencias. `DuplicateLink` solo se persistiría tras confirmación humana. Las prioridades propuestas son `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`, con una `PriorityDecision` justificable y auditable. Los identificadores del proveedor externo de un activo urbano (`externalId`) no equivalen a las claves UUID internas.

#### Catálogo Category

Su finalidad es clasificar incidencias y contiene únicamente `id` UUIDv7 de Hibernate, `name`, `description` nullable y `active`. V1 crea `categories` sin semillas; V2 añade la FK opcional desde Incident. No tiene `code`, `createdAt` ni `updatedAt`: las fechas no son necesarias para las operaciones contratadas ni sustituyen la [auditoría pendiente RF18](architecture.md#4-persistencia-y-transacciones).

El nombre se normaliza con `trim()`, conserva espacios interiores y admite 2–100 puntos de código Unicode; no puede estar en blanco. La descripción, sin recortar, admite hasta 500. `CodePointLength` cuenta puntos de código, en lugar de unidades UTF-16, para coincidir con PostgreSQL y OpenAPI. V1 usa `name VARCHAR(100) NOT NULL`, `description VARCHAR(500)` nullable y `active BOOLEAN NOT NULL DEFAULT true`; sus restricciones también exigen nombre recortado y no blanco en inserciones SQL externas. El índice único `lower(name)` incluye filas inactivas. Las comprobaciones preventivas del repositorio usan el mismo `lower`, evitando diferencias de conversión de mayúsculas. No se normalizan acentos ni formas Unicode equivalentes.

La creación admite descripción ausente y activa la categoría por defecto. Desactivación y reactivación conservan UUID y nombre reservado; no hay borrado. Mantener el propio nombre o cambiar solo sus mayúsculas no produce conflicto consigo misma. El [contrato HTTP](api/README.md#contrato-de-categorías) define omisión/null, actualización parcial y orden del listado; la [arquitectura](architecture.md#4-persistencia-y-transacciones) describe las garantías de concurrencia.

La relación implementada es **Category 0..N Incident; cada Incident tiene 0..1 Category**. Una categoría recibida debe existir y estar activa; su desactivación posterior conserva las referencias históricas. Los [diagramas ER01 y de clases](diagrams/README.md) recogen nulabilidad y restricciones de Category; la integración con incidencias utiliza la entidad y el catálogo existentes.

### 3.2. Identidad, roles y organización — parcialmente implementado

| Concepto                       | Regla o responsabilidad                                                                                                                   | Estado                                                         |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `User`                         | Una cuenta por email normalizado; rol base `CITIZEN`, `emailVerified=false` al registrarse, `tokenVersion` y preferencias de notificación | JPA y V1 implementados                                         |
| `Role`, `RoleCode`, `UserRole` | `CITIZEN`, `OPERATOR` y `ADMIN` globales; código de rol persistido y asociación compuesta, sin `Role.scope`                               | JPA y V1 implementados                                         |
| `EmailVerification`            | Código de ocho dígitos protegido por HMAC, caducidad, intentos y limpieza de cuentas pendientes                                           | JPA y V1 implementados                                         |
| `StaffInvitation`              | Autorización temporal ligada a email para obtener un rol; no concede permisos antes del canje                                             | JPA y V1 implementados                                         |
| `Department`                   | Nombre y código únicos, descripción, estado y fechas                                                                                      | JPA, V1 y administración implementados; sin DELETE             |
| `DepartmentMembership`         | Permiso departamental `TECHNICIAN` o `DEPARTMENT_COORDINATOR`, con inicio, fin opcional e historial                                       | JPA, V1 y ciclo interno de alta/baja; sin endpoint de retirada |
| `Team`/`TeamMembership`        | Equipos estables e historial de pertenencias/adscripciones                                                                                | Solo diseño, sin tabla                                         |
| `AuditEvent`                   | Evento administrativo con actor, recurso, instante, detalles y correlación opcional                                                       | JPA y V1, sin endpoint público                                 |

**Ámbitos y autorización:** `OPERATOR` actúa transversalmente en operaciones futuras de tramitación; `ADMIN` es global. Técnicos y coordinadores solo dispondrán de permisos en departamentos con pertenencia activa. Un usuario puede conservar `CITIZEN` y sumar permisos. Los roles globales vigentes se leen desde PostgreSQL en cada petición con JWT, que por sí solo **no** incorpora los permisos como fuente de verdad.

### 3.3. Tramitación — estructuras base; operaciones futuras

El ciclo de vida propuesto es `REPORTED → VALIDATED → ASSIGNED_TO_DEPARTMENT → WORK_ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED`, con rechazo justificado y reapertura directa desde RESOLVED a ASSIGNED_TO_DEPARTMENT o WORK_ASSIGNED con trabajo válido. CLOSED es terminal. La **matriz exhaustiva** de transiciones, actores, precondiciones y motivos sigue por diseñar/validar. Se puede pasar a `VALIDATED` sin tener departamento. Derivar a un departamento modifica `Incident.assignedDepartment` y puede producir auditoría sin generar una `Assignment`.

Una `Assignment` representa **trabajo** concedido temporalmente a un equipo **o** técnico (exclusión XOR), con aceptación `acceptedBy`/`acceptedAt`, histórico y como máximo una asignación vigente. Reasignar cierra el vínculo activo anterior y crea otro; aceptar no implica comenzar automáticamente en `IN_PROGRESS`. `StatusChange` registra transiciones; `AuditEvent` registra decisiones relevantes también sin cambio de estado. Las operaciones de Tramitación no están implementadas. StatusChange, IncidentReview y PriorityDecision tienen tabla/JPA/repositorio; alta y corrección solo crean AuditEvent INCIDENT_CREATED/INCIDENT_UPDATED. El historial público es una proyección, no una tabla.

### 3.4. Territorio y contexto urbano — diseño provisional

`UrbanAsset`, `UrbanAssetAssociation`, `ExternalSource`, `ExternalObservation` y `UrbanContextViewDTO` representan propuestas para vincular activos, fuentes y datos contextuales con incidencias o zonas. `UrbanContextViewDTO` es una **vista**, no una tabla. Proveedores, geometrías y posibles componentes PostGIS/analíticos no están elegidos ni implementados. Los ámbitos IA, clasificación, predicción, hotspots y RAG son mejoras futuras y no se deben documentar como ejecución actual.

## 4. Modelo implementado de Invitaciones (Flyway V1)

`StaffInvitation` tiene UUIDv7, `codeHash` como `byte[]` de **32 bytes** con unicidad SQL, `email` normalizado obligatorio (máximo 254; **no** FK a destinatario), rol obligatorio, departamento obligatorio para roles departamentales y ausente para globales, emisor `createdBy`, actores opcionales de canje/revocación y sus instantes emparejados, `createdAt`/`expiresAt` obligatorios. No hay columna persistida `status`: `ACTIVE`, `REDEEMED`, `REVOKED` y `EXPIRED` se derivan de las fechas (`statusAt`). Las restricciones comprueban ámbito, exclusión entre revocación/canje, pares fecha/actor, fechas válidas y `expiresAt` exclusivo.

`Department` posee UUIDv7, nombre de 2–120, código ASCII de 2–20 normalizado a mayúsculas, descripción opcional hasta 500, estado y fechas internas `createdAt`/`updatedAt`. Nombre y código son únicos sin distinguir mayúsculas. PostgreSQL 18 proporciona `uuidv7()` como default para inserciones SQL; JPA genera su UUID y mantiene las fechas. Se revalida que esté activo al emitir o canjear una invitación departamental. No hay departamentos precargados; se administran mediante GET/POST/PATCH, sin DELETE. [contrato de Departamentos](api/README.md#51-departamentos-implementados-catálogos). `DepartmentMembership` contiene UUIDv7, usuario, departamento, rol departamental, `joinedAt`, `leftAt` opcional, `CHECK` temporal y un índice único parcial sobre `(user_id, department_id, role_code) WHERE left_at IS NULL`; conserva la historia con baja y reingreso internos; sin API de retirada.

`AuditEvent` contiene UUIDv7, `actor` opcional, `action`/`resourceType` hasta 80, `resourceId` hasta 128, `summary` opcional hasta 500, `details` JSONB obligatorio, `requestId` opcional hasta 128 y `occurredAt`. Los eventos de Invitaciones guardan solo rol, ámbito y `departmentId` si corresponde, nunca código, HMAC, correo del invitado ni JWT. El recurso histórico es una referencia lógica, no una FK polimórfica.

Las FK históricas usan `ON DELETE RESTRICT`; la limpieza de cuentas **no verificadas** conserva cualquier usuario con referencias históricas, sin bloquear el barrido de cuentas eliminables. `StaffInvitation.email` sin FK permite que una cuenta pendiente se elimine tras 24 horas y que su futuro titular vuelva a registrarse y canjear, si el código sigue vigente. Emisión no crea `User`; canje, concesión y auditoría comparten una única transacción con bloqueos **usuario → invitación → departamento**, protección ante duplicados y conservación de `CITIZEN`. [ADR-0007: reglas de concesión](adr/0007-invitaciones-personal.md) y [Desarrollo: pruebas](development.md).

## 5. Identificadores, tipos y invariantes

Category pertenece al módulo `categories`; Department y DepartmentMembership pertenecen a `organization`. Son catálogos distintos y solo Department tiene código y fechas internas.

La política general es **UUIDv7** generados en Hibernate para identidades de entidad persistida, con excepciones deliberadas de claves compuestas/naturales (`UserRoleId`, `RoleCode`) y claves externas de proveedores que pueden ser cadenas. Las relaciones se expresan con FK y nulabilidad reales en V1/V2; las representaciones OpenAPI usan `format: uuid` cuando corresponde, sin garantizar la versión específica de UUID por el esquema. Las fechas UTC representan instantes. Los DTO omiten información sensible y no son copias de entidades.

Los cinco códigos de rol son `CITIZEN`, `OPERATOR`, `ADMIN`, `TECHNICIAN` y `DEPARTMENT_COORDINATOR`. `RoleCode` determina ámbito global/departamental; no existe `RoleScope` persistido. Los permisos departamentales se consultarán siempre en el contexto del recurso. Un estado dibujado de incidencia **no** es una transición programada ni autoriza acciones por sí mismo.

## 6. Revisión obligatoria del dominio con cada tag OpenAPI

1. Identificar casos de uso, requisitos, operaciones, roles, resultados y límites de alcance.
2. Revisar simultáneamente `docs/api/openapi/paths/<tag>.yaml`, los esquemas de `components/`, ER, catálogo de clases y ADR asociados.
3. Resolver discrepancias de PK/FK, nulabilidad, asociaciones, valores permitidos, estados, reglas de autorización y DTO **antes de implementar**.
4. Actualizar fuentes `.drawio`, exportaciones SVG, textos, ADR, OpenAPI modular y pruebas en el mismo cambio cuando evolucionen las entidades compartidas. No editar los archivos de salida `api/openapi.yaml` y `api/openapi.html` directamente; regenerarlos con Redocly.
5. Implementar **solo** las entidades/servicios/endpoints necesarios en el tag elegido, con migraciones nuevas y tests apropiados. Una ruta descrita en OpenAPI no demuestra que exista en Java.

## 7. Estado de los tags y próximas decisiones

**Autenticación**, **Usuarios**, **Invitaciones**, **Departamentos**, **Categorías** e **Incidencias** (OpenAPI `0.6.0`) cuentan con endpoints backend. La tramitación operativa, equipos, estadísticas, mapas, fotografías e IA continúan en diseño; el núcleo de incidencias ya tiene seis endpoints. Sus límites, campos omitidos y pendientes se detallan en el [roadmap](incidents-implementation-roadmap.md). Faltan el primer administrador de producción, API de retirada de permisos, política de retención y anonimización de históricos (RGPD), rotación no disruptiva de HMAC, mensajería confiable y autorización por titularidad/estado/ámbito cuando se desarrollen dichos tags.

- [Contrato dominio–API](api/domain-openapi.md) y [guía de la API](api/README.md).
- [Arquitectura modular real](architecture.md), [diagramas actualizados](diagrams/README.md) y [ADR](adr/README.md).
- [Autenticación](auth-design.md), [ADR-0007 (Invitaciones)](adr/0007-invitaciones-personal.md) y [contrato API](api/README.md).

## Identidad y ciclo de pertenencias

El módulo `identity` implementa perfil propio y administración de usuarios. `UserDTO` incluye roles globales y todas las pertenencias, ordenadas por alta descendente e ID ascendente. La presencia de historia no otorga permisos: el filtro por rol departamental solo utiliza pertenencias con `leftAt=null`. Las altas requieren cuenta activa/verificada y departamento activo; las bajas conservan la fila y el reingreso crea otra. El índice parcial `department_memberships_active_unique` protege las altas concurrentes.

`displayName` utiliza 2–120 puntos Unicode después de recortar espacios de borde y rechaza controles interiores. Actividad y perfil se modifican bajo bloqueo del usuario; la administración protege al último ADMIN activo y verificado, incluida la autodesactivación. La limpieza de cuentas caducadas excluye referencias históricas antes de paginar el lote. Detalles en [ADR-0012](adr/0012-gestion-usuarios-perfiles-y-administracion.md) y [ADR-0013](adr/0013-pertenencias-usuarios-departamentos.md).

La persistencia base de revisión exige motivo en REJECT y REQUEST_INFORMATION; en VALIDATE es opcional. Cuando existe, se normaliza y valida a 3–2000 puntos Unicode. PriorityDecision exige justificación con la misma regla. StatusChange conserva textos opcionales (2000/5000), incluso blancos; exigirlos por transición y sincronizar estado/asignación es responsabilidad del futuro servicio. V3 conserva las columnas, relaciones y estados del diseño y refuerza únicamente restricciones.
