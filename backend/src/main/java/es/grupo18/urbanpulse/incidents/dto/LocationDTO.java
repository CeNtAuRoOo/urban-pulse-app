package es.grupo18.urbanpulse.incidents.dto;

/**
 * Ubicación observable del núcleo; el enriquecimiento geográfico es futuro.
 *
 * @param coordinates posición real; la API no devuelve geocodificación ni
 *                    estados simulados
 */
public record LocationDTO(CoordinatesDTO coordinates) {
}
