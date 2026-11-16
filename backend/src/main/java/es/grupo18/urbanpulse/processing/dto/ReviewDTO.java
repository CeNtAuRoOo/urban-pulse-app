package es.grupo18.urbanpulse.processing.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.grupo18.urbanpulse.identity.dto.UserSummaryDTO;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.processing.model.ReviewDecision;

/**
 * Contrato de Tramitación.
 *
 * @param id              revisión
 * @param incidentId      incidencia
 * @param decision        decisión
 * @param reason          motivo interno histórico o público de
 *                        rechazo/solicitud
 * @param reviewedBy      responsable interno
 * @param reviewedAt      instante
 * @param resultingStatus snapshot; null para filas anteriores a V2 sin
 *                        evidencia
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewDTO(UUID id, UUID incidentId, ReviewDecision decision, String reason, UserSummaryDTO reviewedBy,
        Instant reviewedAt, IncidentStatus resultingStatus) {
}
