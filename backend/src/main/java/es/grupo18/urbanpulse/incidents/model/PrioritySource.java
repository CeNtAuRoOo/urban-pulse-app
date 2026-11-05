package es.grupo18.urbanpulse.incidents.model;

/**
 * Procedencia de una decisión futura, siempre justificada por un responsable.
 */
public enum PrioritySource {
    /** Decisión humana sin sugerencia asistida registrada. */
    MANUAL,
    /** Decisión con asistencia; no equivale a aprobación automática de una sugerencia. */
    ASSISTED
}
