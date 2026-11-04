package es.grupo18.urbanpulse.invitations.mail;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;

import es.grupo18.urbanpulse.identity.model.RoleCode;

/**
 * Solicitud efímera en memoria; nunca se persiste ni se devuelve por HTTP.
 *
 * @param email          destinatario
 * @param code           secreto para entrega exclusiva por email
 * @param role           privilegio concedible
 * @param departmentName unidad opcional
 * @param expiresAt      caducidad exacta UTC
 */
public record StaffInvitationRequested(
        @JsonIgnore String email,
        @JsonIgnore String code,
        RoleCode role,
        String departmentName,
        Instant expiresAt) {

    /** @return representación sin destinatario ni secreto */
    @Override
    public String toString() {
        return "StaffInvitationRequested[REDACTED]";
    }
}
