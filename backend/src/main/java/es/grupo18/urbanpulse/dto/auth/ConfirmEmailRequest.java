package es.grupo18.urbanpulse.dto.auth;

import jakarta.validation.constraints.*;

/**
 * Representa los datos necesarios para confirmar el correo electrónico
 * de un usuario mediante un código de exactamente ocho dígitos ASCII.
 *
 * @param code código numérico de verificación proporcionado por el usuario
 */
public record ConfirmEmailRequest(@NotNull @Pattern(regexp = "[0-9]{8}") String code) {

    /**
     * Devuelve una representación del DTO sin revelar el código
     * de verificación proporcionado por el usuario.
     *
     * @return representación textual con el código de verificación oculto
     */
    @Override
    public String toString() {
        return "ConfirmEmailRequest[REDACTED]";
    }
}
