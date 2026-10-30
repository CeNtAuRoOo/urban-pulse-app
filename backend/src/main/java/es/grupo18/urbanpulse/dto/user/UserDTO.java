package es.grupo18.urbanpulse.dto.user;

import java.time.Instant;
import java.util.*;
import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.entity.model.User;

/**
 * Representa los datos de un usuario autorizados para una respuesta concreta de
 * la API, excluyendo información sensible como el hash de su contraseña o la
 * versión de sus tokens. Mantiene una copia ordenada e inmodificable de sus
 * roles.
 *
 * @param id            identificador único del usuario
 * @param displayName   nombre público del usuario
 * @param email         correo electrónico del usuario
 * @param emailVerified indica si el correo electrónico está verificado
 * @param active        indica si la cuenta está activa
 * @param createdAt     fecha de creación de la cuenta
 * @param roles         conjunto de roles globales del usuario
 */
public record UserDTO(
        UUID id,
        String displayName,
        String email,
        boolean emailVerified,
        boolean active,
        Instant createdAt,
        Set<RoleCode> roles) {

    /**
     * Construye el DTO del usuario conservando una copia ordenada
     * e inmodificable de sus roles globales.
     *
     * @param id            identificador único del usuario
     * @param displayName   nombre público del usuario
     * @param email         correo electrónico del usuario
     * @param emailVerified indica si el correo electrónico está verificado
     * @param active        indica si la cuenta está activa
     * @param createdAt     fecha de creación de la cuenta
     * @param roles         conjunto de roles globales del usuario
     */
    public UserDTO {
        roles = Collections.unmodifiableSet(new TreeSet<>(roles));
    }

    /**
     * Construye un DTO a partir de una entidad User y sus roles globales,
     * seleccionando únicamente los datos que pueden exponerse al cliente.
     *
     * @param user  entidad del usuario
     * @param roles roles globales asociados al usuario
     * @return DTO con los datos públicos del usuario
     */
    public static UserDTO from(User user, Set<RoleCode> roles) {
        return new UserDTO(user.getId(), user.getDisplayName(), user.getEmail(), user.isEmailVerified(),
                user.isActive(), user.getCreatedAt(), roles);
    }
}
