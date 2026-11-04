package es.grupo18.urbanpulse.categories.dto;

import jakarta.validation.constraints.NotBlank;

import org.hibernate.validator.constraints.CodePointLength;

/**
 * Datos de alta con límites en puntos de código Unicode.
 *
 * @param name        nombre obligatorio de 2 a 100 puntos tras trim; conserva
 *                    espacios interiores
 * @param description texto opcional sin recortar, hasta 500 puntos de código
 * @param active      estado inicial; omisión o null equivale a true
 */
public record CreateCategoryRequest(
        @NotBlank @CodePointLength(min = 2, max = 100) String name,
        @CodePointLength(max = 500) String description,
        Boolean active) {
    /**
     * Recorta el nombre antes de Bean Validation, conservando null para detectar su
     * ausencia.
     *
     * @param name        nombre recibido
     * @param description texto opcional sin normalizar
     * @param active      estado opcional, interpretado por el servicio
     */
    public CreateCategoryRequest {
        name = name == null ? null : name.trim();
    }
}
