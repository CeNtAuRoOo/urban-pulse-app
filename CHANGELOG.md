# Changelog

Registro de cambios relevantes de UrbanPulse. Se seguirá [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) como formato de referencia y, cuando existan versiones distribuidas, versionado semántico cuando corresponda.

## [Unreleased]

### Added

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

- ADR-0004 pasa a **Aceptado** tras aprovisionar el bucket privado y superar la prueba de acceso S3; permanecen pendientes las operaciones de fotografías.
- Documentación de Neon, ramas `development`/`production`, rol `backend`, configuración de variables y política de tests externos bajo demanda.

- Repositorio GitHub público; instrucciones de clonación y contribución actualizadas.
- Guías y ADR ajustados a la estructura efectivamente creada y a los comandos verificados.

### Fixed

- Configuración de TypeScript/JSX y ESLint del frontend; comprobaciones locales superadas.
- Convenciones de indentación de JavaScript/TypeScript en `.editorconfig`.

### Removed

- Componentes y recursos de demostración innecesarios de la plantilla Expo.
- Migración Flyway `V1__create_initial_schema.sql` de relleno (sustituida por `.gitkeep` hasta definir el esquema).

### Verification

- Frontend (13/10/2026): dependencias compatibles, `expo-doctor` 21/21, `tsc` y lint correctos, ejecución web, exportación `dist/`, y Compose de desarrollo.
- Backend/Neon (15/10/2026): `docker compose up --build` (frontend + backend) y `docker compose run --rm backend mvn -B -Dtest=NeonConnectivityIT test` superados (JDBC + `HeadBucket`).
- Pendientes: Android/iOS en dispositivo, imagen Nginx de producción, esquema y migraciones de negocio, endpoints, subida/descarga de evidencias y pruebas funcionales.

> Las entradas describen la propuesta de cambios para el próximo PR; no indican una versión liberada ni un despliegue realizado.
