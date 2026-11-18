## Título

ADR-011: Identificadores UUIDv7 en el modelo del dominio

## Estado

Aceptado. Aplicado a las entidades de negocio que generan identificadores UUID con Hibernate. El catálogo de activos conserva su identificador numérico local ([ADR-018](ADR-018-catalogo-espacial.md)).

## Fecha

2026-10-18

## Ámbito

Identidad de entidades y persistencia PostgreSQL

## Contexto

UrbanPulse expone recursos REST y persiste relaciones entre incidencias, usuarios, asignaciones y evidencias. Se precisa un identificador opaco, estable, utilizable desde Java y PostgreSQL, sin duplicar claves internas/públicas injustificadamente.

## Opciones consideradas

1. `BIGINT`/secuencia: pequeño y eficiente, pero secuencial y dependiente de generación centralizada.
2. UUIDv4: descentralizado y opaco, pero aleatorio en índices.
3. **UUIDv7**: 128 bits con ordenación aproximadamente temporal, adecuado como PK general (**elegida**).
4. Clave interna `BIGINT` más identificador público.

## Decisión

Utilizar **UUIDv7** como PK de entidades con identidad propia: `UUID` en Java, `uuid` en PostgreSQL 18 y `string`/`format: uuid` en OpenAPI. Excepciones: `Role.code` es código estable (`RoleCode`) y `UserRole` puede tener PK compuesta (`user_id`, `role_code`). Los códigos externos no reemplazan a UUID internos.

**La generación se hará en Java mediante el generador de Hibernate** `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)` y `@GeneratedValue`, para las entidades JPA pertinentes. No se utilizará indiscriminadamente `GenerationType.UUID` sin fijar la versión. Las migraciones Flyway crearán columnas PostgreSQL `uuid` y PK/FK y Hibernate generará los identificadores de nuevas entidades del backend. No añadir un generador alternativo sin necesidad.

PostgreSQL 18 también soporta `uuidv7()` como alternativa, pero no se adopta como generador ordinario de entidades JPA en esta fase. Las fechas `createdAt` se conservan donde forman parte del modelo; Category no incorpora fechas internas y el UUIDv7 nunca constituye control de seguridad ni de autorización.

## Consecuencias

**Ventajas:** identificadores estables sin clave duplicada; generación coherente con el proceso JPA; menor aleatoriedad de inserción respecto a UUIDv4.

**Costes:** 16 bytes por identificador, índices mayores que `BIGINT`, inferencia temporal aproximada y necesidad de probar la estrategia de generación del Hibernate real. `VERSION_7` puede marcarse como API incubadora en algunas versiones de Hibernate; verificar su compatibilidad y versionado al implementar.

[Referencia relacionada](../domain-model.md).
