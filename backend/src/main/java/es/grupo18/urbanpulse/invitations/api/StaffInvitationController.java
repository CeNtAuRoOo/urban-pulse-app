package es.grupo18.urbanpulse.invitations.api;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.grupo18.urbanpulse.invitations.dto.InvitationDTO;
import es.grupo18.urbanpulse.invitations.dto.InvitationPageDTO;
import es.grupo18.urbanpulse.invitations.dto.InvitationQuery;
import es.grupo18.urbanpulse.invitations.dto.InvitationRedemptionDTO;
import es.grupo18.urbanpulse.invitations.dto.InvitationRedemptionRequest;
import es.grupo18.urbanpulse.invitations.dto.InvitationRequest;
import es.grupo18.urbanpulse.invitations.service.StaffInvitationService;
import es.grupo18.urbanpulse.shared.security.DatabaseJwtAuthenticationConverter;

/**
 * Cinco operaciones de invitación, separadas del registro público ciudadano.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StaffInvitationController {

    private final StaffInvitationService invitations;

    /**
     * Emite metadatos y Location; la entrega del código se solicita por SMTP.
     *
     * @param jwt     administrador autenticado
     * @param request variante estricta validada
     * @return 201 sin secreto y con no-store
     */
    @PostMapping("/staff-invitations")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('EMAIL_VERIFIED')")
    public ResponseEntity<InvitationDTO> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InvitationRequest request) {

        var dto = invitations.create(DatabaseJwtAuthenticationConverter.subject(jwt),
                DatabaseJwtAuthenticationConverter.version(jwt), request);

        return ResponseEntity.created(URI.create("/api/v1/staff-invitations/" + dto.id()))
                .cacheControl(CacheControl.noStore()).body(dto);
    }

    /**
     * Lista solo para administradores verificados.
     *
     * @param jwt   administrador
     * @param query filtros y paginación
     * @return 200 con página y totales
     */
    @GetMapping("/staff-invitations")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('EMAIL_VERIFIED')")
    public ResponseEntity<InvitationPageDTO> list(@AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute InvitationQuery query) {

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitations.list(
                DatabaseJwtAuthenticationConverter.subject(jwt),
                DatabaseJwtAuthenticationConverter.version(jwt), query));
    }

    /**
     * Consulta el recurso al que apunta Location.
     *
     * @param jwt          administrador
     * @param invitationId identificador administrativo
     * @return respuesta HTTP 200 con los metadatos de la invitación
     * @throws es.grupo18.urbanpulse.shared.error.ApiException si no dispone
     *                                                         de permisos (401/403)
     *                                                         o la invitación no
     *                                                         existe (404)
     */
    @GetMapping("/staff-invitations/{invitationId}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('EMAIL_VERIFIED')")
    public ResponseEntity<InvitationDTO> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID invitationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitations.get(
                DatabaseJwtAuthenticationConverter.subject(jwt),
                DatabaseJwtAuthenticationConverter.version(jwt), invitationId));
    }

    /**
     * Revoca una invitación ACTIVE, sin retirar permisos ya concedidos.
     *
     * @param jwt          administrador
     * @param invitationId identificador administrativo
     * @return respuesta HTTP 204 sin contenido
     * @throws es.grupo18.urbanpulse.shared.error.ApiException si falta
     *                                                         autorización
     *                                                         (401/403), no existe
     *                                                         la invitación (404)
     *                                                         o no está activa
     *                                                         (409)
     */
    @PostMapping("/staff-invitations/{invitationId}/revocations")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('EMAIL_VERIFIED')")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID invitationId) {
        invitations.revoke(DatabaseJwtAuthenticationConverter.subject(jwt),
                DatabaseJwtAuthenticationConverter.version(jwt), invitationId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Canjea para el titular del JWT; no acepta identidad ni alcance en el cuerpo.
     *
     * @param jwt     ciudadano verificado
     * @param request código de uso único
     * @return respuesta HTTP 200 con el permiso concedido
     * @throws es.grupo18.urbanpulse.shared.error.ApiException si falta
     *                                                         autorización
     *                                                         (401/403), el código
     *                                                         o departamento no es
     *                                                         válido
     *                                                         (422), o el permiso
     *                                                         ya existe (409)
     */
    @PostMapping("/users/me/staff-invitation-redemptions")
    @PreAuthorize("hasRole('CITIZEN') and hasAuthority('EMAIL_VERIFIED')")
    public ResponseEntity<InvitationRedemptionDTO> redeem(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InvitationRedemptionRequest request) {

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitations.redeem(
                DatabaseJwtAuthenticationConverter.subject(jwt),
                DatabaseJwtAuthenticationConverter.version(jwt), request.invitationCode()));
    }
}
