# Changelog

## 2026-10-28 — Verificación de correo y transporte SMTP (implementación local)

- Implementados `POST /api/v1/auth/email-verifications/confirm` (`204`) y `POST /api/v1/auth/email-verifications/resend` (`202` si se solicita envío, `204` si ya está verificado, `410` si ha caducado la cuenta). El registro inicial sigue devolviendo `201 TokenDTO` con acceso limitado.
- Nueva entidad JPA `EmailVerification` y tabla `email_verifications` dentro de **Flyway V1 modificada antes del primer despliegue**; un código numérico ASCII de ocho dígitos, HMAC-SHA256 independiente de JWT, caducidad máxima de diez minutos y límite de cuenta no verificada de 24 horas.
- Acceso JWT limitado hasta comprobar `emailVerified` en PostgreSQL en cada petición: rutas de negocio devuelven `403 EMAIL_NOT_VERIFIED`; confirmación, reenvío y cambio de contraseña siguen habilitados. Tras verificar se reutiliza el mismo JWT.
- Infraestructura `EmailSender`/`SmtpEmailSender` con Spring Mail, emisión después del commit mediante evento transaccional, Mailpit en Docker Compose y timeouts SMTP de cinco segundos. Fallos SMTP no revierten registro/reenvío, pero el envío no queda garantizado ni dispone de outbox.
- Nuevos buckets de confirmación y reenvío en memoria, con expiración por política. Cuerpos de los cinco POST limitados a 16 KiB. Limpieza programada horaria, lotes acotados y `SKIP LOCKED` para códigos caducados y cuentas no verificadas de más de 24 h.
- Codex comunica `BUILD SUCCESS` con **55 pruebas: 35 unitarias/MVC y 20 PostgreSQL 18 efímero**, además de verificación aislada de SMTP Mailpit. No se ha ejecutado el backend real junto con Mailpit ni se ha aplicado la V1 sobre Neon.
- Aplazados el proveedor SMTP real/dominio, outbox y reintentos automáticos, recuperación de contraseña e invitaciones. Revisar la eliminación automática **antes** de añadir nuevas FKs hacia `users`.

Registro de cambios relevantes de UrbanPulse. Se sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) como formato de referencia y, cuando existan versiones distribuidas, versionado semántico cuando corresponda.

## [Unreleased]

### Implementación del tag Autenticación (revisión de documentación, 27/10/2026)

- Implementados `POST /api/v1/auth/register` (`201`), `POST /api/v1/auth/login` (`200`) y `POST /api/v1/users/me/password-changes` (`204`). Registro crea `CITIZEN` en una transacción, emite JWT y omite `Location` hasta disponer del GET de perfil.
- Persistencia mínima con `V1__create_authentication.sql`: tablas `users`, `roles`, `user_roles`; cinco roles sin `Role.scope`/`RoleScope`, unicidad normalizada de correo, preferencias `true`, `email_verified=false`, `token_version=0`, UUIDv7 con Hibernate.
- JWT HS256 firmado con secreto externo Base64 de ≥32 bytes, `iss`/`sub`/`iat`/`exp`/`ver` y duración de 7200 s; BCrypt coste 12 sin truncar contraseñas de más de 72 bytes UTF-8. Consulta de cuenta activa, rol base `CITIZEN`, `tokenVersion` y roles vigentes en cada petición protegida. Cambio de contraseña con bloqueo pesimista y revocación por versión.
- Protección de los tres cuerpos de autenticación a 16 KiB. Token buckets en memoria por instancia: registro/IP 5/300 s, login/IP 20/1200 s y login/email 5/300 s; máximo 10 000 claves, TTL derivado del mayor periodo de reposición y `429`/`Retry-After`. Sin confianza en `Forwarded` ni `X-Forwarded-For` de clientes.
- Errores `ProblemDetail`: `400` JSON no interpretable o cuerpo excesivo, `422` validación y propiedades desconocidas, `409` correo duplicado, `401` credenciales/JWT/cuenta/rol base inválidos, `403` acceso denegado, `429` abuso, `503` indisponibilidad tipada de PostgreSQL y `500` errores internos; logs técnicos selectivos sin datos sensibles.
- Tests unitarios y MVC con simulaciones de seguridad y fallos de base de datos; integración PostgreSQL 18 efímero para Flyway, Hibernate, UUIDv7, permisos, rollback y concurrencia. **Evidencia histórica de Codex (25/10/2026): 16 pruebas unitarias/MVC y 7 de integración superadas. El último refactor requiere una nueva ejecución verificable de `mvn clean verify -Ppostgres-it` antes de confirmar el código.**
- **Fuera del alcance:** refresh, verificación de correo y recuperación por email, primer ADMIN, invitaciones, departamentos/equipos, `GET fields`, frontend y despliegue. No consta V1 aplicada a la rama Neon `development`; no se aplicará sobre `production` para probar.

### Documentación de diseño — autenticación (25/10/2026; histórico)

- **Ajuste posterior de diseño — roles:** se elimina el enum `RoleScope` y la columna `Role.scope`; el ámbito GLOBAL/DEPARTMENT se determina en `RoleCode` (`isGlobal`/`isDepartmental`) y se valida en asociaciones. El cambio ya está reflejado en `RoleCode` y en el esquema V1. La actualización de ambos diagramas y regeneración del bundle/HTML se efectúan antes del PR.

- Revisión **solo documental y de contrato de diseño**, sin introducir entidades JPA, endpoints ejecutables, configuración JWT activa, scripts Flyway funcionales ni tests nuevos.
- Revisión del contrato OpenAPI modular a `0.3.0` de diseño: registro ciudadano `201 TokenDTO` con JWT, corrección de `401` y retirada de `404`/`409` para credenciales incorrectas en login, y operación de cambio autenticado `POST /users/me/password-changes` con `204`.
- Acuerdo de JWT Bearer de 7200 segundos sin refresh, claims `sub`/`iat`/`exp`/`iss`/`ver`, verificación de cuenta activa, `tokenVersion` y roles actuales desde PostgreSQL en cada petición protegida. El cambio de contraseña incrementará `tokenVersion` en la misma transacción para invalidar todos los tokens previos.
- Diseño de `User.emailVerified=false` sin verificación obligatoria todavía, `User.tokenVersion=0`, preferencias de notificación `true` por defecto y `CITIZEN` como rol global base incluso para personal municipal.
- Restricción de invitaciones globales a `OPERATOR` y `ADMIN`; se conserva la incorporación a cuentas existentes pero no se implementa en esta rama.
- Decisión de generar UUIDv7 en Hibernate con `UuidGenerator.Style.VERSION_7` (por comprobar en JPA), utilizar BCrypt, responder `400` para JSON ilegible/`422` para datos inválidos y configurar rate limiting en memoria por instancia para registro/login.
- Se añade `docs/auth-design.md` y se actualizan ADR-0005/0007/0009/0011, documentación de arquitectura, dominio y diseño API. **Pendientes de sincronización manual**: `.drawio` y exportaciones de identidad. **Pendientes de regeneración mediante Redocly**: `docs/api/openapi.yaml` y `docs/api/openapi.html` desde el modular.
- Mejoras expresamente aplazadas: refresh tokens, verificación bloqueante de correo mediante email, recuperación por correo, procedimiento manual del primer `ADMIN` y estudio de selección dinámica `fields`/anotaciones por propiedad.

### Added

- **Modelo de dominio v1 documental:** `docs/diagrams/relacional.drawio` (cuatro páginas de PK/FK y relaciones) y `docs/diagrams/clases-simplificado.drawio` (catálogo UML simplificado de clases Java, enums, `@Embeddable` y DTO, sin relaciones); exportaciones SVG junto a editables.
- Fuente editable `docs/diagrams/c4.drawio` para los C4 de contexto y contenedores.
- [`docs/domain-model.md`](docs/domain-model.md): alcance, conceptos, reglas, limitaciones y **revisión incremental obligatoria del dominio al implementar cada tag**, junto a OpenAPI.
- ADR-0009 para roles globales/departamentales, coordinadores y equipos; ADR-0010 para dos fases de asignación y trazabilidad; ADR-0011 para UUIDv7 como identificador base.
- Primera propuesta de contrato REST **OpenAPI 3.0.3**, modular por 16 tags (`docs/api/openapi/`), con esquemas y componentes compartidos, pendiente de revisión por tag sin endpoints implementados.
- Flujo documental **design-first** para revisar y acordar cada tag antes de desarrollar operaciones y DTO.
- Integración del backend de desarrollo con Neon PostgreSQL 18 (JDBC/HikariCP, JPA/Hibernate y Flyway) y Neon Object Storage (AWS SDK for Java 2.x 2.56.0, BOM, `StorageProperties`, `StorageConfig`).
- Prueba manual `NeonConnectivityIT` para JDBC y `HeadBucket` del bucket privado, sin modificar datos de negocio.
- Configuración separada por entorno: `backend/.env.development` (privado) y secretos externos para despliegues de producción.
- Estructura base Spring Boot (Java 25, Maven, paquetes por capas y clase principal).
- Configuración inicial para PostgreSQL/Neon, JPA/Hibernate, Flyway y Spring Security (sin API funcional).
- Frontend universal Expo SDK 57, React Native, React Native Web, TypeScript y Expo Router con pantalla inicial.
- ESLint, TypeScript, recursos de frontend y lockfile npm.
- Dockerfile de desarrollo Expo Web (puerto 7777) y Dockerfile multietapa SPA/Nginx previsto.
- Documentación de instalación, desarrollo, arquitectura, despliegue y ADR.

### Changed

- Ajuste de coherencia de OpenAPI `0.2.0`: roles globales/departamentales acotados en esquemas, invitaciones validadas por ámbito, UUID internos diferenciados de IDs externos, propiedades opcionales `StatusChangeDTO` y endpoints IA prematuros fuera del contrato activo; bundle/HTML sincronizados **en la etapa anterior**.
- Sincronizados el contrato OpenAPI `0.2.0` y dominio v1: roles por ámbito, dos fases de asignación, invitaciones, duplicados y UUID; flujo unificado por tag.
- ADR-0007 de invitaciones municipales pasó a **Aceptado** como mecanismo, contemplando cuentas existentes y roles departamentales; su política concreta quedó para el tag correspondiente.
- ADR-0005, índice de ADR, guías de arquitectura, diagramas, API y contribución alineados con revisión incremental del dominio por tag.
- Estado `VALIDATED` puede existir sin departamento; `ASSIGNED` se desglosa en `ASSIGNED_TO_DEPARTMENT` y `WORK_ASSIGNED` (sin implementación).
- ADR-0004 pasó a **Aceptado** tras aprovisionar bucket privado y superar `HeadBucket`; siguen pendientes operaciones de fotografías.
- Documentación de Neon, ramas `development`/`production`, rol `backend`, variables y política de tests externos bajo demanda.
- Repositorio GitHub público; instrucciones de clonación y contribución actualizadas.
- Guías y ADR ajustados a estructura real y comandos verificados.

### Fixed

- Referencias documentales que presentaban ER/clases como pendientes e invitaciones/Neon Object Storage como propuestas pese a decisiones anteriores.
- Configuración TypeScript/JSX y ESLint de frontend; comprobaciones locales superadas.
- Convenciones de indentación JavaScript/TypeScript en `.editorconfig`.

### Removed

- Componentes de demostración innecesarios de la plantilla Expo.
- Migración Flyway `V1__create_initial_schema.sql` de relleno (sustituida por `.gitkeep` hasta definir el esquema).

### Verification

- **Revisión documental de auth (25/10/2026):** análisis de fuentes GitHub y preparación de contratos/ADR de diseño. **No se han ejecutado tests de backend, migraciones Flyway ni pruebas de seguridad funcional**. El OpenAPI fuente se validará/regenerará con Redocly y los diagramas se editarán manualmente antes de confirmar la sincronización.
- **PR documental dominio–OpenAPI (18/10/2026):** revisión de XML de diagramas, páginas y contenido; revisión documental/enlaces. Sin tests funcionales ni migraciones.
- Frontend (13/10/2026): dependencias compatibles, Expo Doctor 21/21, TypeScript, lint, ejecución web, exportación `dist/` y Compose de desarrollo.
- Backend/Neon (15/10/2026): `docker compose up --build` y `docker compose run --rm backend mvn -B -Dtest=NeonConnectivityIT test` superados (JDBC + `HeadBucket`).
- Pendientes: repetir la suite auth tras los últimos cambios, arranque controlado con Flyway V1 en Neon `development`, Android/iOS en dispositivo, imagen Nginx producción, tablas de los demás tags, sus endpoints y subida/descarga de evidencias.

> La sección Unreleased registra tanto el diseño histórico como el código actual. **Implementado en el repositorio no significa desplegado**: falta comprobar el arranque sobre Neon `development` y la versión final de pruebas, además de completar el resto de tags.
