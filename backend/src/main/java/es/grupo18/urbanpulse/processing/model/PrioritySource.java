package es.grupo18.urbanpulse.processing.model;

/**
 * Procedencia de una decisión; esta fase solo crea MANUAL.
 */
public enum PrioritySource {
    /** Decisión humana sin sugerencia asistida registrada. */
    MANUAL,
    /**
     * Reservada para asistencia futura; no admitida como entrada HTTP en esta fase.
     */
    ASSISTED
}
