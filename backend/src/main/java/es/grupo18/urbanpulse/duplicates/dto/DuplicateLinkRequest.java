package es.grupo18.urbanpulse.duplicates.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Confirmación humana de que dos reportes describen el mismo hecho. */
public record DuplicateLinkRequest(@NotNull UUID candidateIncidentId,
        @Size(max = 4000) String reason) {}
