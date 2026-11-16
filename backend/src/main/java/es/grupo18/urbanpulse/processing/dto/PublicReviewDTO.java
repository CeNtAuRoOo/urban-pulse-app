package es.grupo18.urbanpulse.processing.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.processing.model.ReviewDecision;

/**
 * Contrato de Tramitación.
 *
 * @param decision        decisión pública
 * @param reviewedAt      instante
 * @param resultingStatus snapshot histórico nullable
 * @param reason          motivo solo para rechazo o solicitud
 * @param responsible     identificación institucional sin identidad personal
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PublicReviewDTO(ReviewDecision decision, Instant reviewedAt, IncidentStatus resultingStatus,
        String reason, String responsible) {
}
