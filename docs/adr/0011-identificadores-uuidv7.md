# ADR-0011: Identificadores UUIDv7 en el modelo del dominio

- **Estado:** Aceptado; UUIDv7 implementado y probado para `User` y `Department`; futuros tipos pendientes.
- **Fecha:** 2026-10-18.
- **Revisión:** 2026-10-30.
- **Ámbito:** Identidad de entidades y persistencia PostgreSQL.

## Contexto

UrbanPulse expone recursos REST y persiste relaciones entre incidencias, usuarios, asignaciones y evidencias. Se precisa un identificador opaco, estable, utilizable desde Java y PostgreSQL, sin duplicar claves internas/públicas injustificadamente.

## Alternativas

1. `BIGINT`/secuencia: pequeño y eficiente, pero secuencial y dependiente de generación centralizada.
2. UUIDv4: descentralizado y opaco, pero aleatorio en índices.
3. **UUIDv7**: 128 bits con ordenación aproximadamente temporal, adecuado como PK general (**elegida**).
4. Clave interna `BIGINT` más identificador público.

## Decisión

Utilizar **UUIDv7** como PK de entidades con identidad propia: `UUID` en Java, `uuid` en PostgreSQL 18 y `string`/`format: uuid` en OpenAPI. Excepciones: `Role.code` es código estable (`RoleCode`) y `UserRole` puede tener PK compuesta (`user_id`, `role_code`). Los códigos externos no reemplazan a UUID internos.

**La generación se hará en Java mediante el generador de Hibernate** `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)` y `@GeneratedValue`, para las entidades JPA pertinentes. No se utilizará indiscriminadamente `GenerationType.UUID` sin fijar la versión. Las migraciones Flyway crearán columnas PostgreSQL `uuid` y PK/FK y Hibernate generará los identificadores de nuevas entidades del backend. No añadir un generador alternativo sin necesidad.

PostgreSQL 18 también soporta `uuidv7()` como alternativa, pero no se adopta como generador ordinario de entidades JPA en esta fase. Las fechas `createdAt` siguen existiendo y el UUIDv7 nunca constituye control de seguridad ni de autorización.

## Consecuencias

**Ventajas:** identificadores estables sin clave duplicada; generación coherente con el proceso JPA; menor aleatoriedad de inserción respecto a UUIDv4.

**Costes:** 16 bytes por identificador, índices mayores que `BIGINT`, inferencia temporal aproximada y necesidad de probar la estrategia de generación del Hibernate real. `VERSION_7` puede marcarse como API incubadora en algunas versiones de Hibernate; verificar su compatibilidad y versionado al implementar.

## Evidencias de implementación y pendientes

- **Probado en `AuthenticationPostgresIT` con PostgreSQL 18 efímero:** inserción/recuperación de `User` mediante JPA y UUID con `id.version() == 7`; Flyway y validación Hibernate.
- Verificar FKs, restricciones, índices, relaciones compuestas y tratamiento de identidades ya persistidas.
- Ejecutar pruebas de persistencia en PostgreSQL efímera, y posteriormente comprobar `development` sin ejecutar migraciones experimentales en `production`.

**Estado real:** generado mediante Hibernate para `User` y `Department`, comprobado en las suites PostgreSQL aisladas; la V1 no consta aplicada en Neon `development`, y los demás tipos del modelo siguen por implementar.

### Revisión Department — 30/10/2026

Department aplica esta decisión: V2 conserva la tabla y semillas, Hibernate VERSION_7 genera los UUID; consultas con EMAIL_VERIFIED y escrituras con ADMIN global. Se mantienen las relaciones con pertenencias/equipos como diseño. Detalles y evidencias en [informe de cierre](../departments-finalization.md).
