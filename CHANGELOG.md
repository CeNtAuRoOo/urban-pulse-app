# Changelog

Registro de cambios relevantes de UrbanPulse, inspirado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). La documentación diferencia el diseño, la implementación versionada, las pruebas ejecutadas y el despliegue real; **ningún cambio descrito aquí implica por sí mismo despliegue en Neon/producción**.

## [Unreleased]

- Revisión correctiva de Incidencias: fixture propio de paginación restaurado y aislado; ETag fuerte opaca del detalle completo (cambio aprobado del formato decimal), precisión temporal estable y precondiciones sobre datos relacionados.
- Límite bruto de 64 KiB en POST/PATCH de Incidencias, con 413; reutilización del cuerpo almacenado sin cambiar las cuotas de Autenticación/Invitaciones.
- V3 aditiva para invariantes de revisión/prioridad, límites Java de StatusChange y regresiones SQL/HTTP; sin reescribir V1/V2 ni habilitar Tramitación.
- Trazabilidad RF01–RF26, estado explícito de operaciones OpenAPI y corrección de contradicciones documentales. Resultados comprobados en la guía de desarrollo.

### Incidencias (05/11/2026)

- Seis operaciones backend de registro, listados, detalle, corrección e historial. Informante JWT, REPORTED inicial, permisos por titularidad/rol global y corrección solo en REPORTED.
- V2 con Incident, Location/Coordinates embebidos y estructuras StatusChange/IncidentReview/PriorityDecision sin operaciones. Auditoría atómica CREATED/UPDATED, @Version y ETag/If-Match con 428/412; no-op sin evento.
- OpenAPI 0.6.0, roadmap del modelo objetivo, ADR de ciclo/concurrencia y fuentes de diagramas distinguen datos actuales y funciones futuras.
- Limpieza de cuentas conserva las nuevas referencias históricas; los tests existentes reconocen V1/V2/V3. No se aplican migraciones a Neon.

### Añadido

- Cinco operaciones de Usuarios en `identity`: perfil propio, consulta administrativa, listado paginado y activación/desactivación. Roles globales y pertenencias activas/históricas en registro, login y consultas; DTO estrictos y sin credenciales.
- Ciclo interno de altas y bajas departamentales en `organization`, con historial, reingresos y cuenta activa/verificada. Una sola entidad, repositorio e índice parcial compartidos con Invitaciones; sin nuevas rutas de pertenencias.
- Protección transaccional del último ADMIN activo y verificado; autodesactivación permitida cuando queda otro. ADR-0012/0013 y contrato OpenAPI 0.5.0 actualizados.

### Corregido

- Nombre público normalizado antes de validar, contado en puntos Unicode y alineado con PostgreSQL; evita errores SQL tras recortar el nombre.
- Limpieza de cuentas caducadas compatible con todas las FK históricas, sin borrado de evidencia ni bloqueo del resto del lote.
- Roles de usuarios recuperados en lote sin N+1; bloqueos de perfil, actividad y pertenencias evitan actualizaciones perdidas y bajas simultáneas incoherentes.
- Esquema de Usuarios y pertenencias mantenido en la V1 única para destino vacío, sin duplicar migraciones ni precargar catálogos.
- Longitudes Unicode de nombre y descripción de Departamentos alineadas con PostgreSQL y OpenAPI, tanto en alta como en PATCH; los caracteres suplementarios cuentan como un punto de código.

### Verificado el 04/11/2026

- `clean verify -Ppostgres-it`: **228 pruebas correctas** (148 Surefire y 80 Failsafe en siete suites PostgreSQL), sin fallos, errores ni omisiones. V1 única y Hibernate validados en PostgreSQL 18.6 efímero con Java 25.0.4.
- Frontend: ESLint y TypeScript correctos. Redocly 2.16.0: contrato 0.5.0 válido, YAML/HTML regenerados, dos avisos conocidos. Tres fuentes draw.io y siete SVG validados como XML e inspeccionados visualmente.

### Pendientes actuales

Aplicación controlada de la única `V1__initial_schema.sql` consolidada a Neon `development` (todavía no acreditada), despliegue productivo, primer administrador, API de retirada de permisos, equipos, tramitación/fotografías, frontend conectado, auditoría administrativa completa RF18, SMTP fiable, políticas de retención, claves rotables, CI/CD, imágenes de producción y pruebas móviles/end-to-end. Autenticación, Usuarios, Invitaciones y la administración de Departamentos y Categorías ya están implementadas; sus pruebas aisladas no acreditan despliegue.

## 2026-11-03 — Categorías en el backend modular

- Cuatro operaciones de Categorías en `categories`, con correo verificado y ADMIN global para escritura. Nombre Unicode único incluso inactivo, PATCH estricto y desactivación/reactivación sin borrado.
- Definición de Categorías incorporada a la única V1 para bases vacías, sin semillas, código ni fechas internas. Se conservan las restricciones y bloqueos de los demás módulos.
- Pruebas MVC, de servicio y PostgreSQL: unicidad concurrente, renombrados, bloqueo de actualizaciones parciales y permisos vigentes. [Resultados](docs/development.md#verificación-del-backend).
- Contratos y documentación de catálogos mantenidos en API, dominio, arquitectura y ADR; OpenAPI 0.5.0, bundle/HTML y fuentes drawio actualizados. RF18 e Incidencias continúan pendientes.

## 2026-11-02 — Integración de Departamentos e Invitaciones

- Administración de departamentos incorporada en `organization`, con entidad/repositorio compartidos y bloqueos compatibles con canjes y pertenencias.
- Contrato completo de Department aprobado; única `V1__initial_schema.sql` para bases vacías, sin departamentos precargados.
- Se conservan las suites de ambas ramas y se cubre la interacción entre PATCH, canje e historial.
- Documentación modular y fuentes `.drawio` integradas. En esta entrega se conservaron los SVG originales; posteriormente se regeneraron manualmente desde diagrams.net, como recoge el [estado actual de los diagramas](docs/diagrams/README.md#criterios-de-sincronización). Resultados de ejecución en [Desarrollo](docs/development.md).

## 2026-10-31 — Entrega modular de Invitaciones

### 2026-10-31 — Reorganización del backend por funcionalidades

**Cambio anterior a la implementación de Invitaciones en el historial de commits**: `c393418` (`refactor(backend): reorganizar la estructura de directoros por funcionalidad en vez de por capas`). Se conserva un **único monolito Spring Boot**, ahora estructurado en los módulos `auth`, `identity`, `invitations`, `organization`, `audit` y `shared`, sustituyendo los paquetes globales `config`, `controller`, `dto`, `entity`, `repository`, `service`, `security` y `exception`. Las capas técnicas viven dentro de cada módulo cuando son necesarias.

- `auth`: controlador, DTO, servicios JWT y verificación, SMTP de verificación, filtros de cuotas y limpieza.
- `identity`: `User`, `Role`, `RoleCode`, `UserRole`, preferencias y repositorios comunes.
- `organization`: base de departamentos y pertenencias, sin CRUD de Catálogos ni equipos.
- `audit`: eventos y persistencia reutilizables.
- `shared`: configuración central de Spring Security, JWT/autoridades, `ProblemDetail`, correo SMTP común, limitadores de tasa, `PageMetadata` y S3.
- `invitations`: módulo que se incorpora **después** en la secuencia de commits; controlador, DTO, servicio transaccional, HMAC, persistencia, correo y filtro de canje.
- Pruebas Java reubicadas en los mismos módulos; `NeonConnectivityIT` pasa a `shared/storage/integration/`. No es una división en microservicios ni un cambio de rutas HTTP por sí mismo.

### 2026-10-31 — Invitaciones municipales (backend)

Commits siguientes a la reorganización: `81d8d85` (`feat(backend): implementar invitaciones municipales seguras`) y `0bb0ca3` (runner/verificaciones PostgreSQL y SMTP aislados).

- Cinco operaciones bajo `/api/v1`: emitir, listar paginado, consultar una invitación, revocar y canjear. La emisión no crea `User`; el canje autenticado requiere `CITIZEN` y correo verificado coincidente y conserva permisos previos.
- Roles invitables globales `OPERATOR`/`ADMIN` y departamentales `TECHNICIAN`/`DEPARTMENT_COORDINATOR`. El departamento debe existir y permanecer activo. Emisión/consulta/listado/revocación requieren `ADMIN` activo y verificado.
- Migración **V2** para `departments`, `department_memberships`, `staff_invitations` y `audit_events`, manteniendo V1 intacta. UUIDv7, restricciones `CHECK`, FK `RESTRICT`, índices y unicidad parcial de pertenencia activa. El estado de invitación se deriva de las fechas.
- Códigos de 256 bits Base64URL, protegidos con HMAC-SHA256 y secreto independiente del JWT y de verificación. Vigencia por defecto de siete días (configurable 1..30). No se expone código ni hash en respuestas administrativas, SQL o auditoría.
- Servicio único de transacciones: bloqueos de usuario → invitación → departamento; validación de cuenta/JWT/rol/email/permisos, concesión y auditoría atómicas. La emisión utiliza `StaffInvitationRequested` y `StaffInvitationEmailListener` para SMTP **después del commit**; no hay outbox/reintentos ni garantía de recepción.
- Canje con cuotas independientes por usuario (10/900 s) e IP (30/900 s) y protección de cuerpo de 16 KiB. Respuestas de error uniformes `ProblemDetail` y `429 Retry-After`; consulta paginada `PageMetadata`.
- OpenAPI modular **0.5.0**: retirada del diseño `/auth/staff-registrations` y de la respuesta con secreto, nuevas respuestas/DTO y GET individual. El bundle `openapi.yaml` y el HTML se generan desde fuentes Redocly.
- Tests con JUnit/MVC y Testcontainers PostgreSQL 18; el informe histórico describe además un runner HTTP con JAR, PostgreSQL `tmpfs`, Mailpit y fixtures sintéticos, sin Neon. **Los archivos de ese runner no aparecen en el repositorio**, por lo que no se puede dar por reproducible en el árbol actual.
- Sin frontend de invitaciones, bootstrap productivo del primer ADMIN, retirada de permisos, CRUD de Catálogos/equipos, SMTP público, despliegue real ni migración de Neon. Ver [ADR-0007](docs/adr/0007-invitaciones-personal.md), [API](docs/api/README.md) y [Desarrollo](docs/development.md).

### 2026-10-31 — Documentación y diagramas sincronizados

Documentación de la entrega `56fc44d`:

- Actualizadas arquitectura y desarrollo para reflejar el monolito **modular**, los paquetes y la nueva ubicación de las pruebas.
- Fuentes `.drawio` y SVG de C4, ER02, ER03 y clases simplificadas **ya actualizadas** para la implementación, la auditoría, el correo y el alcance de fotografías. Se elimina la excepción gráfica y las indicaciones de cambios manuales pendientes.
- Redistribuida la información del informe temporal de Invitaciones entre documentos **ya existentes**: [ADR-0007](docs/adr/0007-invitaciones-personal.md) (decisiones funcionales), [API](docs/api/README.md) (contrato), [Desarrollo](docs/development.md) (pruebas y diagnóstico), [Arquitectura](docs/architecture.md) (componentes) y [Despliegue](docs/deployment.md) (riesgos). No se mantiene un documento independiente ni referencias al informe temporal de Codex.
- Actualizados índices y referencias cruzadas del README, API, ADR y guías. Se distingue el último contrato OpenAPI `0.5.0` del diseño histórico `0.2.0`/`0.3.0`/`0.4.0`.

### Evidencia de validación de la entrega original (30/10/2026)

- **`BUILD SUCCESS`: 90 pruebas superadas (48 unitarias/MVC y 42 IT)**, sin fallos ni omisiones; integración: `AuthenticationPostgresIT=8`, `EmailVerificationPostgresIT=12`, `StaffInvitationPostgresIT=22`. Flyway V1/V2 y Hibernate `validate` en PostgreSQL 18 efímero, Java 25/Spring Boot 4.1.2. La reorganización precede a la implementación y al commit de pruebas según Git; se trata de validaciones **ya completadas** en la entrega.
- El flujo HTTP aislado con backend, PostgreSQL/Mailpit registró y verificó usuarios, emitió y canjeó permisos globales/departamentales, comprobó JWT previo, estados, auditoría, cuotas, duplicados, limpieza de cuenta pendiente, ausencia de códigos en respuestas y `Location`; terminó correctamente.
- Lint, bundle y build-docs de Redocly `0.5.0` completados sin errores y con dos avisos conocidos (`info.license` sin definir y servidor localhost). Se registraron además `git diff --check` y revisión de enlaces.
- Mailpit valida captura SMTP local, **no** entrega en Internet. `NeonConnectivityIT` no se ejecutó en esas suites; V1/V2 no constan aplicadas sobre Neon `development`.

## 2026-10-28 — Verificación de correo y transporte SMTP (implementación local)

- Implementados `POST /api/v1/auth/email-verifications/confirm` (`204`) y `/resend` (`202`, `204` o `410` según estado), ampliando los tres POST anteriores de auth a cinco. Registro inicial devuelve `201 TokenDTO` con JWT de permisos limitados.
- Entidad y tabla `EmailVerification` incorporadas a **V1 antes de su primer despliegue**. Código decimal de ocho dígitos protegido por HMAC distinto de JWT, caducidad máxima de diez minutos y cuenta pendiente limitada a 24 horas.
- Revalidación `emailVerified` desde PostgreSQL en peticiones protegidas y `403 EMAIL_NOT_VERIFIED` fuera de excepciones de auth; mismo JWT funciona al confirmar.
- Transporte `EmailSender`/`SmtpEmailSender`, listener `AFTER_COMMIT`, Mailpit en Compose y timeouts SMTP; los fallos de envío no revierten registro/reenvío y no existe outbox.
- Cuotas en memoria por usuario/IP y cuerpos de cinco POST con máximo 16 KiB. Limpieza horaria de códigos y cuentas sin verificar con lotes y `SKIP LOCKED`.
- Evidencia histórica: Codex comunicó **55 pruebas (35 unitarias/MVC + 20 PostgreSQL IT)**. Quedaban pendientes entrega SMTP externa, recuperación de contraseña, primera cuenta ADMIN, despliegue en Neon y evolución de la política de limpieza.

## Octubre de 2026 — Evolución histórica del diseño y Autenticación

### Implementación del tag Autenticación (27/10/2026)

- `POST /api/v1/auth/register` (`201`), `/auth/login` (`200`) y `/users/me/password-changes` (`204`); rol global base `CITIZEN`, email normalizado, preferencias por defecto, `emailVerified=false` y `tokenVersion=0`.
- Flyway V1 para `users`, `roles`, `user_roles` y, después, verificación. Cinco códigos de rol; roles globales y departamentales distinguidos mediante `RoleCode`, sin columna `Role.scope`.
- JWT HS256 de 7200 s (`sub`, `iat`, `exp`, `iss`, `ver`), BCrypt 12, autoridad y cuenta actual consultadas en BD, invalidación de JWT previos tras cambio de contraseña por versión.
- Cuotas de registro/login por IP y email, límite de cuerpo 16 KiB, `429 Retry-After`, errores `ProblemDetail`, protección de datos en logs, `503` para indisponibilidad tipada de DB.
- Evidencia histórica más temprana (25/10): 23 pruebas (16 unitarias/MVC + 7 PostgreSQL IT), anterior a la ampliación de correo y a los cambios siguientes; no extrapolarla a estados posteriores.

### Diseño y contrato original (octubre de 2026)

- Modelo de dominio v1 con relacional de cuatro páginas, catálogo simplificado de clases y C4 fuente `.drawio`; contrato OpenAPI 3.0.3 modular por tags y diseño _design-first_.
- OpenAPI `0.2.0` armonizó roles globales/departamentales, asignaciones, identificadores, sugerencias de duplicados y contextos; `0.3.0` fijó auth/JWT; `0.4.0` añadió verificación; `0.5.0` incorpora Invitaciones. Las referencias a versiones antiguas son **históricas**, no el contrato vigente.
- ADR-0005 (JWT), ADR-0007 (invitar personal), ADR-0009 (roles por ámbito), ADR-0010 (derivación/asignaciones) y ADR-0011 (UUIDv7); las entidades de negocio pendientes no se deben crear por aparecer en diagramas.
- Primera versión del backend Spring Boot, Expo Router/React Native Web, Dockerfiles de desarrollo y producción prevista, PostgreSQL/S3 Neon de desarrollo y documentación inicial.
- Conectividad histórica (15/10): Docker Compose conjunto, JDBC Neon PostgreSQL 18 y `HeadBucket` privado, **sin** acreditar operaciones S3 de contenido ni migraciones de autenticación a Neon.
- Frontend (13/10): comprobaciones TypeScript, ESLint, Expo Doctor 21/21 y exportación web superadas; no equivalen a pruebas funcionales de incidencias o dispositivos.

### Límites de aquellas versiones

Las entregas originales de Autenticación e Invitaciones no incluían administración de catálogos y usaban V1/V2. Departamentos y Categorías se incorporaron después y sus migraciones se consolidaron en la única V1 vigente, según las entradas del 02 y 03/11/2026. La aplicación de ese esquema consolidado en Neon sigue sin acreditarse; los pendientes funcionales actuales figuran al comienzo del documento.

## 2026-10-30 — Cierre técnico de departamentos (histórico de main)

- Completado el subconjunto Department del tag Catálogos: listado/detalle con correo verificado y creación/PATCH con ADMIN global; desactivación sin DELETE.
- UUIDv7 de Hibernate conforme al ADR-0011, PATCH con omisión diferenciada de null explícito, validación tras trim y bloqueo de fila para conservar cambios concurrentes.
- ProblemDetail común con códigos de departamentos; solo las violaciones de los índices únicos conocidos se traducen a 409. UUID mal formado devuelve 400.
- V2 y semillas conservadas; nuevas pruebas PostgreSQL aisladas para persistencia, restricciones, concurrencia y JWT real. Ver [contrato de Departamentos](docs/api/README.md#51-departamentos-implementados-catálogos) para comandos y resultados exactos.
- Contrato modular y artefactos Redocly sincronizados; fuentes y exportaciones de Department actualizadas. Revisión visual manual completada posteriormente en diagrams.net: comprobadas las cuatro páginas del diagrama relacional y la página del diagrama de clases, sin textos cortados ni elementos solapados.

Esta entrada describe la rama de Departamentos antes de la consolidación; las semillas y el esquema se sustituyen por la decisión del 02/11/2026.
