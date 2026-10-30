package es.grupo18.urbanpulse.entity.model;

import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.*;
import jakarta.validation.constraints.Min;
import jakarta.persistence.*;
import lombok.*;

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
    private Incident incident;*/
    

   // TODO: Añadir la FK con Usuario
   //       A momento de escritura no se ha mergeado todavía User.

   /*@OneToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "uploaded_by", nullable = false)
    private User user;*/

    @Column(name = "original_filename",
        nullable = false, updatable = false, 
        length = 128)
    private String originalFilename;

    //Clave para el recurso
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    //Tamaño en bytes del recurso
    @Min(0)
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    //Tiempos de creación
    @Column(name = "created_at", 
        nullable = false, updatable = false)
    private Instant createdAt;

    //Tiempo de eliminación
    @Column(name = "deleted_at", 
        nullable = true)
    private Instant deletedAt;

    //Descripción de la evidencia
    @Column(name = "description",
        length = 248)
    private String description;

}