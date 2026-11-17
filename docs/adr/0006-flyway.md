# ADR-0006: Migraciones PostgreSQL mediante Flyway

- Estado vigente: aceptado; consolidación autorizada el 16/11/2026 antes de la primera aplicación en destinos.

## Decisión vigente de integración

Esquema inicial consolidado V1: 16 tablas de dominio, cinco roles y siete funciones geográficas; sin semillas de usuarios ni catálogos. Solo para bases vacías sin historial Flyway previo, conforme a la premisa del responsable. No acredita aplicación en Neon.

La única migración ejecutable es [V1__initial_schema.sql](../../backend/src/main/resources/db/migration/V1__initial_schema.sql). Integra directamente AWAITING_INFORMATION, snapshot obligatorio/coherente de revisión, VALIDATE sin motivo, respuestas con FK compuesta, vínculos canónicos únicos de Duplicados y geografía de Mapas. No hay CHECK NOT VALID ni datos ficticios; solo cinco roles legítimos. Hibernate mantiene validate.

El generador comprueba o actualiza exclusivamente la región delimitada de geografía en V1; las tablas no se reescriben. Se preservan las siete funciones, tres índices, 12.339 vértices y checksum IGN. Solo se prueba en PostgreSQL 18 efímero. Después de aplicar V1, su contenido es inmutable y cualquier evolución requiere una migración posterior compatible.

La premisa de destinos sin migraciones procede del responsable; no equivale a una garantía universal. Si un destino tiene otro historial o datos conservables, detener el arranque y diseñar una transición específica con respaldo. No utilizar repair, baseline, outOfOrder ni alterar checksums para aplicar esta consolidación.

Las pruebas incrementales anteriores se transforman en instalación final, restricciones, idempotencia, conservación de fotografías/revisiones válidas y reconstrucción de índices con datos importados en un esquema aislado. [Trazabilidad y validación](../integration-reconciliation.md).

## Archivo histórico anterior a la integración

**Todo lo que sigue documenta las decisiones anteriores en A `27357ea1` y C `50d7ed7`. Sus secuencias V1/V2/V5 y procedimientos no son ejecutables ni instrucciones para el esquema vigente. B `b08ad608` añadió V3 de Duplicados. Se conserva el texto para explicar la evolución, sin duplicar SQL histórico fuera de Git.**

### Texto anterior conservado

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

Las restricciones históricas de V1 exigen motivo en REJECT y REQUEST_INFORMATION; V1 permitía motivo opcional en VALIDATE, prohibido para nuevas filas desde V2. Los motivos presentes y la justificación de prioridad tienen 3–2000 puntos Unicode normalizados y no blancos. StatusChange conserva null/blancos y límites 2000/5000; las reglas de revisión pertenecen a ProcessingService; las de ejecución permanecen futuras.

attachments nace con incident_id NOT NULL, autor obligatorio, FK RESTRICT, clave de objeto única, tamaños 1–10485760, JPEG/PNG, nombre saneado y fecha de baja posterior al alta. El índice parcial por incidencia/created_at DESC/id DESC respalda el listado de activas. El límite de cinco se coordina en el servicio mediante bloqueo pesimista del padre.

## Validación y evolución

El perfil postgres-it aplica V1, V2 y V5 en PostgreSQL 18 efímero con Testcontainers y Hibernate validate, comprueba el historial ordenado de las tres migraciones, inventario, ausencia de semillas, índices, restricciones, relaciones y comportamiento de los módulos. Las pruebas históricas de actualización incremental se sustituyen por verificaciones del esquema final limpio; se conserva la cobertura de filas inválidas. [Comandos y resultados](../development.md#verificación-del-backend).

Las comprobaciones históricas JDBC/HeadBucket de Neon no acreditan aplicación del esquema consolidado. No se han ejecutado migraciones remotas en esta revisión. La CI aislada se incorpora mediante [ADR-0016](0016-ci-postgresql-local.md). Siguen fuera de alcance CD, rol migrador dedicado y políticas operativas de retención y backups.

## Desarrollo paralelo y CI (14/11/2026)

Cada desarrollador utiliza PostgreSQL 18 en su propio Docker, con persistencia local. Cambiar de rama conserva el mismo volumen y su historial: revisar compatibilidad antes de arrancar y conservar los datos si se necesita una base nueva de prueba. El aislamiento entre compañeros no resuelve colisiones de versiones al integrar. GitHub Actions ejecuta `mvn -B -ntp clean verify -Ppostgres-it`: Surefire y trece suites Failsafe con PostgreSQL efímero, independiente de Compose. V1 permanece intacta. La secuencia vigente es V1 → V2 → V5. V5 permanece intacta y se prueba sobre V1/V2 con datos. No se crea V4 de Evidencias, ya consolidada en V1. Véase [ADR-0017](0017-mapas-provinciales-privacidad.md).

Coordinar versiones antes de integrar: una colisión V2/V2 solo se resuelve renumerando trabajo aún no publicado ni aplicado en bases compartidas y verificando el orden conjunto desde cero. Si una versión ya se aplicó en una base compartida, es inmutable: revisar los historiales y diseñar una reconciliación compatible con respaldo. No editar el historial, activar outOfOrder ni utilizar repair para ocultar conflictos. [Procedimiento completo y ciclo de vida](../development.md#3-ramas-y-migraciones-concurrentes).

## Antecedentes

Las entregas anteriores construyeron migraciones separadas para identidad/catálogos, Incidencias, restricciones correctivas y evidencias. Las consolidaciones previas de 02–03/11/2026 eliminaron semillas ficticias. La decisión de 11/11/2026 sustituye esa cadena antes de su primera aplicación y conserva las restricciones finales, no el mecanismo incremental histórico.

## Evolución V2 (15/11/2026)

V1 permanece intacta. V2\_\_processing_phase_one.sql incorpora estado de espera, snapshot de revisión y respuestas con FK compuesta/unicidad. Las filas históricas conservan snapshot desconocido (null); un CHECK NOT VALID exige valores coherentes en nuevas escrituras sin inventar historia ni eliminar motivos anteriores. ProcessingMigrationPostgresIT verifica V1 poblada con revisión y foto; las integraciones Spring verifican V1→V2→V5 y Hibernate validate. No se aplican migraciones remotas ni repair.

## V2 recibida después de V5

La secuencia nueva desde vacío es V1 → V2 → V5. Una base de la rama anterior puede
contener únicamente V1 → V5: V2 es una migración resuelta pero no aplicada inferior
a la versión actual. Con los valores por defecto, la validación y el arranque de
Flyway fallan; no se instala V2 automáticamente. No se debe arrancar la nueva
aplicación ignorando la validación: Hibernate y Tramitación necesitan el esquema V2.

- Para desarrollo desechable, crear una base local nueva y ejecutar la cadena
  completa; conservar la anterior si contiene datos necesarios. No borrar volúmenes
  compartidos ni suponer que cualquier base local es desechable.
- Para conservar datos de una base con **exactamente esta V1 consolidada y esta V5**,
  revisar historial/checksums y probar primero una copia aislada con respaldo
  recuperable. V2 no cambia coordenadas, funciones o índices geográficos; amplía
  estados y revisiones y crea respuestas. La prueba de integración ensaya una
  ejecución administrativa puntual de Flyway con `outOfOrder(true)` exclusivamente
  en Testcontainers. Conserva incidencias, revisiones históricas, metadatos de fotos,
  puntos públicos y checksums V1/V5. El historial instalado queda V1 → V5 → V2;
  el número mayor no sustituye a la comprobación de las tres versiones aplicadas.
- Cualquier aplicación a una base con datos conservables exige un procedimiento
  operativo separado: detener escritores, respaldo y restauración ensayada, artefacto
  revisado con solo las migraciones esperadas, ejecución única con esa opción local,
  comprobar historial/checksums/datos/privacidad y volver a validar con los valores
  por defecto antes de arrancar. No se añade la opción a Spring, Compose, CI, variables
  permanentes o configuración global. En esta integración no se ejecuta ese proceso
  sobre bases reales; únicamente se prueba el comportamiento en un contenedor.

No se usa `repair`, no se edita el historial ni se renumera o modifica una migración
aplicada. Un historial con las antiguas V2__incidents.sql/V3, V1 distinta o cualquier
otra divergencia queda fuera de este procedimiento y requiere su propia transición.
La prueba `ProcessingMigrationPostgresIT` verifica tanto el rechazo por defecto
como el ensayo puntual y la validación posterior sin outOfOrder.
