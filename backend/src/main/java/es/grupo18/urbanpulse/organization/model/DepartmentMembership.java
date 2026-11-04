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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.UuidGenerator;

import es.grupo18.urbanpulse.identity.model.Role;
import es.grupo18.urbanpulse.identity.model.User;

/**
 * Representa la pertenencia temporal de un usuario a un departamento
 * mediante un rol de ámbito departamental.
 * Conserva el historial de adscripciones mediante las fechas de alta y baja,
 * sin sustituir los roles globales gestionados por UserRole.
 */
@Entity
@Table(name = "department_memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepartmentMembership {

    /**
     * Identificador único de la pertenencia departamental.
     * Se genera automáticamente utilizando UUID versión 7.
     */
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /**
     * Usuario al que pertenece la adscripción departamental.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Departamento al que se adscribe el usuario.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /**
     * Rol departamental asociado a esta pertenencia.
     * Solo admite DEPARTMENT_COORDINATOR o TECHNICIAN.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_code", nullable = false)
    private Role role;

    /**
     * Fecha y hora en la que comienza la pertenencia.
     */
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    /**
     * Fecha y hora en la que finaliza la pertenencia.
     * Permanece a null mientras la adscripción esté activa.
     */
    @Column(name = "left_at")
    private Instant leftAt;

    /**
     * Crea una nueva pertenencia departamental activa.
     *
     * @param user       usuario que se adscribe al departamento
     * @param department departamento al que se incorpora el usuario
     * @param role       rol departamental que desempeñará
     * @param joinedAt   fecha y hora de inicio de la pertenencia
     * @throws NullPointerException     si alguno de los argumentos es null
     * @throws IllegalArgumentException si el rol no es departamental o el
     *                                  departamento está inactivo
     */
    public DepartmentMembership(User user, Department department, Role role, Instant joinedAt) {
        this.user = Objects.requireNonNull(user, "El usuario es obligatorio.");
        this.department = Objects.requireNonNull(department, "El departamento es obligatorio.");
        this.role = Objects.requireNonNull(role, "El rol es obligatorio.");
        this.joinedAt = Objects.requireNonNull(joinedAt, "La fecha de alta es obligatoria.");

        if (!role.getCode().isDepartmental() || !department.isActive()) {
            throw new IllegalArgumentException("DepartmentMembership requires a departmental role");
        }
    }

    /**
     * Comprueba la coherencia de las fechas antes de persistir la pertenencia.
     * Mantiene la fecha de alta recibida por el constructor.
     */
    @PrePersist
    void validateBeforePersist() {
        if (leftAt != null && leftAt.isBefore(joinedAt)) {
            throw new IllegalStateException("La fecha de baja no puede ser anterior a la fecha de alta.");
        }
    }

    /**
     * Finaliza una pertenencia activa sin eliminar su registro histórico.
     *
     * @param leftAt fecha y hora en la que termina la pertenencia
     * @throws NullPointerException     si la fecha recibida es null
     * @throws IllegalStateException    si la pertenencia ya está finalizada
     * @throws IllegalArgumentException si la fecha es anterior al alta
     */
    public void end(Instant leftAt) {
        Objects.requireNonNull(leftAt, "La fecha de baja es obligatoria.");

        if (this.leftAt != null) {
            throw new IllegalStateException("La pertenencia ya está finalizada.");
        }
        if (leftAt.isBefore(joinedAt)) {
            throw new IllegalArgumentException("La fecha de baja no puede ser anterior al alta.");
        }

        this.leftAt = leftAt;
    }

    /**
     * Comprueba si la pertenencia continúa activa.
     *
     * @return true si no se ha registrado una fecha de baja
     */
    public boolean isActive() {
        return leftAt == null;
    }
}
