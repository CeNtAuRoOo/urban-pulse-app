package es.grupo18.urbanpulse.processing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.grupo18.urbanpulse.incidents.model.IncidentPriority;

/**
 * Contrato de Tramitación.
 *
 * @param priority                nivel manual obligatorio
 * @param justification           justificación interna
 * @param expectedIncidentVersion versión esperada del agregado
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PriorityRequest(IncidentPriority priority, String justification, Long expectedIncidentVersion) {
}
