package es.grupo18.urbanpulse.processing.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.grupo18.urbanpulse.identity.dto.UserSummaryDTO;

/**
 * Contrato de Tramitación.
 *
 * @param id          respuesta
 * @param incidentId  incidencia
 * @param reviewId    solicitud atendida
 * @param message     texto
 * @param respondedBy informante
 * @param respondedAt instante
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record InformationResponseDTO(UUID id, UUID incidentId, UUID reviewId, String message,
        UserSummaryDTO respondedBy, Instant respondedAt) {
}
