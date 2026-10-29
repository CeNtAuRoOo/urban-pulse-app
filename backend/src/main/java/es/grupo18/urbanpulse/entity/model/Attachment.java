package es.grupo18.urbanpulse.entity.model;

import org.hibernate.annotations.UuidGenerator;

import java.util.*;
import jakarta.persistence.*;
import lombok.*;

/**
 * Esta entidad representa un archivo externo asociado a una Incidencia de UrbanPulse.
 * 
 */

@Entity 
@Table(name = "Attachment") //Como está descrito en el ER
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
}