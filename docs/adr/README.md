# Architecture Decision Records (ADR)

Cada ADR explica el **contexto**, las alternativas, la **decisión** y sus consecuencias. Se distingue **decisión de diseño aceptada** de **funcionalidad implementada**: los ADR del dominio v1 no acreditan entidades, migraciones ni endpoints existentes.

## Estados

- **Propuesto:** pendiente de aprobación del equipo.
- **Aceptado:** criterio vigente para el diseño; puede requerir concretar detalles en la implementación.
- **Sustituido:** reemplazado por una nueva decisión, con referencia al ADR sucesor.

## Índice

| ID                                         | Decisión                                                              | Estado                                                 |
| ------------------------------------------ | --------------------------------------------------------------------- | ------------------------------------------------------ |
| [0001](0001-postgresql.md)                 | PostgreSQL como base de datos principal                               | Aceptado                                               |
| [0002](0002-neon-postgresql.md)            | Neon como proveedor PostgreSQL                                        | Aceptado                                               |
| [0003](0003-expo-react-native-web.md)      | Frontend universal Expo / React Native Web                            | Aceptado                                               |
| [0004](0004-neon-object-storage.md)        | Fotografías en Neon Object Storage                                    | Aceptado                                               |
| [0005](0005-jwt-spring-security.md)        | Autenticación JWT y autorización Spring Security                      | Aceptado                                               |
| [0006](0006-flyway.md)                     | Migraciones PostgreSQL con Flyway                                     | Aceptado                                               |
| [0007](0007-invitaciones-personal.md)      | Invitaciones para personal, incluida incorporación a cuenta existente | **Aceptado**; reglas operativas pendientes             |
| [0008](0008-frontend-spa-nginx.md)         | Exportación web SPA y Nginx independiente                             | Aceptado; imagen de producción pendiente de validación |
| [0009](0009-rbac-departamentos-equipos.md) | Roles globales/departamentales, coordinadores y equipos               | Aceptado como diseño                                   |
| [0010](0010-ciclo-vida-y-asignaciones.md)  | Estados de incidencias, derivación y asignaciones de trabajo          | Aceptado como diseño; transiciones finas pendientes    |
| [0011](0011-identificadores-uuidv7.md)     | UUIDv7 como identificador base                                        | Aceptado como diseño; mapeo JPA pendiente              |

**Al implementar cada tag**, verificar que el [modelo de dominio base](../domain-model.md), su contrato OpenAPI y los ADR afectados continúan siendo coherentes. Si una decisión cambia materialmente, actualizarla con trazabilidad o crear un ADR sucesor; no modificar decisiones en silencio.

La adopción de Object Storage y su conectividad están comprobadas; la gestión real de fotografías no lo está. Las entidades del modelo compartido **siguen sin estar implementadas**.
