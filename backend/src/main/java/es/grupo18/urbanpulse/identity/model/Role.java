package es.grupo18.urbanpulse.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
