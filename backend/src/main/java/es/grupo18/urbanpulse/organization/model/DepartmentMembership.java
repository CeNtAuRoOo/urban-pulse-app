package es.grupo18.urbanpulse.organization.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.UuidGenerator;

import es.grupo18.urbanpulse.identity.model.Role;
import es.grupo18.urbanpulse.identity.model.User;

/**
 * Pertenencia temporal a un departamento. La baja queda fuera de este módulo.
 */
@Entity
@Table(name = "department_memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepartmentMembership {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_code", nullable = false)
    private Role role;

    @Column(nullable = false)
    private Instant joinedAt;

    private Instant leftAt;

    /**
     * Abre una pertenencia; el índice parcial SQL impide duplicar el permiso
     * activo.
     *
     * @param user       titular verificado
     * @param department unidad activa
     * @param role       rol departamental
     * @param joinedAt   instante de concesión
     * @throws IllegalArgumentException si el rol o departamento no son admisibles
     */
    public DepartmentMembership(User user, Department department, Role role, Instant joinedAt) {
        if (!role.getCode().isDepartmental() || !department.isActive()) {
            throw new IllegalArgumentException("Pertenencia departamental no válida.");
        }

        this.user = Objects.requireNonNull(user);
        this.department = department;
        this.role = role;
        this.joinedAt = Objects.requireNonNull(joinedAt);
    }
}
