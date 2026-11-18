package es.grupo18.urbanpulse.processing.model;

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

import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.incidents.model.Incident;

/**
 * Respuesta inmutable a una solicitud exacta; las fotos siguen perteneciendo a Incident.
 */
@Entity
@Table(name = "incident_information_responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentInformationResponse {

    /** Identificador temporal UUIDv7. */
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** Padre redundante protegido por FK compuesta. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Incident incident;

    /** Solicitud atendida una sola vez. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, unique = true)
    private IncidentReview review;

    /** Discriminante SQL fijo que impide responder a VALIDATE/REJECT. */
    @Column(nullable = false, length = 32, updatable = false)
    private String requestDecision = "REQUEST_INFORMATION";

    /** Informante original revalidado por el servicio. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responded_by_user_id", nullable = false)
    private User respondedBy;

    /** Mensaje normalizado independiente del reporte. */
    @Column(nullable = false, length = 2000)
    private String message;

    /** Instante de respuesta UTC. */
    @Column(nullable = false)
    private Instant respondedAt;

    /**
     * Construye una respuesta a una revisión de solicitud.
     *
     * @param review  solicitud pendiente validada bajo bloqueo
     * @param author  informante
     * @param message texto de 3–2000 puntos Unicode
     * @param at      instante UTC
     * @throws IllegalArgumentException si la revisión no solicita información
     */
    public IncidentInformationResponse(IncidentReview review, User author, String message, Instant at) {
        this.review = Objects.requireNonNull(review);
        if (review.getDecision() != ReviewDecision.REQUEST_INFORMATION) {
            throw new IllegalArgumentException("La revisión no solicita información.");
        }
        this.incident = review.getIncident();
        this.respondedBy = Objects.requireNonNull(author);
        this.message = Incident.normalizeText(message, 3, 2000);
        this.respondedAt = Objects.requireNonNull(at);
    }
}
