package es.grupo18.urbanpulse.duplicates.model;

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
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.UuidGenerator;

import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.incidents.model.Incident;

/** Relación confirmada por personal municipal; no modifica los reportes originales. */
@Entity
@Table(name = "duplicate_links", uniqueConstraints = @UniqueConstraint(name = "uk_duplicate_links_pair",
        columnNames = { "incident_id", "candidate_incident_id" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DuplicateLink {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_incident_id", nullable = false, updatable = false)
    private Incident candidateIncident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "linked_by_user_id", nullable = false, updatable = false)
    private User linkedBy;

    @Column(nullable = false, updatable = false)
    private Instant linkedAt;

    @Column(length = 2000, updatable = false)
    private String reason;

    public DuplicateLink(Incident first, Incident second, User actor, Instant now, String reason) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        if (first.getId().equals(second.getId())) throw new IllegalArgumentException("An incident cannot duplicate itself");
        if (first.getId().compareTo(second.getId()) < 0) {
            this.incident = first;
            this.candidateIncident = second;
        } else {
            this.incident = second;
            this.candidateIncident = first;
        }
        this.linkedBy = Objects.requireNonNull(actor);
        this.linkedAt = Objects.requireNonNull(now);
        this.reason = reason == null ? null : reason.trim();
    }
}
