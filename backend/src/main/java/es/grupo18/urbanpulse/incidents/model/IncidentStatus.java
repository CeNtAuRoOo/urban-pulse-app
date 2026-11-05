package es.grupo18.urbanpulse.incidents.model;

/**
 * Estados del modelo objetivo; esta API solo crea REPORTED y no realiza
 * transiciones.
 */
public enum IncidentStatus {
    /** Reporte inicial; único estado que admite correcciones en el núcleo. */
    REPORTED,
    /** Validación municipal futura, todavía sin derivación obligatoria. */
    VALIDATED,
    /** Rechazo municipal futuro con motivo conservado. */
    REJECTED,
    /** Derivación futura al departamento, distinta de asignar trabajo. */
    ASSIGNED_TO_DEPARTMENT,
    /** Trabajo futuro asignado a equipo o técnico mediante Assignment. */
    WORK_ASSIGNED,
    /** Ejecución del trabajo; la aceptación no inicia este estado por sí sola. */
    IN_PROGRESS,
    /** Resolución; podrá volver directamente a fase de trabajo según reglas futuras. */
    RESOLVED,
    /** Cierre definitivo; no permite transiciones salientes. */
    CLOSED
}
