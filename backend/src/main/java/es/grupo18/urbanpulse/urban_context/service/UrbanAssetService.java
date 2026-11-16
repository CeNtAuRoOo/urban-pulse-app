package es.grupo18.urbanpulse.urban_context.service;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.urban_context.dto.UrbanAssetDTO;
import es.grupo18.urbanpulse.urban_context.mappers.UrbanAssetMapper;
import es.grupo18.urbanpulse.urban_context.repository.UrbanAssetRepository;

/**
 * Consulta pública del dataset unificado de activos urbanos. Solo lectura.
 * El dataset es de dominio público; la autorización se limita a JWT válido
 * (gestionado por el filtro de seguridad).
 *
 * <p>
 * Todos los activos devueltos tienen {@code geom} no nulo: es invariante de
 * {@link es.grupo18.urbanpulse.urban_context.model.UrbanAsset#create}.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UrbanAssetService {

    private static final double MAX_RADIUS_METERS = 5000.0;
    private static final double MIN_RADIUS_METERS = 1.0;

    private final UrbanAssetRepository assets;
    private final UrbanAssetMapper mapper;

    /**
     * Busca activos urbanos por tipo y, opcionalmente, por proximidad.
     *
     * <p>
     * Los parámetros de proximidad deben venir los tres o ninguno. Si no se
     * aporta ninguno, se devuelve un listado acotado filtrado solo por tipo.
     * </p>
     *
     * @param type         tabla de origen; {@code null}/vacío para no filtrar
     * @param latitude     latitud central WGS84; {@code null} si no hay proximidad
     * @param longitude    longitud central WGS84; {@code null} si no hay proximidad
     * @param radiusMeters radio en metros (1..5000); {@code null} si no hay
     *                     proximidad
     * @return activos coincidentes, cada uno con {@code geom} no nulo; lista
     *         vacía si no hay resultados
     * @throws ApiException 400 si la combinación de parámetros es inválida
     */
    @Transactional(readOnly = true)
    public List<UrbanAssetDTO> search(String type, Double latitude, Double longitude, Double radiusMeters) {
        boolean anyProximity = latitude != null || longitude != null || radiusMeters != null;
        boolean allProximity = latitude != null && longitude != null && radiusMeters != null;

        if (anyProximity && !allProximity) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INCOMPLETE_PROXIMITY_PARAMETERS",
                    "latitude, longitude y radiusMeters deben enviarse juntos.");
        }

        String sourceTable = normalizeType(type);

        if (allProximity) {
            validateProximity(latitude, longitude, radiusMeters);
            return assets
                    .searchByProximity(sourceTable, longitude, latitude, radiusMeters, UrbanAssetRepository.MAX_RESULTS)
                    .stream()
                    .map(mapper::toDTO)
                    .toList();
        }

        return assets.searchBySourceTable(sourceTable)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    private static String normalizeType(String type) {
        if (type == null)
            return null;
        String trimmed = type.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void validateProximity(double latitude, double longitude, double radiusMeters) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LATITUDE",
                    "latitude debe estar en [-90, 90].");
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LONGITUDE",
                    "longitude debe estar en [-180, 180].");
        }
        if (radiusMeters < MIN_RADIUS_METERS || radiusMeters > MAX_RADIUS_METERS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RADIUS",
                    "radiusMeters debe estar en [1, 5000].");
        }
    }
}
