package es.grupo18.urbanpulse.dto.user;

import jakarta.validation.constraints.NotNull;

/**
 * DTO que representa los datos permitidos para actualizar
 * una cuenta de usuario mediante operaciones administrativas.
 * Incluye el estado de actividad que se desea establecer
 * y la validación necesaria para exigir su especificación.
 *
 * @param active indica si la cuenta debe permanecer activa.
 *               No puede ser null y debe contener true o false.
 */
public record AdminUpdateUserRequest(
                @NotNull(message = "Debes indicar si la cuenta estará activa.") Boolean active) {
}