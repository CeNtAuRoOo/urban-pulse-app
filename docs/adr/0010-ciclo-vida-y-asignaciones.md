# ADR-0010: Ciclo de vida de incidencias y separación de asignaciones

- **Estado:** Aceptado como decisión de diseño; transiciones finas pendientes
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

`REPORTED`, `VALIDATED`, `REJECTED`, `ASSIGNED_TO_DEPARTMENT`, `WORK_ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`.

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

## Pendiente antes de implementar el tag Tramitación

- Tabla/máquina completa de transiciones, incluidos rechazo, solicitud de información, reapertura directa desde RESOLVED y cierre definitivo.
- Qué sucede si un departamento no tiene coordinador o si el trabajo queda sin técnico disponible.
- Índice único parcial para una asignación activa, concurrencia, reversión ante error y criterios de cierre/reasignación.
- Qué eventos son visibles al ciudadano y cuáles son internos.
- El OpenAPI modular vigente `0.6.0` distingue derivación departamental y asignación de trabajo; concretar la matriz de transiciones y permisos de cada operación al implementar el tag.

**Estado de entrega:** núcleo de Incidencias operativo en V2; StatusChange, IncidentReview y PriorityDecision tienen persistencia base sin operaciones. Assignment y el resto de Tramitación siguen como diseño.

## Revisión del 05/11/2026: núcleo y reapertura

El alta crea Incident en REPORTED y AuditEvent INCIDENT_CREATED en una sola transacción; no crea StatusChange inicial. Las correcciones efectivas generan INCIDENT_UPDATED, nunca revisiones o decisiones artificiales. HistoryEventDTO es una proyección pública paginada de esos eventos; deberá integrar las transiciones futuras sin duplicar hechos.

Se elimina el estado persistente de reapertura del diseño anterior. Una incidencia RESOLVED podrá reabrirse en el futuro mediante transición directa, motivada, a ASSIGNED_TO_DEPARTMENT o WORK_ASSIGNED si conserva una asignación válida y coherente. CLOSED es terminal: un nuevo aviso requiere otra incidencia. No se implementan transiciones en esta entrega.

REQUEST_INFORMATION no cambia necesariamente el estado; tanto REQUEST_INFORMATION como REJECT exigen motivo no blanco de 3–2000 puntos Unicode tras trim. VALIDATE admite omitirlo, pero si existe debe cumplir esos mismos límites. Prioridad es null hasta una decisión justificada. El permiso provisional de OPERATOR/ADMIN para corregir avisos de terceros solo en REPORTED requiere revisión de seguridad/negocio por ámbito y campo antes de extender el flujo municipal. Los permisos departamentales no otorgan lectura global.

[Estado, modelo objetivo y pendientes](../incidents-implementation-roadmap.md); [control de concurrencia](0014-incidencias-concurrencia-historial.md).

## Invariantes estructurales verificadas

V3 refuerza los CHECK de revisión/prioridad sin reescribir V1/V2. Java normaliza los motivos de revisión y la justificación de prioridad con trim, rechaza blancos, NUL y sustitutos aislados, y cuenta puntos Unicode. SQL exige su forma ya normalizada y limita longitudes. PriorityDecision requiere prioridad, origen MANUAL/ASSISTED, responsable y justificación de 3–2000; no actualiza Incident.priority por sí sola.

StatusChange requiere estados no nulos y distintos, nunca origen CLOSED; no existe null → REPORTED ni REOPENED. Java y varchar limitan reason a 2000 y resolutionNotes a 5000 puntos Unicode; ambos son opcionales y conservan formato/blancos. El futuro servicio exigirá texto no blanco para las transiciones que lo necesiten y notas al resolver, autorizará actor/ámbito, comprobará estado actual, asignación y fechas, y escribirá todo atómicamente. Esas reglas de negocio no se atribuyen al constructor ni al CHECK estructural. Las entidades no habilitan endpoints ni ejecutan la máquina de estados.

El estado REOPENED del enunciado original se adapta deliberadamente a la reapertura directa desde RESOLVED acordada por el equipo; CLOSED permanece terminal.
