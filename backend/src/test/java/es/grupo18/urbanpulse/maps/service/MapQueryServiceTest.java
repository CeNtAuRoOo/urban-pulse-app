package es.grupo18.urbanpulse.maps.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.maps.dto.MapFeatureDTO;
import es.grupo18.urbanpulse.maps.repository.MapIncidentRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapQueryServiceTest {
    private final MapVisibilityPolicy visibility = mock(MapVisibilityPolicy.class);
    private final MapIncidentRepository repository = mock(MapIncidentRepository.class);
    private final MapQueryService service = new MapQueryService(visibility, repository, new MapTitlePolicy());
    private final UUID actor = UUID.randomUUID();
    private final MapQuery query = new MapQuery(36, -6, 38, -3, 8, null, null);
    private final MapVisibilityPolicy.Access access = new MapVisibilityPolicy.Access(actor, false);

    @Test
    void distinguishesTotalsAndProjectsOnlyPermittedFields() {
        when(visibility.authorize(actor, 0)).thenReturn(access);
        when(repository.find(query, access)).thenReturn(List.of(row(3, false), row(1, false), row(1, true)));
        var result = service.find(actor, 0, query);
        assertThat(result.count()).isEqualTo(3);
        assertThat(result.totalIncidents()).isEqualTo(5);
        assertThat(result.items().getFirst()).isInstanceOf(MapFeatureDTO.ClusterFeature.class);
        var publicItem = (MapFeatureDTO.IncidentFeature) result.items().get(1);
        assertThat(publicItem.canAccessPrivateDetail()).isFalse();
        assertThat(publicItem.coordinatePrecision()).isEqualTo("APPROXIMATE");
        assertThat(publicItem.title()).isEqualTo("Incidencia " + publicItem.incidentId());
        assertThat(((MapFeatureDTO.IncidentFeature) result.items().get(2)).canAccessPrivateDetail()).isTrue();
    }

    @Test
    void oversizedResultsAndTimeoutsNeverReturnPartialSuccess() {
        when(visibility.authorize(actor, 0)).thenReturn(access);
        when(repository.find(query, access)).thenReturn(Collections.nCopies(1001, row(1, false)));
        assertThatThrownBy(() -> service.find(actor, 0, query)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo("MAP_RESULT_LIMIT"));
        when(repository.find(query, access)).thenThrow(new QueryTimeoutException("private database details"));
        assertThatThrownBy(() -> service.find(actor, 0, query)).isInstanceOfSatisfying(ApiException.class,
                e -> { assertThat(e.code()).isEqualTo("MAP_QUERY_TIMEOUT");
                    assertThat(e.getMessage()).doesNotContain("private"); });
    }

    @Test
    void deniedServiceCallNeverQueriesIncidents() {
        when(visibility.authorize(actor, 0)).thenThrow(ApiException.unauthorized());
        assertThatThrownBy(() -> service.find(actor, 0, query)).isInstanceOf(ApiException.class);
        verifyNoInteractions(repository);
    }

    private MapIncidentRepository.Row row(long count, boolean exact) {
        return new MapIncidentRepository.Row("g1:8:0:0", count, UUID.randomUUID(), null, null,
                IncidentStatus.REPORTED, null, Instant.parse("2026-11-10T10:00:00Z"), exact, 36.72, -4.42);
    }
}
