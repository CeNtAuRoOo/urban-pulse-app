package es.grupo18.urbanpulse.organization.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.organization.model.DepartmentMembership;

/**
 * Persistencia de pertenencias con historial e índice único de permiso activo.
 */
public interface DepartmentMembershipRepository extends JpaRepository<DepartmentMembership, UUID> {

    /**
     * Comprueba un permiso vigente, bajo el bloqueo previo de su titular.
     *
     * @param userId       titular
     * @param departmentId unidad
     * @param roleCode     rol departamental
     * @return true si ya existe el permiso activo
     */
    boolean existsByUserIdAndDepartmentIdAndRoleCodeAndLeftAtIsNull(
            UUID userId, UUID departmentId, RoleCode roleCode);
}
