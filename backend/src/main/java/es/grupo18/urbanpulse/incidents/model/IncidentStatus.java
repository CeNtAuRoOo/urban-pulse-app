package es.grupo18.urbanpulse.incidents.model;

/**
 * Estados del agregado; revisión e información operativas, ejecución futura.
 */
public enum IncidentStatus {
    /** Reporte inicial; único estado que admite correcciones en el núcleo. */
    REPORTED,
    /** Solicitud municipal pendiente de respuesta del informante. */
    AWAITING_INFORMATION,
    /** Validación municipal, todavía sin derivación obligatoria. */
    VALIDATED,
    /** Rechazo municipal terminal con motivo conservado. */
    REJECTED,
    /** Derivación futura al departamento, distinta de asignar trabajo. */
    ASSIGNED_TO_DEPARTMENT,
    /** Trabajo futuro asignado a equipo o técnico mediante Assignment. */
    WORK_ASSIGNED,
    /** Ejecución del trabajo; la aceptación no inicia este estado por sí sola. */
    IN_PROGRESS,
    /**
     * Resolución; podrá volver directamente a fase de trabajo según reglas futuras.
     */
    RESOLVED,
    /** Cierre definitivo; no permite transiciones salientes. */
    CLOSED
}
