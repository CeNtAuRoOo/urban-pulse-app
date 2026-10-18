# ADR-0006: Migraciones PostgreSQL con Flyway

- **Estado:** Aceptado
- **Fecha:** 2026-10-11
- **Actualización documental:** 2026-10-18
- **Ámbito:** UrbanPulse

## Contexto

El esquema relacional de UrbanPulse evolucionará por entregas, con varios desarrolladores. Los cambios deben poder reproducirse en orden y auditarse desde el repositorio. Existe ya un [modelo relacional lógico base](../domain-model.md), pero **no está validado entidad por entidad** ni justifica crear ahora todas las tablas.

## Opciones consideradas

1. Flyway con migraciones SQL versionadas.
2. Liquibase.
3. Creación/alteración automática con Hibernate `ddl-auto=update`.
4. Scripts manuales fuera del repositorio.

## Decisión

Utilizar **Flyway** en `backend/src/main/resources/db/migration/`. La primera migración funcional podrá seguir el patrón `V1__create_initial_schema.sql`, pero creará **únicamente las tablas y restricciones necesarias para el primer alcance funcional realmente implementado**. Cada tag posterior añadirá migraciones propias si necesita ampliar el esquema.

Hibernate deberá conservar `spring.jpa.hibernate.ddl-auto=validate`: **Flyway crea/evoluciona el esquema; Hibernate valida las entidades presentes**. No editar migraciones ya aplicadas; ampliar con nuevos scripts de versión. Revisar FKs, índices, campos nullable, tablas intermedias, restricciones de unicidad y concurrencia **al implementar cada entidad**; contrastar siempre [diagrama relacional](../diagrams/relacional.drawio), [catálogo de clases](../diagrams/clases-simplificado.drawio), OpenAPI y ADR.

## Consecuencias

**Positivas:** trazabilidad y migraciones reproducibles, sin adelantar tablas ni imponer decisiones de campos todavía en revisión.

**Costes:** coordinar cambios de esquema entre ramas/tags compartidos, versiones de migraciones y compatibilidad de código. Las entidades de un tag no pueden depender de tablas no existentes; comprobar integridad y orden de migración.

## Configuración comprobada (15/10/2026)

`spring.flyway.enabled=true` y `spring.flyway.locations=classpath:db/migration` están configurados. El backend conecta con Neon `development` a través de JDBC directo; el rol `backend` puede crear objetos en el esquema `public`.

Flyway ya creó su tabla técnica `flyway_schema_history` en `development`, **pero no hay scripts de migración del dominio**: la carpeta contiene `.gitkeep`. La prueba de conectividad desactiva Flyway y la validación Hibernate **solo en su contexto de test**; la aplicación ordinaria conserva su configuración.

## Consideraciones pendientes

Diseñar el primer script que corresponda al tag que inicie la persistencia; probar en una BD aislada y aplicar después a `development`, nunca experimentalmente en `production`. Evaluar un rol exclusivo de migración, índices reales, política de cambios concurrentes y uso de bases/ramas efímeras para CI. No inventar tablas para todos los elementos del diagrama por aparecer en el borrador.
