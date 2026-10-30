package es.grupo18.urbanpulse.dto.auth;

import es.grupo18.urbanpulse.entity.model.User;
import jakarta.validation.constraints.*;

/**
 * Representa los datos necesarios para registrar un nuevo usuario.
 * Normaliza el correo electrónico durante la construcción del DTO.
 *
 * @param displayName nombre público del usuario
 * @param email       correo electrónico utilizado para el registro
 * @param password    contraseña de la nueva cuenta
 */
public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 120) String displayName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Size(min = 8) String password) {

    /**
     * Construye el DTO de registro normalizando el correo electrónico
     * proporcionado por el usuario antes de almacenarlo.
     *
     * @param displayName nombre público del nuevo usuario
     * @param email       correo electrónico que se desea registrar
     * @param password    contraseña de la nueva cuenta
     */
    public RegisterRequest {
        email = User.normalizeEmail(email);
    }

    /**
     * Devuelve una representación del DTO sin revelar los datos del registro.
     *
     * @return representación textual con los datos ocultos
     */
    @Override
    public String toString() {
        return "RegisterRequest[REDACTED]";
    }
}
