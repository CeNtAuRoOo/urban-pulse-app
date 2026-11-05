# Architecture Decision Records (ADR)

Cada ADR explica el **contexto**, las alternativas, la **decisión** y sus consecuencias. Se distingue **decisión de diseño aceptada** de **funcionalidad implementada**: los ADR del dominio v1 no acreditan entidades, migraciones ni endpoints existentes.

## Estados

- **Propuesto:** pendiente de aprobación del equipo.
- **Aceptado:** criterio vigente para el diseño; puede requerir concretar detalles en la implementación.
- **Sustituido:** reemplazado por una nueva decisión, con referencia al ADR sucesor.

## Índice

| ID                                         | Decisión                                                              | Estado                                                     |
| ------------------------------------------ | --------------------------------------------------------------------- | ---------------------------------------------------------- |
| [0001](0001-postgresql.md)                 | PostgreSQL como base de datos principal                               | Aceptado                                                   |
| [0002](0002-neon-postgresql.md)            | Neon como proveedor PostgreSQL                                        | Aceptado                                                   |
| [0003](0003-expo-react-native-web.md)      | Frontend universal Expo / React Native Web                            | Aceptado                                                   |
| [0004](0004-neon-object-storage.md)        | Fotografías en Neon Object Storage                                    | Aceptado                                                   |
| [0005](0005-jwt-spring-security.md)        | Autenticación JWT y autorización Spring Security                      | Aceptado                                                   |
| [0006](0006-flyway.md)                     | Migraciones PostgreSQL con Flyway                                     | Aceptado                                                   |
| [0007](0007-invitaciones-personal.md)      | Invitaciones para personal, incluida incorporación a cuenta existente | **Aceptado e implementado**; SMTP local y canje verificado |
| [0008](0008-frontend-spa-nginx.md)         | Exportación web SPA y Nginx independiente                             | Aceptado; imagen de producción pendiente de validación     |
| [0009](0009-rbac-departamentos-equipos.md) | Roles globales/departamentales, coordinadores y equipos               | Roles y pertenencias implementados; equipos pendientes     |
| [0010](0010-ciclo-vida-y-asignaciones.md)  | Estados de incidencias, derivación y asignaciones de trabajo          | Aceptado como diseño; transiciones finas pendientes        |
| [0011](0011-identificadores-uuidv7.md)     | UUIDv7 como identificador base                                        | UUIDv7 para identidad, invitaciones y ambos catálogos      |

| [0012](0012-gestion-usuarios-perfiles-y-administracion.md) | Perfiles y administración de usuarios; protección del último ADMIN | Aceptado e implementado |
| [0013](0013-pertenencias-usuarios-departamentos.md) | Pertenencias activas/históricas, altas y bajas internas | Aceptado e implementado |
| [0014](0014-almacenamiento-evidencias.md) | Utilizar Neon Object Storage para almacenar las evidencias |

**Al implementar cada tag**, verificar que el [modelo de dominio base](../domain-model.md), su contrato OpenAPI y los ADR afectados continúan siendo coherentes. Si una decisión cambia materialmente, actualizarla con trazabilidad o crear un ADR sucesor; no modificar decisiones en silencio.

La adopción de Object Storage y su conectividad están comprobadas; la gestión real de fotografías no lo está. Las entidades `User`, `Role`, `UserRole` y `EmailVerification` del núcleo de autenticación están implementadas; **Department, DepartmentMembership, StaffInvitation, AuditEvent y Category se incluyen en la V1 consolidada; las demás entidades siguen pendientes**. JWT, BCrypt, validación de CITIZEN y protección de rutas de auth están implementados en el backend, sin despliegue de la V1 consolidada en Neon confirmado.
**Ampliación 28/10/2026:** el ADR-0005 recoge la verificación de correo mediante JWT limitado y código HMAC de ocho dígitos; el ADR-0006 registra la ampliación de V1 previa a la primera aplicación. Mailpit es una herramienta de desarrollo, no proveedor SMTP de producción.
Ampliación 04/11/2026: el ADR-0012 documenta la gestión de evidencias fotográficas, incluyendo la separación entre metadatos en PostgreSQL y contenido binario en Neon Object Storage, la identificación mediante UUIDv7, la validación y sanitización de imágenes y la estrategia compensatoria ante fallos de persistencia. La implementación inicial está desarrollada en feature/modulo-evidencia; la relación con Incident y las pruebas de integración siguen pendientes.
