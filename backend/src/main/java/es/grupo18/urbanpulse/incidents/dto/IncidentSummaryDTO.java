package es.grupo18.urbanpulse.incidents.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.grupo18.urbanpulse.categories.dto.CategoryResponse;
import es.grupo18.urbanpulse.incidents.model.IncidentPriority;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;

/**
 * Resumen para listado, sin descripción ni identidad del informante.
 * 
 * @param id        identidad estable
 * @param title     título normalizado
 * @param category  categoría opcional
 * @param location  posición
 * @param status    estado actual
 * @param priority  prioridad opcional
 * @param createdAt alta UTC
 * @param updatedAt última modificación UTC
 * @param version   versión actual
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IncidentSummaryDTO(UUID id, String title, CategoryResponse category, LocationDTO location,
        IncidentStatus status, IncidentPriority priority, Instant createdAt, Instant updatedAt, long version) {
}
