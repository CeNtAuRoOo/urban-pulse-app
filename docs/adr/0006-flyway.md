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

## Configuración comprobada (15/10/2026)

`spring.flyway.enabled=true` y `spring.flyway.locations=classpath:db/migration` están configurados; el backend conecta a la rama Neon `development` a través de JDBC directo. El rol `backend` tiene permisos para crear objetos en `public`.

Flyway ya ha creado su tabla de control `flyway_schema_history` en `development`, aunque **no existe ningún script de migración de dominio** (la carpeta solo contiene `.gitkeep`). Esta tabla técnica no implica que se haya diseñado o creado el esquema de UrbanPulse. La prueba de conectividad deshabilita Flyway y la validación de Hibernate **solo durante ese test**; la configuración normal sigue activa.

## Consideraciones pendientes

Crear `V1__create_initial_schema.sql`, probar las migraciones en `development` y aplicar las aprobadas posteriormente a `production` mediante conexión directa. Evitar ejecutar migraciones compartidas desde pruebas automatizadas ordinarias; valorar un rol exclusivo de migración y ramas o bases efímeras para CI. No modificar migraciones que ya hayan sido aplicadas.
