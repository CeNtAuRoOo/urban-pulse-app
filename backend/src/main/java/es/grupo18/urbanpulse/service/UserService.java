package es.grupo18.urbanpulse.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.grupo18.urbanpulse.dto.user.AdminUpdateUserRequest;
import es.grupo18.urbanpulse.dto.user.PageMetadata;
import es.grupo18.urbanpulse.dto.user.UpdateMyProfileRequest;
import es.grupo18.urbanpulse.dto.user.UserDTO;
import es.grupo18.urbanpulse.dto.user.UsersPageDTO;
import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.entity.model.User;
import es.grupo18.urbanpulse.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Servicio encargado de gestionar las operaciones relacionadas
 * con los usuarios de UrbanPulse.
 * Proporciona funcionalidades para consultar y actualizar el perfil
 * propio, administrar otros usuarios y obtener listados paginados
 * con filtros por rol y estado de actividad.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    /**
     * Repositorio utilizado para consultar y modificar
     * los usuarios almacenados en la base de datos.
     */
    private final UserRepository users;

    /**
     * Obtiene los datos del usuario que ha iniciado sesión.
     *
     * @param userId identificador del usuario autenticado
     * @return DTO con los datos del perfil y sus roles globales
     * @throws EntityNotFoundException si el usuario no existe
     */
    @Transactional(readOnly = true)
    public UserDTO getMe(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado."));

        return UserDTO.from(user, user.globalRoles());
    }

    /**
     * Actualiza el nombre público del usuario autenticado.
     *
     * @param userId  identificador del usuario cuyo perfil se actualiza
     * @param request datos recibidos para modificar el perfil
     * @return DTO con los datos actualizados del usuario y sus roles
     * @throws EntityNotFoundException si el usuario no existe
     */
    @Transactional
    public UserDTO updateMe(UUID userId, UpdateMyProfileRequest request) {

        User user = users.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado."));

        user.updateDisplayName(request.displayName());

        return UserDTO.from(user, user.globalRoles());
    }

    /**
     * Obtiene los datos de un usuario concreto mediante su identificador.
     * Carga también las asignaciones de roles para construir el DTO.
     *
     * @param userId identificador del usuario que se desea consultar
     * @return DTO con los datos del usuario y sus roles globales
     * @throws EntityNotFoundException si el usuario no existe
     */
    @Transactional(readOnly = true)
    public UserDTO getUserById(UUID userId) {
        User user = users.findUserWithRolesById(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario no encontrado."));

        return UserDTO.from(user, user.globalRoles());
    }

    /**
     * Actualiza el estado de actividad de un usuario.
     * Esta operación está destinada a la administración de cuentas.
     *
     * @param userId  identificador del usuario que se desea modificar
     * @param request datos recibidos para actualizar el estado de la cuenta
     * @return DTO con los datos actualizados del usuario y sus roles
     * @throws EntityNotFoundException si el usuario no existe
     */
    @Transactional
    public UserDTO updateUser(
            UUID userId,
            AdminUpdateUserRequest request) {

        User user = users.findUserWithRolesById(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario no encontrado."));

        user.setActive(request.active());

        return UserDTO.from(user, user.globalRoles());
    }

    /**
     * Obtiene un listado paginado de usuarios aplicando los filtros indicados.
     * Los resultados se ordenan por fecha de creación descendente y,
     * en caso de empate, por identificador ascendente.
     *
     * @param page     número de página solicitado, comenzando por 1
     * @param pageSize cantidad máxima de usuarios por página
     * @param role     rol global por el que se desea filtrar, o null para no
     *                 filtrar
     * @param active   estado de actividad por el que se desea filtrar,
     *                 o null para incluir usuarios activos e inactivos
     * @return DTO con la lista de usuarios y los metadatos de paginación
     */
    @Transactional(readOnly = true)
    public UsersPageDTO getUsers(
            int page,
            int pageSize,
            RoleCode role,
            Boolean active) {

        PageRequest pageable = PageRequest.of(
                page - 1,
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.ASC, "id")));

        Page<User> result = users.searchUsers(role, active, pageable);

        List<UserDTO> data = result.getContent()
                .stream()
                .map(user -> UserDTO.from(user, user.globalRoles()))
                .toList();

        PageMetadata pagination = new PageMetadata(
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());

        return new UsersPageDTO(data, pagination);
    }
}