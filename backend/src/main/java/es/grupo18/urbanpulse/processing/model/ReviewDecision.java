package es.grupo18.urbanpulse.processing.model;

/**
 * Decisión municipal que ProcessingService aplica con su transición atómica.
 */
public enum ReviewDecision {
    /** Validar con categoría activa explícita; motivo prohibido. */
    VALIDATE,
    /** Rechazar; motivo obligatorio de 3–2000 puntos Unicode. */
    REJECT,
    /** Solicitar información con motivo; pasa a AWAITING_INFORMATION. */
    REQUEST_INFORMATION
}
