# Modelo de dominio UrbanPulse: diseño v1 y módulos implementados

> **Estado a 31/10/2026:** el dominio funcional de incidencias sigue siendo una propuesta **revisable por tag**. El backend implementa **Autenticación/Identidad (Flyway V1)** e **Invitaciones/organización mínima/auditoría (V2)**. Las fuentes `.drawio` y los SVG existentes **ya se actualizaron** para esos módulos; su presencia no significa que el resto del dominio esté implementado o desplegado.

## 1. Fuentes y criterio de interpretación

El dominio de UrbanPulse parte del [enunciado](../README.md), los [ADR](adr/README.md), el [diseño OpenAPI modular](api/README.md), las entidades JPA realmente presentes y sus migraciones SQL. El contrato actual de la API es `0.5.0`; `0.2.0` fue una versión inicial de diseño, no la versión vigente. Los modelos se mantienen de forma incremental, con un alcance deliberadamente reducido en cada tag.

Una **entidad de dominio** expresa identidad y reglas de negocio; una **entidad JPA** representa persistencia efectivamente implementada y un **DTO** representa datos intercambiables de la API. No se deben confundir. Una clase puede figurar en el diagrama sin código Java ni tabla; el YAML puede definir rutas de diseño que todavía no responden en la aplicación.

## 2. Los diagramas y sus funciones

- [Modelo relacional, cuatro páginas](diagrams/relacional.drawio): **ER01** Incidencias, **ER02** tramitación y auditoría, **ER03** identidad, invitaciones, departamentos y organización, **ER04** territorio/contexto. Las exportaciones [`relacional-01.svg`](diagrams/relacional-01.svg) a [`relacional-04.svg`](diagrams/relacional-04.svg) se encuentran en el mismo directorio.
- [Catálogo de clases simplificadas](diagrams/clases-simplificado.drawio): una página de clases, enums, embebidos y DTO, **sin líneas de relación UML por decisión de diseño**. Relaciones y claves se estudian en el ER; [SVG exportado](diagrams/clases-simplificado.svg).
- [C4 contexto y contenedores](diagrams/README.md): arquitectura exterior y almacenamiento. Reflejan correo SMTP/Mailpit y solo fotografías en el alcance inicial.

**Estado de sincronización:** las correcciones de EmailVerification, código HMAC `byte[]`, `StaffInvitation`/`revokedBy`, pertenencias activas, restricciones, `AuditEvent.details JSONB`, tipos/fechas, `createdAt`, `isDepartmental`, roles globales derivados y C4 fueron aplicadas a los `.drawio` y a sus exportaciones. No queda pendiente la actualización gráfica de estos cambios. Sí faltan diagramas **nuevos** para despliegue productivo, secuencias completas de incidencias/fotografías y matriz definitiva de transiciones.

## 3. Conceptos principales y estado

### 3.1. Incidencias y ubicación — diseño, no implementado

`Incident` será el reporte central: informante, título y descripción, categoría opcional, coordenadas, dirección/barrio/distrito cuando se conozcan, estado, prioridad, departamento responsable e historial. Los datos geográficos/contextuales enriquecen el reporte sin impedir su creación si un proveedor falla. `Attachment` representa metadatos de fotografías, cuyo binario se almacenará fuera de PostgreSQL en el bucket S3 privado `incident-evidence`; todavía no se implementa el flujo de autorización/subida/descarga.

Las sugerencias de duplicados se calculan y no deben fusionar ni rechazar automáticamente incidencias. `DuplicateLink` solo se persistiría tras confirmación humana. Las prioridades propuestas son `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`, con una `PriorityDecision` justificable y auditable. Los identificadores del proveedor externo de un activo urbano (`externalId`) no equivalen a las claves UUID internas.

### 3.2. Identidad, roles y organización — parcialmente implementado

| Concepto | Regla o responsabilidad | Estado |
| --- | --- | --- |
| `User` | Una cuenta por email normalizado; rol base `CITIZEN`, `emailVerified=false` al registrarse, `tokenVersion` y preferencias de notificación | JPA y V1 implementados |
| `Role`, `RoleCode`, `UserRole` | `CITIZEN`, `OPERATOR` y `ADMIN` globales; código de rol persistido y asociación compuesta, sin `Role.scope` | JPA y V1 implementados |
| `EmailVerification` | Código de ocho dígitos protegido por HMAC, caducidad, intentos y limpieza de cuentas pendientes | JPA y V1 implementados |
| `StaffInvitation` | Autorización temporal ligada a email para obtener un rol; no concede permisos antes del canje | JPA y V2 implementados |
| `Department` | Ámbito departamental activo/inactivo, nombre y descripción | JPA y V2 mínimos, sin CRUD |
| `DepartmentMembership` | Permiso departamental `TECHNICIAN` o `DEPARTMENT_COORDINATOR`, con inicio, fin opcional e historial | JPA y V2, sin endpoint de retirada |
| `Team`/`TeamMembership` | Equipos estables e historial de pertenencias/adscripciones | Solo diseño, no V2 |
| `AuditEvent` | Evento administrativo con actor, recurso, instante, detalles y correlación opcional | JPA y V2, sin endpoint público |

**Ámbitos y autorización:** `OPERATOR` actúa transversalmente en operaciones futuras de tramitación; `ADMIN` es global. Técnicos y coordinadores solo dispondrán de permisos en departamentos con pertenencia activa. Un usuario puede conservar `CITIZEN` y sumar permisos. Los roles globales vigentes se leen desde PostgreSQL en cada petición con JWT, que por sí solo **no** incorpora los permisos como fuente de verdad.

### 3.3. Tramitación — diseño, no implementado

El ciclo de vida propuesto es `REPORTED → VALIDATED → ASSIGNED_TO_DEPARTMENT → WORK_ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED`, con posibilidades justificadas de `REJECTED` y `REOPENED`. La **matriz exhaustiva** de transiciones, actores, precondiciones y motivos sigue por diseñar/validar. Se puede pasar a `VALIDATED` sin tener departamento. Derivar a un departamento modifica `Incident.assignedDepartment` y puede producir auditoría sin generar una `Assignment`.

Una `Assignment` representa **trabajo** concedido temporalmente a un equipo **o** técnico (exclusión XOR), con aceptación `acceptedBy`/`acceptedAt`, histórico y como máximo una asignación vigente. Reasignar cierra el vínculo activo anterior y crea otro; aceptar no implica comenzar automáticamente en `IN_PROGRESS`. `StatusChange` registra transiciones; `AuditEvent` registra decisiones relevantes también sin cambio de estado. Ninguna de estas operaciones de incidencias está implementada todavía.

### 3.4. Territorio y contexto urbano — diseño provisional

`UrbanAsset`, `UrbanAssetAssociation`, `ExternalSource`, `ExternalObservation` y `UrbanContextViewDTO` representan propuestas para vincular activos, fuentes y datos contextuales con incidencias o zonas. `UrbanContextViewDTO` es una **vista**, no una tabla. Proveedores, geometrías y posibles componentes PostGIS/analíticos no están elegidos ni implementados. Los ámbitos IA, clasificación, predicción, hotspots y RAG son mejoras futuras y no se deben documentar como ejecución actual.

## 4. Modelo implementado de Invitaciones (Flyway V2)

`StaffInvitation` tiene UUIDv7, `codeHash` como `byte[]` de **32 bytes** con unicidad SQL, `email` normalizado obligatorio (máximo 254; **no** FK a destinatario), rol obligatorio, departamento opcional únicamente para rol departamental, emisor `createdBy`, actores opcionales de canje/revocación y sus instantes emparejados, `createdAt`/`expiresAt` obligatorios. No hay columna persistida `status`: `ACTIVE`, `REDEEMED`, `REVOKED` y `EXPIRED` se derivan de las fechas (`statusAt`). Las restricciones comprueban ámbito, exclusión entre revocación/canje, pares fecha/actor, fechas válidas y `expiresAt` exclusivo.

`Department` posee UUIDv7, `name` de 2 a 120 caracteres, `description` opcional hasta 500 y `active`. Se revalida que esté activo al emitir o canjear una invitación departamental. **No** existen datos iniciales de catálogo productivo ni CRUD. `DepartmentMembership` contiene UUIDv7, usuario, departamento, rol departamental, `joinedAt`, `leftAt` opcional, `CHECK` temporal y un índice único parcial sobre `(user_id, department_id, role_code) WHERE left_at IS NULL`; conserva la historia sin implementar retirada.

`AuditEvent` contiene UUIDv7, `actor` opcional, `action`/`resourceType` hasta 80, `resourceId` hasta 128, `summary` opcional hasta 500, `details` JSONB obligatorio, `requestId` opcional hasta 128 y `occurredAt`. Los eventos de Invitaciones guardan solo rol, ámbito y `departmentId` si corresponde, nunca código, HMAC, correo del invitado ni JWT. El recurso histórico es una referencia lógica, no una FK polimórfica.

Las FK históricas usan `ON DELETE RESTRICT`; los actores de emisión/canje/revocación están verificados y la limpieza de cuentas **no verificadas** no les afecta. `StaffInvitation.email` sin FK permite que una cuenta pendiente se elimine tras 24 horas y que su futuro titular vuelva a registrarse y canjear, si el código sigue vigente. Emisión no crea `User`; canje, concesión y auditoría comparten una única transacción con bloqueos **usuario → invitación → departamento**, protección ante duplicados y conservación de `CITIZEN`. [ADR-0007: reglas de concesión](adr/0007-invitaciones-personal.md) y [Desarrollo: pruebas](development.md).

## 5. Identificadores, tipos y invariantes

La política general es **UUIDv7** generados en Hibernate para identidades de entidad persistida, con excepciones deliberadas de claves compuestas/naturales (`UserRoleId`, `RoleCode`) y claves externas de proveedores que pueden ser cadenas. Las relaciones se expresan con FK y nulabilidad reales en V1/V2; las representaciones OpenAPI usan `format: uuid` cuando corresponde, sin garantizar la versión específica de UUID por el esquema. Las fechas UTC representan instantes. Los DTO omiten información sensible y no son copias de entidades.

Los cinco códigos de rol son `CITIZEN`, `OPERATOR`, `ADMIN`, `TECHNICIAN` y `DEPARTMENT_COORDINATOR`. `RoleCode` determina ámbito global/departamental; no existe `RoleScope` persistido. Los permisos departamentales se consultarán siempre en el contexto del recurso. Un estado dibujado de incidencia **no** es una transición programada ni autoriza acciones por sí mismo.

## 6. Revisión obligatoria del dominio con cada tag OpenAPI

1. Identificar casos de uso, requisitos, operaciones, roles, resultados y límites de alcance.
2. Revisar simultáneamente `docs/api/openapi/paths/<tag>.yaml`, los esquemas de `components/`, ER, catálogo de clases y ADR asociados.
3. Resolver discrepancias de PK/FK, nulabilidad, asociaciones, valores permitidos, estados, reglas de autorización y DTO **antes de implementar**.
4. Actualizar fuentes `.drawio`, exportaciones SVG, textos, ADR, OpenAPI modular y pruebas en el mismo cambio cuando evolucionen las entidades compartidas. No editar los archivos de salida `api/openapi.yaml` y `api/openapi.html` directamente; regenerarlos con Redocly.
5. Implementar **solo** las entidades/servicios/endpoints necesarios en el tag elegido, con migraciones nuevas y tests apropiados. Una ruta descrita en OpenAPI no demuestra que exista en Java.

## 7. Estado de los tags y próximas decisiones

Solo **Autenticación** e **Invitaciones** (OpenAPI `0.5.0`) cuentan con endpoints backend. El dominio de incidencias, tramitación, catálogos completos, equipos, estadísticas, mapas, fotografías operativas e IA continúa en fases de diseño. Faltan el primer administrador de producción, retirada de permisos, política de retención y anonimización de históricos (RGPD), rotación no disruptiva de HMAC, mensajería confiable y autorización por titularidad/estado/ámbito cuando se desarrollen dichos tags.

- [Contrato dominio–API](api/domain-openapi.md) y [guía de la API](api/README.md).
- [Arquitectura modular real](architecture.md), [diagramas actualizados](diagrams/README.md) y [ADR](adr/README.md).
- [Autenticación](auth-design.md), [ADR-0007 (Invitaciones)](adr/0007-invitaciones-personal.md) y [contrato API](api/README.md).
