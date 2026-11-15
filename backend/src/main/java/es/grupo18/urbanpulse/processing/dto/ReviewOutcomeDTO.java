package es.grupo18.urbanpulse.processing.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.grupo18.urbanpulse.incidents.model.IncidentStatus;

/**
 * Contrato de Tramitación.
 *
 * @param incidentId         incidencia
 * @param status             estado vigente
 * @param informationPending si la última solicitud sigue pendiente
 * @param latestReview       última revisión o null antes de la primera
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewOutcomeDTO(UUID incidentId, IncidentStatus status, boolean informationPending,
        PublicReviewDTO latestReview) {
}
