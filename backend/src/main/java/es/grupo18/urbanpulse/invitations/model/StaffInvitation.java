package es.grupo18.urbanpulse.invitations.model;

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

import com.fasterxml.jackson.annotation.JsonIgnore;

import es.grupo18.urbanpulse.identity.model.Role;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.organization.model.Department;

/**
 * Invitación de un uso ligada a un correo, independiente de la existencia de
 * cuenta.
 */
@Entity
@Table(name = "staff_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffInvitation {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] codeHash;

    @Column(nullable = false, length = 254)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_code", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redeemed_by_user_id")
    private User redeemedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_user_id")
    private User revokedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant redeemedAt;
    private Instant revokedAt;

    /**
     * Construye una invitación sin crear identidad ni almacenar el código original.
     *
     * @param hash       digest HMAC de 32 bytes
     * @param email      destinatario obligatorio, normalizado como User
     * @param role       privilegio municipal
     * @param department unidad requerida exclusivamente para roles departamentales
     * @param creator    administrador emisor
     * @param now        instante de emisión del reloj del servidor
     * @param expires    instante final exclusivo
     * @throws IllegalArgumentException si se incumple el ámbito, digest o fechas
     */
    public StaffInvitation(byte[] hash, String email, Role role, Department department, User creator, Instant now,
            Instant expires) {

        String normalized = User.normalizeEmail(email);

        if (hash == null || hash.length != 32 || normalized == null || normalized.isBlank()
                || normalized.length() > 254 || role.getCode() == RoleCode.CITIZEN
                || role.getCode().isDepartmental() != (department != null)
                || !expires.isAfter(now)) {
            throw new IllegalArgumentException("Invitación no válida.");
        }

        this.codeHash = hash.clone();
        this.email = normalized;
        this.role = role;
        this.department = department;
        this.createdBy = Objects.requireNonNull(creator);
        this.createdAt = now;
        this.expiresAt = expires;
    }

    /**
     * Calcula el estado sin scheduler ni escritura adicional.
     *
     * @param now instante de evaluación
     * @return estado final o ACTIVE antes de la caducidad
     */
    public InvitationStatus statusAt(Instant now) {
        if (redeemedAt != null) {
            return InvitationStatus.REDEEMED;
        }

        if (revokedAt != null) {
            return InvitationStatus.REVOKED;
        }

        return now.isBefore(expiresAt) ? InvitationStatus.ACTIVE : InvitationStatus.EXPIRED;
    }

    /**
     * Marca el canje dentro de la transacción que concede el permiso.
     *
     * @param user titular que canjea
     * @param now  instante del canje
     * @throws IllegalStateException si ya no está activa o el tiempo retrocede
     */
    public void redeem(User user, Instant now) {
        requireActive(now);
        redeemedBy = Objects.requireNonNull(user);
        redeemedAt = now;
    }

    /**
     * Registra la revocación y su actor sin borrar historia.
     *
     * @param admin administrador responsable
     * @param now   instante de revocación
     * @throws IllegalStateException si ya no está activa o el tiempo retrocede
     */
    public void revoke(User admin, Instant now) {
        requireActive(now);
        revokedBy = Objects.requireNonNull(admin);
        revokedAt = now;
    }

    /**
     * Comprueba que la invitación esté activa en el instante indicado y que
     * dicho instante no sea anterior a su creación.
     *
     * @param now instante en el que se comprueba el estado
     * @throws IllegalStateException si la invitación no está activa o el
     *                               instante es anterior a su creación
     */
    private void requireActive(Instant now) {
        if (now.isBefore(createdAt) || statusAt(now) != InvitationStatus.ACTIVE) {
            throw new IllegalStateException("La invitación no está activa.");
        }
    }
}
