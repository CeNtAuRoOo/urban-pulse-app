package es.grupo18.urbanpulse.incidents.model;

import java.util.Objects;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Localización actual: solo coordenadas; ningún proceso de geocodificación se
 * simula.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Location {
    @Embedded
    private Coordinates coordinates;

    /**
     * Encapsula coordenadas sin añadir dirección o contexto inexistentes.
     * @param coordinates posición validada y obligatoria
     * @throws NullPointerException si falta el valor obligatorio
     */
    public Location(Coordinates coordinates) {
        this.coordinates = Objects.requireNonNull(coordinates);
    }
}
