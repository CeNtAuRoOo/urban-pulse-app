package es.grupo18.urbanpulse.processing.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.grupo18.urbanpulse.identity.dto.UserSummaryDTO;
import es.grupo18.urbanpulse.incidents.model.IncidentPriority;
import es.grupo18.urbanpulse.processing.model.PrioritySource;

/**
 * Contrato de Tramitación.
 *
 * @param id            decisión
 * @param incidentId    incidencia
 * @param priority      nivel
 * @param source        origen
 * @param justification justificación interna
 * @param decidedBy     responsable interno
 * @param decidedAt     instante
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PriorityDecisionDTO(UUID id, UUID incidentId, IncidentPriority priority, PrioritySource source,
        String justification, UserSummaryDTO decidedBy, Instant decidedAt) {
}
