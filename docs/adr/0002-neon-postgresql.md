# ADR-0002: Neon como proveedor de PostgreSQL

- **Estado:** Aceptado
- **Fecha:** 2026-10-11
- **Ámbito:** UrbanPulse

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
- Flyway está habilitado. En `development` existe `flyway_schema_history` tras el arranque; **todavía no hay migraciones de negocio ni tablas del dominio**. En `production` todavía no se han creado tablas.
- `compose.yaml` utiliza `backend/.env.development` (ignorado por Git). Las credenciales y endpoints se inyectan mediante el entorno, no se fijan en los Dockerfile ni en el código cliente.

## Consideraciones pendientes

Definir migraciones SQL reales y validarlas en `development` antes de aplicarlas en `production`; separar permisos de migración/ejecución cuando convenga; automatizar pruebas aisladas sin dependencias de la base compartida; preparar backups y recuperación. La protección automática de `production` no está disponible en el plan gratuito actual, por lo que la disciplina de despliegue es especialmente importante. Las cuotas y condiciones del plan se revisarán periódicamente.
