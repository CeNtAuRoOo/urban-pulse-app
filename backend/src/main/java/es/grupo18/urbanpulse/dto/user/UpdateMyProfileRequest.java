package es.grupo18.urbanpulse.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO que representa los datos permitidos para actualizar
 * el perfil del usuario autenticado.
 * Incluye las validaciones necesarias para garantizar
 * que el nombre público tenga un formato válido.
 *
 * @param displayName nuevo nombre público del usuario.
 *                    No puede estar vacío y debe tener entre 2 y 120
 *                    caracteres.
 */
public record UpdateMyProfileRequest(

        @NotBlank(message = "El nombre no puede estar vacío.") @Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres.") String displayName

) {
}