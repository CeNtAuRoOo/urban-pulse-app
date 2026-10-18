# ADR-0011: Identificadores UUIDv7 en el modelo del dominio

- **Estado:** Aceptado como criterio de diseño; generación y mapeo pendientes de prueba
- **Fecha:** 2026-10-18
- **Ámbito:** Identidad de entidades y persistencia PostgreSQL

## Contexto

UrbanPulse expone recursos REST y persiste relaciones entre incidencias, usuarios, asignaciones y evidencias. Se buscó un identificador opaco, estable, utilizable desde Java y PostgreSQL y que evite mantener dos identificadores por registro sin una necesidad justificada.

## Alternativas

1. `BIGINT`/secuencia: pequeño y eficiente, pero secuencial y dependiente de generación centralizada.
2. UUIDv4: descentralizado y opaco, pero aleatorio en índices.
3. **UUIDv7**: 128 bits con ordenación aproximadamente temporal, adecuado como PK general.
4. Clave interna `BIGINT` + identificador público: añade correspondencia sin necesidad funcional demostrada en esta fase.

## Decisión

Usar **UUIDv7** como identificador principal de entidades con identidad propia: Java `UUID`, PostgreSQL `uuid`, API como representación textual. En PostgreSQL 18 existe la función nativa `uuidv7()`. Como excepción, `Role.code` es una clave natural estable del catálogo (`RoleCode`), y `UserRole` puede tener PK compuesta por las claves vinculadas. Los códigos externos de activos (`externalId`) no reemplazan a la PK local y deben acompañarse de fuente y restricciones según su procedencia.

No elegir aún de forma definitiva si genera UUIDv7 el backend o PostgreSQL: hacerlo **al implementar el primer mapeo JPA** y comprobar de extremo a extremo el tipo, la versión generada y el comportamiento de inserción. `GenerationType.UUID` no debe suponerse equivalente a v7 sin verificarlo. UUIDv7 **no sustituye** `createdAt` ni constituye un mecanismo de seguridad/autorización.

## Consecuencias

**Ventajas:** identificadores estables y no secuenciales, sin doble clave de API/persistencia, soportados por Java y PostgreSQL.

**Costes:** 16 bytes por clave, con más espacio en índices y FK que `BIGINT`; permite inferir aproximadamente el tiempo de generación; hay que validar la estrategia de generación real con el stack adoptado.

## Verificaciones pendientes

- Definir generador único y coherencia Hibernate/Flyway.
- Evaluar índices, formatos en OpenAPI (`string`, `format: uuid`), claves compuestas, unicidad e integridad referencial por entidad.
- Confirmar funcionamiento en base PostgreSQL de pruebas aislada y posteriormente en Neon `development`; no modificar `production` durante esta fase documental.

**Estado de entrega:** diseño dibujado, no tabla ni migración aplicada.
