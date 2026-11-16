package es.grupo18.urbanpulse.incidents.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.incidents.model.Incident;
import es.grupo18.urbanpulse.incidents.repository.IncidentRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;

/**
 * Política común de cuenta y visibilidad para incidencias y sus evidencias.
 * Los llamadores deben mantener una transacción durante el uso de las
 * entidades.
 */
@Component
@RequiredArgsConstructor
public class IncidentAccess {

    private final UserRepository users;
    private final IncidentRepository incidents;

    /**
     * Revalida la cuenta y, en escrituras, coordina su bloqueo con identidad.
     *
     * @param id           sujeto autenticado
     * @param tokenVersion versión vigente del JWT
     * @param writing      si se debe bloquear el usuario
     * @return usuario activo, verificado y con roles cargados
     * @throws ApiException 401 por cuenta/token inválido; 403 sin verificar
     */
    public User account(UUID id, long tokenVersion, boolean writing) {
        if (id == null)
            throw ApiException.unauthorized();
        User user = (writing ? users.findForEmailVerification(id) : users.findUserWithRolesById(id))
                .orElseThrow(ApiException::unauthorized);
        if (!user.isActive() || user.getTokenVersion() != tokenVersion
                || !user.globalRoles().contains(RoleCode.CITIZEN))
            throw ApiException.unauthorized();
        if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Debes verificar tu correo.");
        }
        return user;
    }

    /**
     * Oculta por igual inexistencia y falta de visibilidad, sin restricción de
     * estado.
     *
     * @param actor cuenta revalidada
     * @param id    incidencia solicitada
     * @param lock  bloqueo para coordinar recuento/alta/baja de evidencias
     * @return incidencia visible
     * @throws ApiException 404 indistinguible para incidencia ajena o inexistente
     */
    public Incident visible(User actor, UUID id, boolean lock) {
        if (id == null)
            throw notFound();
        Incident incident = (lock ? incidents.findForAttachments(id) : incidents.findById(id))
                .orElseThrow(IncidentAccess::notFound);
        if (!actor.getId().equals(incident.getReporter().getId()) && !isStaff(actor))
            throw notFound();
        return incident;
    }

    /**
     * Determina si la cuenta dispone de acceso global al módulo.
     *
     * @param user cuenta vigente
     * @return acceso transversal solo por rol global
     */
    public static boolean isStaff(User user) {
        var roles = user.globalRoles();
        return roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.OPERATOR);
    }

    /**
     * Oculta existencia y falta de permisos mediante el mismo error.
     *
     * @return error público sin revelar la existencia del recurso
     */
    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incidencia no encontrada.");
    }
}
