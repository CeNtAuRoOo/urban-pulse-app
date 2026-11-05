package es.grupo18.urbanpulse.identity.dto;

import java.time.Instant;

import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.organization.dto.DepartmentResponse;
import es.grupo18.urbanpulse.organization.model.DepartmentMembership;

/**
 * Representa una pertenencia departamental incluida en la respuesta de un
 * usuario.
 * Expone los datos del departamento, el rol desempeñado y las fechas que
 * permiten
 * distinguir las adscripciones activas de las que forman parte del historial.
 *
 * @param department departamento al que corresponde la pertenencia
 * @param role       código del rol departamental
 * @param joinedAt   fecha y hora de inicio de la pertenencia
 * @param leftAt     fecha y hora de finalización, o null si continúa activa
 */
public record DepartmentMembershipDTO(
        DepartmentResponse department,
        RoleCode role,
        Instant joinedAt,
        Instant leftAt) {

    /**
     * Construye el DTO a partir de una entidad de pertenencia departamental.
     * Selecciona únicamente los datos del departamento y de la adscripción
     * que pueden exponerse en la respuesta de la API.
     *
     * @param membership entidad de pertenencia departamental
     * @return DTO con el departamento, el rol y las fechas de la pertenencia
     */
    public static DepartmentMembershipDTO from(DepartmentMembership membership) {
        var department = membership.getDepartment();
        return new DepartmentMembershipDTO(
                new DepartmentResponse(
                        department.getId(),
                        department.getName(),
                        department.getCode(),
                        department.getDescription(),
                        department.isActive()),
                membership.getRole().getCode(),
                membership.getJoinedAt(),
                membership.getLeftAt());
    }
}
