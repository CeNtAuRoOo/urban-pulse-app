# Arquitectura de UrbanPulse

> **Estado a 03/11/2026:** backend Spring Boot organizado como **monolito modular** en `auth`, `identity`, `invitations`, `organization`, `categories`, `audit` y `shared`. Autenticación, Invitaciones, Departamentos y Categorías están implementados; los demás tags de negocio siguen siendo diseño. Existe una V1 consolidada y hay pruebas previas en PostgreSQL 18 aislado; **no se acredita** haber desplegado estas migraciones sobre Neon `development`. Las fuentes C4, ER y clases y sus SVG regenerados manualmente incorporan los módulos actuales; la correspondencia estructural está descrita en el [inventario](diagrams/README.md).

## 1. Propósito y decisiones arquitectónicas

UrbanPulse está planteado para registrar incidencias con localización y fotografías, validarlas, priorizarlas, asignarlas y enriquecerlas gradualmente con contexto urbano. Las funciones operativas de incidencias, fotografías completas, analítica y proveedores externos **no están implementadas** en el backend actual. Los clientes web y móviles podrán compartir código Expo/React Native; todas las decisiones de autorización se aplicarán en el servidor.

Se adopta **un único proceso Spring Boot** con módulos organizados por responsabilidad de negocio, en lugar de los antiguos paquetes globales `controller`, `entity`, `service`, `repository`, `dto` y `config`. Dentro de cada módulo siguen existiendo capas técnicas (`api`, `dto`, `model`, `repository`, `service`, etc.) cuando son necesarias. Por tanto, hay separación de capas _dentro de módulos_, pero **no** un «monolito organizado globalmente por capas». No se incorporan microservicios, colas ni cachés distribuidas sin una necesidad verificable; las transacciones de identidad, permisos y auditoría se benefician de una misma base PostgreSQL.

Los principales criterios son integridad, trazabilidad, seguridad por rol y contexto, mantenibilidad, interoperabilidad y costes controlados. El uso de una infraestructura cloud no significa que la aplicación se haya desplegado en producción.

## 2. Contenedores C4 y tecnologías

| Contenedor lógico                   | Tecnología                                                        | Situación                                                                                                                                       |
| ----------------------------------- | ----------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Web                                 | Expo SDK 57, React Native Web, TypeScript, Expo Router            | Pantalla inicial, ejecución/exportación web de desarrollo verificadas; sin cliente de incidencias ni login conectado.                           |
| Android/iOS                         | Expo/React Native                                                 | Código compartido; prueba funcional completa en dispositivos pendiente.                                                                         |
| API REST                            | Java 25, Spring Boot 4.1.2, Spring Security, JPA/Hibernate, Maven | Cinco operaciones de auth, cinco de Invitaciones y cuatro por catálogo (Departamentos y Categorías) disponibles; módulos de negocio pendientes. |
| Datos relacionales                  | PostgreSQL 18 en Neon / Testcontainers para pruebas               | JDBC Neon comprobado históricamente; V1 consolidada; validación actual en Desarrollo, aplicación a Neon no acreditada.                          |
| Objetos privados                    | Neon Object Storage, S3                                           | Bucket privado y `HeadBucket` comprobados; subida, descarga, borrado y ciclo completo de evidencias pendientes.                                 |
| Transporte SMTP                     | Spring Mail / Mailpit local                                       | Emisión tras commit implementada; no se ha probado entrega en Internet ni proveedor productivo.                                                 |
| Mapas/contexto/otras notificaciones | Por seleccionar                                                   | Diseño, sin integración funcional.                                                                                                              |

### Contexto y contenedores

![C4: contexto](diagrams/c4-context.svg)

![C4: contenedores](diagrams/c4-containers.svg)

La fuente editable de ambas vistas es [`diagrams/c4.drawio`](diagrams/c4.drawio). Los contenedores C4 representan unidades lógicas desplegables o almacenes y **no** implican que cada uno sea un contenedor Docker. El C4 de contenedores refleja el alcance inicial de **fotografías** (ADR-0004) y los componentes SMTP/Mailpit conforme a la versión gráfica actual.

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
│   ├── dto/              # UserDTO
│   ├── model/            # User, Role, RoleCode, UserRole y embebidos
│   └── repository/
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
│   └── service/          # DepartmentService y excepciones
├── categories/
│   ├── api/              # CategoryController; errores compartidos
│   ├── dto/              # Alta, PATCH y respuesta
│   ├── model/            # Category
│   ├── repository/       # Catálogo y bloqueo de fila
│   └── service/          # CategoryService y excepciones
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
- **`identity`**: modelo canónico de usuarios y roles globales. No genera invitaciones ni conoce dependencias HTTP.
- **`invitations`**: ciclo de emisión/listado/consulta/revocación/canje, códigos HMAC, cuotas y notificación SMTP; utiliza `identity`, `organization`, `audit` y utilidades `shared`, pero **no** depende de `AuthService` para registrar al invitado.
- **`organization`**: administración de departamentos y persistencia de pertenencias. Comparte entidad y bloqueos con Invitaciones; equipos siguen pendientes.
- **`categories`**: catálogo independiente con API, DTO, modelo, repositorio y servicio; reutiliza seguridad y errores de `shared`.
- **`audit`**: persistencia de eventos de invitaciones; no existe un endpoint de consulta pública.
- **`shared`**: adaptadores y preocupaciones transversales: `SecurityConfig` centralizado, autorización JWT contra datos vigentes, errores, paginación, limitación en memoria, SMTP y almacenamiento S3.

La dirección típica dentro de un caso de uso es `Controller → Service → Repository → PostgreSQL`; las reglas del dominio y la persistencia no se trasladan al controlador. `StaffInvitationService` concentra las cinco operaciones y las transacciones necesarias, sin servicio delegador redundante. La configuración externa se inyecta en el inicio de Spring Boot.

## 4. Persistencia y transacciones

Flyway gestiona migraciones en `backend/src/main/resources/db/migration/`:

| Migración                | Alcance                                                                                                                                                                                                                                                                                      |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `V1__initial_schema.sql` | `users`, `roles`, `user_roles`, `email_verifications`, `departments`, `department_memberships`, `staff_invitations`, `audit_events`, `categories`; cinco roles, restricciones e índices de los módulos implementados, FK históricas `RESTRICT`, sin departamentos ni categorías precargados. |

`spring.jpa.hibernate.ddl-auto=validate` comprueba los mapeos, no crea ni migra tablas. Se generan UUIDv7 desde Hibernate en las entidades apropiadas. La consolidación se autoriza únicamente bajo la premisa de destino vacío y ninguna migración previa aplicada en bases que deban conservarse; después de aplicar V1 no debe reescribirse. Se ha comprobado JDBC y el bucket privados en Neon `development` en una **prueba histórica de conectividad**, distinta de las pruebas V1 consolidada con PostgreSQL efímero. No ejecutar tests de escritura ni migraciones experimentales contra Neon `production`.

Invitaciones utiliza bloqueos pesimistas de PostgreSQL en el orden **usuario → invitación → departamento**. El canje conserva de forma atómica permiso (`UserRole` o `DepartmentMembership`), estado de invitación y `AuditEvent`. Revalida sesión, versión JWT, cuenta, email verificado, permiso, ámbito y caducidad; la FK y unicidad SQL son garantías adicionales. Las invitaciones conservan su historial y calculan el estado al consultar, en lugar de tener una columna `status`. La emisión persiste la invitación antes de enviar el correo **fuera de la transacción**, con `AFTER_COMMIT`: fallo SMTP no revierte el dato.

PATCH de Departamentos adquiere un bloqueo pesimista sobre la misma fila que Invitaciones. Su repositorio mantiene las consultas de ambos casos de uso. Los índices únicos de nombre/código resuelven carreras de alta, y solo sus infracciones 23505 se traducen a duplicado. Fechas JPA y UUIDv7 conservados; [contrato detallado](api/README.md#51-departamentos-implementados-catálogos).

Category tiene su entidad y repositorio exclusivos en `categories`, sin dependencia de `organization`. PATCH bloquea la fila con `PESSIMISTIC_WRITE` antes de modificar; el índice único y `saveAndFlush` resuelven altas y renombrados concurrentes. Solo SQLSTATE 23505 del índice `uk_categories_name_normalized` se traduce a `CATEGORY_DUPLICATE`; otras infracciones conservan el tratamiento común. La transacción revierte todos los cambios del perdedor. No hay FK a Incident ni implementación anticipada de incidencias.

## 5. Autenticación, permisos y APIs activas

`AuthController` expone bajo `/api/v1` los cinco POST: `/auth/register`, `/auth/login`, `/users/me/password-changes`, `/auth/email-verifications/confirm` y `/auth/email-verifications/resend`. El registro crea un usuario con `CITIZEN`, `emailVerified=false` y `tokenVersion=0`. El JWT de acceso HS256 dura dos horas e incorpora `sub`, `iat`, `exp`, `iss` y `ver`; no hay refresh. Cambiar la contraseña incrementa la versión e invalida tokens anteriores. `DatabaseJwtAuthenticationConverter` consulta en PostgreSQL estado, versión, roles y correo verificado **en cada petición protegida**; los roles del JWT no son la fuente de verdad.

`StaffInvitationController` expone bajo `/api/v1` cinco operaciones de invitaciones (emisión, listado, GET individual, revocación, canje) descritas en [ADR-0007](adr/0007-invitaciones-personal.md) y [OpenAPI](api/README.md). Emitir/consultar/listar/revocar requiere `ADMIN` y correo verificado; canjear requiere `CITIZEN` y correo verificado coincidente. `OPERATOR`/`ADMIN` son globales; `TECHNICIAN`/`DEPARTMENT_COORDINATOR` requieren pertenencia departamental activa. La autorización contextual de futuras incidencias y equipos **no** está implementada.

Los módulos de auth e Invitaciones reutilizan `ProblemDetail`, protección HTTP de cuerpos de 16 KiB, buckets de tasa locales y transporte SMTP configurable. Los buckets de canje por usuario (10/900 s) y por IP (30/900 s) no son distribuidos. Una futura extracción de protección general de cuerpos desde `auth/security/AuthRateLimitFilter` a `shared/security` sería solo una mejora organizativa; el código actual funciona con la separación mostrada.

`organization.api.DepartmentController` expone GET de colección y detalle, POST y PATCH. Consultas con correo verificado, escrituras con ADMIN global; CORS expone Location. Desactivar conserva pertenencias e historial y bloquea nuevas invitaciones/canjes. La auditoría específica de estas escrituras sigue pendiente de especificación (RF18).

`categories.api.CategoryController` comparte las reglas de acceso de Departamentos, sin duplicar Spring Security ni handlers. La auditoría administrativa RF18 de ambos catálogos requiere definir acciones, actor, contenido y política; la existencia de `AuditEvent` no la completa.

## 6. Frontend, almacenamiento y resiliencia

`frontend/` contiene `src/app` de Expo Router, `components`, `config`, `constants`, `features`, `services` y `types`. `web.output="single"` genera SPA; `EXPO_PUBLIC_API_URL` es una variable pública, nunca un almacén de secretos. No hay todavía formulario de incidencias, integración real de API, login completo ni validación funcional Android/iOS. Ninguna ocultación de controles en UI sustituye la autorización servidor.

El bucket `incident-evidence` es privado; los metadatos previstos se almacenarán en PostgreSQL y los binarios en S3. Faltan flujos de subida/descarga, validación de tipos y tamaños, eliminación de EXIF, autorización por fotografía, retención y limpieza de huérfanos. Los datos de contexto urbano pueden enriquecer incidencias, pero su indisponibilidad no debería bloquear el alta; todavía no hay proveedores integrados.

Antes del despliegue son necesarios HTTPS, CORS revisado, transporte de JWT, política de claves/rotación, correos reales, retención de datos, observabilidad, coordinación de rate limiting entre réplicas, gestión de backups y procedimientos de migración. No se debe presentar el Compose de test como imagen productiva.

## 7. Evidencias y trabajo pendiente

Las suites reproducibles de backend, las comprobaciones del frontend y sus límites se mantienen en [Desarrollo](development.md#verificación-del-backend). Las pruebas aisladas no acreditan despliegue en Neon ni entrega SMTP en Internet.

Siguen pendientes bootstrap productivo de ADMIN, retirada de permisos, equipos, APIs de incidencias, flujos S3 reales, auditoría administrativa completa RF18, SMTP externo, despliegue y CI/CD. Las reglas de Invitaciones están en [ADR-0007](adr/0007-invitaciones-personal.md); el contrato, en [API](api/README.md); y el diseño incremental, en [Modelo de dominio](domain-model.md).
