package es.grupo18.urbanpulse.processing.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.incidents.model.Incident;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.shared.error.ApiException;

/**
 * Registro histórico de revisión. ProcessingService coordina la decisión y su transición atómica.
 */
@Entity
@Table(name = "incident_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentReview {

    /** Identificador histórico UUIDv7. */
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** Incidencia a la que pertenece el registro. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Incident incident;

    /** Cuenta municipal que efectuó la revisión; solo visible internamente. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewed_by_user_id", nullable = false)
    private User reviewedBy;

    /** Decisión municipal inmutable. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewDecision decision;

    /** Motivo; obligatorio en rechazo/solicitud, ausente en VALIDATE nuevo. */
    @Column(length = 2000)
    private String reason;

    /** Instante UTC de la revisión. */
    @Column(nullable = false)
    private Instant reviewedAt;

    /**
     * Snapshot; null exclusivamente para revisiones anteriores a V2 no reconstruibles.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private IncidentStatus resultingStatus;

    /**
     * Construye evidencia de revisión sin ejecutar reglas de tramitación.
     *
     * @param incident   incidencia revisada
     * @param reviewedBy responsable obligatorio
     * @param decision   decisión; REQUEST_INFORMATION resulta en AWAITING_INFORMATION
     * @param reason     motivo recortado de 3–2000 puntos Unicode; obligatorio en REJECT/REQUEST_INFORMATION, prohibido
     *                   en VALIDATE
     * @param reviewedAt instante UTC
     * @throws ApiException         si el motivo incumple las reglas (422)
     * @throws NullPointerException si falta una referencia, decisión o fecha obligatoria
     */
    public IncidentReview(Incident incident, User reviewedBy, ReviewDecision decision, String reason,
            Instant reviewedAt) {
        this.incident = Objects.requireNonNull(incident);
        this.reviewedBy = Objects.requireNonNull(reviewedBy);
        this.decision = Objects.requireNonNull(decision);
        if (decision == ReviewDecision.VALIDATE && reason != null) {
            throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR",
                    "VALIDATE no admite motivo.");
        }
        this.resultingStatus = switch (decision) {
        case VALIDATE -> IncidentStatus.VALIDATED;
        case REJECT -> IncidentStatus.REJECTED;
        case REQUEST_INFORMATION -> IncidentStatus.AWAITING_INFORMATION;
        };
        this.reason = decision == ReviewDecision.VALIDATE && reason == null ? null
                : Incident.normalizeText(reason, 3, 2000);
        this.reviewedAt = Objects.requireNonNull(reviewedAt);
    }
}
