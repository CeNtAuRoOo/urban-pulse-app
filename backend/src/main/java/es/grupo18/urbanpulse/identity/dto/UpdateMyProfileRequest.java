package es.grupo18.urbanpulse.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.hibernate.validator.constraints.CodePointLength;

import es.grupo18.urbanpulse.identity.model.DisplayNames;

/**
 * Cambio del nombre público: normaliza antes de validar y cuenta puntos
 * Unicode.
 *
 * @param displayName nombre recortado de 2–120 caracteres, sin controles
 *                    interiores
 */
public record UpdateMyProfileRequest(
        @NotBlank(message = "El nombre no puede estar vacío.") @CodePointLength(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres.") @Pattern(regexp = "[^\\p{Cc}\\p{Cs}]*", message = "El nombre no admite caracteres de control.") String displayName) {
    /**
     * Recorta espacios de borde antes de la validación Jakarta de la petición.
     *
     * @param displayName valor recibido, incluido null para que lo rechace NotBlank
     */
    public UpdateMyProfileRequest {
        displayName = DisplayNames.normalize(displayName);
    }
}
