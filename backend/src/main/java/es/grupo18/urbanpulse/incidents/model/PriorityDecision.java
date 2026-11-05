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
 * Evidencia persistente de una decisión futura; no asigna prioridad a Incident.
 */
@Entity
@Table(name = "priority_decisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriorityDecision {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Incident incident;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decided_by_user_id", nullable = false)
    private User decidedBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentPriority priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PrioritySource source;
    @Column(nullable = false, length = 2000)
    private String justification;
    @Column(nullable = false)
    private Instant decidedAt;

    /**
     * Conserva responsable y justificación sin implementar priorización.
     * 
     * @param incident      incidencia afectada
     * @param decidedBy     responsable de la decisión
     * @param priority      nivel acordado
     * @param source        origen manual o asistido
     * @param justification texto obligatorio de 3–2000 puntos Unicode normalizados
     * @param decidedAt     instante de decisión
     * @throws es.grupo18.urbanpulse.shared.error.ApiException justificación inválida (422)
     * @throws NullPointerException referencia, prioridad, origen o instante ausente
     */
    public PriorityDecision(Incident incident, User decidedBy, IncidentPriority priority, PrioritySource source,
            String justification, Instant decidedAt) {
        this.incident = Objects.requireNonNull(incident);
        this.decidedBy = Objects.requireNonNull(decidedBy);
        this.priority = Objects.requireNonNull(priority);
        this.source = Objects.requireNonNull(source);
        this.justification = Incident.normalizeText(justification, 3, 2000);
        this.decidedAt = Objects.requireNonNull(decidedAt);
    }
}
