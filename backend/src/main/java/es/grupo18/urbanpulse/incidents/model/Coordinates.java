package es.grupo18.urbanpulse.incidents.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.http.HttpStatus;

import es.grupo18.urbanpulse.shared.error.ApiException;

/**
 * Valor WGS84 inmutable desde la API, embebido en incidents sin tabla propia.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coordinates {
    @Column(nullable = false)
    private double latitude;
    @Column(nullable = false)
    private double longitude;
    private Double accuracyMeters;

    /**
     * Valida rangos y finitud, sin redondear la posición recibida.
     * 
     * @param latitude       latitud obligatoria entre -90 y 90
     * @param longitude      longitud obligatoria entre -180 y 180
     * @param accuracyMeters precisión opcional, finita y no negativa
     * @throws ApiException si faltan coordenadas o no son válidas (422)
     */
    public Coordinates(Double latitude, Double longitude, Double accuracyMeters) {
        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180
                || (accuracyMeters != null && (!Double.isFinite(accuracyMeters) || accuracyMeters < 0))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", "Coordenadas no válidas.");
        }
        this.latitude = latitude == 0 ? 0 : latitude;
        this.longitude = longitude == 0 ? 0 : longitude;
        this.accuracyMeters = accuracyMeters;
        if (accuracyMeters != null && accuracyMeters == 0) {
            this.accuracyMeters = 0.0;
        }
    }
}
