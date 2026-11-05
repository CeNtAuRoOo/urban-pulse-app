package es.grupo18.urbanpulse.incidents.model;

/** Decisión de revisión futura; pedir información no implica una transición. */
public enum ReviewDecision {
    /** Validar; motivo opcional, pero validado si se aporta. */
    VALIDATE,
    /** Rechazar; motivo obligatorio de 3–2000 puntos Unicode. */
    REJECT,
    /** Solicitar información motivadamente; no implica una transición automática. */
    REQUEST_INFORMATION
}
