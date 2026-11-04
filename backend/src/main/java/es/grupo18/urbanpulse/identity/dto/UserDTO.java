package es.grupo18.urbanpulse.identity.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;

/**
 * Representa los datos de un usuario autorizados para una respuesta concreta de
 * la API, excluyendo información sensible como el hash de su contraseña o la
 * versión de sus tokens. Mantiene copias inmodificables de los roles globales
 * y de las pertenencias departamentales activas e históricas.
 *
 * @param id                    identificador único del usuario
 * @param displayName           nombre público del usuario
 * @param email                 correo electrónico del usuario
 * @param emailVerified         indica si el correo electrónico está verificado
 * @param active                indica si la cuenta está activa
 * @param createdAt             fecha de creación de la cuenta
 * @param roles                 conjunto de roles globales del usuario
 * @param departmentMemberships pertenencias departamentales activas e
 *                              históricas
 */
public record UserDTO(
        UUID id,
        String displayName,
        String email,
        boolean emailVerified,
        boolean active,
        Instant createdAt,
        Set<RoleCode> roles,
        List<DepartmentMembershipDTO> departmentMemberships) {

    /**
     * Construye el DTO del usuario conservando copias ordenadas e inmodificables
     * de sus roles globales y de sus pertenencias departamentales.
     *
     * @param id                    identificador único del usuario
     * @param displayName           nombre público del usuario
     * @param email                 correo electrónico del usuario
     * @param emailVerified         indica si el correo electrónico está verificado
     * @param active                indica si la cuenta está activa
     * @param createdAt             fecha de creación de la cuenta
     * @param roles                 conjunto de roles globales del usuario
     * @param departmentMemberships pertenencias departamentales del usuario
     */
    public UserDTO {
        roles = Collections.unmodifiableSet(new TreeSet<>(roles));
        departmentMemberships = List.copyOf(departmentMemberships);
    }

    /**
     * Construye un DTO a partir de una entidad User y sus asignaciones,
     * seleccionando únicamente los datos que pueden exponerse al cliente.
     *
     * @param user                  entidad del usuario
     * @param roles                 roles globales asociados al usuario
     * @param departmentMemberships pertenencias departamentales del usuario
     * @return DTO con los datos públicos, roles y adscripciones del usuario
     */
    public static UserDTO from(
            User user,
            Set<RoleCode> roles,
            List<DepartmentMembershipDTO> departmentMemberships) {

        return new UserDTO(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.isEmailVerified(),
                user.isActive(),
                user.getCreatedAt(),
                roles,
                departmentMemberships);
    }
}
