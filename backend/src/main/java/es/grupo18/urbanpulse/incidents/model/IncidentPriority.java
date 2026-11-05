package es.grupo18.urbanpulse.incidents.model;

/**
 * Escala futura de prioridad; la ausencia de decisión se representa con null.
 */
public enum IncidentPriority {
    /** Nivel bajo; no se asigna por defecto al registrar. */
    LOW,
    /** Nivel medio, pendiente de una decisión justificada. */
    MEDIUM,
    /** Nivel alto, pendiente de una decisión justificada. */
    HIGH,
    /** Nivel crítico; su asignación no habilita transiciones automáticamente. */
    CRITICAL
}
