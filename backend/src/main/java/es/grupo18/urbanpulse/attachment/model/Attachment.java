package es.grupo18.urbanpulse.attachment.model;

import java.time.Instant;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.validator.constraints.CodePointLength;

import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.incidents.model.Incident;

/**
 * Esta entidad representa un archivo externo asociado a una Incidencia de
 * UrbanPulse.
 */
@Entity
@Table(name = "attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User user;

    // Nombre del archivo
    @Column(name = "original_filename", nullable = false, updatable = false, length = 128)
    private String originalFilename;

    // Tipo de contenido
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, updatable = false, length = 20)
    private ContentType contentType;

    // Clave para el recurso
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    // Tamaño en bytes del recurso
    @Min(1)
    @Max(10485760)
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    // Tiempos de creación, creado automáticamente.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Tiempo de eliminación
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Descripción de la evidencia
    @CodePointLength(max = 300)
    @Column(name = "description", length = 300)
    private String description;

    /**
     * Crea metadatos de una imagen ya validada, pendientes de persistir.
     *
     * @return nueva entidad sin identidad asignada todavía
     *
     * @param incident         incidencia a la que pertenece el archivo (no puede
     *                         ser nula)
     * @param user             usuario que lo sube
     * @param originalFilename nombre original del archivo
     * @param contentType      MIME del archivo
     * @param storageKey       clave del objeto en Object Storage
     * @param sizeBytes        tamaño del archivo en bytes
     * @param description      descripción opcional
     */
    public static Attachment create(
            Incident incident,
            User user,
            String originalFilename,
            ContentType contentType,
            String storageKey,
            long sizeBytes,
            String description) {

        if (incident == null) {
            throw new IllegalArgumentException("incident is required");
        }

        Attachment attachment = new Attachment();

        attachment.incident = incident;
        attachment.user = user;
        attachment.originalFilename = originalFilename;
        attachment.contentType = contentType;
        attachment.storageKey = storageKey;
        attachment.sizeBytes = sizeBytes;
        attachment.description = description;

        return attachment;
    }

    /**
     * Obtiene la identidad del padre sin exponer la entidad.
     *
     * @return identidad de la incidencia obligatoria
     */
    public UUID getIncidentId() {
        return incident == null ? null : incident.getId();
    }

    /**
     * Establece la baja una sola vez; conserva metadatos, autor y objeto externo.
     */
    public void markAsDeleted() {
        if (this.deletedAt == null) {
            this.deletedAt = Instant.now();
        }
    }
}
