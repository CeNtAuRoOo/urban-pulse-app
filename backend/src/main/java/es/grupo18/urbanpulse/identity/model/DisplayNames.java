package es.grupo18.urbanpulse.identity.model;

import org.springframework.http.HttpStatus;

import es.grupo18.urbanpulse.shared.error.ApiException;

/**
 * Normalización y límites del nombre público compartidos por registro, perfil y
 * entidad.
 */
public final class DisplayNames {
    /** Impide crear instancias de esta utilidad sin estado. */
    private DisplayNames() {
    }

    /**
     * Recorta espacios Unicode y controles de borde sin cambiar espacios
     * interiores.
     *
     * @param value texto recibido, posiblemente null
     * @return texto recortado, o null cuando no se suministra
     */
    public static String normalize(String value) {
        if (value == null)
            return null;
        int start = 0;
        int end = value.length();
        while (start < end && isEdgeSpace(value.codePointAt(start)))
            start += Character.charCount(value.codePointAt(start));
        while (end > start && isEdgeSpace(value.codePointBefore(end)))
            end -= Character.charCount(value.codePointBefore(end));
        return value.substring(start, end);
    }

    /**
     * Reconoce los espacios de borde con el mismo conjunto documentado para SQL.
     *
     * @param codePoint punto de código Unicode
     * @return true para controles ASCII, espacios y separadores Unicode
     */
    private static boolean isEdgeSpace(int codePoint) {
        return codePoint <= 32 || Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    /**
     * Normaliza y valida antes de modificar el estado de una entidad.
     *
     * @param value nombre solicitado
     * @return nombre de 2–120 puntos de código, sin controles ni sustitutos
     *         aislados
     * @throws ApiException si el nombre incumple las reglas (422 VALIDATION_ERROR)
     */
    public static String requireValid(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.codePointCount(0, normalized.length()) < 2
                || normalized.codePointCount(0, normalized.length()) > 120
                || normalized.codePoints().anyMatch(cp -> Character.isISOControl(cp)
                        || Character.getType(cp) == Character.SURROGATE)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR",
                    "El nombre debe contener entre 2 y 120 caracteres, sin caracteres de control.");
        }
        return normalized;
    }
}
