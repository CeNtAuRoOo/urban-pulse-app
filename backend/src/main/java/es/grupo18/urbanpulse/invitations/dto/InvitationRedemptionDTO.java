package es.grupo18.urbanpulse.invitations.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.grupo18.urbanpulse.identity.model.RoleCode;

/**
 * Resultado mínimo de concesión; no modifica el UserDTO de autenticación.
 *
 * @param invitationId invitación consumida
 * @param role         rol concedido
 * @param scope        GLOBAL o DEPARTMENT
 * @param departmentId unidad opcional
 * @param redeemedAt   instante de concesión
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvitationRedemptionDTO(
        UUID invitationId,
        RoleCode role,
        String scope,
        UUID departmentId,
        Instant redeemedAt) {
}
