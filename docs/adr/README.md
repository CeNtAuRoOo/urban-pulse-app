# Architecture Decision Records (ADR)

Cada ADR explica el **contexto**, las alternativas, la **decisión** y sus consecuencias. Se distingue **decisión de diseño aceptada** de **funcionalidad implementada**: los ADR del dominio v1 no acreditan entidades, migraciones ni endpoints existentes.

## Estados

- **Propuesto:** pendiente de aprobación del equipo.
- **Aceptado:** criterio vigente para el diseño; puede requerir concretar detalles en la implementación.
- **Sustituido:** reemplazado por una nueva decisión, con referencia al ADR sucesor.

## Índice

| ID                                                         | Decisión                                                              | Estado                                                                            |
| ---------------------------------------------------------- | --------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| [0001](0001-postgresql.md)                                 | PostgreSQL como base de datos principal                               | Aceptado                                                                          |
| [0002](0002-neon-postgresql.md)                            | Neon PostgreSQL para producción (evolución en ADR-0016)               | Aceptado                                                                          |
| [0003](0003-expo-react-native-web.md)                      | Frontend universal Expo / React Native Web                            | Aceptado                                                                          |
| [0004](0004-neon-object-storage.md)                        | Fotografías en Neon Object Storage                                    | Aceptado                                                                          |
| [0005](0005-jwt-spring-security.md)                        | Autenticación JWT y autorización Spring Security                      | Aceptado                                                                          |
| [0006](0006-flyway.md)                                     | Migraciones PostgreSQL con Flyway                                     | Aceptado                                                                          |
| [0007](0007-invitaciones-personal.md)                      | Invitaciones para personal, incluida incorporación a cuenta existente | **Aceptado e implementado**; SMTP local y canje verificado                        |
| [0008](0008-frontend-spa-nginx.md)                         | Exportación web SPA y Nginx independiente                             | Aceptado; imagen de producción pendiente de validación                            |
| [0009](0009-rbac-departamentos-equipos.md)                 | Roles globales/departamentales, coordinadores y equipos               | Roles y pertenencias implementados; equipos pendientes                            |
| [0010](0010-ciclo-vida-y-asignaciones.md)                  | Estados de incidencias, derivación y asignaciones de trabajo          | Aceptado como diseño; transiciones finas pendientes                               |
| [0011](0011-identificadores-uuidv7.md)                     | UUIDv7 como identificador base                                        | UUIDv7 para identidad, invitaciones, catálogos, incidencias y evidencias          |
| [0012](0012-gestion-usuarios-perfiles-y-administracion.md) | Perfiles y administración de usuarios; protección del último ADMIN    | Aceptado e implementado                                                           |
| [0013](0013-pertenencias-usuarios-departamentos.md)        | Pertenencias activas/históricas, altas y bajas internas               | Aceptado e implementado                                                           |
| [0014](0014-incidencias-concurrencia-historial.md)         | Corrección de incidencias con precondición y auditoría pública        | Aceptado e implementado                                                           |
| [0015](0015-almacenamiento-evidencias.md)                  | Almacenamiento externo y gestión de evidencias fotográficas           | **Aceptado e implementado** (FK a incidencia y suites de integración verificadas) |
| [0016](0016-ci-postgresql-local.md)                        | CI y PostgreSQL local para desarrollo con Docker Compose              | Aceptado e implementado; sin CD                                                   |
| [0017](0017-mapas-provinciales-privacidad.md)              | Mapas provinciales, publicación, privacidad y agrupaciones            | Implementado; títulos aprobados pendientes de Tramitación    |

**Al implementar cada tag**, verificar que el [modelo de dominio base](../domain-model.md), su contrato OpenAPI y los ADR afectados continúan siendo coherentes. Si una decisión cambia materialmente, actualizarla con trazabilidad o crear un ADR sucesor; no modificar decisiones en silencio.

La adopción de Object Storage y su conectividad están comprobadas; la gestión real de fotografías también está implementada. Las entidades `User`, `Role`, `UserRole` y `EmailVerification` del núcleo de autenticación están implementadas; **V1 consolida 16 tablas de identidad, organización, auditoría, catálogos, incidencias, tramitación, evidencias y duplicados, más funciones/índices de Mapas**. JWT, BCrypt, validación de CITIZEN y protección de rutas de auth están implementados en el backend, sin despliegue de la V1 consolidada en Neon confirmado.

**Ampliación 28/10/2026:** el ADR-0005 recoge la verificación de correo mediante JWT limitado y código HMAC de ocho dígitos; el ADR-0006 registra la ampliación de V1 previa a la primera aplicación. Mailpit es una herramienta de desarrollo, no proveedor SMTP de producción.

**Ampliación 04/11/2026:** el ADR-0015 documenta la gestión de evidencias fotográficas, incluyendo la separación entre metadatos en PostgreSQL y contenido binario en Neon Object Storage, la identificación mediante UUIDv7, la validación y sanitización de imágenes y la estrategia compensatoria ante fallos de persistencia. La implementación inicial se desarrolló en la rama histórica `feature/modulo-evidencia`; la corrección posterior parte de `feature/modulo-evidencia-fix`.

**Revisión 11/11/2026:** ADR-0015 concreta cinco rutas privadas, JPEG/PNG, 10 MiB, máximo cinco activas por incidencia, acceso compartido con Incidencias, borrado lógico y compensación. ADR-0006 documenta la consolidación predespliegue en una sola V1. Retención/purgado y permisos departamentales son futuros. Las pruebas aisladas se distinguen de Neon real en [Desarrollo](../development.md#verificación-del-backend).

**Integración anterior de Evidencias/CI, 16/11/2026:** se conserva ADR-0015 para Evidencias y ADR-0016 para CI; Mapas pasa a [ADR-0017](0017-mapas-provinciales-privacidad.md). El esquema se aplica como V1 consolidada más V5 geográfica, sin V4 adicional.

**Antecedente histórico: integración de Tramitación PR #24, 16/11/2026 (antes de la V1 única actual):** se conserva ADR-0017 de Mapas y se incorpora V2__processing_phase_one.sql. Secuencia desde vacío V1 → V2 → V5; el caso de V2 recibida después de V5 se prueba y documenta en [ADR-0006](0006-flyway.md#v2-recibida-después-de-v5).

**Integración actual (16/11/2026):** ADR-0006 define la única V1. Los párrafos previos fechados son antecedentes, no instrucciones para el esquema consolidado. [Trazabilidad](../integration-reconciliation.md).
