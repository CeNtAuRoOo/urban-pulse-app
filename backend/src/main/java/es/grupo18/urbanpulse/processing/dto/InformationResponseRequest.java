package es.grupo18.urbanpulse.processing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Contrato de Tramitación.
 *
 * @param message                 mensaje obligatorio
 * @param expectedIncidentVersion versión esperada del agregado
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record InformationResponseRequest(String message, Long expectedIncidentVersion) {
}
