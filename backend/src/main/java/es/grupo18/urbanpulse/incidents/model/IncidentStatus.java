package es.grupo18.urbanpulse.incidents.model;

/**
 * Estados del agregado; revisión e información operativas, ejecución futura.
 */
public enum IncidentStatus {

    REPORTED, AWAITING_INFORMATION, VALIDATED, REJECTED, ASSIGNED_TO_DEPARTMENT, WORK_ASSIGNED, IN_PROGRESS, RESOLVED,
    CLOSED
}
