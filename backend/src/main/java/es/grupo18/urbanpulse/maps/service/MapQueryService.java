package es.grupo18.urbanpulse.maps.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import es.grupo18.urbanpulse.maps.dto.MapFeatureDTO;
import es.grupo18.urbanpulse.maps.dto.MapIncidentsDTO;
import es.grupo18.urbanpulse.maps.repository.MapIncidentRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;

@Service
@RequiredArgsConstructor
public class MapQueryService {
    private final MapVisibilityPolicy visibility;
    private final MapIncidentRepository incidents;
    private final MapTitlePolicy titles;

    /** Permissions and data share a database snapshot; no partial successful responses. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MapIncidentsDTO find(UUID actor, long tokenVersion, MapQuery query) {
        var access = visibility.authorize(actor, tokenVersion);
        try {
            var rows = incidents.find(query, access);
            if (rows.size() > MapIncidentRepository.MAX_FEATURES) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "MAP_RESULT_LIMIT",
                        "Hay demasiados elementos. Reduce el área o el zoom para agrupar más.");
            }
            var items = rows.stream().map(row -> {
                var coordinates = new MapFeatureDTO.Coordinates(row.latitude(), row.longitude());
                if (row.incidentCount() > 1) {
                    return (MapFeatureDTO) new MapFeatureDTO.ClusterFeature("CLUSTER", row.bucket(), coordinates,
                            row.incidentCount());
                }
                var category = row.categoryId() == null ? null
                        : new MapFeatureDTO.Category(row.categoryId(), row.categoryName());
                return new MapFeatureDTO.IncidentFeature("INCIDENT", row.id(), titles.visibleTitle(row.id()), category,
                        row.status(), row.priority(), row.createdAt(), coordinates,
                        row.exact() ? "EXACT" : "APPROXIMATE", row.exact());
            }).toList();
            return new MapIncidentsDTO(items, items.size(), rows.stream().mapToLong(r -> r.incidentCount()).sum());
        } catch (QueryTimeoutException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAP_QUERY_TIMEOUT",
                    "Consulta temporalmente no disponible. Reduce el área o vuelve a intentarlo.");
        }
    }
}
