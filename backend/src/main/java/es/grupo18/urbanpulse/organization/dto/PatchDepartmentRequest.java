package es.grupo18.urbanpulse.organization.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/** Parche parcial: distingue propiedades omitidas de null explícito y rechaza el objeto vacío. */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class PatchDepartmentRequest {
    @Size(min = 2, max = 120)
    @Pattern(regexp = "(?s).*\\S.*")
    private String name;
    @Size(min = 2, max = 20)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    private String code;
    @Size(max = 500)
    private String description;
    private Boolean active;

    /**
     * Inicializa un parche vacío para deserialización JSON.
     */
    public PatchDepartmentRequest() {
    }

    /**
     * Construye un parche para uso interno; null representa omisión, mientras JSON null se rechaza en los setters.
     *
     * @param name nombre opcional que se recorta
     * @param code código opcional que se recorta
     * @param description descripción opcional, vacía para borrar contenido
     * @param active estado opcional
     */
    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public PatchDepartmentRequest(String name, String code, String description, Boolean active) {
        this.name = name == null ? null : name.trim();
        this.code = code == null ? null : code.trim();
        this.description = description;
        this.active = active;
    }

    /**
     * Recibe una propiedad JSON presente; Jackson rechaza null explícito.
     *
     * @param name nombre no nulo; se recortan los extremos
     */
    @JsonSetter(nulls = Nulls.FAIL)
    public void setName(String name) {
        this.name = name.trim();
    }

    /**
     * Recibe una propiedad JSON presente; Jackson rechaza null explícito.
     *
     * @param code código no nulo; se recortan los extremos
     */
    @JsonSetter(nulls = Nulls.FAIL)
    public void setCode(String code) {
        this.code = code.trim();
    }

    /**
     * Recibe una propiedad JSON presente; Jackson rechaza null explícito.
     *
     * @param description descripción no nula, vacía para borrar contenido
     */
    @JsonSetter(nulls = Nulls.FAIL)
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Recibe una propiedad JSON presente; Jackson rechaza null explícito.
     *
     * @param active estado no nulo; false desactiva sin borrar
     */
    @JsonSetter(nulls = Nulls.FAIL)
    public void setActive(Boolean active) {
        this.active = active;
    }

    /**
     * Proporciona al servicio el campo opcional del parche.
     *
     * @return nombre recortado, o null si se omitió
     */
    public String name() {
        return name;
    }

    /**
     * Proporciona al servicio el campo opcional del parche.
     *
     * @return código recortado, o null si se omitió
     */
    public String code() {
        return code;
    }

    /**
     * Proporciona al servicio el campo opcional del parche.
     *
     * @return descripción presente, o null si se omitió
     */
    public String description() {
        return description;
    }

    /**
     * Proporciona al servicio el campo opcional del parche.
     *
     * @return estado presente, o null si se omitió
     */
    public Boolean active() {
        return active;
    }

    /**
     * Rechaza objetos vacíos mediante Bean Validation.
     *
     * @return true si al menos un campo está presente
     */
    @AssertTrue(message = "Debe proporcionar al menos un campo para actualizar")
    public boolean isNotEmpty() {
        return name != null || code != null || description != null || active != null;
    }
}
