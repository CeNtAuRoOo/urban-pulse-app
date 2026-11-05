package es.grupo18.urbanpulse.incidents.model;

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

/**
 * Estructura de revisión futura; no cambia estados ni se crea al
 * registrar/corregir.
 */
@Entity
@Table(name = "incident_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentReview {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Incident incident;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewed_by_user_id", nullable = false)
    private User reviewedBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewDecision decision;
    @Column(length = 2000)
    private String reason;
    @Column(nullable = false)
    private Instant reviewedAt;

    /**
     * Construye evidencia de revisión sin ejecutar reglas de tramitación.
     * 
     * @param incident   incidencia revisada
     * @param reviewedBy responsable obligatorio
     * @param decision   decisión; REQUEST_INFORMATION no implica cambio de estado
     * @param reason     motivo recortado de 3–2000 puntos Unicode; obligatorio en
     *                   REJECT/REQUEST_INFORMATION, nullable en VALIDATE
     * @param reviewedAt instante UTC
     * @throws es.grupo18.urbanpulse.shared.error.ApiException si el motivo incumple las reglas (422)
     * @throws NullPointerException si falta una referencia, decisión o fecha obligatoria
     */
    public IncidentReview(Incident incident, User reviewedBy, ReviewDecision decision, String reason,
            Instant reviewedAt) {
        this.incident = Objects.requireNonNull(incident);
        this.reviewedBy = Objects.requireNonNull(reviewedBy);
        this.decision = Objects.requireNonNull(decision);
        this.reason = decision == ReviewDecision.VALIDATE && reason == null ? null
                : Incident.normalizeText(reason, 3, 2000);
        this.reviewedAt = Objects.requireNonNull(reviewedAt);
    }
}
