package es.grupo18.urbanpulse.incidents.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.grupo18.urbanpulse.incidents.model.Coordinates;

/**
 * Coordenadas de entrada/salida; omite precisión desconocida en respuestas.
 * 
 * @param latitude       latitud WGS84 obligatoria
 * @param longitude      longitud WGS84 obligatoria
 * @param accuracyMeters precisión finita no negativa, opcional
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoordinatesDTO(Double latitude, Double longitude, Double accuracyMeters) {
    /**
     * Comprueba WGS84 y finitud antes de construir el valor persistente.
     *
     * @return coordenadas con ceros de signo normalizado
     * @throws es.grupo18.urbanpulse.shared.error.ApiException si faltan datos o son inválidos (422)
     */
    public Coordinates toValue() {
        return new Coordinates(latitude, longitude, accuracyMeters);
    }

    /**
     * Proyecta la posición persistida sin geocodificación ni estimaciones.
     *
     * @param value posición persistida
     * @return coordenadas sin información calculada
     */
    public static CoordinatesDTO from(Coordinates value) {
        return new CoordinatesDTO(value.getLatitude(), value.getLongitude(), value.getAccuracyMeters());
    }
}
