package es.grupo18.urbanpulse.maps.service;

import java.util.UUID;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.shared.error.ApiException;
import org.springframework.http.HttpStatus;

/** Validated viewport. Large views are allowed at low zoom, including the whole province. */
public record MapQuery(double minLat, double minLon, double maxLat, double maxLon, int zoom,
        IncidentStatus status, UUID categoryId) {
    public MapQuery {
        if (!Double.isFinite(minLat) || !Double.isFinite(minLon) || !Double.isFinite(maxLat)
                || !Double.isFinite(maxLon) || minLat < -90 || maxLat > 90 || minLon < -180
                || maxLon > 180 || minLat >= maxLat || minLon >= maxLon || zoom < 0 || zoom > 22) {
            throw invalid("Límites WGS84 o zoom no válidos.");
        }
        double maxSpan = Math.min(360, 1440.0 / Math.pow(2, zoom));
        if (maxLat - minLat > maxSpan || maxLon - minLon > maxSpan) {
            throw invalid("El rectángulo es demasiado grande para este zoom. Reduce el zoom o el área.");
        }
    }

    /** Nested grid of 100 m * powers of two; at zoom 18+ every incident is individual. */
    public double cellMeters() {
        return zoom >= 18 ? 0 : 100.0 * Math.pow(2, Math.max(0, 17 - zoom));
    }

    public static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", detail);
    }
}
