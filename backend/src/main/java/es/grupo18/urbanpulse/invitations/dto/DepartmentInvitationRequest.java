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
 * Solicitud departamental sin atributos de identidad ni vigencia controlados
 * por cliente.
 *
 * @param email        correo obligatorio
 * @param role         rol departamental
 * @param departmentId unidad obligatoria
 */
public record DepartmentInvitationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull RoleCode role,
        @NotNull UUID departmentId) implements InvitationRequest {

    /** Normaliza el correo antes de validar. */
    public DepartmentInvitationRequest {
        email = User.normalizeEmail(email);
    }

    /** @return true únicamente para roles departamentales */
    @AssertTrue
    @JsonIgnore
    public boolean isInvitableRole() {
        return role != null && role.isDepartmental();
    }
}
