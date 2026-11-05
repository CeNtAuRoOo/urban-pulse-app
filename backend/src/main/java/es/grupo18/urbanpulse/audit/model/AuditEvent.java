package es.grupo18.urbanpulse.audit.model;

import java.time.Instant;
import java.util.Map;
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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import es.grupo18.urbanpulse.identity.model.User;

/**
 * Registro funcional persistente, sin secretos ni datos innecesarios del
 * destinatario.
 */
@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 80)
    private String resourceType;

    @Column(nullable = false, length = 128)
    private String resourceId;

    @Column(length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> details;

    @Column(length = 128)
    private String requestId;

    @Column(nullable = false)
    private Instant occurredAt;

    /**
     * Crea un evento mínimo, reutilizable por otros módulos.
     *
     * @param actor        cuenta responsable, opcional para futuros eventos del
     *                     sistema
     * @param action       código estable de acción
     * @param resourceType tipo lógico de recurso
     * @param resourceId   identificador lógico, no FK con borrado en cascada
     * @param details      metadatos permitidos por el servicio; nunca secretos ni
     *                     email
     * @param occurredAt   instante de la acción
     */
    public AuditEvent(User actor, String action, String resourceType, String resourceId, Map<String, String> details,
            Instant occurredAt) {

        this.actor = actor;
        this.action = Objects.requireNonNull(action);
        this.resourceType = Objects.requireNonNull(resourceType);
        this.resourceId = Objects.requireNonNull(resourceId);
        this.details = Map.copyOf(details);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}
