package es.grupo18.urbanpulse.duplicates.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Confirmación humana; el servicio valida el texto original y su longitud tras trim.
 */
public record DuplicateLinkRequest(@NotNull UUID candidateIncidentId, String reason) {
}
