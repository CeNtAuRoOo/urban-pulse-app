package es.grupo18.urbanpulse.organization.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.grupo18.urbanpulse.identity.model.Role;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.identity.repository.RoleRepository;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.organization.model.Department;
import es.grupo18.urbanpulse.organization.model.DepartmentMembership;
import es.grupo18.urbanpulse.organization.repository.DepartmentMembershipRepository;
import es.grupo18.urbanpulse.organization.repository.DepartmentRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias del ciclo de vida de las pertenencias departamentales.
 * Comprueba las reglas de asignación, la prevención de duplicados y la
 * finalización de pertenencias sin eliminar su historial.
 */
@ExtendWith(MockitoExtension.class)
class DepartmentMembershipServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private DepartmentRepository departments;

    @Mock
    private RoleRepository roles;

    @Mock
    private DepartmentMembershipRepository memberships;

    @InjectMocks
    private DepartmentMembershipService service;

    private UUID userId;
    private UUID departmentId;
    private UUID membershipId;
    private User user;
    private Department department;
    private Role role;

    /**
     * Prepara los identificadores y las entidades simuladas compartidas
     * por los escenarios de asignación y finalización.
     */
    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        membershipId = UUID.randomUUID();
        user = mock(User.class);
        department = mock(Department.class);
        role = mock(Role.class);

    }

    /**
     * Comprueba que una asignación válida crea y persiste una nueva pertenencia.
     */
    @Test
    void assignCreatesDepartmentalMembership() {
        when(user.isActive()).thenReturn(true);
        when(user.isEmailVerified()).thenReturn(true);
        when(department.isActive()).thenReturn(true);
        when(role.getCode()).thenReturn(RoleCode.TECHNICIAN);
        when(users.findForEmailVerification(userId)).thenReturn(Optional.of(user));
        when(departments.findForUpdate(departmentId)).thenReturn(Optional.of(department));
        when(roles.findById(RoleCode.TECHNICIAN)).thenReturn(Optional.of(role));
        when(memberships.existsByUserIdAndDepartmentIdAndRoleCodeAndLeftAtIsNull(
                userId, departmentId, RoleCode.TECHNICIAN)).thenReturn(false);
        when(memberships.saveAndFlush(any(DepartmentMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DepartmentMembership result = service.assign(
                userId, departmentId, RoleCode.TECHNICIAN);

        assertNotNull(result);
        assertEquals(RoleCode.TECHNICIAN, result.getRole().getCode());
        verify(memberships).saveAndFlush(any(DepartmentMembership.class));
    }

    /**
     * Comprueba que no se pueden utilizar roles globales en una pertenencia.
     */
    @Test
    void assignRejectsGlobalRole() {
        assertThrows(
                ApiException.class,
                () -> service.assign(userId, departmentId, RoleCode.ADMIN));

        verify(users, never()).findById(any());
        verify(memberships, never()).saveAndFlush(any());
    }

    /**
     * Comprueba que una cuenta inactiva no puede recibir una pertenencia.
     */
    @Test
    void assignRejectsInactiveUser() {
        when(user.isActive()).thenReturn(false);
        when(users.findForEmailVerification(userId)).thenReturn(Optional.of(user));

        assertThrows(
                ApiException.class,
                () -> service.assign(userId, departmentId, RoleCode.TECHNICIAN));

        verify(departments, never()).findForUpdate(any());
        verify(memberships, never()).saveAndFlush(any());
    }

    /**
     * Comprueba que no se asignan pertenencias a departamentos inactivos.
     */
    @Test
    void assignRejectsInactiveDepartment() {
        when(user.isActive()).thenReturn(true);
        when(user.isEmailVerified()).thenReturn(true);
        when(department.isActive()).thenReturn(false);
        when(users.findForEmailVerification(userId)).thenReturn(Optional.of(user));
        when(departments.findForUpdate(departmentId)).thenReturn(Optional.of(department));

        assertThrows(
                ApiException.class,
                () -> service.assign(userId, departmentId, RoleCode.TECHNICIAN));

        verify(memberships, never()).saveAndFlush(any());
    }

    /**
     * Comprueba que una pertenencia activa duplicada se rechaza antes de persistir.
     */
    @Test
    void assignRejectsExistingActiveMembership() {
        when(user.isActive()).thenReturn(true);
        when(user.isEmailVerified()).thenReturn(true);
        when(department.isActive()).thenReturn(true);
        when(users.findForEmailVerification(userId)).thenReturn(Optional.of(user));
        when(departments.findForUpdate(departmentId)).thenReturn(Optional.of(department));
        when(roles.findById(RoleCode.TECHNICIAN)).thenReturn(Optional.of(role));
        when(memberships.existsByUserIdAndDepartmentIdAndRoleCodeAndLeftAtIsNull(
                userId, departmentId, RoleCode.TECHNICIAN)).thenReturn(true);

        assertThrows(
                ApiException.class,
                () -> service.assign(userId, departmentId, RoleCode.TECHNICIAN));

        verify(memberships, never()).saveAndFlush(any());
    }

    /**
     * Comprueba que una colisión concurrente con el índice único parcial
     * se traduce al mismo conflicto de dominio que una duplicidad detectada
     * antes de persistir.
     */
    @Test
    void assignMapsConcurrentDuplicateConstraintToConflict() {
        when(user.isActive()).thenReturn(true);
        when(user.isEmailVerified()).thenReturn(true);
        when(department.isActive()).thenReturn(true);
        when(role.getCode()).thenReturn(RoleCode.TECHNICIAN);
        when(users.findForEmailVerification(userId)).thenReturn(Optional.of(user));
        when(departments.findForUpdate(departmentId)).thenReturn(Optional.of(department));
        when(roles.findById(RoleCode.TECHNICIAN)).thenReturn(Optional.of(role));
        when(memberships.existsByUserIdAndDepartmentIdAndRoleCodeAndLeftAtIsNull(
                userId, departmentId, RoleCode.TECHNICIAN)).thenReturn(false);

        ConstraintViolationException violation = mock(ConstraintViolationException.class);
        when(violation.getSQLState()).thenReturn("23505");
        when(violation.getConstraintName()).thenReturn("department_memberships_active_unique");
        when(memberships.saveAndFlush(any(DepartmentMembership.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicated membership", violation));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.assign(userId, departmentId, RoleCode.TECHNICIAN));

        assertEquals("DEPARTMENT_MEMBERSHIP_DUPLICATE", exception.code());
    }

    /**
     * Comprueba que se devuelve un error de recurso inexistente si no existe
     * el usuario al que se intenta asignar la pertenencia.
     */
    @Test
    void assignThrowsWhenUserDoesNotExist() {
        when(users.findForEmailVerification(userId)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> service.assign(userId, departmentId, RoleCode.TECHNICIAN));
    }

    /**
     * Comprueba que finalizar una pertenencia persiste la fecha de baja
     * sin eliminar la entidad.
     */
    @Test
    void endFinalizesAndPersistsMembership() {
        DepartmentMembership membership = mock(DepartmentMembership.class);
        Instant leftAt = Instant.parse("2026-06-01T09:00:00Z");

        when(memberships.findOwnerId(membershipId)).thenReturn(Optional.of(userId));
        when(users.findForEmailVerification(userId)).thenReturn(Optional.of(user));
        when(memberships.findForUpdate(membershipId)).thenReturn(Optional.of(membership));
        when(memberships.saveAndFlush(membership)).thenReturn(membership);

        DepartmentMembership result = service.end(membershipId, leftAt);

        assertNotNull(result);
        verify(membership).end(leftAt);
        verify(memberships).saveAndFlush(membership);
    }

    /**
     * Comprueba que finalizar una pertenencia inexistente devuelve un error 404
     * de dominio sin intentar modificar ninguna entidad.
     */
    @Test
    void endThrowsWhenMembershipDoesNotExist() {
        when(memberships.findOwnerId(membershipId)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> service.end(membershipId, Instant.now()));

        verify(memberships, never()).saveAndFlush(any());
    }

    @Test
    void assignRejectsUnverifiedUserBeforeLockingDepartment() {
        when(users.findForEmailVerification(userId)).thenReturn(Optional.of(user));
        when(user.isActive()).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class,
                () -> service.assign(userId, departmentId, RoleCode.TECHNICIAN));
        assertEquals("USER_EMAIL_NOT_VERIFIED", ex.code());
        verify(departments, never()).findForUpdate(any());
        verify(memberships, never()).saveAndFlush(any());
    }
}
