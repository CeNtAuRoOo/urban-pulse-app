# ADR-0006: Migraciones PostgreSQL con Flyway

- **Estado:** Aceptado
- **Fecha:** 2026-10-11
- **Actualización documental:** 2026-10-27
- **Ámbito:** UrbanPulse

## Contexto

El esquema relacional de UrbanPulse evolucionará por entregas, con varios desarrolladores. Los cambios deben poder reproducirse en orden y auditarse desde el repositorio. Existe ya un [modelo relacional lógico base](../domain-model.md), pero **no está validado entidad por entidad** ni justifica crear ahora todas las tablas.

## Opciones consideradas

1. Flyway con migraciones SQL versionadas.
2. Liquibase.
3. Creación/alteración automática con Hibernate `ddl-auto=update`.
4. Scripts manuales fuera del repositorio.

## Decisión

Utilizar **Flyway** en `backend/src/main/resources/db/migration/`. La primera migración funcional es `V1__create_authentication.sql` y crea **únicamente las tablas y restricciones necesarias para el primer alcance funcional realmente implementado**. Cada tag posterior añadirá migraciones propias si necesita ampliar el esquema.

Hibernate deberá conservar `spring.jpa.hibernate.ddl-auto=validate`: **Flyway crea/evoluciona el esquema; Hibernate valida las entidades presentes**. No editar migraciones ya aplicadas; ampliar con nuevos scripts de versión. Revisar FKs, índices, campos nullable, tablas intermedias, restricciones de unicidad y concurrencia **al implementar cada entidad**; contrastar siempre [diagrama relacional](../diagrams/relacional.drawio), [catálogo de clases](../diagrams/clases-simplificado.drawio), OpenAPI y ADR.

## Consecuencias

**Positivas:** trazabilidad y migraciones reproducibles, sin adelantar tablas ni imponer decisiones de campos todavía en revisión.

**Costes:** coordinar cambios de esquema entre ramas/tags compartidos, versiones de migraciones y compatibilidad de código. Las entidades de un tag no pueden depender de tablas no existentes; comprobar integridad y orden de migración.

## Configuración comprobada (15/10/2026)

`spring.flyway.enabled=true` y `spring.flyway.locations=classpath:db/migration` están configurados. El backend conecta con Neon `development` a través de JDBC directo; el rol `backend` puede crear objetos en el esquema `public`.

Flyway ya creó su tabla técnica `flyway_schema_history` en `development` (evidencia histórica). Ahora existe `V1__create_authentication.sql` para `users`, `roles`, `user_roles` y `email_verifications` y precarga cinco roles sin `scope`. **La V1 se ha comprobado con PostgreSQL 18 efímero y Hibernate `ddl-auto=validate`; no consta aplicada a Neon `development`.** La prueba de conectividad desactiva Flyway y Hibernate **solo en su contexto de test**; la aplicación ordinaria conserva su configuración.

## Consideraciones pendientes

Repetir pruebas de la V1 actual tras los últimos cambios y **aplicarla de forma controlada a `development`** antes del primer arranque ordinario de este backend, nunca experimentalmente a `production`. No modificar la V1 después de aplicarla en algún entorno: añadir V2 para cambios posteriores. Evaluar un rol exclusivo de migración, índices reales, política de cambios concurrentes y uso de bases/ramas efímeras para CI. No inventar tablas para todos los elementos del diagrama por aparecer en el borrador.

### Ampliación previa a la primera aplicación de V1 — 28/10/2026

La V1 `V1__create_authentication.sql` se amplió en la rama de autenticación **antes de aplicarse a Neon `development`** para incluir `email_verifications`, PK/FK compartida con `users`, restricciones sobre hash/caducidad e índices para barrido. Esto es una excepción válida **solo cuando la V1 original no está aplicada en la base de destino**. Flyway registra checksums: si alguna BD ya ejecutó una V1 anterior, no sustituirla silenciosamente ni alterar `flyway_schema_history`; crear una nueva migración compatible en su lugar. La V1 ampliada fue probada en PostgreSQL 18 efímero por Testcontainers; la aplicación sobre Neon compartido no está acreditada.

Las operaciones transaccionales de registro/confirmación/reenvío usan la tabla; la limpieza periódica borra solicitudes caducadas y cuentas no verificadas de más de 24 horas, con lotes y bloqueos. Esta limpieza de datos **no es una migración de esquema Flyway** y debe revisarse antes de agregar nuevas FK a `users`.
