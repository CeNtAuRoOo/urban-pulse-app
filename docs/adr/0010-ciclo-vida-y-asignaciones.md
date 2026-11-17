# ADR-0010: Ciclo de vida de incidencias y separación de asignaciones

- **Estado:** Aceptado; revisión e información implementadas, ejecución futura
- **Fecha:** 2026-10-18
- **Ámbito:** Incidencias, tramitación municipal, auditoría

## Contexto

El enunciado utiliza `ASSIGNED` e introduce `Assignment` como vínculo temporal con departamento, equipo o técnico. Para el flujo municipal acordado, es útil distinguir la **derivación a un departamento** (operador central) de la **asignación del trabajo concreto** (coordinador departamental). No son el mismo evento ni deben modelarse como dos `Assignment` ordinarias.

## Alternativas consideradas

1. Un único estado `ASSIGNED` y dos registros consecutivos de `Assignment`, uno para departamento y otro para equipo/técnico.
2. Dos fases de estado y `Assignment` solo para trabajo concreto (elegida).
3. Utilizar exclusivamente `StatusChange` como historial de destinatarios (descartada porque hay reasignaciones sin cambio de estado).

## Decisión

El enum de diseño `IncidentStatus` contiene:

`REPORTED`, `AWAITING_INFORMATION`, `VALIDATED`, `REJECTED`, `ASSIGNED_TO_DEPARTMENT`, `WORK_ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`.

- El operador municipal puede validar un reporte y **dejarlo en `VALIDATED`** hasta determinar un departamento. El departamento responsable actual se referencia mediante `Incident.assignedDepartment` (nullable antes de la derivación).
- Al derivarlo al departamento se registra el cambio hacia `ASSIGNED_TO_DEPARTMENT`. La información anterior/nueva de departamento debe auditarse incluso si una transferencia posterior no cambia el estado.
- Un coordinador autorizado designa un **equipo o técnico individual**. Se crea una `Assignment` y la incidencia pasa a `WORK_ASSIGNED`. En una asignación de trabajo válida hay **exactamente uno** de `team` o `technician`; equipo y técnico deben corresponder al departamento responsable.
- `Assignment` guarda actor, motivo, asignación, aceptación (`acceptedBy`, `acceptedAt`) y fin (`endedAt`). Se conserva el historial; solo puede existir **una asignación activa por incidencia**. La aceptación no equivale automáticamente a `IN_PROGRESS`.
- `StatusChange` registra transiciones del ciclo de vida; `Assignment` cambios de destinatario de trabajo; `AuditEvent` registra acciones importantes adicionales, como transferencias y cambios de permisos.
- Reasignar el equipo/técnico puede conservar `WORK_ASSIGNED` o `IN_PROGRESS`: finalizar la anterior y crear la siguiente **sin inventar una transición de estado**. Un cambio de departamento con asignación activa requiere una regla explícita de finalización del trabajo y nueva distribución; inicialmente se contempla volver a `ASSIGNED_TO_DEPARTMENT`.
- `DuplicateSuggestionDTO` ofrece candidatos automáticos sin tomar decisiones. Solo una confirmación humana crea `DuplicateLink`; ambos reportes conservan identidad, evidencias e historial.
- `PriorityDecision` registra `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` y su justificación; `IncidentReview` registra las decisiones de validación, rechazo o solicitud de información.

## Consecuencias

**Ventajas:** se pueden medir tiempos entre derivación y distribución mediante `StatusChange`, y tiempos de atención/aceptación mediante `Assignment`; se conserva auditoría de reasignaciones sin confundir estado y personal responsable.

**Costes:** se necesita lógica transaccional para la consistencia entre `Incident.status`, `StatusChange` y `Assignment`; restricciones sobre asignaciones simultáneas y comprobaciones de permisos por departamento.

## Pendiente para fases posteriores de Tramitación

- Transiciones de ejecución, reapertura directa desde RESOLVED y cierre definitivo; revisión e información ya implementadas.
- Qué sucede si un departamento no tiene coordinador o si el trabajo queda sin técnico disponible.
- Índice único parcial para una asignación activa, concurrencia, reversión ante error y criterios de cierre/reasignación.
- Visibilidad de los eventos de ejecución; las proyecciones de revisión e información ya separan datos internos y públicos.
- El OpenAPI modular vigente `0.8.0` conserva derivación departamental y asignación de trabajo como futuras; concretar su matriz al implementarlas.

**Estado de entrega a 15/11/2026:** revisión, respuestas y prioridad manual operativas mediante processing y V2; Assignment, derivación y ejecución siguen como diseño.

## Revisión del 05/11/2026: núcleo y reapertura

El alta crea Incident en REPORTED y AuditEvent INCIDENT_CREATED en una sola transacción; no crea StatusChange inicial. Las correcciones efectivas generan INCIDENT_UPDATED, nunca revisiones o decisiones artificiales. HistoryEventDTO es una proyección pública paginada de esos eventos; deberá integrar las transiciones futuras sin duplicar hechos.

Se elimina el estado persistente de reapertura del diseño anterior. Una incidencia RESOLVED podrá reabrirse en el futuro mediante transición directa, motivada, a ASSIGNED_TO_DEPARTMENT o WORK_ASSIGNED si conserva una asignación válida y coherente. CLOSED es terminal: un nuevo aviso requiere otra incidencia. No se implementan transiciones en esta entrega.

Decisión histórica del 05/11/2026, sustituida el 15/11/2026: REQUEST_INFORMATION ahora cambia a AWAITING_INFORMATION y exige motivo; REJECT también lo exige. VALIDATE prohíbe reason y exige categoría activa explícita. Prioridad es null hasta una decisión justificada. El permiso provisional de OPERATOR/ADMIN para corregir avisos de terceros solo en REPORTED requiere revisión de seguridad/negocio por ámbito y campo antes de extender el flujo municipal. Los permisos departamentales no otorgan lectura global.

[Estado, modelo objetivo y pendientes](../incidents-implementation-roadmap.md); [control de concurrencia](0014-incidencias-concurrencia-historial.md).

## Invariantes estructurales verificadas

V1 integra los CHECK finales de revisión/prioridad bajo la consolidación previa a su primera aplicación. Java normaliza los motivos de revisión y la justificación de prioridad con trim, rechaza blancos, NUL y sustitutos aislados, y cuenta puntos Unicode. SQL exige su forma ya normalizada y limita longitudes. PriorityDecision requiere prioridad, origen MANUAL/ASSISTED, responsable y justificación de 3–2000; no actualiza Incident.priority por sí sola.

StatusChange requiere estados no nulos y distintos, nunca origen CLOSED; no existe null → REPORTED ni REOPENED. Java y varchar limitan reason a 2000 y resolutionNotes a 5000 puntos Unicode; ambos son opcionales y conservan formato/blancos. El futuro servicio exigirá texto no blanco para las transiciones que lo necesiten y notas al resolver, autorizará actor/ámbito, comprobará estado actual, asignación y fechas, y escribirá todo atómicamente. Esas reglas de negocio no se atribuyen al constructor ni al CHECK estructural. Las entidades no habilitan endpoints ni ejecutan la máquina de estados.

El estado REOPENED del enunciado original se adapta deliberadamente a la reapertura directa desde RESOLVED acordada por el equipo; CLOSED permanece terminal.

## Tramitación: primera fase (15/11/2026)

`processing/{api,dto,model,repository,service}` coordina revisiones, respuestas y prioridades en el mismo monolito. `IncidentReview`, `PriorityDecision` y `StatusChange` se trasladan desde `incidents` conservando tablas; `IncidentInformationResponse` es una entidad propia, distinta de los comentarios futuros. Incidencias conserva el agregado y su política `IncidentAccess`; Evidencias conserva sus objetos y metadatos.

| Origen               | Acción              | Destino              | Actor y requisitos                                                             |
| -------------------- | ------------------- | -------------------- | ------------------------------------------------------------------------------ |
| REPORTED             | VALIDATE            | VALIDATED            | OPERATOR/ADMIN distinto del informante; categoría activa explícita, sin reason |
| REPORTED             | REJECT              | REJECTED             | OPERATOR/ADMIN distinto del informante; motivo obligatorio                     |
| REPORTED             | REQUEST_INFORMATION | AWAITING_INFORMATION | OPERATOR/ADMIN distinto del informante; motivo obligatorio                     |
| AWAITING_INFORMATION | Respuesta           | REPORTED             | Solo informante original; mensaje obligatorio y una respuesta por solicitud    |
| AWAITING_INFORMATION | REJECT              | REJECTED             | OPERATOR/ADMIN distinto del informante; motivo obligatorio                     |

Las demás transiciones de revisión no están permitidas. Los motivos y mensajes usan trim Java y 3–2000 puntos Unicode, sin blancos, NUL ni sustitutos aislados. VALIDATE confirma o sustituye categoría atómicamente, sin un evento adicional de corrección. La prohibición de autorrevisión también afecta a ADMIN. Las pertenencias departamentales no conceden permiso global.

La prioridad comienza en null y solo cambia en VALIDATED sin departamento. El servidor fija MANUAL y exige justificación interna; mismo nivel produce 409 sin insertar historial. El ciudadano ve solo el nivel. La derivación futura exigirá prioridad previa y congelará su modificación, pero aún no tiene implementación.

Los POST nuevos reciben `expectedIncidentVersion` obligatorio. Se bloquea usuario → incidencia → categoría cuando procede; se revalida cuenta, permiso, estado y versión dentro de la transacción. Agregado, registro de dominio, StatusChange cuando cambia estado y AuditEvent se confirman juntos. Una versión obsoleta produce 409 PROCESSING_VERSION_MISMATCH. PATCH conserva If-Match, 412 y su autorización; también coordina el bloqueo de incidencia. La fotografía por sí sola no cambia estado, versión ni fecha del agregado.

**Antecedente histórico de A, 15/11/2026 (sustituido por la consolidación):** V2 era aditiva sobre V1. Añade AWAITING_INFORMATION a los CHECK de incidencia y transiciones, el snapshot `incident_reviews.resulting_status` y `incident_information_responses`. La respuesta tiene unicidad por solicitud y FK compuesta `(review_id, incident_id, request_decision)` hacia una revisión REQUEST_INFORMATION de esa misma incidencia. Los índices existentes de historia se reutilizan; las respuestas tienen índice incidencia/fecha/UUID. No se añade un índice de prioridad sin medición de utilidad: la selección y ordenación funcional se resuelven en SQL.

**Antecedente histórico de la V1 anterior a Tramitación:** las revisiones no probaban un estado resultante: su snapshot permanece null, sin inventar decisiones ni eliminar motivos antiguos de VALIDATE. El CHECK `incident_reviews_result` se incorpora NOT VALID para conservar esas filas, pero PostgreSQL lo aplica a todas las inserciones/actualizaciones nuevas. Estas exigen snapshot coherente y VALIDATE sin motivo. No se reconstruye historia a partir del estado vigente ni se usa `flyway repair`.

El resultado público separa estado actual, snapshot y `informationPending`; usa «Ayuntamiento» y oculta identidad del personal y motivos históricos de VALIDATE. El historial continúa como proyección de AuditEvent, con un hecho por revisión, respuesta o cambio de prioridad; no incluye justificación interna ni ATTACHMENT_CREATED/ATTACHMENT_DELETED. Los historiales internos nuevos ordenan fecha DESC, UUID DESC; el historial público conserva occurredAt ASC, id ASC.

Evidencias solo permite POST/DELETE en REPORTED y AWAITING_INFORMATION. Otros estados producen 409 ATTACHMENTS_FROZEN sin excepción ADMIN; las lecturas conservan su autorización. Tras S3 se vuelve a comprobar el estado bajo bloqueo, compensando el objeto si quedó congelado antes de confirmar SQL. DELETE comprueba la pareja exacta antes de la restricción de estado. El límite de cinco fotos, saneamiento JPEG/PNG, 10 MiB y baja lógica permanecen.

Limitaciones aprobadas: sin rectificación posterior a VALIDATED/REJECTED; sin moderación extraordinaria ni borrado físico de fotos o reportes con datos erróneos/personales descubiertos después. Recodificar elimina metadatos EXIF/GPS, no rostros ni matrículas. No hay caducidad automática, correo, frontend, IA, derivación, equipos, asignación, ejecución, resolución, cierre ni comentarios en esta fase.

**Evolución 16/11/2026:** Esquema inicial consolidado V1: 16 tablas de dominio, cinco roles y siete funciones geográficas; sin semillas de usuarios ni catálogos. Solo para bases vacías sin historial Flyway previo, conforme a la premisa del responsable. No acredita aplicación en Neon. Las restricciones de revisión/respuestas se integran en CREATE TABLE, todas validadas. El historial anterior se conserva aquí como antecedente; no autoriza outOfOrder ni actualización de bases existentes. [ADR-0006](0006-flyway.md).
