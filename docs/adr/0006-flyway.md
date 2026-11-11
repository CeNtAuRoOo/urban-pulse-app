# ADR-0006: Migraciones de PostgreSQL mediante Flyway

- **Estado:** Aceptado e implementado; consolidación previa al primer despliegue, 11/11/2026.
- **Alcance:** esquema PostgreSQL 18, aislamiento de pruebas y evolución explícita.

## Contexto y decisión

Flyway gestiona SQL auditable en `backend/src/main/resources/db/migration/`. Hibernate utiliza `spring.jpa.hibernate.ddl-auto=validate`; `spring.sql.init.mode=never` evita otra inicialización. No se usa ddl-auto=update. Flyway forma parte del arranque y puede modificar la base configurada; no es una comprobación inocua de conectividad.

El responsable del proyecto establece que todas las bases objetivo están vacías y ninguna ha ejecutado Flyway. Bajo esa premisa se consolida excepcionalmente **una sola V1**, antes de su primera aplicación. Se integran las restricciones finales directamente en CREATE TABLE; no se mantienen versiones incrementales ejecutables anteriores. No se consulta ni modifica una base compartida para realizar esta consolidación.

Una migración ya aplicada **no se edita**, ni se repara su checksum para ocultar diferencias. Tras aplicar esta V1, cualquier cambio necesita una nueva migración compatible. Si aparece un destino con historial previo, esta consolidación no sirve como ruta de actualización: debe diseñarse y revisarse una transición específica sin sobrescribir datos.

## Esquema inicial vigente

[V1\_\_initial_schema.sql](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) contiene **14 tablas de dominio**, aparte de flyway_schema_history:

- Identidad: users, roles, user_roles y email_verifications.
- Organización: departments, department_memberships y staff_invitations.
- Auditoría y catálogo: audit_events y categories.
- Incidencias: incidents, status_changes, incident_reviews y priority_decisions.
- Evidencias: attachments.

Solo se siembran los cinco roles legítimos. No se crean usuarios, ADMIN, departamentos ni categorías ficticias. Se conservan UUIDv7, claves naturales/compuestas, fechas timestamptz, auditoría JSONB, índices normalizados y unicidad parcial de pertenencias activas. Las FK históricas no tienen cascadas de borrado.

Las restricciones finales de revisión exigen motivo en REJECT y REQUEST_INFORMATION; VALIDATE lo permite opcional. Los motivos presentes y la justificación de prioridad tienen 3–2000 puntos Unicode normalizados y no blancos. StatusChange conserva null/blancos y límites 2000/5000; las reglas por transición pertenecen al futuro servicio de Tramitación.

attachments nace con incident_id NOT NULL, autor obligatorio, FK RESTRICT, clave de objeto única, tamaños 1–10485760, JPEG/PNG, nombre saneado y fecha de baja posterior al alta. El índice parcial por incidencia/created_at DESC/id DESC respalda el listado de activas. El límite de cinco se coordina en el servicio mediante bloqueo pesimista del padre.

## Validación y evolución

El perfil postgres-it aplica V1 en PostgreSQL 18 efímero con Testcontainers y Hibernate validate, comprueba historial de una migración, inventario, ausencia de semillas, índices, restricciones, relaciones y comportamiento de los módulos. Las pruebas históricas de actualización incremental se sustituyen por verificaciones del esquema final limpio; se conserva la cobertura de filas inválidas. [Comandos y resultados](../development.md#verificación-del-backend).

Las comprobaciones históricas JDBC/HeadBucket de Neon no acreditan aplicación del esquema consolidado. No se han ejecutado migraciones remotas en esta revisión. Siguen fuera de este alcance CI/CD, rol migrador dedicado y políticas operativas de retención y backups.

## Antecedentes

Las entregas anteriores construyeron migraciones separadas para identidad/catálogos, Incidencias, restricciones correctivas y evidencias. Las consolidaciones previas de 02–03/11/2026 eliminaron semillas ficticias. La decisión de 11/11/2026 sustituye esa cadena antes de su primera aplicación y conserva las restricciones finales, no el mecanismo incremental histórico.
