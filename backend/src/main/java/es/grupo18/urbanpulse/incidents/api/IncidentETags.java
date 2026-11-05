package es.grupo18.urbanpulse.incidents.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.http.HttpStatus;

import es.grupo18.urbanpulse.incidents.dto.IncidentDTO;
import es.grupo18.urbanpulse.shared.error.ApiException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validador fuerte del detalle completo, incluidos los datos relacionados y la
 * versión JPA. El cliente conserva la etiqueta opaca sin reconstruirla.
 */
public final class IncidentETags {
    private IncidentETags() {
    }

    /**
     * Resume los mismos bytes JSON que serializa MVC con el mapper compartido.
     * No usa hashCode, fechas auxiliares ni el estado interno de las entidades.
     *
     * @param mapper configuración JSON de la aplicación
     * @param detail representación autorizada e inmutable
     * @return SHA-256 hexadecimal entre comillas, precedido por sha256-
     */
    public static String format(JsonMapper mapper, IncidentDTO detail) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(detail));
            return "\"sha256-" + HexFormat.of().formatHex(digest) + "\"";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM.", exception);
        }
    }

    /**
     * Interpreta una única etiqueta fuerte del contrato; no admite listas,
     * wildcard, etiquetas débiles ni el antiguo formato decimal.
     *
     * @param values líneas If-Match; null o vacío representan cabecera ausente
     * @return etiqueta canónica o null; el servicio decide 428 tras autorizar
     * @throws ApiException 400 para sintaxis no admitida
     */
    public static String parse(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String value = values.getFirst().trim();
        if (values.size() != 1 || !value.matches("\"sha256-[0-9a-f]{64}\"")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IF_MATCH",
                    "If-Match debe contener una única ETag fuerte recibida del servidor.");
        }
        return value;
    }
}
