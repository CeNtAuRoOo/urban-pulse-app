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

## Estado real de las migraciones

| Fichero | Alcance | Observación |
| --- | --- | --- |
| `V1__create_authentication.sql` | `users`, `roles`, `user_roles`, `email_verifications`, checks, índices y cinco roles | Amplió verificación de correo **antes** de la primera aplicación prevista sobre Neon; comprobada en PostgreSQL efímero |
| `V2__create_staff_invitations.sql` | `departments`, `department_memberships`, `staff_invitations`, `audit_events` | Añadida **después de la reorganización de directorios** al implementar Invitaciones; V1 permaneció intacta |

La primera comprobación histórica de Neon en octubre utilizó `NeonConnectivityIT` para JDBC y S3 `HeadBucket`, con Flyway y validación Hibernate desactivadas **solo en ese test**. No prueba la aplicación de V1/V2 sobre Neon `development`. Ante un despliegue real, preparar copias de seguridad, plan de migración/reversión, permisos del rol migrador, ventana de intervención y comprobación de las conexiones. No experimentar en `production`.

### Ampliación V1 — verificación de correo (28/10/2026)

V1 se completó para incluir `email_verifications`, vinculada al usuario con PK/FK, HMAC del código, fechas/caducidad, restricciones e índices de barrido **antes de su primer despliegue previsto**. El hecho de ser correcto mientras V1 no haya sido aplicada **no** permite sobreescribirla más tarde si algún entorno conserva otro checksum: habría que añadir una nueva migración de corrección. `EmailVerificationCleanup` borra códigos vencidos y cuentas que sigan sin verificar pasadas 24 horas, en transacciones con lotes y `SKIP LOCKED`; **la limpieza de datos no es una migración Flyway** y su impacto sobre nuevas FK debe revisarse.

### V2 — Invitaciones y organización mínima (31/10/2026)

V2 añade cuatro tablas, sin datos de usuarios ADMIN ni departamentos ficticios para producción. `departments` soporta el ámbito administrativo; `department_memberships` conserva `joined_at`/`left_at`, restringe roles departamentales, y un índice parcial único impide pertenencias activas duplicadas. `staff_invitations` exige email normalizado sin FK a `users`, `code_hash bytea` de **32 bytes** único, rol/ámbito coherentes, fechas consistentes, pares fecha-actor para canje/revocación, exclusión de estados finales e índices para filtros/listado. `audit_events` almacena actor, acción, recurso, JSONB de detalles y marcas de tiempo, con campos opcionales.

Las FK históricas a usuarios/departamentos emplean `ON DELETE RESTRICT` para preservar trazabilidad; no se usan cascadas destructivas. Los actores de invitaciones están verificados, por lo que la limpieza de cuentas **no verificadas** no consume invitaciones ni destruye su auditoría. Antes de producción hace falta definir política RGPD de conservación y posible anonimización/retirada controlada de estos registros.

## Validación aislada y reproducibilidad

`backend/pom.xml` define el perfil `postgres-it` para ejecutar explícitamente `AuthenticationPostgresIT`, `EmailVerificationPostgresIT` y `StaffInvitationPostgresIT`, cada una con PostgreSQL 18 efímero. `NeonConnectivityIT` en `shared/storage/integration/` **no** se incluye; se invoca bajo demanda con credenciales controladas contra `development`.

Desde `backend/`:

```bash
mvn -B -ntp clean verify -Ppostgres-it
```

**Validación completada (informe del 30/10/2026):** **90 pruebas superadas** (48 unitarias/MVC + 42 IT), Flyway V1/V2 y Hibernate `validate` correctos en PostgreSQL 18 aislado, más un flujo HTTP satisfactorio del JAR con PostgreSQL `tmpfs` y Mailpit. La reorganización de directorios es **anterior a Invitaciones y al commit de pruebas** en Git. Este resultado no acredita todavía la aplicación de V1/V2 a Neon. Los comandos y resultados históricos están en [Desarrollo](../development.md). Los archivos del runner HTTP descrito en el informe no figuran en el estado local adjunto.

## Consecuencias y pendientes

Flyway reduce la deriva del esquema y permite construir entornos reproducibles, pero exige disciplina para gestionar checksums, compatibilidad de migraciones, cambios de restricciones e índices y posibilidad de reversión. Todavía quedan por decidir el rol exclusivo de migración, ejecución en CI/CD, plan de backup/restauración, índice/métricas de bases reales, política para datos históricos restringidos y migraciones por tags futuros.

No se crearán por adelantado las tablas del resto de dominios de incidencias, equipos, contexto o analítica solo porque figuren en las vistas ER. Véase [modelo de dominio](../domain-model.md), [arquitectura](../architecture.md), [ADR-0007](0007-invitaciones-personal.md) y [desarrollo](../development.md).
