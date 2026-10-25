# Changelog

Registro de cambios relevantes de UrbanPulse. Se sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) como formato de referencia y, cuando existan versiones distribuidas, versionado semántico cuando corresponda.

## [Unreleased]

### Documentación de diseño — autenticación (25/10/2026; pendiente de implementar)

- **Ajuste posterior de diseño — roles:** se elimina el enum `RoleScope` y la columna `Role.scope`; el ámbito GLOBAL/DEPARTMENT se determina en `RoleCode` (`isGlobal`/`isDepartmental`) y se valida en asociaciones. Este cambio documental permanece pendiente de implementación. La actualización de ambos diagramas y regeneración del bundle/HTML se efectúan antes del PR.

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
- Pendientes: Android/iOS en dispositivo, imagen Nginx producción, tablas/migraciones de negocio, endpoints, subida/descarga evidencias, seguridad JWT y pruebas funcionales.

> La sección Unreleased documenta diseño/cambios preparados para revisión, **no** una versión liberada o funcionalidad desplegada. Hasta que se ejecute Codex y se pruebe el backend, la autenticación continúa **pendiente de implementar**.
