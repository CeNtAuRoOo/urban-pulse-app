package es.grupo18.urbanpulse.incidents.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.grupo18.urbanpulse.categories.dto.CategoryResponse;
import es.grupo18.urbanpulse.identity.dto.UserSummaryDTO;
import es.grupo18.urbanpulse.incidents.model.IncidentPriority;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.organization.dto.DepartmentResponse;

/**
 * Detalle autorizado, independiente de JPA; omite atributos opcionales sin
 * valor. Todo el JSON, incluidos nombres/catálogos vigentes y version, participa
 * en la ETag; version por sí sola no es un validador HTTP.
 * 
 * @param id                 UUIDv7
 * @param title              título normalizado
 * @param description        descripción normalizada
 * @param reporter           identidad mínima del titular
 * @param category           catálogo compartido opcional, incluso si se
 *                           desactivó posteriormente
 * @param location           coordenadas reales
 * @param status             estado persistido
 * @param priority           ausente hasta una decisión futura
 * @param assignedDepartment ausente hasta la derivación futura
 * @param createdAt          alta UTC
 * @param updatedAt          última corrección efectiva UTC
 * @param resolvedAt         resolución futura, opcional
 * @param closedAt           cierre definitivo futuro, opcional
 * @param version            versión optimista; distinta de la versión JWT
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IncidentDTO(UUID id, String title, String description, UserSummaryDTO reporter,
        CategoryResponse category, LocationDTO location, IncidentStatus status, IncidentPriority priority,
        DepartmentResponse assignedDepartment, Instant createdAt, Instant updatedAt, Instant resolvedAt,
        Instant closedAt, long version) {
}
