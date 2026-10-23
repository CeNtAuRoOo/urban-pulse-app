# Changelog

Registro de cambios relevantes de UrbanPulse. Se sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) como formato de referencia y, cuando existan versiones distribuidas, versionado semántico cuando corresponda.

## [Unreleased]

### Added

- **Modelo de dominio v1 documental:** `docs/diagrams/relacional.drawio` (cuatro páginas de PK/FK y relaciones) y `docs/diagrams/clases-simplificado.drawio` (catálogo UML simplificado de clases Java, enums, `@Embeddable` y DTO, sin relaciones); sus exportaciones SVG se versionan junto con los editables.
- Fuente editable `docs/diagrams/c4.drawio` para los C4 de contexto y contenedores ya documentados.
- [`docs/domain-model.md`](docs/domain-model.md): alcance, conceptos, reglas, limitaciones y **revisión incremental obligatoria del dominio al implementar cada tag**, junto a OpenAPI.
- ADR-0009 para roles globales/departamentales, coordinadores y equipos; ADR-0010 para las dos fases de asignación y trazabilidad; ADR-0011 para UUIDv7 como estrategia base de identificadores.
- Primera propuesta de contrato REST **OpenAPI 3.0.3**, modular por 16 tags (`docs/api/openapi/`), con esquemas y componentes compartidos. Contrato **pendiente de revisión por tag**, sin endpoints implementados.
- Flujo documental **design-first** para revisar y acordar cada tag antes de desarrollar sus operaciones y DTOs.
- Integración del backend de desarrollo con Neon PostgreSQL 18 (JDBC/HikariCP, JPA/Hibernate y Flyway) y Neon Object Storage (AWS SDK for Java 2.x 2.56.0, BOM, `StorageProperties` y `StorageConfig`).
- Prueba manual `NeonConnectivityIT` para verificar conexión JDBC y acceso `HeadBucket` al bucket privado, sin modificar datos de negocio.
- Configuración separada por entorno: `backend/.env.development` (privado) y secretos externos para despliegues de producción.
- Estructura base de Spring Boot (Java 25, Maven, paquetes por capas y clase principal).
- Configuración inicial para PostgreSQL/Neon, JPA/Hibernate, Flyway y Spring Security (sin API funcional).
- Frontend universal Expo SDK 57, React Native, React Native Web, TypeScript y Expo Router, con pantalla inicial.
- ESLint para Expo, configuración de TypeScript, recursos base y lockfile de npm.
- Dockerfile de desarrollo Expo Web (puerto 7777) y Dockerfile multietapa previsto para web SPA/Nginx.
- Documentación de instalación, desarrollo, arquitectura y despliegue actualizada; ADR sobre exportación SPA y Nginx.

### Changed

- Ajuste de coherencia de OpenAPI 0.2.0: roles globales/departamentales acotados en esquemas, invitaciones validadas por ámbito, UUID internos diferenciados de IDs externos, propiedades opcionales de `StatusChangeDTO` y endpoints IA prematuros fuera del contrato activo; bundle/HTML sincronizados.

- Sincronizados el contrato OpenAPI `0.2.0` y el modelo de dominio v1: roles por ámbito, dos fases de asignación, invitaciones, duplicados y UUID; flujo unificado por tag.

- ADR-0007 de invitaciones municipales pasa a **Aceptado** como mecanismo de incorporación, contemplando cuentas ya existentes y roles departamentales; su política concreta queda para el tag correspondiente.
- ADR-0005, índice de ADR, guías de arquitectura, diagramas, API y contribución alineados con el flujo de verificación del dominio **por tag**. En el PR previo el contrato OpenAPI no se modificaba; esta entrada describe ese hito anterior.
- El estado inicial de una incidencia puede permanecer en `VALIDATED`; el modelo desglosa `ASSIGNED` en `ASSIGNED_TO_DEPARTMENT` y `WORK_ASSIGNED` (decisión documentada, aún sin implementación).
- ADR-0004 pasa a **Aceptado** tras aprovisionar el bucket privado y superar la prueba de acceso S3; permanecen pendientes las operaciones de fotografías.
- Documentación de Neon, ramas `development`/`production`, rol `backend`, configuración de variables y política de tests externos bajo demanda.
- Repositorio GitHub público; instrucciones de clonación y contribución actualizadas.
- Guías y ADR ajustados a la estructura efectivamente creada y a los comandos verificados.

### Fixed

- Referencias documentales que presentaban el ER/clases como pendientes y las invitaciones/Neon Object Storage como propuestas aunque la decisión de diseño ya estaba acordada.
- Configuración de TypeScript/JSX y ESLint del frontend; comprobaciones locales superadas.
- Convenciones de indentación de JavaScript/TypeScript en `.editorconfig`.

### Removed

- Componentes y recursos de demostración innecesarios de la plantilla Expo.
- Migración Flyway `V1__create_initial_schema.sql` de relleno (sustituida por `.gitkeep` hasta definir el esquema).

### Verification

- **PR de dominio/documentación (18/10/2026):** inspección de XML de los dos diagramas, sus páginas y contenido; revisión documental y de enlaces en los archivos modificados. **No** se han ejecutado tests funcionales de frontend/backend ni migraciones para este cambio.
- Frontend (13/10/2026): dependencias compatibles, `expo-doctor` 21/21, `tsc` y lint correctos, ejecución web, exportación `dist/`, y Compose de desarrollo.
- Backend/Neon (15/10/2026): `docker compose up --build` (frontend + backend) y `docker compose run --rm backend mvn -B -Dtest=NeonConnectivityIT test` superados (JDBC + `HeadBucket`).
- Pendientes: Android/iOS en dispositivo, imagen Nginx de producción, esquema y migraciones de negocio, endpoints, subida/descarga de evidencias y pruebas funcionales.

> La sección Unreleased documenta cambios preparados para revisión en PR; no indica una versión liberada ni un despliegue realizado. **Los diagramas son una primera versión de diseño, no un esquema aprobado por completo ni una implementación.**
