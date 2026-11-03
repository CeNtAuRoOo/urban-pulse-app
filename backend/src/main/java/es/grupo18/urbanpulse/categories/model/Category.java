package es.grupo18.urbanpulse.categories.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/**
 * Categoría persistente sin código ni fechas; su UUIDv7 se conserva al
 * desactivarla.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active;

    /** Constructor sin argumentos para el framework. */
    protected Category() {
    }

    /**
     * Inicializa los atributos recibidos; el servicio aplica las reglas de dominio.
     *
     * @param name nombre previamente normalizado y validado
     * @param description descripción opcional, sin recortar
     * @param active estado inicial de disponibilidad
     */
    public Category(String name, String description, boolean active) {
        this.name = name;
        this.description = description;
        this.active = active;
    }

    /** @return identidad estable UUIDv7, o null antes de persistir */
    public UUID getId() {
        return id;
    }

    /** @return nombre normalizado de la categoría */
    public String getName() {
        return name;
    }

    /**
     * Asigna el nombre previamente normalizado y validado por el servicio.
     *
     * @param name nombre que sustituye al actual
     */
    public void setName(String name) {
        this.name = name;
    }

    /** @return descripción opcional, que puede ser null o vacía */
    public String getDescription() {
        return description;
    }

    /**
     * Asigna la descripción sin recortarla; una cadena vacía limpia su contenido.
     *
     * @param description descripción que sustituye a la actual; puede ser null
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /** @return true si la categoría está activa */
    public boolean isActive() {
        return active;
    }

    /**
     * Asigna el estado sin eliminar la categoría.
     *
     * @param active nuevo estado de disponibilidad
     */
    public void setActive(boolean active) {
        this.active = active;
    }
}
