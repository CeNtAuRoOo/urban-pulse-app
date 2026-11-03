package es.grupo18.urbanpulse.categories.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

import org.hibernate.validator.constraints.CodePointLength;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * Actualización parcial: omitir conserva el valor y null explícito se rechaza
 * al deserializar.
 */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class PatchCategoryRequest {
    @CodePointLength(min = 2, max = 100)
    @Pattern(regexp = "(?s).*\\S.*")
    private String name;
    @CodePointLength(max = 500)
    private String description;
    private Boolean active;

    /** Constructor sin argumentos para el framework. */
    public PatchCategoryRequest() {
    }

    /**
     * Construye un parche desde Java; null representa un campo omitido.
     * Este constructor no se utiliza para deserializar el JSON.
     *
     * @param name nombre que se recorta antes de validar, o null si se omite
     * @param description descripción sin recortar, o null si se omite
     * @param active estado solicitado, o null si se omite
     */
    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public PatchCategoryRequest(String name, String description, Boolean active) {
        this.name = name == null ? null : name.trim();
        this.description = description;
        this.active = active;
    }

    /**
     * Recorta el nombre antes de validar; Jackson rechaza null explícito.
     *
     * @param name nombre no nulo recibido en el JSON
     */
    @JsonSetter(nulls = Nulls.FAIL)
    public void setName(String name) {
        this.name = name.trim();
    }

    /**
     * Asigna la descripción sin recortarla; una cadena vacía limpia su contenido.
     *
     * @param description texto recibido; Jackson rechaza null explícito
     */
    @JsonSetter(nulls = Nulls.FAIL)
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Recoge el cambio de estado sin solicitar el borrado de la categoría.
     *
     * @param active estado recibido; Jackson rechaza null explícito
     */
    @JsonSetter(nulls = Nulls.FAIL)
    public void setActive(Boolean active) {
        this.active = active;
    }

    /** @return nombre normalizado, o null si se omitió */
    public String name() {
        return name;
    }

    /** @return descripción sin recortar, o null si se omitió */
    public String description() {
        return description;
    }

    /** @return estado solicitado, o null si se omitió */
    public Boolean active() {
        return active;
    }

    /**
     * Exige al menos un campo presente; esta propiedad interna no pertenece al JSON
     * de entrada.
     *
     * @return true si hay al menos un atributo que actualizar
     */
    @AssertTrue(message = "Debe proporcionar al menos un campo para actualizar")
    public boolean isNotEmpty() {
        return name != null || description != null || active != null;
    }
}
