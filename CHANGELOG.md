# Changelog

Registro de cambios relevantes de UrbanPulse. Se seguirá [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) como formato de referencia y, cuando existan versiones distribuidas, versionado semántico cuando corresponda.

## [Unreleased]

### Added

- Estructura base de Spring Boot (Java 25, Maven, paquetes por capas y clase principal).
- Configuración inicial para PostgreSQL/Neon, JPA/Hibernate, Flyway y Spring Security (sin API funcional).
- Frontend universal Expo SDK 57, React Native, React Native Web, TypeScript y Expo Router, con pantalla inicial.
- ESLint para Expo, configuración de TypeScript, recursos base y lockfile de npm.
- Dockerfile de desarrollo Expo Web (puerto 7777) y Dockerfile multietapa previsto para web SPA/Nginx.
- Documentación de instalación, desarrollo, arquitectura y despliegue actualizada; ADR sobre exportación SPA y Nginx.

### Changed

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
- Pendientes: pruebas Android/iOS en dispositivo, imagen Nginx de producción, backend/Neon, endpoints y pruebas funcionales.

> Las entradas describen la propuesta de cambios para el próximo PR; no indican una versión liberada ni un despliegue realizado.
