package es.grupo18.urbanpulse.invitations.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.invitations.model.InvitationStatus;
import es.grupo18.urbanpulse.invitations.model.StaffInvitation;

/**
 * Metadatos administrativos sin código ni HMAC; los campos opcionales ausentes
 * se omiten.
 *
 * @param id           identificador
 * @param email        destinatario normalizado
 * @param role         privilegio
 * @param departmentId departamento opcional
 * @param status       estado calculado
 * @param createdBy    emisor
 * @param redeemedBy   titular que canjeó, opcional
 * @param revokedBy    administrador que revocó, opcional
 * @param createdAt    emisión
 * @param expiresAt    límite exclusivo
 * @param redeemedAt   canje opcional
 * @param revokedAt    revocación opcional
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvitationDTO(
        UUID id,
        String email,
        RoleCode role,
        UUID departmentId,
        InvitationStatus status,
        UUID createdBy,
        UUID redeemedBy,
        UUID revokedBy,
        Instant createdAt,
        Instant expiresAt,
        Instant redeemedAt,
        Instant revokedAt) {

    /**
     * Proyecta solo campos permitidos dentro de la transacción JPA.
     *
     * @param invitation entidad persistida
     * @param now        instante único de evaluación
     * @return metadatos administrativos
     */
    public static InvitationDTO from(StaffInvitation invitation, Instant now) {
        return new InvitationDTO(invitation.getId(), invitation.getEmail(), invitation.getRole().getCode(),
                invitation.getDepartment() == null ? null : invitation.getDepartment().getId(),
                invitation.statusAt(now), invitation.getCreatedBy().getId(),
                invitation.getRedeemedBy() == null ? null : invitation.getRedeemedBy().getId(),
                invitation.getRevokedBy() == null ? null : invitation.getRevokedBy().getId(),
                invitation.getCreatedAt(), invitation.getExpiresAt(),
                invitation.getRedeemedAt(), invitation.getRevokedAt());
    }
}
