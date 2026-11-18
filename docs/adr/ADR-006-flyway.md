## Título

ADR-006: Migraciones PostgreSQL mediante Flyway

## Estado

Aceptado. La consolidación previa se autorizó el 16/11/2026.

## Fecha

No consta fecha original.

## Ámbito

Migraciones PostgreSQL mediante Flyway

## Contexto

El monolito necesita reproducir tablas, restricciones y funciones en PostgreSQL desde una base vacía y detectar diferencias de historial.

## Opciones consideradas

DDL automático de Hibernate, scripts manuales y migraciones SQL versionadas. La validación de mapeos complementa la aplicación ordenada de SQL.

## Decisión

Flyway instala el esquema. Hibernate usa validate. La V1 consolidada pertenece al arranque desde cero. El reordenamiento autorizado del bloque geográfico conserva su SQL y cambia el checksum. Las bases locales descartables con la V1 anterior se recrean.

## Consecuencias

Las migraciones aplicadas en destinos que deban conservarse requieren evolución compatible y respaldo. No se debe reparar el historial para ocultar diferencias ni habilitar outOfOrder para resolver colisiones entre ramas. [Desarrollo](../development.md) · [Despliegue](../deployment.md).
