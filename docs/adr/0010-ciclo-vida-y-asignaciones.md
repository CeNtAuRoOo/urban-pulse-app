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

`REPORTED`, `VALIDATED`, `REJECTED`, `ASSIGNED_TO_DEPARTMENT`, `WORK_ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `REOPENED`, `CLOSED`.

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

- Tabla/máquina completa de transiciones, incluida `REOPENED`, `REJECTED`, `REQUEST_INFORMATION`, revisión posterior y cierre.
- Qué sucede si un departamento no tiene coordinador o si el trabajo queda sin técnico disponible.
- Índice único parcial para una asignación activa, concurrencia, reversión ante error y criterios de cierre/reasignación.
- Qué eventos son visibles al ciudadano y cuáles son internos.
- El OpenAPI modular `0.2.0` ya distingue derivación departamental y asignación de trabajo; concretar la matriz de transiciones y permisos de cada operación al implementar el tag.

**Estado de entrega:** representación conceptual/documental, no migración ni lógica de negocio ya implementada.
