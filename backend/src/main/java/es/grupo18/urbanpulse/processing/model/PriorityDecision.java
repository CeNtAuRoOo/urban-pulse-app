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
import es.grupo18.urbanpulse.incidents.model.IncidentPriority;

/**
 * Decisión histórica. ProcessingService actualiza la prioridad del agregado en
 * la misma transacción.
 */
@Entity
@Table(name = "priority_decisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriorityDecision {

    /** Identificador histórico UUIDv7. */
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** Incidencia a la que pertenece el registro. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Incident incident;

    /** Responsable interno de la decisión. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decided_by_user_id", nullable = false)
    private User decidedBy;

    /** Nivel acordado en esta decisión. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentPriority priority;

    /** Origen de dominio; el servicio fija MANUAL en esta fase. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PrioritySource source;

    /** Texto interno normalizado, de 3–2000 puntos Unicode. */
    @Column(nullable = false, length = 2000)
    private String justification;

    /** Instante UTC de la decisión. */
    @Column(nullable = false)
    private Instant decidedAt;

    /**
     * Conserva responsable y justificación; el servicio coordina autorización,
     * agregado y auditoría.
     *
     * @param incident      incidencia afectada
     * @param decidedBy     responsable de la decisión
     * @param priority      nivel acordado
     * @param source        origen manual o asistido
     * @param justification texto obligatorio de 3–2000 puntos Unicode normalizados
     * @param decidedAt     instante de decisión
     * @throws es.grupo18.urbanpulse.shared.error.ApiException justificación
     *                                                         inválida (422)
     * @throws NullPointerException                            referencia,
     *                                                         prioridad, origen o
     *                                                         instante ausente
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
