package es.grupo18.urbanpulse.incidents.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * PATCH con presencia explícita de categoría; null desclasifica, omisión
 * conserva.
 */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class UpdateIncidentRequest {
    private String title;
    private String description;
    private CoordinatesDTO coordinates;
    private UUID categoryId;
    private boolean categoryPresent;

    /**
     * Constructor para Jackson; no expone campos internos como propiedades de
     * entrada.
     */
    public UpdateIncidentRequest() {
    }

    /**
     * Conserva el texto bruto para normalizarlo y validarlo con las reglas del dominio.
     *
     * @param title título presente; null explícito produce 400 antes del servicio
     */
    @JsonSetter(nulls = Nulls.FAIL)
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Recoge la descripción sin confundir su omisión con un borrado.
     * @param description descripción presente; null explícito produce 400
     */
    @JsonSetter(nulls = Nulls.FAIL)
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Recoge una sustitución completa, nunca una modificación parcial de ejes.
     *
     * @param coordinates sustitución completa; omitir precisión la elimina; null
     *                    produce 400
     */
    @JsonSetter(nulls = Nulls.FAIL)
    public void setCoordinates(CoordinatesDTO coordinates) {
        this.coordinates = coordinates;
    }

    /**
     * Marca presencia incluso para null, para conservar la desclasificación explícita.
     * @param categoryId categoría presente; null elimina la clasificación
     */
    @JsonSetter(nulls = Nulls.SET)
    public void setCategoryId(UUID categoryId) {
        this.categoryPresent = true;
        this.categoryId = categoryId;
    }

    /** Texto pendiente de normalizar en el dominio.
     * @return título bruto o null si se omitió */
    public String title() {
        return title;
    }

    /** Texto pendiente de validar en puntos Unicode.
     * @return descripción bruta o null si se omitió */
    public String description() {
        return description;
    }

    /** Sustitución de posición solicitada.
     * @return posición completa o null si se omitió */
    public CoordinatesDTO coordinates() {
        return coordinates;
    }

    /** Identidad que solo se aplica si categoryPresent es true.
     * @return categoría solicitada o null para omisión/desclasificación */
    public UUID categoryId() {
        return categoryId;
    }

    /** Distingue desclasificación explícita de ausencia del campo.
     * @return true incluso si el JSON contiene categoryId:null */
    public boolean categoryPresent() {
        return categoryPresent;
    }

    /** Comprueba presencia, sin prejuzgar si el cambio será efectivo.
     * @return true si existe algún campo permitido presente */
    public boolean hasChanges() {
        return title != null || description != null || coordinates != null || categoryPresent;
    }
}
