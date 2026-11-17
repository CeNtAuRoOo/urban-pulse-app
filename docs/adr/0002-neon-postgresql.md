# ADR-0002: Neon como proveedor de PostgreSQL

- **Estado:** Aceptado para PostgreSQL de producción; política local evolucionada por [ADR-0016](0016-ci-postgresql-local.md)
- **Fecha:** 2026-10-11
- **Ámbito:** UrbanPulse

## Evolución del desarrollo local (14/11/2026)

[ADR-0016](0016-ci-postgresql-local.md) sustituye la política original de desarrollo: PostgreSQL 18 local en el único Docker Compose y Neon PostgreSQL exclusivamente para producción. Neon Object Storage mantiene recursos de desarrollo y producción como decisión independiente. CI usa PostgreSQL efímero sin credenciales remotas. Las secciones de contexto, decisión y aprovisionamiento siguientes conservan los hechos de octubre; no son instrucciones operativas actuales de desarrollo.

## Contexto

El proyecto universitario necesita un PostgreSQL remoto con coste inicial nulo, sin configurar un servicio propio en Compose para desarrollo. La actividad será esporádica y el sistema deberá poder conectarse desde Spring Boot.

## Opciones consideradas

1. **Neon:** PostgreSQL administrado con cómputo que escala a cero y plan gratuito condicionado a cuotas.
2. **Supabase:** PostgreSQL administrado; posible pausa de proyectos gratuitos por inactividad.
3. **Aiven:** PostgreSQL administrado; otras restricciones del plan gratuito.
4. **PostgreSQL en Docker:** útil para pruebas locales, pero no se adopta como servicio permanente del Compose base.

## Decisión

Utilizar un proyecto PostgreSQL administrado en Neon. Los clientes web y móvil **no** se conectarán a PostgreSQL; todas las consultas y cambios pasarán por Spring Boot. La cadena JDBC y las credenciales se proporcionarán mediante variables de entorno sin almacenarlas en Git.

## Consecuencias

**Positivas:** administración simplificada, aislamiento del backend y coste inicial bajo; suspensión/reanudación automática.

**Riesgos:** latencia de arranque después de inactividad, límites de almacenamiento/cómputo/conexiones y dependencia del proveedor. El backend debe manejar desconexiones de forma robusta. El grupo revisará periódicamente las cuotas vigentes.

## Aprovisionamiento y verificación (15/10/2026)

- Proyecto Neon **`urban-pulse-app`** en **AWS Frankfurt (`aws-eu-central-1`)**, plan gratuito, PostgreSQL **18**.
- Base de datos **`urban-pulse`** en dos ramas Neon: **`production`** (principal) y **`development`** (derivada de `production`). Las ramas de Neon no son ramas Git.
- Roles PostgreSQL `owner` (administración) y `backend` (aplicación). Se comprobó que `backend` puede conectar y crear objetos en el esquema `public`, pero no crear bases de datos ni roles ni pertenece a `neon_superuser`.
- La conexión JDBC del backend con `development` se ha verificado mediante `NeonConnectivityIT`, y ambos servicios arrancan con `docker compose up --build`. La prueba depende de los endpoints inyectados: los nombres de base de datos y rol, idénticos en las dos ramas, no bastan para identificar `development`.
- Flyway está habilitado. En `development` existe `flyway_schema_history` tras el arranque; **en aquella fecha aún no había migraciones de negocio ni tablas del dominio**. En `production` todavía no se han creado tablas.
- `compose.yaml` utiliza `backend/.env.development` (ignorado por Git). Las credenciales y endpoints se inyectan mediante el entorno, no se fijan en los Dockerfile ni en el código cliente.

## Consideraciones pendientes

La V1 con Contexto Urbano requiere PostGIS. En un destino nuevo hay que comprobar que la extensión esté disponible y que Flyway pueda instalarla o utilizarla ya instalada. La conectividad histórica de Neon no acredita el despliegue de V1. [ADR-0018](0018-catalogo-espacial.md).

La V1 consolidada se valida en PostgreSQL local/efímero (ADR-0006). Su despliegue controlado en Neon pertenece a producción, con respaldo, revisión de historial y permisos; no se acredita por las pruebas locales. Quedan políticas de backups/recuperación y, cuando corresponda, separación de roles de migración/ejecución. Las cuotas y condiciones del proveedor se revisan al preparar el despliegue.
