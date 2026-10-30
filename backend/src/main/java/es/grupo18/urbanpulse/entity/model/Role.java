package es.grupo18.urbanpulse.entity.model;

import es.grupo18.urbanpulse.entity.enums.RoleCode;
import jakarta.persistence.*;
import lombok.*;

/**
 * Representa un rol del catálogo de permisos de UrbanPulse.
 * Almacena su código identificador y una descripción de su función,
 * incluyendo tanto roles globales como departamentales.
 */
@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RoleCode code;

    @Column(nullable = false, columnDefinition = "text")
    private String description;
}
