package es.grupo18.urbanpulse.duplicates.dto;

import java.util.List;
import java.util.UUID;

/** Candidato heurístico; requiere siempre confirmación humana. */
public record DuplicateSuggestionDTO(UUID candidateIncidentId, double score, List<String> reasons) {}
