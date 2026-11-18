# Architecture Decision Records (ADR)

Cada ADR explica el **contexto**, las alternativas, la **decisión** y sus consecuencias. Se distingue **decisión de diseño aceptada** de **funcionalidad implementada**: los ADR del dominio v1 no acreditan entidades, migraciones ni endpoints existentes.

## Estados

- **Propuesto:** pendiente de aprobación del equipo.
- **Aceptado:** criterio vigente para el diseño; puede requerir concretar detalles en la implementación.
- **Sustituido:** reemplazado por una nueva decisión, con referencia al ADR sucesor.

Utiliza la [plantilla ADR](adr-template.md) y conserva la fecha y el estado originales. Los detalles de implementación residen en los [DD](../design-docs/README.md) y el trabajo abierto en el [registro de pendientes](../pending.md). Una descripción retrospectiva no implica una nueva aprobación.

## Índice

| ID                                                           | Decisión                                                              | Estado                                                                            |
| ------------------------------------------------------------ | --------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| [001](ADR-001-postgresql.md)                                 | PostgreSQL como base de datos principal                               | Aceptado                                                                          |
| [002](ADR-002-neon-postgresql.md)                            | Neon PostgreSQL para producción (evolución en ADR-016)                | Aceptado                                                                          |
| [003](ADR-003-expo-react-native-web.md)                      | Frontend universal Expo / React Native Web                            | Aceptado                                                                          |
| [004](ADR-004-neon-object-storage.md)                        | Fotografías en Neon Object Storage                                    | Aceptado                                                                          |
| [005](ADR-005-jwt-spring-security.md)                        | Autenticación JWT y autorización Spring Security                      | Aceptado                                                                          |
| [006](ADR-006-flyway.md)                                     | Migraciones PostgreSQL con Flyway                                     | Aceptado                                                                          |
| [007](ADR-007-invitaciones-personal.md)                      | Invitaciones para personal, incluida incorporación a cuenta existente | **Aceptado e implementado**; SMTP local y canje verificado                        |
| [008](ADR-008-frontend-spa-nginx.md)                         | Exportación web SPA y Nginx independiente                             | Aceptado; imagen de producción pendiente de validación                            |
| [009](ADR-009-rbac-departamentos-equipos.md)                 | Roles globales/departamentales, coordinadores y equipos               | Roles y pertenencias implementados; equipos pendientes                            |
| [010](ADR-010-ciclo-vida-y-asignaciones.md)                  | Estados de incidencias, derivación y asignaciones de trabajo          | Aceptado como diseño; transiciones finas pendientes                               |
| [011](ADR-011-identificadores-uuidv7.md)                     | UUIDv7 como identificador base                                        | UUIDv7 para identidad, invitaciones, catálogos, incidencias y evidencias          |
| [012](ADR-012-gestion-usuarios-perfiles-y-administracion.md) | Perfiles y administración de usuarios; protección del último ADMIN    | Aceptado e implementado                                                           |
| [013](ADR-013-pertenencias-usuarios-departamentos.md)        | Pertenencias activas/históricas, altas y bajas internas               | Aceptado e implementado                                                           |
| [014](ADR-014-incidencias-concurrencia-historial.md)         | Corrección de incidencias con precondición y auditoría pública        | Aceptado e implementado                                                           |
| [015](ADR-015-almacenamiento-evidencias.md)                  | Almacenamiento externo y gestión de evidencias fotográficas           | **Aceptado e implementado** (FK a incidencia y suites de integración verificadas) |
| [016](ADR-016-ci-postgresql-local.md)                        | CI y PostgreSQL local para desarrollo con Docker Compose              | Aceptado e implementado; sin CD                                                   |
| [017](ADR-017-mapas-provinciales-privacidad.md)              | Mapas provinciales, publicación, privacidad y agrupaciones            | Implementado; títulos aprobados pendientes de Tramitación                         |
| [018](ADR-018-catalogo-espacial.md)                          | Catálogo de activos, PostGIS y contrato de lectura                    | Aceptado                                                                          |
