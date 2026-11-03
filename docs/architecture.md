# Arquitectura de UrbanPulse

> **Estado a 02/11/2026:** backend Spring Boot organizado como **monolito modular** en `auth`, `identity`, `invitations`, `organization`, `audit` y `shared`. Autenticación, Invitaciones y Departamentos están implementados; los demás tags de negocio siguen siendo diseño. Existe una V1 consolidada y hay pruebas previas en PostgreSQL 18 aislado; **no se acredita** haber desplegado estas migraciones sobre Neon `development`. Las fuentes C4, ER y clases incorporan la integración; los SVG se conservan para exportación manual.

## 1. Propósito y decisiones arquitectónicas

UrbanPulse está planteado para registrar incidencias con localización y fotografías, validarlas, priorizarlas, asignarlas y enriquecerlas gradualmente con contexto urbano. Las funciones operativas de incidencias, fotografías completas, analítica y proveedores externos **no están implementadas** en el backend actual. Los clientes web y móviles podrán compartir código Expo/React Native; todas las decisiones de autorización se aplicarán en el servidor.

Se adopta **un único proceso Spring Boot** con módulos organizados por responsabilidad de negocio, en lugar de los antiguos paquetes globales `controller`, `entity`, `service`, `repository`, `dto` y `config`. Dentro de cada módulo siguen existiendo capas técnicas (`api`, `dto`, `model`, `repository`, `service`, etc.) cuando son necesarias. Por tanto, hay separación de capas *dentro de módulos*, pero **no** un «monolito organizado globalmente por capas». No se incorporan microservicios, colas ni cachés distribuidas sin una necesidad verificable; las transacciones de identidad, permisos y auditoría se benefician de una misma base PostgreSQL.

Los principales criterios son integridad, trazabilidad, seguridad por rol y contexto, mantenibilidad, interoperabilidad y costes controlados. El uso de una infraestructura cloud no significa que la aplicación se haya desplegado en producción.

## 2. Contenedores C4 y tecnologías

| Contenedor lógico | Tecnología | Situación |
| --- | --- | --- |
| Web | Expo SDK 57, React Native Web, TypeScript, Expo Router | Pantalla inicial, ejecución/exportación web de desarrollo verificadas; sin cliente de incidencias ni login conectado. |
| Android/iOS | Expo/React Native | Código compartido; prueba funcional completa en dispositivos pendiente. |
| API REST | Java 25, Spring Boot 4.1.2, Spring Security, JPA/Hibernate, Maven | Cinco operaciones de auth, cinco de Invitaciones y cuatro de Departamentos disponibles; módulos de negocio pendientes. |
| Datos relacionales | PostgreSQL 18 en Neon / Testcontainers para pruebas | JDBC Neon comprobado históricamente; V1 consolidada; validación actual en Desarrollo, aplicación a Neon no acreditada. |
| Objetos privados | Neon Object Storage, S3 | Bucket privado y `HeadBucket` comprobados; subida, descarga, borrado y ciclo completo de evidencias pendientes. |
| Transporte SMTP | Spring Mail / Mailpit local | Emisión tras commit implementada; no se ha probado entrega en Internet ni proveedor productivo. |
| Mapas/contexto/otras notificaciones | Por seleccionar | Diseño, sin integración funcional. |

### Contexto y contenedores

![C4: contexto](diagrams/c4-context.svg)

![C4: contenedores](diagrams/c4-containers.svg)

La fuente editable de ambas vistas es [`diagrams/c4.drawio`](diagrams/c4.drawio). Los contenedores C4 representan unidades lógicas desplegables o almacenes y **no** implican que cada uno sea un contenedor Docker. El C4 de contenedores refleja el alcance inicial de **fotografías** (ADR-0004) y los componentes SMTP/Mailpit conforme a la versión gráfica actual.

### Vistas del dominio

- [Modelo relacional](diagrams/relacional.drawio) y exportaciones ER01–ER04: dominio lógico, incluidos los esquemas de identidad y organización de V1 consolidada.
- [Clases simplificadas](diagrams/clases-simplificado.drawio), sin asociaciones UML gráficas por diseño: campos, tipos, enumeraciones, embebidos y DTO de apoyo.
- [Inventario gráfico actualizado](diagrams/README.md), [modelo de dominio](domain-model.md) y [contrato dominio–OpenAPI](api/domain-openapi.md).

**Una clase dibujada no es automáticamente una entidad JPA implementada.** Fuera de los módulos actuales, las demás páginas son modelos de diseño que deben revisarse y ampliarse por tag. Siguen pendientes **nuevos tipos de diagramas** no incluidos en esta entrega: C4 Deployment del hosting definitivo, secuencia de fotografía/incidencia y matriz completa de estados y permisos. No deben confundirse con «actualizar los diagramas existentes de Invitaciones», tarea ya realizada.

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

La misma división se reproduce en `backend/src/test/java/es/grupo18/urbanpulse/`: las suites auth bajo `auth/{api,integration,service}`, Invitaciones bajo `invitations/{api,integration,service}`, pruebas compartidas en `shared/*` y **`NeonConnectivityIT` en `shared/storage/integration/`**, no en la raíz de tests.

### Responsabilidades y dependencias

- **`auth`**: registro ciudadano, login, cambio de contraseña, verificación/reenvío del correo y limpieza de cuentas pendientes; usa identidad, seguridad y correo compartidos.
- **`identity`**: modelo canónico de usuarios y roles globales. No genera invitaciones ni conoce dependencias HTTP.
- **`invitations`**: ciclo de emisión/listado/consulta/revocación/canje, códigos HMAC, cuotas y notificación SMTP; utiliza `identity`, `organization`, `audit` y utilidades `shared`, pero **no** depende de `AuthService` para registrar al invitado.
- **`organization`**: administración de departamentos y persistencia de pertenencias. Comparte entidad y bloqueos con Invitaciones; categorías y equipos siguen pendientes.
- **`audit`**: persistencia de eventos de invitaciones; no existe un endpoint de consulta pública.
- **`shared`**: adaptadores y preocupaciones transversales: `SecurityConfig` centralizado, autorización JWT contra datos vigentes, errores, paginación, limitación en memoria, SMTP y almacenamiento S3.

La dirección típica dentro de un caso de uso es `Controller → Service → Repository → PostgreSQL`; las reglas del dominio y la persistencia no se trasladan al controlador. `StaffInvitationService` concentra las cinco operaciones y las transacciones necesarias, sin servicio delegador redundante. La configuración externa se inyecta en el inicio de Spring Boot.

## 4. Persistencia y transacciones

Flyway gestiona migraciones en `backend/src/main/resources/db/migration/`:

| Migración | Alcance |
| --- | --- |
| `V1__initial_schema.sql` | `users`, `roles`, `user_roles`, `email_verifications`, `departments`, `department_memberships`, `staff_invitations`, `audit_events`; cinco roles, restricciones e índices de ambas funcionalidades, FK históricas `RESTRICT`, sin departamentos precargados. |

`spring.jpa.hibernate.ddl-auto=validate` comprueba los mapeos, no crea ni migra tablas. Se generan UUIDv7 desde Hibernate en las entidades apropiadas. La consolidación se autoriza únicamente bajo la premisa de destino vacío y ninguna migración previa aplicada en bases que deban conservarse; después de aplicar V1 no debe reescribirse. Se ha comprobado JDBC y el bucket privados en Neon `development` en una **prueba histórica de conectividad**, distinta de las pruebas V1 consolidada con PostgreSQL efímero. No ejecutar tests de escritura ni migraciones experimentales contra Neon `production`.

Invitaciones utiliza bloqueos pesimistas de PostgreSQL en el orden **usuario → invitación → departamento**. El canje conserva de forma atómica permiso (`UserRole` o `DepartmentMembership`), estado de invitación y `AuditEvent`. Revalida sesión, versión JWT, cuenta, email verificado, permiso, ámbito y caducidad; la FK y unicidad SQL son garantías adicionales. Las invitaciones conservan su historial y calculan el estado al consultar, en lugar de tener una columna `status`. La emisión persiste la invitación antes de enviar el correo **fuera de la transacción**, con `AFTER_COMMIT`: fallo SMTP no revierte el dato.

PATCH de Departamentos adquiere un bloqueo pesimista sobre la misma fila que Invitaciones. Su repositorio mantiene las consultas de ambos casos de uso. Los índices únicos de nombre/código resuelven carreras de alta, y solo sus infracciones 23505 se traducen a duplicado. Fechas JPA y UUIDv7 conservados; [contrato detallado](departments-finalization.md).

## 5. Autenticación, permisos y APIs activas

`AuthController` expone bajo `/api/v1` los cinco POST: `/auth/register`, `/auth/login`, `/users/me/password-changes`, `/auth/email-verifications/confirm` y `/auth/email-verifications/resend`. El registro crea un usuario con `CITIZEN`, `emailVerified=false` y `tokenVersion=0`. El JWT de acceso HS256 dura dos horas e incorpora `sub`, `iat`, `exp`, `iss` y `ver`; no hay refresh. Cambiar la contraseña incrementa la versión e invalida tokens anteriores. `DatabaseJwtAuthenticationConverter` consulta en PostgreSQL estado, versión, roles y correo verificado **en cada petición protegida**; los roles del JWT no son la fuente de verdad.

`StaffInvitationController` expone bajo `/api/v1` cinco operaciones de invitaciones (emisión, listado, GET individual, revocación, canje) descritas en [ADR-0007](adr/0007-invitaciones-personal.md) y [OpenAPI](api/README.md). Emitir/consultar/listar/revocar requiere `ADMIN` y correo verificado; canjear requiere `CITIZEN` y correo verificado coincidente. `OPERATOR`/`ADMIN` son globales; `TECHNICIAN`/`DEPARTMENT_COORDINATOR` requieren pertenencia departamental activa. La autorización contextual de futuras incidencias y equipos **no** está implementada.

Los módulos de auth e Invitaciones reutilizan `ProblemDetail`, protección HTTP de cuerpos de 16 KiB, buckets de tasa locales y transporte SMTP configurable. Los buckets de canje por usuario (10/900 s) y por IP (30/900 s) no son distribuidos. Una futura extracción de protección general de cuerpos desde `auth/security/AuthRateLimitFilter` a `shared/security` sería solo una mejora organizativa; el código actual funciona con la separación mostrada.

`organization.api.DepartmentController` expone GET de colección y detalle, POST y PATCH. Consultas con correo verificado, escrituras con ADMIN global; CORS expone Location. Desactivar conserva pertenencias e historial y bloquea nuevas invitaciones/canjes. La auditoría específica de estas escrituras sigue pendiente de especificación (RF18).

## 6. Frontend, almacenamiento y resiliencia

`frontend/` contiene `src/app` de Expo Router, `components`, `config`, `constants`, `features`, `services` y `types`. `web.output="single"` genera SPA; `EXPO_PUBLIC_API_URL` es una variable pública, nunca un almacén de secretos. No hay todavía formulario de incidencias, integración real de API, login completo ni validación funcional Android/iOS. Ninguna ocultación de controles en UI sustituye la autorización servidor.

El bucket `incident-evidence` es privado; los metadatos previstos se almacenarán en PostgreSQL y los binarios en S3. Faltan flujos de subida/descarga, validación de tipos y tamaños, eliminación de EXIF, autorización por fotografía, retención y limpieza de huérfanos. Los datos de contexto urbano pueden enriquecer incidencias, pero su indisponibilidad no debería bloquear el alta; todavía no hay proveedores integrados.

Antes del despliegue son necesarios HTTPS, CORS revisado, transporte de JWT, política de claves/rotación, correos reales, retención de datos, observabilidad, coordinación de rate limiting entre réplicas, gestión de backups y procedimientos de migración. No se debe presentar el Compose de test como imagen productiva.

## 7. Evidencias y trabajo pendiente

- **13–15/10/2026:** Expo Doctor 21/21, TypeScript, ESLint, exportación web, Compose de desarrollo y conectividad Neon PostgreSQL/Object Storage de forma aislada.
- **25–28/10/2026:** pruebas previas de autenticación (23 y posteriormente 55), anteriores a Invitaciones.
- **Orden de commits:** reorganización de paquetes en `c393418` **antes** de la implementación de Invitaciones (`81d8d85`) y de sus pruebas (`0bb0ca3`).
- **30/10/2026 (fecha del informe de pruebas):** **90 pruebas de backend superadas** (48 unitarias/MVC + 42 PostgreSQL IT), recorrido HTTP correcto contra PostgreSQL/Mailpit aislados y validación de Redocly `0.5.0`. La evidencia corresponde a la funcionalidad implementada, sin implicar un despliegue en Neon.
- **31/10/2026 (documentación):** fuentes `.drawio` y exportaciones SVG de C4, ER y clases sincronizadas con Autenticación e Invitaciones.

Los comandos disponibles y las evidencias históricas están en [Desarrollo](development.md); las reglas de Invitaciones, en [ADR-0007](adr/0007-invitaciones-personal.md); el contrato, en [API](api/README.md); y el diseño incremental, en [Modelo de dominio](domain-model.md). Siguen fuera de alcance bootstrap productivo de ADMIN, retirada de permisos, categorías/equipos, APIs de incidencias, flujos S3 reales, SMTP externo, despliegue y CI/CD.
