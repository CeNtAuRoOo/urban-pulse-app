## Título

ADR-016: CI y PostgreSQL local para desarrollo con Docker Compose

## Estado

Aceptado e implementado

## Fecha

2026-11-14

## Ámbito

desarrollo y validación automática; despliegue productivo fuera de alcance

## Contexto

Los desarrolladores necesitan ensayar migraciones y pruebas sin interferir en el historial de una base compartida.

## Opciones consideradas

Una BD remota compartida mezcla los historiales. Testcontainers aísla las suites. Un único Compose local permite además desarrollar y probar el circuito HTTP y correo.

## Decisión

Desarrollo usa PostgreSQL local y Mailpit. Esta decisión evoluciona el uso inicial de Neon descrito en [ADR-002](ADR-002-neon-postgresql.md). El Compose fija JDBC y carga el dataset tras Flyway. PostgreSQL usa tmpfs y las cachés Maven y Node persisten. La CI automatiza Maven con Testcontainers, frontend, OpenAPI/Compose y k6 offline.

## Consecuencias

Separar datos locales y producción evita conceder acceso SQL remoto a CI. Se consume memoria local y se pierde la BD al detener PostgreSQL. El bucket sigue siendo externo. [Operación local](../development.md).
