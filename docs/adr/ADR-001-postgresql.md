## Título

ADR-001: PostgreSQL como base de datos principal

## Estado

Aceptado

## Fecha

2026-10-11

## Ámbito

UrbanPulse

## Contexto

UrbanPulse administra entidades relacionadas (usuarios, incidencias, asignaciones, cambios de estado, permisos y metadatos de evidencias). Se requieren integridad referencial, transacciones y consultas mediante filtros. El documento inicial prevé una base relacional.

## Opciones consideradas

1. PostgreSQL relacional.
2. MySQL/MariaDB relacional.
3. Base documental como MongoDB.

## Decisión

Utilizar PostgreSQL como almacén principal de datos estructurados. Spring Boot accederá mediante JDBC, JPA/Hibernate y repositorios. Los binarios de evidencias permanecerán fuera de la base de datos y en PostgreSQL se almacenarán sus metadatos y referencias.

## Consecuencias

**Positivas:** transacciones, integridad referencial, consultas SQL y soporte para índices y funciones espaciales. La migración V1 utiliza PostGIS para el catálogo de activos urbanos; Mapas mantiene su implementación independiente con tipos y funciones nativas de PostgreSQL.

**Costes:** diseño y evolución del esquema; migraciones y mantenimiento de índices. Elegir PostgreSQL no implica que todas las consultas sean realizadas exclusivamente por JPA.

[Referencia relacionada](../design-docs/DD-004-territorio.md).
