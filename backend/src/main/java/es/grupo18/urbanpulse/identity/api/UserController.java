package es.grupo18.urbanpulse.identity.api;

import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import es.grupo18.urbanpulse.identity.dto.AdminUpdateUserRequest;
import es.grupo18.urbanpulse.identity.dto.UpdateMyProfileRequest;
import es.grupo18.urbanpulse.identity.dto.UserDTO;
import es.grupo18.urbanpulse.identity.dto.UsersPageDTO;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.service.UserService;
import es.grupo18.urbanpulse.shared.security.DatabaseJwtAuthenticationConverter;

/**
 * Controlador REST encargado de gestionar los endpoints relacionados
 * con los usuarios de UrbanPulse.
 * Permite consultar y modificar el perfil propio, así como realizar
 * operaciones administrativas sobre otros usuarios.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    /**
     * Servicio que contiene la lógica de negocio
     * relacionada con la gestión de usuarios.
     */
    private final UserService userService;

    /**
     * Obtiene el perfil del usuario autenticado.
     * El identificador se extrae del JWT recibido en la petición.
     *
     * @param jwt token JWT del usuario autenticado
     * @return DTO con los datos del perfil y sus roles
     */
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('EMAIL_VERIFIED')")
    public UserDTO getMe(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = DatabaseJwtAuthenticationConverter.subject(jwt);

        return userService.getMe(userId);
    }

    /**
     * Actualiza el perfil del usuario autenticado.
     * Valida los datos recibidos antes de enviarlos al servicio.
     *
     * @param jwt     token JWT del usuario autenticado
     * @param request datos necesarios para actualizar el perfil
     * @return DTO con los datos actualizados del usuario
     */
    @PatchMapping("/me")
    @PreAuthorize("hasAuthority('EMAIL_VERIFIED')")
    @ResponseStatus(HttpStatus.OK)
    public UserDTO updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMyProfileRequest request) {

        UUID userId = DatabaseJwtAuthenticationConverter.subject(jwt);

        return userService.updateMe(userId, request);
    }

    /**
     * Obtiene los datos de un usuario concreto.
     * Este endpoint requiere que el usuario autenticado tenga el rol ADMIN.
     *
     * @param userId identificador del usuario que se desea consultar
     * @return DTO con los datos del usuario y sus roles
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('EMAIL_VERIFIED')")
    public UserDTO getUserById(@PathVariable UUID userId) {
        return userService.getUserById(userId);
    }

    /**
     * Actualiza el estado de actividad de un usuario.
     * Esta operación está restringida a usuarios con el rol ADMIN.
     *
     * @param userId  identificador del usuario que se desea modificar
     * @param request datos necesarios para actualizar su estado
     * @return DTO con los datos actualizados del usuario
     */
    @PatchMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('EMAIL_VERIFIED')")
    public UserDTO updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUpdateUserRequest request) {

        return userService.updateUser(userId, request);
    }

    /**
     * Obtiene un listado paginado de usuarios.
     * Permite aplicar filtros opcionales por rol global, rol departamental activo
     * y estado de actividad.
     * Este endpoint requiere permisos de administrador.
     *
     * @param page     número de página solicitado, comenzando por 1
     * @param pageSize cantidad de usuarios por página
     * @param role     rol global o departamental utilizado como filtro, si se
     *                 especifica
     * @param active   estado de actividad utilizado como filtro, si se especifica
     * @return DTO con los usuarios, sus roles, pertenencias y datos de paginación
     * @throws ResponseStatusException si los parámetros de paginación no son
     *                                 válidos
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('EMAIL_VERIFIED')")
    public UsersPageDTO getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false) Boolean active) {

        UserService.validatePagination(page, pageSize);

        return userService.getUsers(page, pageSize, role, active);
    }
}
