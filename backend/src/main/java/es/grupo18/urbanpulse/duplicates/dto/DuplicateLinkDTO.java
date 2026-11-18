package es.grupo18.urbanpulse.duplicates.dto;

import java.time.Instant;
import java.util.UUID;

import es.grupo18.urbanpulse.identity.dto.UserSummaryDTO;

public record DuplicateLinkDTO(UUID id, UUID incidentId, UUID candidateIncidentId, UserSummaryDTO linkedBy,
        Instant linkedAt) {
}
