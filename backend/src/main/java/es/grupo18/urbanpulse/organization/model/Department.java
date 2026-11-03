package es.grupo18.urbanpulse.organization.model;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/** Unidad municipal compartida por administración, invitaciones y pertenencias históricas. */
@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Constructor reservado a JPA.
     */
    protected Department() {
    }

    /**
     * Inicializa una unidad normalizando nombre y código, sin precargar catálogos.
     *
     * @param name nombre de 2–120 puntos de código Unicode tras trim
     * @param code código ASCII de 2–20 caracteres, convertido a mayúsculas
     * @param description descripción opcional de hasta 500 puntos de código Unicode
     * @param active estado inicial; false impide emisión y canje
     * @throws IllegalArgumentException si las longitudes o el formato no son válidos
     */
    public Department(String name, String code, String description, boolean active) {
        String normalizedName = name == null ? null : name.trim();
        int nameLength = normalizedName == null ? 0 : normalizedName.codePointCount(0, normalizedName.length());
        if (nameLength < 2 || nameLength > 120
                || code == null || !code.trim().matches("[A-Za-z0-9_-]{2,20}")
                || (description != null && description.codePointCount(0, description.length()) > 500)) {
            throw new IllegalArgumentException("Datos de departamento no válidos.");
        }
        this.name = normalizedName;
        this.code = code.trim().toUpperCase(Locale.ROOT);
        this.description = description;
        this.active = active;
    }

    /**
     * Fija ambas fechas al mismo instante UTC antes de la inserción JPA.
     */
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Actualiza la fecha cuando Hibernate realiza un UPDATE; no controla escrituras SQL externas.
     */
    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Expone el estado persistente compartido por los casos de uso.
     *
     * @return UUIDv7 generado por Hibernate, o null antes de persistir
     */
    public UUID getId() {
        return id;
    }

    /**
     * Expone el estado persistente compartido por los casos de uso.
     *
     * @return nombre normalizado, único por índice SQL
     */
    public String getName() {
        return name;
    }

    /**
     * Aplica el dato bajo la transacción y validaciones del servicio.
     *
     * @param name nombre previamente normalizado y validado
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Expone el estado persistente compartido por los casos de uso.
     *
     * @return código normalizado, único por índice SQL
     */
    public String getCode() {
        return code;
    }

    /**
     * Aplica el dato bajo la transacción y validaciones del servicio.
     *
     * @param code código previamente normalizado y validado
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Expone el estado persistente compartido por los casos de uso.
     *
     * @return descripción opcional; cadena vacía cuando PATCH borra contenido
     */
    public String getDescription() {
        return description;
    }

    /**
     * Aplica el dato bajo la transacción y validaciones del servicio.
     *
     * @param description descripción opcional de hasta 500 caracteres
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Expone el estado persistente compartido por los casos de uso.
     *
     * @return true si admite nuevas invitaciones y canjes
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Aplica el dato bajo la transacción y validaciones del servicio.
     *
     * @param active estado; desactivar conserva pertenencias e historial
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Expone el estado persistente compartido por los casos de uso.
     *
     * @return instante UTC de inserción JPA, inmutable después del alta
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Expone el estado persistente compartido por los casos de uso.
     *
     * @return instante del último cambio persistido por JPA
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
