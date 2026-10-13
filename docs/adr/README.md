# Architecture Decision Records (ADR)

Cada ADR documenta una **decisión con impacto arquitectónico**. Se mantiene en el repositorio y se revisa cuando cambia el diseño.

## Estados

- **Propuesto**: sujeto a validación técnica o aprobación del equipo.
- **Aceptado**: decisión vigente.
- **Sustituido**: la decisión ha cambiado y se enlaza el ADR posterior.

## Decisiones

| ID | Título | Estado |
| --- | --- | --- |
| [0001](0001-postgresql.md) | PostgreSQL como base de datos principal | Aceptado |
| [0002](0002-neon-postgresql.md) | Neon como proveedor PostgreSQL | Aceptado |
| [0003](0003-expo-react-native-web.md) | Frontend universal con Expo y React Native Web | Aceptado |
| [0004](0004-neon-object-storage.md) | Evidencias en Neon Object Storage | Propuesto |
| [0005](0005-jwt-spring-security.md) | Autenticación JWT y autorización con Spring Security | Aceptado |
| [0006](0006-flyway.md) | Migraciones de PostgreSQL con Flyway | Aceptado |
| [0007](0007-invitaciones-personal.md) | Invitaciones controladas para personal municipal | Propuesto |

Los detalles no decididos se marcan expresamente; no deben confundirse con funcionalidades ya implementadas.
