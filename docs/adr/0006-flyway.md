# ADR-0006: Migraciones PostgreSQL con Flyway

- **Estado:** Aceptado
- **Fecha:** 2026-10-11
- **Ámbito:** UrbanPulse

## Contexto

El esquema relacional de UrbanPulse evolucionará por entregas y varios desarrolladores. Los cambios deben poder reproducirse de forma ordenada y auditable desde el repositorio.

## Opciones consideradas

1. Flyway mediante migraciones SQL versionadas.
2. Liquibase.
3. Creación/alteración automática mediante Hibernate `ddl-auto=update`.
4. Scripts manuales fuera del control de versiones.

## Decisión

Utilizar Flyway y almacenar scripts en `backend/src/main/resources/db/migration/`, con nombres del tipo `V1__create_initial_schema.sql`. Configurar Hibernate con `spring.jpa.hibernate.ddl-auto=validate` una vez exista el esquema. Hibernate valida; Flyway crea y modifica. Los scripts aplicados no se reescriben: los cambios posteriores se introducen con una nueva versión.

## Consecuencias

**Positivas:** trazabilidad, repetibilidad y sincronía entre cambios de código y esquema.

**Costes:** disciplina de migraciones compatibles y precaución con operaciones destructivas. La aplicación puede no arrancar si la base de datos no está configurada o si el esquema no coincide.

## Consideraciones pendientes

Definir estrategia de conexión para migraciones en Neon, permisos, entornos de ensayo y pruebas automatizadas. No ejecutar todavía Flyway contra Neon: el backend aún carece de código y primera migración.
