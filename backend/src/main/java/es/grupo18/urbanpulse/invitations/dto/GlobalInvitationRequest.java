package es.grupo18.urbanpulse.invitations.dto;

import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;

import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;

/**
 * Solicitud global estricta: no admite departmentId, ni siquiera null.
 *
 * @param email correo obligatorio
 * @param role  OPERATOR o ADMIN
 */
public record GlobalInvitationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull RoleCode role) implements InvitationRequest {

    /** Normaliza el correo con la misma política que el registro. */
    public GlobalInvitationRequest {
        email = User.normalizeEmail(email);
    }

    /** @return true únicamente para roles globales invitables */
    @AssertTrue
    @JsonIgnore
    public boolean isInvitableRole() {
        return role == RoleCode.ADMIN || role == RoleCode.OPERATOR;
    }

    /** @return null; este campo no forma parte del JSON global */
    @Override
    public UUID departmentId() {
        return null;
    }
}
