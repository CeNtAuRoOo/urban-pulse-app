package es.grupo18.urbanpulse.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Representa los datos necesarios para cambiar la contraseña de un usuario.
 *
 * @param currentPassword contraseña actual del usuario
 * @param newPassword     nueva contraseña que se desea establecer
 */
public record ChangePasswordRequest(
        @NotNull @Size(min = 1) String currentPassword,
        @NotNull @Size(min = 8) String newPassword) {

    /**
     * Devuelve una representación del DTO sin revelar las contraseñas.
     *
     * @return representación textual con los datos sensibles ocultos
     */
    @Override
    public String toString() {
        return "ChangePasswordRequest[REDACTED]";
    }
}
