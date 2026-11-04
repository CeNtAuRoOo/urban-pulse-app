package es.grupo18.urbanpulse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.hibernate.validator.constraints.CodePointLength;

import es.grupo18.urbanpulse.identity.model.DisplayNames;
import es.grupo18.urbanpulse.identity.model.User;

/**
 * Representa los datos necesarios para registrar un nuevo usuario.
 * Normaliza nombre público y correo antes de la validación del DTO.
 * Cuenta el nombre en puntos de código Unicode, igual que PostgreSQL.
 *
 * @param displayName nombre público del usuario
 * @param email       correo electrónico utilizado para el registro
 * @param password    contraseña de la nueva cuenta
 */
public record RegisterRequest(
        @NotBlank @CodePointLength(min = 2, max = 120) @Pattern(regexp = "[^\\p{Cc}\\p{Cs}]*") String displayName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Size(min = 8) String password) {

    /**
     * Construye el DTO de registro normalizando nombre y correo antes
     * de las restricciones Jakarta; no transforma la contraseña.
     *
     * @param displayName nombre público del nuevo usuario
     * @param email       correo electrónico que se desea registrar
     * @param password    contraseña de la nueva cuenta
     */
    public RegisterRequest {
        displayName = DisplayNames.normalize(displayName);
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
