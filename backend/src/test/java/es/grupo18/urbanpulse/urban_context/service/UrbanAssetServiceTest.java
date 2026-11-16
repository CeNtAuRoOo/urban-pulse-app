package es.grupo18.urbanpulse.urban_context.service;

import java.util.List;

import org.springframework.http.HttpStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.urban_context.dto.UrbanAssetDTO;
import es.grupo18.urbanpulse.urban_context.mappers.UrbanAssetMapper;
import es.grupo18.urbanpulse.urban_context.model.UrbanAsset;
import es.grupo18.urbanpulse.urban_context.repository.UrbanAssetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrbanAssetServiceTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 25830);

    @Mock
    private UrbanAssetRepository assets;

    @Mock
    private UrbanAssetMapper mapper;

    @InjectMocks
    private UrbanAssetService service;

    // geom obligatorio por invariante de UrbanAsset.create
    private static final UrbanAsset ENTITY = UrbanAsset.create(
            "Ferrocarriles", "Estación", null, null, null, null, null, null,
            GF.createPoint(new Coordinate(439000.0, 4473000.0)));

    private static final UrbanAssetDTO DTO = new UrbanAssetDTO(
            1L, "Ferrocarriles", "Estación", null, null, null, null, null, null, "POINT (439000 4473000)");

    // --- Sin proximidad ---------------------------------------------------

    @Test
    void nullTypeDelegatesWithoutFilter() {
        when(assets.searchBySourceTable(null)).thenReturn(List.of(ENTITY));
        when(mapper.toDTO(ENTITY)).thenReturn(DTO);

        assertThat(service.search(null, null, null, null)).containsExactly(DTO);
    }

    @Test
    void blankTypeIsTreatedAsAbsent() {
        when(assets.searchBySourceTable(null)).thenReturn(List.of());

        service.search("   ", null, null, null);

        verify(assets).searchBySourceTable(null);
    }

    @Test
    void typeIsTrimmedBeforeFiltering() {
        when(assets.searchBySourceTable("Ferrocarriles")).thenReturn(List.of());

        service.search("  Ferrocarriles  ", null, null, null);

        verify(assets).searchBySourceTable("Ferrocarriles");
    }

    @Test
    void emptyResultMapsToEmptyList() {
        when(assets.searchBySourceTable("Nada")).thenReturn(List.of());

        assertThat(service.search("Nada", null, null, null)).isEmpty();
    }

    // --- Con proximidad ---------------------------------------------------

    @Test
    void proximityDelegatesWithCorrectArgumentOrder() {
        when(assets.searchByProximity(eq("Ferrocarriles"), eq(-5.9845), eq(37.3891), eq(1500.0), anyInt()))
                .thenReturn(List.of(ENTITY));
        when(mapper.toDTO(ENTITY)).thenReturn(DTO);

        List<UrbanAssetDTO> result = service.search("Ferrocarriles", 37.3891, -5.9845, 1500.0);

        assertThat(result).containsExactly(DTO);
        // Verifica el orden (sourceTable, lon, lat, radius): fácil de invertir
        verify(assets).searchByProximity("Ferrocarriles", -5.9845, 37.3891, 1500.0,
                UrbanAssetRepository.MAX_RESULTS);
    }

    @Test
    void proximityWithoutTypeForwardsNullSourceTable() {
        when(assets.searchByProximity(isNull(), eq(-5.9845), eq(37.3891), eq(500.0), anyInt()))
                .thenReturn(List.of());

        service.search(null, 37.3891, -5.9845, 500.0);

        verify(assets).searchByProximity(null, -5.9845, 37.3891, 500.0, UrbanAssetRepository.MAX_RESULTS);
    }

    @ParameterizedTest(name = "lat={0}, lon={1}, radius={2} -> {3}")
    @CsvSource({
            "91.0,     -5.98,  1000, INVALID_LATITUDE",
            "-90.1,    -5.98,  1000, INVALID_LATITUDE",
            "37.3891,  181.0,  1000, INVALID_LONGITUDE",
            "37.3891, -180.1,  1000, INVALID_LONGITUDE",
            "37.3891, -5.9845,  0.5, INVALID_RADIUS",
            "37.3891, -5.9845, 5001, INVALID_RADIUS",
    })
    void outOfRangeProximityIsRejectedBeforeHittingTheRepository(
            double lat, double lon, double radius, String code) {
        assertThatThrownBy(() -> service.search(null, lat, lon, radius))
                .isInstanceOf(ApiException.class)
                .extracting("status", "code")
                .containsExactly(HttpStatus.BAD_REQUEST, code);
        verifyNoInteractions(assets, mapper);
    }

    @ParameterizedTest(name = "only {0} -> INCOMPLETE_PROXIMITY_PARAMETERS")
    @CsvSource({
            "37.3891, ,        ,",
            "        , -5.9845, ,",
            "        ,        , 1000",
    })
    void partiallySuppliedProximityIsRejected(
            Double lat, Double lon, Double radius) {
        assertThatThrownBy(() -> service.search(null, lat, lon, radius))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INCOMPLETE_PROXIMITY_PARAMETERS");
        verifyNoInteractions(assets, mapper);
    }

    @Test
    void radiusExactlyAtBoundsIsAccepted() {
        when(assets.searchByProximity(eq(null), anyDouble(), anyDouble(), eq(5000.0), anyInt()))
                .thenReturn(List.of());

        service.search(null, 37.3891, -5.9845, 5000.0);

        verify(assets).searchByProximity(eq(null), eq(-5.9845), eq(37.3891), eq(5000.0),
                eq(UrbanAssetRepository.MAX_RESULTS));
    }

    @Test
    void latitudeExactlyAtBoundsIsAccepted() {
        when(assets.searchByProximity(eq(null), anyDouble(), eq(90.0), anyDouble(), anyInt()))
                .thenReturn(List.of());

        service.search(null, 90.0, 0.0, 100.0);

        verify(assets).searchByProximity(eq(null), eq(0.0), eq(90.0), eq(100.0),
                eq(UrbanAssetRepository.MAX_RESULTS));
    }
}
