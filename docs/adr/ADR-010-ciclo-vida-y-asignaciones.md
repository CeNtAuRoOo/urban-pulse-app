## Título

ADR-010: Ciclo de vida de incidencias y separación de asignaciones

## Estado

Aceptado; revisión e información implementadas, ejecución futura

## Fecha

2026-10-18

## Ámbito

Incidencias, tramitación municipal, auditoría

## Contexto

El enunciado utiliza `ASSIGNED` e introduce `Assignment` como vínculo temporal con departamento, equipo o técnico. Para el flujo municipal acordado, es útil distinguir la **derivación a un departamento** (operador central) de la **asignación del trabajo concreto** (coordinador departamental). No son el mismo evento ni deben modelarse como dos `Assignment` ordinarias.

## Opciones consideradas

1. Un único estado `ASSIGNED` y dos registros consecutivos de `Assignment`, uno para departamento y otro para equipo/técnico.
2. Dos fases de estado y `Assignment` solo para trabajo concreto (elegida).
3. Utilizar exclusivamente `StatusChange` como historial de destinatarios (descartada porque hay reasignaciones sin cambio de estado).

## Decisión

El enum compartido de estados incluye el ciclo objetivo. En el backend están implementadas las transiciones de revisión y respuesta. La derivación, el trabajo, la resolución y el cierre siguientes son decisiones de diseño. El enum contiene:

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

[Referencia relacionada](../design-docs/DD-002-expediente.md).
