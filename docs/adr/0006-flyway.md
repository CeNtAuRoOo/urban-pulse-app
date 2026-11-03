# ADR-0006: Migraciones de PostgreSQL mediante Flyway

- **Estado:** Aceptado e implementado en el backend.
- **Alcance:** evolución explícita del esquema PostgreSQL, aislamiento de pruebas y migración segura entre entornos.

## Contexto

UrbanPulse utiliza PostgreSQL 18 y JPA/Hibernate. El esquema debe evolucionar con control de versiones, revisión de restricciones y una estrategia reproducible entre instancias y ambientes. `spring.jpa.hibernate.ddl-auto=update` no es aceptable para introducir cambios no controlados en Neon: no documentaría bien la historia ni permitiría revisar efectos antes de desplegar.

## Alternativas consideradas

Se estudiaron esquemas generados automáticamente por Hibernate, SQL aplicado manualmente y migraciones versionadas. Se adopta **Flyway**, con ficheros SQL auditables en `backend/src/main/resources/db/migration/`, historial de checksums y validación Hibernate del esquema resultante (`spring.jpa.hibernate.ddl-auto=validate`). La ejecución de Flyway forma parte del arranque configurado del backend y **puede modificar la base real** a la que apunten las variables; no es un mecanismo inocuo de conectividad.

## Decisión y configuración

- Cada cambio de esquema estable se incorpora como **nueva migración versionada**; revisar SQL, constraints, índices, compatibilidad y rollback antes de ejecutarlo.
- Mantener `ddl-auto=validate` para comprobar mapeos; `spring.sql.init.mode=never` evita mecanismos de creación paralelos no previstos.
- Separar conexiones y credenciales de `development` y `production`; nunca usar producción como base de pruebas. Conservar la identidad de la rama Neon, no inferirla por nombre de DB/rol/bucket compartidos.
- Ejecutar integración SQL y JPA con PostgreSQL 18 efímero a través de Testcontainers, sin utilizar una base compartida ni material sensible.
- Si una migración **ya** se aplicó en algún entorno, **no modificar su contenido ni editar directamente `flyway_schema_history`**. Diseñar una siguiente versión compatible para cambios posteriores.

## Esquema inicial vigente

`V1__initial_schema.sql` es la única migración activa. Crea `users`, `roles`, `user_roles`, `email_verifications`, `departments`, `department_memberships`, `staff_invitations`, `audit_events` y `categories`. Inicializa únicamente los cinco roles; no crea cuentas ADMIN ni catálogos ficticios.

Antes del primer despliegue funcional se consolidaron las migraciones de autenticación, las dos V2 desarrolladas en paralelo para Departamentos e Invitaciones y la V3 de Categorías. La decisión del 02/11/2026 eliminó las semillas departamentales; la ampliación del 03/11 conserva esa política para ambos catálogos. La premisa es un destino vacío, sin migraciones funcionales previas que deban conservarse. No autoriza modificar una base existente ni ejecutar `repair` para ocultar discrepancias.

Department conserva nombre/código únicos mediante índices `lower(...)`, CHECK de formato, descripción nullable, active y fechas `timestamptz`. `createdAt`/`updatedAt` se mantienen mediante callbacks JPA, no triggers para escrituras SQL externas. Category tiene nombre único `lower(name)` incluso inactivo, descripción nullable y active predeterminado true; no tiene código ni fechas. Sus CHECK preservan los límites Unicode, `Character.isWhitespace` y el recorte de `String.trim()` en inserciones SQL externas. Ambos catálogos usan UUIDv7 Hibernate y default SQL `uuidv7()`.

Se conservan las PK/FK y restricciones históricas de Invitaciones: email normalizado sin FK al destinatario, hash HMAC de 32 bytes único, coherencia rol/ámbito, fechas y actores de estados finales, pertenencias activas únicas e índices de consulta. Las referencias históricas utilizan `RESTRICT`; auditoría conserva detalles JSONB. No se crea FK a Incident ni tablas de módulos pendientes.

## Validación y consecuencias

El perfil `postgres-it` ejecuta las cinco suites de Autenticación, correo, Invitaciones, Departamentos y Categorías sobre PostgreSQL 18 efímero con Hibernate `validate`. Comprueba una sola versión en Flyway, nueve tablas funcionales, cinco roles y ausencia de semillas de catálogo. [Comandos y resultados](../development.md#verificación-del-backend).

La conectividad histórica JDBC/S3 con Neon no acredita la aplicación de la V1 consolidada. El primer despliegue requiere comprobar destino, historial, permisos, backup y plan de migración. Después de aplicar V1, cualquier evolución necesita una versión nueva coordinada. Siguen pendientes automatización CI/CD, rol migrador dedicado y políticas operativas de conservación; no se anticipan tablas por aparecer en los diagramas.
