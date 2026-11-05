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
 * Persistencia de transiciones futuras. No representa el alta ni ejecuta una
 * transición.
 */
@Entity
@Table(name = "status_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StatusChange {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Incident incident;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncidentStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncidentStatus toStatus;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private User changedBy;
    @Column(length = 2000)
    private String reason;
    @Column(length = 5000)
    private String resolutionNotes;
    @Column(nullable = false)
    private Instant changedAt;

    /**
     * Registra datos ya autorizados por la futura tramitación; no modifica
     * Incident. Los textos opcionales conservan blancos y formato; exigir motivos
     * según transición corresponde al futuro servicio de Tramitación.
     * 
     * @param incident        incidencia existente
     * @param fromStatus      estado anterior obligatorio; CLOSED es terminal
     * @param toStatus        estado posterior distinto
     * @param changedBy       actor, o null para un futuro proceso de sistema
     * @param reason          motivo opcional, máximo 2000 puntos Unicode
     * @param resolutionNotes detalle opcional, máximo 5000 puntos Unicode
     * @param changedAt       instante de transición
     * @throws es.grupo18.urbanpulse.shared.error.ApiException texto no representable o demasiado largo (422)
     * @throws NullPointerException referencia, estado o fecha obligatoria ausente
     * @throws IllegalArgumentException si no existe cambio o el origen es terminal
     */
    public StatusChange(Incident incident, IncidentStatus fromStatus, IncidentStatus toStatus, User changedBy,
            String reason, String resolutionNotes, Instant changedAt) {
        this.incident = Objects.requireNonNull(incident);
        this.fromStatus = Objects.requireNonNull(fromStatus);
        this.toStatus = Objects.requireNonNull(toStatus);
        if (fromStatus == toStatus || fromStatus == IncidentStatus.CLOSED) {
            throw new IllegalArgumentException("Transición estructural no válida.");
        }
        this.changedBy = changedBy;
        this.reason = optionalText(reason, 2000);
        this.resolutionNotes = optionalText(resolutionNotes, 5000);
        this.changedAt = Objects.requireNonNull(changedAt);
    }

    /**
     * Conserva el texto opcional, comprobando capacidad SQL y representación UTF-8.
     *
     * @param value texto original, incluido null o blanco
     * @param max máximo de puntos Unicode
     * @return el mismo valor si puede persistirse
     * @throws es.grupo18.urbanpulse.shared.error.ApiException si supera el límite o contiene NUL/sustitutos aislados
     */
    private static String optionalText(String value, int max) {
        if (value != null && (value.codePointCount(0, value.length()) > max
                || value.codePoints().anyMatch(c -> c == 0 || (c >= 0xD800 && c <= 0xDFFF)))) {
            throw new es.grupo18.urbanpulse.shared.error.ApiException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR",
                    "Texto fuera de los límites admitidos.");
        }
        return value;
    }
}
