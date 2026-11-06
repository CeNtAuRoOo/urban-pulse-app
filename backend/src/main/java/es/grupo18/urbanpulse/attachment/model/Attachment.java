package es.grupo18.urbanpulse.attachment.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import es.grupo18.urbanpulse.entity.enums.ContentType;
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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Esta entidad representa un archivo externo asociado a una Incidencia de UrbanPulse.
 * 
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
    

    // TODO: Añadir la FK con Incidencia 
    //      A momento de escritura no existe Incidencia.
    
    /*@ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Object incident;*/
    
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "uploaded_by", nullable = false)
    private User user;

    //Nombre del archivo
    @Column(name = "original_filename",
        nullable = false, updatable = false, 
        length = 128)
    private String originalFilename;

    //Tipo de contenido
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type",
        nullable = false, updatable = false, length = 20)
    private ContentType contentType;

    //Clave para el recurso
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    //Tamaño en bytes del recurso
    @Min(0)
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    //Tiempos de creación, creado automáticamente.
    @CreationTimestamp 
    @Column(name = "created_at", 
        nullable = false, updatable = false)
    private Instant createdAt;

    //Tiempo de eliminación
    @Column(name = "deleted_at", 
        nullable = true)
    private Instant deletedAt;

    //Descripción de la evidencia
    @Size(max = 300)
    @Column(name = "description",
        length = 300)
    private String description;

    public int getIncident() {
        return 0; //TODO
    }

    /**
     * Factory method to create a new Attachment.
     */
    public static Attachment create(
            User user,
            String originalFilename,
            ContentType contentType,
            String storageKey,
            long sizeBytes,
            String description) {

        Attachment attachment = new Attachment();

        attachment.user = user;
        attachment.originalFilename = originalFilename;
        attachment.contentType = contentType;
        attachment.storageKey = storageKey;
        attachment.sizeBytes = sizeBytes;
        attachment.description = description;

        return attachment;
    }

    public void markAsDeleted() {
        if (this.deletedAt == null) {
            this.deletedAt = Instant.now();
        }
    }
}