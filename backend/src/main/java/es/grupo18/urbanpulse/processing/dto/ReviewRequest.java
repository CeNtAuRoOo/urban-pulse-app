package es.grupo18.urbanpulse.processing.dto;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonSetter;

import es.grupo18.urbanpulse.processing.model.ReviewDecision;
import es.grupo18.urbanpulse.shared.error.ApiException;

/**
 * Variantes de revisión con presencia explícita: también se rechazan campos prohibidos nulos.
 */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class ReviewRequest {

    /** Decisión solicitada. */
    private ReviewDecision decision;

    /** Clasificación exclusiva de VALIDATE. */
    private UUID categoryId;

    /** Motivo exclusivo de REJECT/REQUEST_INFORMATION. */
    private String reason;

    /** Versión del agregado, distinta de la versión JWT. */
    private Long expectedIncidentVersion;

    /** Presencia incluso con null. */
    private boolean categoryPresent;

    /** Presencia incluso con null. */
    private boolean reasonPresent;

    /** Constructor de deserialización estricta. */
    public ReviewRequest() {
    }

    /**
     * Recibe decisión solicitada.
     *
     * @param value decisión solicitada
     */
    @JsonSetter
    public void setDecision(ReviewDecision value) {
        decision = value;
    }

    /**
     * Recibe categoría explícita, incluso null.
     *
     * @param value categoría explícita, incluso null
     */
    @JsonSetter
    public void setCategoryId(UUID value) {
        categoryPresent = true;
        categoryId = value;
    }

    /**
     * Recibe motivo explícito, incluso null.
     *
     * @param value motivo explícito, incluso null
     */
    @JsonSetter
    public void setReason(String value) {
        reasonPresent = true;
        reason = value;
    }

    /**
     * Recibe versión esperada.
     *
     * @param value versión esperada
     */
    @JsonSetter
    public void setExpectedIncidentVersion(Long value) {
        expectedIncidentVersion = value;
    }

    /**
     * Devuelve decisión.
     *
     * @return decisión
     */
    public ReviewDecision decision() {
        return decision;
    }

    /**
     * Devuelve categoría.
     *
     * @return categoría
     */
    public UUID categoryId() {
        return categoryId;
    }

    /**
     * Devuelve motivo bruto.
     *
     * @return motivo bruto
     */
    public String reason() {
        return reason;
    }

    /**
     * Devuelve versión esperada.
     *
     * @return versión esperada
     */
    public Long expectedIncidentVersion() {
        return expectedIncidentVersion;
    }

    /**
     * Comprueba la variante antes de aplicar cambios.
     *
     * @throws ApiException 422 si faltan campos o se mezclan variantes
     */
    public void validate() {
        if (decision == null
                || (decision == ReviewDecision.VALIDATE ? categoryId == null || reasonPresent : categoryPresent)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR",
                    "Variante de revisión inválida.");
        }
    }
}
