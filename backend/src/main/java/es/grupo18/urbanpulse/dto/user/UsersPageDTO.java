package es.grupo18.urbanpulse.dto.user;

import java.util.List;

/**
 * DTO que representa una respuesta paginada de usuarios.
 * Agrupa la lista de usuarios obtenidos y los metadatos
 * necesarios para conocer el estado de la paginación.
 *
 * @param data       lista de usuarios incluidos en la página actual
 * @param pagination información sobre la paginación de los resultados
 */
public record UsersPageDTO(
        List<UserDTO> data,
        PageMetadata pagination) {
}