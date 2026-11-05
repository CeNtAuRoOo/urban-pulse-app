# ADR-0011: Identificadores UUIDv7 en el modelo del dominio

- **Estado:** Aceptado; UUIDv7 implementado para User, Department, DepartmentMembership, StaffInvitation, AuditEvent, Category, Incident, StatusChange, IncidentReview y PriorityDecision; restantes tipos pendientes.
- **Fecha:** 2026-10-18.
- **Revisión:** 2026-11-03.
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

PostgreSQL 18 también soporta `uuidv7()` como alternativa, pero no se adopta como generador ordinario de entidades JPA en esta fase. Las fechas `createdAt` se conservan donde forman parte del modelo; Category no incorpora fechas internas y el UUIDv7 nunca constituye control de seguridad ni de autorización.

## Consecuencias

**Ventajas:** identificadores estables sin clave duplicada; generación coherente con el proceso JPA; menor aleatoriedad de inserción respecto a UUIDv4.

**Costes:** 16 bytes por identificador, índices mayores que `BIGINT`, inferencia temporal aproximada y necesidad de probar la estrategia de generación del Hibernate real. `VERSION_7` puede marcarse como API incubadora en algunas versiones de Hibernate; verificar su compatibilidad y versionado al implementar.

## Evidencias y alcance

Las suites PostgreSQL verifican persistencia y UUIDv7 de las entidades implementadas. Department y Category conservan también `uuidv7()` como default SQL para inserciones externas en V1. Category no añade código ni fechas; el UUID no sustituye la auditoría RF18 pendiente. Se conservan identidades, referencias e historial al desactivar o reactivar un catálogo.

[Resultados reproducibles](../development.md#verificación-del-backend), [migración vigente](0006-flyway.md#esquema-inicial-vigente) y [modelo de dominio](../domain-model.md). Los futuros tipos requieren revisar sus claves, restricciones y pruebas al implementarse; las verificaciones aisladas no acreditan despliegue en Neon.
