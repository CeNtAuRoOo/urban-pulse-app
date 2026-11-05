package es.grupo18.urbanpulse.identity.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import es.grupo18.urbanpulse.identity.dto.AdminUpdateUserRequest;
import es.grupo18.urbanpulse.identity.dto.DepartmentMembershipDTO;
import es.grupo18.urbanpulse.identity.dto.UpdateMyProfileRequest;
import es.grupo18.urbanpulse.identity.dto.UserDTO;
import es.grupo18.urbanpulse.identity.dto.UsersPageDTO;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.organization.repository.DepartmentMembershipRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.shared.pagination.PageMetadata;

/**
 * Servicio encargado de gestionar las operaciones relacionadas
 * con los usuarios de UrbanPulse.
 * Proporciona funcionalidades para consultar y actualizar el perfil
 * propio, administrar otros usuarios y obtener listados paginados
 * con filtros por roles globales, pertenencias departamentales activas
 * y estado de actividad.
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
     * Repositorio utilizado para recuperar las pertenencias departamentales
     * activas e históricas de uno o varios usuarios.
     */
    private final DepartmentMembershipRepository memberships;

    /**
     * Obtiene los datos del usuario que ha iniciado sesión.
     *
     * @param userId identificador del usuario autenticado
     * @return DTO con los datos del perfil, sus roles globales y sus pertenencias
     *         departamentales
     * @throws EntityNotFoundException si el usuario no existe
     */
    @Transactional(readOnly = true)
    public UserDTO getMe(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado."));

        return toUserDTO(user);
    }

    /**
     * Actualiza el nombre público del usuario autenticado bajo bloqueo de fila,
     * compartido con contraseña, verificación e Invitaciones.
     *
     * @param userId  identificador del usuario cuyo perfil se actualiza
     * @param request datos recibidos para modificar el perfil
     * @return DTO con los datos actualizados, roles globales y pertenencias
     *         departamentales
     * @throws EntityNotFoundException si el usuario no existe
     * @throws ApiException            si el nombre normalizado incumple sus límites
     */
    @Transactional
    public UserDTO updateMe(UUID userId, UpdateMyProfileRequest request) {

        User user = users.findForEmailVerification(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado."));

        user.updateDisplayName(request.displayName());

        return toUserDTO(user);
    }

    /**
     * Obtiene los datos de un usuario concreto mediante su identificador.
     * Carga también las asignaciones de roles para construir el DTO.
     *
     * @param userId identificador del usuario que se desea consultar
     * @return DTO con los datos del usuario, sus roles globales y sus pertenencias
     *         departamentales
     * @throws EntityNotFoundException si el usuario no existe
     */
    @Transactional(readOnly = true)
    public UserDTO getUserById(UUID userId) {
        User user = users.findUserWithRolesById(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario no encontrado."));

        return toUserDTO(user);
    }

    /**
     * Actualiza el estado de actividad de un usuario.
     * Esta operación está destinada a la administración de cuentas. Serializa las
     * bajas y protege al último ADMIN activo y verificado, incluso en
     * autodesactivación.
     *
     * @param userId  identificador del usuario que se desea modificar
     * @param request datos recibidos para actualizar el estado de la cuenta
     * @return DTO con los datos actualizados, roles globales y pertenencias
     *         departamentales
     * @throws EntityNotFoundException si el usuario no existe
     * @throws ApiException            si la baja dejaría la API sin administradores
     *                                 disponibles
     */
    @Transactional
    public UserDTO updateUser(
            UUID userId,
            AdminUpdateUserRequest request) {

        users.lockAccountAdministration();
        User user = users.findForEmailVerification(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Usuario no encontrado."));

        if (user.isActive() && user.isEmailVerified() && !request.active()
                && user.globalRoles().contains(RoleCode.ADMIN)
                && users.countAvailableAdministrators() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ACTIVE_ADMIN",
                    "Debe permanecer al menos un administrador activo con correo verificado.");
        }
        user.setActive(request.active());

        return toUserDTO(user);
    }

    /**
     * Obtiene un listado paginado de usuarios aplicando los filtros indicados.
     * Los resultados se ordenan por fecha de creación descendente y,
     * en caso de empate, por identificador ascendente.
     *
     * @param page     número de página solicitado, comenzando por 1
     * @param pageSize cantidad máxima de usuarios por página
     * @param role     rol global o departamental activo por el que se desea
     *                 filtrar, o null para no filtrar
     * @param active   estado de actividad por el que se desea filtrar,
     *                 o null para incluir usuarios activos e inactivos
     * @return DTO con usuarios, roles, pertenencias departamentales y metadatos de
     *         paginación
     * @throws ResponseStatusException si página, tamaño u offset exceden los
     *                                 límites
     */
    @Transactional(readOnly = true)
    public UsersPageDTO getUsers(
            int page,
            int pageSize,
            RoleCode role,
            Boolean active) {

        validatePagination(page, pageSize);
        PageRequest pageable = PageRequest.of(
                page - 1,
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.ASC, "id")));

        Page<User> result = users.searchUsers(role, active, pageable);
        if (!result.isEmpty()) {
            users.findAllWithRolesByIdIn(result.getContent().stream().map(user -> user.getId()).toList());
        }

        Map<UUID, List<DepartmentMembershipDTO>> membershipsByUser = membershipsByUser(
                result.getContent().stream().map(user -> user.getId()).toList());

        List<UserDTO> data = result.getContent()
                .stream()
                .map(user -> UserDTO.from(
                        user,
                        user.globalRoles(),
                        membershipsByUser.getOrDefault(user.getId(), List.of())))
                .toList();

        PageMetadata pagination = new PageMetadata(
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());

        return new UsersPageDTO(data, pagination);
    }

    /**
     * Construye el DTO completo de un usuario, incluyendo sus pertenencias
     * departamentales activas e históricas.
     *
     * @param user entidad del usuario que se desea transformar
     * @return DTO con los datos personales, roles y pertenencias del usuario
     */
    private UserDTO toUserDTO(User user) {
        Map<UUID, List<DepartmentMembershipDTO>> membershipsByUser = membershipsByUser(List.of(user.getId()));

        return UserDTO.from(
                user,
                user.globalRoles(),
                membershipsByUser.getOrDefault(user.getId(), List.of()));
    }

    /**
     * Recupera y agrupa las pertenencias departamentales de varios usuarios.
     * Ejecuta una única consulta para evitar el problema N+1 en los listados
     * paginados y conserva tanto las pertenencias activas como las históricas.
     *
     * @param userIds identificadores de los usuarios consultados
     * @return mapa que asocia cada usuario con sus pertenencias departamentales
     */
    private Map<UUID, List<DepartmentMembershipDTO>> membershipsByUser(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        return memberships.findAllForUsers(userIds).stream()
                .collect(Collectors.groupingBy(
                        membership -> membership.getUser().getId(),
                        Collectors.mapping(DepartmentMembershipDTO::from,
                                Collectors.toList())));
    }

    /**
     * Valida límites públicos y el offset admitido por JPA antes de consultar.
     * Usa long para que la multiplicación de parámetros int no desborde.
     *
     * @param page     página 1-based
     * @param pageSize tamaño entre 1 y 100
     * @throws ResponseStatusException si la página, tamaño u offset no son
     *                                 admisibles (400)
     */
    public static void validatePagination(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100
                || ((long) page - 1) * pageSize > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La página debe ser positiva, pageSize de 1 a 100 y el offset como máximo 2147483647.");
        }
    }
}
