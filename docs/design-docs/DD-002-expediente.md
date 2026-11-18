# DD-002 — Concurrencia e historial de un expediente

Descripción retrospectiva del núcleo, la primera fase de Tramitación y sus lectores. La corrección del informante, una decisión municipal y una carga de evidencia pueden competir por el mismo expediente. El estado mostrado por el cliente y los permisos pueden haber cambiado desde su última lectura.

## Escrituras y precondiciones

El registro crea una incidencia REPORTED y su evento de creación en la misma transacción. Para corregirla en REPORTED se lee la ETag del detalle y se envía como If-Match. Una representación antigua produce 412. El bloqueo de fila y la versión JPA coordinan escrituras concurrentes. Un bloqueo por sí solo no detectaría que el cliente partía de un detalle antiguo.

Tramitación comprueba la versión numérica expectedIncidentVersion y bloquea usuario e incidencia. Una versión obsoleta produce 409. Desde REPORTED el revisor global puede validar con categoría activa, rechazar con motivo o pedir información con motivo. El informante nunca revisa su propio reporte. Desde AWAITING_INFORMATION el informante puede responder a la solicitud abierta, volviendo a REPORTED. El personal también puede rechazar. La solicitud tiene como máximo una respuesta y la FK compuesta impide referenciar otra incidencia. VALIDATED admite decisión de prioridad manual. Las variantes de entrada y códigos de error están en el [contrato de Tramitación](../api/openapi/paths/tramitacion.yaml).

## Historial y acceso

La revisión conserva el estado resultante y el actor interno. El cambio de estado, la versión del expediente y el evento se confirman junto con la decisión. La prioridad conserva su decisión y auditoría aunque no implique transición. La consulta pública de resultado identifica al Ayuntamiento y muestra el motivo comunicable de rechazo o petición de información. El historial oculta actores municipales incluso tras posteriores cambios de rol. La consulta privada y la publicación cartográfica tienen reglas independientes. [Diseño territorial](DD-004-territorio.md).

Duplicados ordena candidatos en la ventana de ±72 horas y 250 m, con hasta veinte sugerencias. Solo una confirmación humana crea la relación y su auditoría. La unicidad del par canónico evita duplicados en sentido inverso y conserva ambos expedientes, sus evidencias y sus historiales.

## Verificación

Las suites de Incidencias, Tramitación y Duplicados ejercitan titularidad, versiones obsoletas, proyecciones y restricciones en PostgreSQL. La integración con almacenamiento añade comprobaciones de concurrencia al límite de cinco evidencias activas. [Evidencias](DD-003-evidencias.md) · [ADR-010](../adr/ADR-010-ciclo-vida-y-asignaciones.md) · [ADR-014](../adr/ADR-014-incidencias-concurrencia-historial.md) · [Siguientes fases](../pending.md#funcionalidad).
