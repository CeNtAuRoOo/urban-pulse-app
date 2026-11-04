package es.grupo18.urbanpulse.invitations.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.invitations.model.InvitationStatus;

/**
 * Filtros exactos y paginación aplicados en SQL antes de proyectar los
 * resultados.
 *
 * @param status       estado opcional
 * @param email        correo exacto normalizado opcional
 * @param role         rol opcional
 * @param departmentId unidad opcional
 * @param page         primera página 1
 * @param pageSize     tamaño entre 1 y 100, por defecto 20
 */
public record InvitationQuery(
        InvitationStatus status,
        @Email @Size(max = 254) String email,
        RoleCode role,
        UUID departmentId,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize) {

    /** Aplica defaults compartidos y normalización antes de Bean Validation. */
    public InvitationQuery {
        email = User.normalizeEmail(email);
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
