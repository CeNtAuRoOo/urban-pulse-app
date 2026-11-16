# ADR-0014: Corrección de incidencias, concurrencia e historial público

- **Estado:** Aceptado e implementado en el núcleo de Incidencias.
- **Fecha:** 2026-11-05.
- **Ámbito:** API `/api/v1/incidents`, persistencia V1 y auditoría compartida.

## Contexto

Una corrección puede partir de un detalle desactualizado mientras otro usuario modifica el mismo aviso. Es necesario impedir actualizaciones perdidas, mantener trazabilidad atómica y no revelar la existencia de incidencias ajenas. El diseño general incluye tramitación futura, pero el núcleo solo registra y corrige reportes iniciales.

## Alternativas

Se descarta aceptar PATCH sin precondición: @Version por sí sola detectaría algunas carreras durante la transacción, pero no un cliente que corrigiese un detalle antiguo después de otro commit. Serializar la fila con bloqueo pesimista tampoco identifica ese caso. Se adopta precondición HTTP junto a bloqueo optimista JPA.

## Decisión

Incident tiene `@Version long`. POST, GET de detalle y PATCH devuelven una ETag fuerte opaca `"sha256-<64 hexadecimales minúsculos>"`: SHA-256 de los bytes JSON de IncidentDTO serializados con el mismo JsonMapper de MVC. Incluye version, reporter.displayName, todos los campos de categoría y, cuando exista, assignedDepartment. Un cambio relacionado relevante modifica el validador sin incrementar artificialmente la versión de todas las incidencias. Una representación idéntica conserva su etiqueta. Las fechas del núcleo se truncan a microsegundos para conservar igualdad entre escritura y lectura PostgreSQL.

PATCH exige una única If-Match de ese formato: ausencia 428, detalle obsoleto o carrera al flush 412; formato no admitido 400. No se admiten wildcard, listas ni etiquetas débiles. La sustitución del formato decimal anterior fue aprobada expresamente en la revisión correctiva: los clientes deben guardar y reenviar la ETag recibida, nunca construirla desde version; una etiqueta decimal antigua produce 400. Se conserva el DTO y @Version. CORS permite If-Match y expone ETag.

Una ETag fuerte debe variar cuando cambia la representación; If-Match usa comparación fuerte ([RFC 9110, §§8.8.3 y 13.1.1](https://www.rfc-editor.org/rfc/rfc9110.html#name-etag)). `Cache-Control: no-store` sigue siendo la política privada de caché, pero no exime de esa regla. Se descartaron congelar datos relacionados y propagar versiones a todas las incidencias por su impacto transversal; no se usa ETag débil.

El servicio revalida la cuenta y la titularidad/rol global; después comprueba precondición y estado REPORTED dentro de la transacción. La comparación usa el detalle cargado antes de modificar; cambios independientes de relaciones posteriores a esa evaluación tendrán una nueva representación al consultar. No se pretende serializar globalmente las escrituras de varios recursos. JPA detecta además la carrera sobre Incident después de superar la precondición. Para ciudadanos sin acceso, inexistencia y recurso ajeno comparten 404, sin versión ni información del estado. OPERATOR/ADMIN pueden corregir terceros provisionalmente solo en REPORTED; esta amplitud requiere revisión de negocio/seguridad por ámbito y campos. Los roles departamentales no conceden acceso global.

Alta y AuditEvent INCIDENT_CREATED son atómicos; no existe StatusChange inicial. Una corrección efectiva incrementa versión, cambia updatedAt y registra INCIDENT_UPDATED en la misma transacción. Los flush detectan fallos antes de devolver; no-op conserva fecha, versión e historial. La auditoría no contiene texto completo, correo ni datos de seguridad; guarda solo versión.

HistoryEventDTO es una proyección de los eventos permitidos, paginada en SQL por recurso/acción exactos y ordenada por occurredAt ASC,id ASC. Devuelve identidad mínima del actor, resumen estable y detalles permitidos, nunca el JSONB interno completo. La futura integración de transiciones debe evitar duplicar hechos entre AuditEvent y StatusChange.

## Consecuencias

El cliente debe conservar ETag y volver a consultar tras 412. Los cambios futuros de estado/asignación deberán participar del mismo control de versión; no se permiten actualizaciones SQL operativas que lo omitan. El bloqueo del actor reutiliza la coordinación de identidad, y el bloqueo de categoría coordina su desactivación; la incidencia sigue siendo optimista. Las relaciones se preservan con FK RESTRICT y la limpieza de cuentas excluye referencias nuevas.

La persistencia base de StatusChange, IncidentReview y PriorityDecision no equivale a tramitación implementada. Se mantienen prioridad, departamento y fechas finales nulos hasta operaciones reales. El [roadmap](../incidents-implementation-roadmap.md) conserva datos futuros, dependencias, revisión de permisos y trazabilidad RF01–RF26 recuperada del enunciado.

## Validación

`IncidentPostgresIT` verifica V1 con Hibernate validate, auditoría/rollback y dos correcciones concurrentes con un solo ganador, además del contrato HTTP con JWT real, cambios legítimos de perfil/categoría, estabilidad de ETag y rechazo de precondiciones anteriores. El límite de cuerpo de 64 KiB se comprueba en MVC y con HTTP real por bloques. Las pruebas de servicio comprueban también autorización independiente de URL y traducción del conflicto de flush. Los resultados de ejecución y entorno se registran en [Desarrollo](../development.md).
