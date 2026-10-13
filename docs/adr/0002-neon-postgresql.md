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

## Consideraciones pendientes

Crear el proyecto Neon, seleccionar región, definir credenciales de aplicación de privilegio mínimo y confirmar cómo ejecutar migraciones. Valorar entornos independientes o ramas para pruebas, sin ejecutar migraciones experimentales sobre datos compartidos.
