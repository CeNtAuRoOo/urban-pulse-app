package es.grupo18.urbanpulse.dto.auth;

import es.grupo18.urbanpulse.entity.model.User;
import jakarta.validation.constraints.*;

/**
 * Representa las credenciales necesarias para iniciar sesión.
 * Normaliza el correo electrónico durante la construcción del DTO.
 *
 * @param email    correo electrónico del usuario
 * @param password contraseña del usuario
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull String password) {

    public LoginRequest {
        email = User.normalizeEmail(email);
    }

    /**
     * Devuelve una representación del DTO sin revelar las credenciales.
     *
     * @return representación textual con los datos sensibles ocultos
     */
    @Override
    public String toString() {
        return "LoginRequest[REDACTED]";
    }
}
