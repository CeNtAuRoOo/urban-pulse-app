# Arquitectura de UrbanPulse

> **Estado a 11/11/2026:** backend Spring Boot organizado como **monolito modular** en `auth`, `identity`, `invitations`, `organization`, `categories`, `incidents`, `attachment`, `audit` y `shared`. Autenticación, Usuarios, Invitaciones, Departamentos, Categorías, Incidencias y Evidencias están implementados; los demás tags de negocio siguen siendo diseño. Existe una única V1 consolidada antes de su primera aplicación y la verificación en PostgreSQL 18 aislado está documentada en [Desarrollo](development.md#verificación-del-backend); **no se acredita** haber desplegado estas migraciones sobre Neon `development`. Las fuentes C4, ER y clases distinguen V1 y diseño futuro; el [inventario](diagrams/README.md) diferencia validación estructural actual y exportaciones históricas.

## 1. Propósito y decisiones arquitectónicas

UrbanPulse está planteado para registrar incidencias con localización y fotografías, validarlas, priorizarlas, asignarlas y enriquecerlas gradualmente con contexto urbano. El núcleo implementa alta, consulta, corrección e historial. La tramitación municipal, analítica y proveedores externos **no están implementados**. Los clientes web y móviles podrán compartir código Expo/React Native; todas las decisiones de autorización se aplicarán en el servidor.

Se adopta **un único proceso Spring Boot** con módulos organizados por responsabilidad de negocio, en lugar de los antiguos paquetes globales `controller`, `entity`, `service`, `repository`, `dto` y `config`. Dentro de cada módulo siguen existiendo capas técnicas (`api`, `dto`, `model`, `repository`, `service`, etc.) cuando son necesarias. Por tanto, hay separación de capas _dentro de módulos_, pero **no** un «monolito organizado globalmente por capas». No se incorporan microservicios, colas ni cachés distribuidas sin una necesidad verificable; las transacciones de identidad, permisos y auditoría se benefician de una misma base PostgreSQL.

Los principales criterios son integridad, trazabilidad, seguridad por rol y contexto, mantenibilidad, interoperabilidad y costes controlados. El uso de una infraestructura cloud no significa que la aplicación se haya desplegado en producción.

## 2. Contenedores C4 y tecnologías

| Contenedor lógico                   | Tecnología                                                        | Situación                                                                                                                                                                                                |
| ----------------------------------- | ----------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Web                                 | Expo SDK 57, React Native Web, TypeScript, Expo Router            | Pantalla inicial, ejecución/exportación web de desarrollo verificadas; sin cliente de incidencias ni login conectado.                                                                                    |
| Android/iOS                         | Expo/React Native                                                 | Código compartido; prueba funcional completa en dispositivos pendiente.                                                                                                                                  |
| API REST                            | Java 25, Spring Boot 4.1.2, Spring Security, JPA/Hibernate, Maven | 34 operaciones implementadas: cinco de auth, cinco de Usuarios, cinco de Invitaciones, cuatro por catálogo, seis de Incidencias y cinco de Evidencias. Tramitación y otros módulos avanzados pendientes. |
| Datos relacionales                  | PostgreSQL 18 en Neon / Testcontainers para pruebas               | JDBC Neon comprobado históricamente; V1 consolidada; validación actual en Desarrollo, aplicación a Neon no acreditada.                                                                                   |
| Objetos privados                    | Neon Object Storage, S3                                           | Evidencias privadas implementadas y probadas con S3 simulado; conectividad histórica comprobada. Ciclo real Neon no ejecutado en esta revisión.                                                          |
| Transporte SMTP                     | Spring Mail / Mailpit local                                       | Emisión tras commit implementada; no se ha probado entrega en Internet ni proveedor productivo.                                                                                                          |
| Mapas/contexto/otras notificaciones | Por seleccionar                                                   | Diseño, sin integración funcional.                                                                                                                                                                       |

### Contexto y contenedores

![C4: contexto](diagrams/c4-context.svg)

![C4: contenedores](diagrams/c4-containers.svg)

La fuente editable de ambas vistas es [`diagrams/c4.drawio`](diagrams/c4.drawio). Los contenedores C4 representan unidades lógicas desplegables o almacenes y **no** implican que cada uno sea un contenedor Docker. El C4 de contenedores refleja el alcance inicial de **fotografías** (ADR-0004) y los componentes SMTP/Mailpit en la fuente editable; los SVG son exportaciones históricas.

### Vistas del dominio

- [Modelo relacional](diagrams/relacional.drawio) y exportaciones ER01–ER04: dominio lógico, incluidos los esquemas de identidad y organización de V1 consolidada.
- [Clases simplificadas](diagrams/clases-simplificado.drawio), sin asociaciones UML gráficas por diseño: campos, tipos, enumeraciones, embebidos y DTO de apoyo.
- [Inventario gráfico actualizado](diagrams/README.md), [modelo de dominio](domain-model.md) y [contrato dominio–OpenAPI](api/domain-openapi.md).

**Una clase dibujada no es automáticamente una entidad JPA implementada.** Fuera de los módulos actuales, las demás páginas son modelos de diseño que deben revisarse y ampliarse por tag. Siguen pendientes **nuevos tipos de diagramas** no incluidos en esta entrega: C4 Deployment del hosting definitivo, secuencia de fotografía/incidencia y matriz completa de estados y permisos.

## 3. Estructura real del backend

Raíz Java: `backend/src/main/java/es/grupo18/urbanpulse/`.

```text
urbanpulse/
├── BackendApplication.java
├── auth/
│   ├── api/             # AuthController
│   ├── config/          # Verificación de correo y propiedades
│   ├── dto/             # Registro, login, cambio de contraseña, JWT
│   ├── mail/            # Eventos/listener de verificación
│   ├── model/           # EmailVerification
│   ├── repository/
│   ├── security/         # Cuotas de auth/verificación y límite de cuerpo
│   └── service/          # AuthService, JWT, verificación y limpieza
├── identity/
│   ├── api/              # UserController
│   ├── dto/              # Perfil, administración y UserDTO
│   ├── model/            # User, Role, RoleCode, UserRole y embebidos
│   ├── repository/
│   └── service/          # UserService
├── invitations/
│   ├── api/              # StaffInvitationController
│   ├── config/           # StaffInvitationProperties
│   ├── dto/              # Peticiones, consultas, respuestas
│   ├── mail/             # Evento y listener SMTP AFTER_COMMIT
│   ├── model/            # StaffInvitation, InvitationStatus
│   ├── repository/       # StaffInvitationRepository
│   ├── security/         # InvitationRateLimitFilter
│   └── service/          # StaffInvitationService, InvitationCodeService
├── organization/
│   ├── api/              # DepartmentController y errores específicos
│   ├── dto/              # Alta, PATCH y respuesta
│   ├── model/            # Department, DepartmentMembership
│   ├── repository/       # Catálogo y pertenencias
│   └── service/          # DepartmentService, DepartmentMembershipService y excepciones
├── categories/
│   ├── api/              # CategoryController; errores compartidos
│   ├── dto/              # Alta, PATCH y respuesta
│   ├── model/            # Category
│   ├── repository/       # Catálogo y bloqueo de fila
│   └── service/          # CategoryService y excepciones
├── attachment/          # Evidencias privadas, validación de imágenes y S3
├── incidents/           # API, DTO, modelo, repositorios y servicios del núcleo V1
├── audit/
│   ├── model/            # AuditEvent
│   └── repository/
└── shared/
    ├── error/            # ApiException, ProblemDetail, excepciones
    ├── mail/             # EmailSender/SmtpEmailSender y propiedades
    ├── pagination/       # PageMetadata
    ├── ratelimit/        # InMemoryRateLimiter y propiedades
    ├── security/         # SecurityConfig, JWT/autoridades y handlers
    └── storage/          # StorageProperties/StorageConfig, S3Client
```

La misma división se reproduce en `backend/src/test/java/es/grupo18/urbanpulse/`: las suites auth bajo `auth/{api,integration,service}`, Invitaciones bajo `invitations/{api,integration,service}`, Departamentos bajo `organization/{api,integration,model,service}`, Categorías bajo `categories/{api,integration,service}`, pruebas compartidas en `shared/*` y **`NeonConnectivityIT` en `shared/storage/integration/`**, no en la raíz de tests.

### Responsabilidades y dependencias

- **`auth`**: registro ciudadano, login, cambio de contraseña, verificación/reenvío del correo y limpieza de cuentas pendientes; usa identidad, seguridad y correo compartidos.
- **`identity`**: modelo canónico de usuarios y roles globales. Incluye API de perfil y administración, DTO, servicios y repositorios; no genera invitaciones.
- **`invitations`**: ciclo de emisión/listado/consulta/revocación/canje, códigos HMAC, cuotas y notificación SMTP; utiliza `identity`, `organization`, `audit` y utilidades `shared`, pero **no** depende de `AuthService` para registrar al invitado.
- **`organization`**: administración de departamentos y ciclo interno de alta/baja de pertenencias. Comparte entidad y bloqueos con Invitaciones; equipos siguen pendientes.
- **`categories`**: catálogo independiente con API, DTO, modelo, repositorio y servicio; reutiliza seguridad y errores de `shared`.
- **`audit`**: persistencia compartida de eventos de invitaciones e incidencias; no existe un endpoint de consulta pública.
- **`shared`**: adaptadores y preocupaciones transversales: `SecurityConfig` centralizado, autorización JWT contra datos vigentes, errores, paginación, limitación en memoria, SMTP y almacenamiento S3.

La dirección típica dentro de un caso de uso es `Controller → Service → Repository → PostgreSQL`; las reglas del dominio y la persistencia no se trasladan al controlador. `StaffInvitationService` concentra las cinco operaciones y las transacciones necesarias, sin servicio delegador redundante. La configuración externa se inyecta en el inicio de Spring Boot.

## 4. Persistencia y transacciones

Flyway gestiona migraciones en `backend/src/main/resources/db/migration/`:

| Migración                | Alcance                                                                                                                                                                                                                                                                                                                                                                              |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `V1__initial_schema.sql` | `users`, `roles`, `user_roles`, `email_verifications`, `departments`, `department_memberships`, `staff_invitations`, `audit_events`, `categories`, `incidents`, `status_changes`, `incident_reviews`, `priority_decisions`, `attachments`; cinco roles, restricciones e índices de los módulos implementados, FK históricas `RESTRICT`, sin departamentos ni categorías precargados. |

`spring.jpa.hibernate.ddl-auto=validate` comprueba los mapeos, no crea ni migra tablas. Se generan UUIDv7 desde Hibernate en las entidades apropiadas. La consolidación se autoriza únicamente bajo la premisa de destino vacío y ninguna migración previa aplicada en bases que deban conservarse; después de aplicar V1 no debe reescribirse. Se ha comprobado JDBC y el bucket privados en Neon `development` en una **prueba histórica de conectividad**, distinta de las pruebas V1 consolidada con PostgreSQL efímero. No ejecutar tests de escritura ni migraciones experimentales contra Neon `production`.

Invitaciones utiliza bloqueos pesimistas de PostgreSQL en el orden **usuario → invitación → departamento**. El canje conserva de forma atómica permiso (`UserRole` o `DepartmentMembership`), estado de invitación y `AuditEvent`. Revalida sesión, versión JWT, cuenta, email verificado, permiso, ámbito y caducidad; la FK y unicidad SQL son garantías adicionales. Las invitaciones conservan su historial y calculan el estado al consultar, en lugar de tener una columna `status`. La emisión persiste la invitación antes de enviar el correo **fuera de la transacción**, con `AFTER_COMMIT`: fallo SMTP no revierte el dato.

PATCH de Departamentos adquiere un bloqueo pesimista sobre la misma fila que Invitaciones. Su repositorio mantiene las consultas de ambos casos de uso. Los índices únicos de nombre/código resuelven carreras de alta, y solo sus infracciones 23505 se traducen a duplicado. Fechas JPA y UUIDv7 conservados; [contrato detallado](api/README.md#51-departamentos-implementados-catálogos).

Category tiene su entidad y repositorio exclusivos en `categories`, sin dependencia de `organization`. PATCH bloquea la fila con `PESSIMISTIC_WRITE` antes de modificar; el índice único y `saveAndFlush` resuelven altas y renombrados concurrentes. Solo SQLSTATE 23505 del índice `uk_categories_name_normalized` se traduce a `CATEGORY_DUPLICATE`; otras infracciones conservan el tratamiento común. La transacción revierte todos los cambios del perdedor. V1 incorpora la FK opcional desde Incident; la desactivación conserva referencias y consultas históricas.

Usuarios bloquea la fila antes de cambiar perfil o actividad, coordinándose con contraseña, verificación y canje. Las bajas administrativas adquieren primero un bloqueo transaccional común en PostgreSQL y comprueban que quede un ADMIN activo y verificado. El listado pagina usuarios en SQL y carga roles y pertenencias en lote (sin fetch paginado de colecciones). La baja interna de pertenencias bloquea usuario y después pertenencia, sin cargar previamente estado mutable.

La limpieza excluye las cuentas no verificadas con referencias históricas antes del límite del lote, vuelve a comprobarlas bajo bloqueo y conserva todas sus relaciones. Solo borra las cuentas caducadas sin referencias; `SKIP LOCKED` permite continuar con otras. Véanse [ADR-0012](adr/0012-gestion-usuarios-perfiles-y-administracion.md) y [ADR-0013](adr/0013-pertenencias-usuarios-departamentos.md).

## 5. Autenticación, permisos y APIs activas

`AuthController` expone bajo `/api/v1` los cinco POST: `/auth/register`, `/auth/login`, `/users/me/password-changes`, `/auth/email-verifications/confirm` y `/auth/email-verifications/resend`. El registro crea un usuario con `CITIZEN`, `emailVerified=false` y `tokenVersion=0`. El JWT de acceso HS256 dura dos horas e incorpora `sub`, `iat`, `exp`, `iss` y `ver`; no hay refresh. Cambiar la contraseña incrementa la versión e invalida tokens anteriores. `DatabaseJwtAuthenticationConverter` consulta en PostgreSQL estado, versión, roles y correo verificado **en cada petición protegida**; los roles del JWT no son la fuente de verdad.

`identity.api.UserController` expone las cinco operaciones de perfil y administración. Todas requieren cuenta activa y verificada; las administrativas exigen ADMIN tanto en rutas como en métodos. `displayName` normalizado se valida en puntos de código Unicode antes de persistir; PATCH administrativo solo cambia `active`. Los DTO incorporan pertenencias activas e históricas sin otorgar autoridad global por ellas.

`StaffInvitationController` expone bajo `/api/v1` cinco operaciones de invitaciones (emisión, listado, GET individual, revocación, canje) descritas en [ADR-0007](adr/0007-invitaciones-personal.md) y [OpenAPI](api/README.md). Emitir/consultar/listar/revocar requiere `ADMIN` y correo verificado; canjear requiere `CITIZEN` y correo verificado coincidente. `OPERATOR`/`ADMIN` son globales; `TECHNICIAN`/`DEPARTMENT_COORDINATOR` requieren pertenencia departamental activa. La autorización contextual de futuras incidencias y equipos **no** está implementada.

Los módulos de auth e Invitaciones reutilizan `ProblemDetail`, protección HTTP de cuerpos de 16 KiB, buckets de tasa locales y transporte SMTP configurable. Los buckets de canje por usuario (10/900 s) y por IP (30/900 s) no son distribuidos. El envoltorio CachedBodyRequest es compartido en shared/security; cada filtro mantiene su selección de rutas y límites, incluido el límite de 64 KiB de Incidencias sin cuotas funcionales.

`organization.api.DepartmentController` expone GET de colección y detalle, POST y PATCH. Consultas con correo verificado, escrituras con ADMIN global; CORS expone Location. Desactivar conserva pertenencias e historial y bloquea nuevas invitaciones/canjes. La auditoría específica de estas escrituras sigue pendiente de especificación (RF18).

`categories.api.CategoryController` comparte las reglas de acceso de Departamentos, sin duplicar Spring Security ni handlers. La auditoría administrativa RF18 de ambos catálogos requiere definir acciones, actor, contenido y política; la existencia de `AuditEvent` no la completa.

## 6. Frontend, almacenamiento y resiliencia

`frontend/` contiene `src/app` de Expo Router, `components`, `config`, `constants`, `features`, `services` y `types`. `web.output="single"` genera SPA; `EXPO_PUBLIC_API_URL` es una variable pública, nunca un almacén de secretos. No hay todavía formulario de incidencias, integración real de API, login completo ni validación funcional Android/iOS. Ninguna ocultación de controles en UI sustituye la autorización servidor.

El bucket `incident-evidence` es privado; los metadatos se almacenan en PostgreSQL y los binarios en S3. Subida/descarga, validación de tipo/tamaño, retirada de EXIF y autorización por incidencia están implementadas. Retención, purgado físico y reconciliación de huérfanos siguen futuros. Los datos de contexto urbano pueden enriquecer incidencias, pero su indisponibilidad no debería bloquear el alta; todavía no hay proveedores integrados.

Antes del despliegue son necesarios HTTPS, CORS revisado, transporte de JWT, política de claves/rotación, correos reales, retención de datos, observabilidad, coordinación de rate limiting entre réplicas, gestión de backups y procedimientos de migración. No se debe presentar el Compose de test como imagen productiva.

## 7. Evidencias y trabajo pendiente

Las suites reproducibles de backend, las comprobaciones del frontend y sus límites se mantienen en [Desarrollo](development.md#verificación-del-backend). Las pruebas aisladas no acreditan despliegue en Neon ni entrega SMTP en Internet.

Siguen pendientes bootstrap productivo de ADMIN, API de retirada de permisos, equipos, tramitación de incidencias, retención/purgado de objetos, auditoría administrativa completa RF18, SMTP externo, despliegue y CI/CD. Las reglas de Invitaciones están en [ADR-0007](adr/0007-invitaciones-personal.md); el contrato, en [API](api/README.md); y el diseño incremental, en [Modelo de dominio](domain-model.md).

## Incidencias: integración modular

`incidents/{api,dto,model,repository,service}` depende de identity, categories, organization, audit y shared; no de servicios externos. V1 contiene incidents y las estructuras base status_changes, incident_reviews y priority_decisions. Se reutilizan User, Category, Department, AuditEvent, DTO de catálogos, UserSummaryDTO de identity, ProblemDetail y PageMetadata. No hay entidades duplicadas, estado de sesión ni nuevos clientes S3.

Las transacciones pertenecen a IncidentService. La escritura revalida cuenta bajo el bloqueo compartido de identidad, comprueba titularidad/rol global, estado y precondición, y usa @Version/flush sobre Incident. Alta/corrección y AuditEvent confirman o revierten conjuntamente. La categoría usa el bloqueo de su catálogo para coordinar desactivaciones. La limpieza de cuentas reconoce las cuatro nuevas FK a User, sin borrar historia. Los entity graphs solo cargan relaciones to-one necesarias y los filtros/paginación se ejecutan en SQL.

La API no implementa tramitación ni geocodificación, mapas ni notificaciones de negocio. Las cuotas específicas de alta/corrección siguen como propuesta proporcional sobre el limitador compartido; no se afirma protección distribuida ni se reutiliza un máximo de 16 KiB incompatible con descripciones Unicode válidas. [ADR-0014](adr/0014-incidencias-concurrencia-historial.md) y [roadmap](incidents-implementation-roadmap.md).

El detalle de Incidencias usa ETag fuerte SHA-256 del JSON completo (incluidos recursos proyectados y version), junto a @Version para carreras de escritura. POST/PATCH tienen un límite bruto de 64 KiB antes de Jackson y responden 413 al excederlo; no incorporan cuotas funcionales nuevas. Se reutiliza CachedBodyRequest con Autenticación, que conserva su límite de 16 KiB. Véase [ADR-0014](adr/0014-incidencias-concurrencia-historial.md).

## Evidencias privadas

`attachment` reutiliza IncidentAccess y AuditEvent, mantiene binarios en S3 y metadatos en la única V1. Máximo cinco activas por incidencia, 10 MiB de entrada/salida, JPEG/PNG recodificadas sin EXIF/GPS, 16 MP y 8192 por lado. Usuario e incidencia se bloquean solo al confirmar metadatos o baja lógica; las transferencias S3 quedan fuera de SQL. Un fallo de confirmación intenta compensar el objeto. DELETE conserva binario e historia; retención y purgado son futuros. [ADR-0015](adr/0015-almacenamiento-evidencias.md).
