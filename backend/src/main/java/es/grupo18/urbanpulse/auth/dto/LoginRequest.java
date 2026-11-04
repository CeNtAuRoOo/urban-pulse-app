package es.grupo18.urbanpulse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import es.grupo18.urbanpulse.identity.model.User;

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

    /**
     * Construye el DTO de inicio de sesión normalizando el correo electrónico
     * mediante la eliminación de espacios exteriores y su conversión
     * a minúsculas.
     *
     * @param email    correo electrónico proporcionado por el usuario
     * @param password contraseña proporcionada para iniciar sesión
     */
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
