package es.grupo18.urbanpulse.entity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.entity.model.DepartmentMembership;
import es.grupo18.urbanpulse.entity.model.Role;
import es.grupo18.urbanpulse.entity.model.User;

/**
 * Pruebas unitarias de las invariantes de dominio de DepartmentMembership.
 * Comprueba el ámbito del rol y las transiciones entre pertenencia activa
 * y pertenencia histórica.
 */
class DepartmentMembershipTest {

    private User user;
    private Department department;
    private Role role;
    private Instant joinedAt;

    /**
     * Prepara las entidades relacionadas y un rol departamental válido.
     */
    @BeforeEach
    void setUp() {
        user = mock(User.class);
        department = mock(Department.class);
        role = mock(Role.class);
        joinedAt = Instant.parse("2026-01-01T09:00:00Z");

        when(role.getCode()).thenReturn(RoleCode.TECHNICIAN);
    }

    /**
     * Comprueba que una pertenencia nueva comienza activa y sin fecha de baja.
     */
    @Test
    void newMembershipStartsActive() {
        DepartmentMembership membership = new DepartmentMembership(user, department, role, joinedAt);

        assertTrue(membership.isActive());
        assertNull(membership.getLeftAt());
    }

    /**
     * Comprueba que una pertenencia finalizada conserva su fecha de baja
     * y deja de considerarse activa.
     */
    @Test
    void endChangesMembershipToHistorical() {
        DepartmentMembership membership = new DepartmentMembership(user, department, role, joinedAt);
        Instant leftAt = joinedAt.plusSeconds(3600);

        membership.end(leftAt);

        assertFalse(membership.isActive());
        assertTrue(membership.getLeftAt().equals(leftAt));
    }

    /**
     * Comprueba que no se puede finalizar una pertenencia con una fecha
     * anterior a su fecha de alta.
     */
    @Test
    void endRejectsDateBeforeJoinDate() {
        DepartmentMembership membership = new DepartmentMembership(user, department, role, joinedAt);

        assertThrows(
                IllegalArgumentException.class,
                () -> membership.end(joinedAt.minusSeconds(1)));

        assertTrue(membership.isActive());
    }

    /**
     * Comprueba que una pertenencia no puede finalizarse dos veces.
     */
    @Test
    void endRejectsAlreadyEndedMembership() {
        DepartmentMembership membership = new DepartmentMembership(user, department, role, joinedAt);
        membership.end(joinedAt.plusSeconds(3600));

        assertThrows(
                IllegalStateException.class,
                () -> membership.end(joinedAt.plusSeconds(7200)));
    }

    /**
     * Comprueba que una pertenencia no admite roles de ámbito global.
     */
    @Test
    void constructorRejectsGlobalRole() {
        when(role.getCode()).thenReturn(RoleCode.ADMIN);

        assertThrows(
                IllegalArgumentException.class,
                () -> new DepartmentMembership(user, department, role, joinedAt));
    }
}
