package es.grupo18.urbanpulse.maps.dto;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import es.grupo18.urbanpulse.incidents.model.IncidentPriority;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;

/** Disjoint JSON variants. No incident entities or private detail DTOs cross this boundary. */
public sealed interface MapFeatureDTO {
    String type();
    Coordinates coordinates();

    record Coordinates(double latitude, double longitude) { }
    record Category(UUID id, String name) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record IncidentFeature(String type, UUID incidentId, String title, Category category,
            IncidentStatus status, IncidentPriority priority, Instant createdAt, Coordinates coordinates,
            String coordinatePrecision, boolean canAccessPrivateDetail) implements MapFeatureDTO { }

    record ClusterFeature(String type, String clusterId, Coordinates coordinates, long incidentCount)
            implements MapFeatureDTO { }
}
