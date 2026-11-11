package es.grupo18.urbanpulse.maps.dto;

import java.util.List;

/** count is graphical features; totalIncidents is the sum represented, never a truncated total. */
public record MapIncidentsDTO(List<MapFeatureDTO> items, int count, long totalIncidents) {
    public MapIncidentsDTO {
        items = List.copyOf(items);
    }
}
