package es.grupo18.urbanpulse.organization.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.UuidGenerator;

/**
 * Unidad municipal mínima. Su administración pertenece al futuro tag Catálogos.
 */
@Entity
@Table(name = "departments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Crea una unidad activa sin introducir un catálogo inicial.
     *
     * @param name        nombre entre 2 y 120 caracteres
     * @param description descripción opcional de hasta 500 caracteres
     * @throws IllegalArgumentException si las longitudes no son válidas
     */
    public Department(String name, String description) {
        if (name == null || name.trim().length() < 2 || name.length() > 120
                || (description != null && description.length() > 500)) {
            throw new IllegalArgumentException("Datos de departamento no válidos.");
        }

        this.name = name.trim();
        this.description = description;
    }
}
