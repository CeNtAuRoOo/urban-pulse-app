package es.grupo18.urbanpulse.service;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.entity.model.DepartmentMembership;
import es.grupo18.urbanpulse.entity.model.Role;
import es.grupo18.urbanpulse.entity.model.User;
import es.grupo18.urbanpulse.exception.ApiException;
import es.grupo18.urbanpulse.repository.DepartmentMembershipRepository;
import es.grupo18.urbanpulse.repository.DepartmentRepository;
import es.grupo18.urbanpulse.repository.RoleRepository;
import es.grupo18.urbanpulse.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Servicio encargado del ciclo de vida de las pertenencias departamentales.
 * Gestiona las altas y bajas conservando el historial y delega en PostgreSQL
 * la garantía definitiva de que no existan duplicados activos.
 */
@Service
@RequiredArgsConstructor
public class DepartmentMembershipService {

    /**
     * Repositorio utilizado para consultar los usuarios que se adscriben.
     */
    private final UserRepository users;

    /**
     * Repositorio utilizado para consultar los departamentos de destino.
     */
    private final DepartmentRepository departments;

    /**
     * Repositorio utilizado para consultar los roles del catálogo.
     */
    private final RoleRepository roles;

    /**
     * Repositorio utilizado para crear, consultar y finalizar pertenencias.
     */
    private final DepartmentMembershipRepository memberships;

    /**
     * Crea una nueva pertenencia departamental activa.
     * Solo admite roles departamentales y departamentos activos.
     *
     * @param userId       identificador del usuario que se adscribe
     * @param departmentId identificador del departamento de destino
     * @param roleCode     código del rol departamental que se asigna
     * @return pertenencia departamental persistida
     * @throws EntityNotFoundException si no existe el usuario, departamento o rol
     * @throws ApiException            si el usuario/departamento no está activo, el
     *                                 rol
     *                                 no es departamental o ya existe la
     *                                 pertenencia activa
     */
    @Transactional
    public DepartmentMembership assign(UUID userId, UUID departmentId, RoleCode roleCode) {
        if (roleCode == null || !roleCode.isDepartmental()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "DEPARTMENT_ROLE_REQUIRED",
                    "La pertenencia debe utilizar un rol de ámbito departamental.");
        }

        User user = users.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado."));
        if (!user.isActive()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USER_INACTIVE",
                    "No se puede asignar una pertenencia a una cuenta inactiva.");
        }

        var department = departments.findForUpdate(departmentId)
                .orElseThrow(() -> new EntityNotFoundException("Departamento no encontrado."));
        if (!department.isActive()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DEPARTMENT_INACTIVE",
                    "No se puede asignar una pertenencia a un departamento inactivo.");
        }

        Role role = roles.findById(roleCode)
                .orElseThrow(() -> new EntityNotFoundException("Rol no encontrado."));

        if (memberships.existsByUser_IdAndDepartment_IdAndRole_CodeAndLeftAtIsNull(
                userId, departmentId, roleCode)) {
            throw duplicateMembership();
        }

        DepartmentMembership membership = new DepartmentMembership(
                user, department, role, Instant.now());

        try {
            return memberships.saveAndFlush(membership);
        } catch (DataIntegrityViolationException exception) {
            if (isActiveMembershipDuplicate(exception)) {
                throw duplicateMembership();
            }
            throw exception;
        }
    }

    /**
     * Finaliza una pertenencia activa sin eliminar su registro histórico.
     *
     * @param membershipId identificador de la pertenencia que se desea finalizar
     * @param leftAt       fecha y hora de finalización
     * @return pertenencia actualizada con su fecha de baja
     * @throws EntityNotFoundException si la pertenencia no existe
     * @throws ApiException            si la pertenencia ya estaba finalizada o la
     *                                 fecha
     *                                 de baja es anterior a la fecha de alta
     */
    @Transactional
    public DepartmentMembership end(UUID membershipId, Instant leftAt) {
        if (leftAt == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "DEPARTMENT_MEMBERSHIP_END_DATE_REQUIRED",
                    "La fecha de baja es obligatoria.");
        }

        DepartmentMembership membership = memberships.findForUpdate(membershipId)
                .orElseThrow(() -> new EntityNotFoundException("Pertenencia departamental no encontrada."));

        try {
            membership.end(leftAt);
            return memberships.saveAndFlush(membership);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DEPARTMENT_MEMBERSHIP_CANNOT_END",
                    exception.getMessage());
        }
    }

    /**
     * Crea una excepción uniforme para los intentos de duplicar
     * una pertenencia activa.
     *
     * @return excepción de conflicto de pertenencia departamental
     */
    private ApiException duplicateMembership() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "DEPARTMENT_MEMBERSHIP_DUPLICATE",
                "El usuario ya tiene esa pertenencia activa en el departamento.");
    }

    /**
     * Identifica la violación del índice único parcial que protege
     * las pertenencias activas frente a operaciones concurrentes.
     *
     * @param exception excepción de integridad devuelta por Spring
     * @return true si la restricción vulnerada es la de pertenencias activas
     */
    private boolean isActiveMembershipDuplicate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "uk_department_memberships_active".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
