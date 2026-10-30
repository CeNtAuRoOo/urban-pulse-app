package es.grupo18.urbanpulse.config;

import java.util.Base64;
import java.util.List;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de seguridad de los tokens JWT y los orígenes CORS permitidos.
 *
 * @param issuer         identificador del emisor de los tokens
 * @param secret         clave secreta codificada en Base64
 * @param ttlSeconds     duración de los tokens en segundos
 * @param allowedOrigins orígenes autorizados para realizar peticiones CORS
 */
@Validated
@ConfigurationProperties("security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String secret,
        @Min(7200) @Max(7200) long ttlSeconds,
        @NotEmpty List<@NotBlank String> allowedOrigins) {

    /**
     * Decodifica la clave secreta JWT y comprueba su longitud mínima.
     *
     * @return clave secreta decodificada en bytes
     * @throws IllegalArgumentException si la clave no es Base64 válido o contiene
     *                                  menos de 32 bytes
     */
    public byte[] keyBytes() {
        try {
            byte[] key = Base64.getDecoder().decode(secret);

            if (key.length < 32) {
                throw new IllegalArgumentException();
            }

            return key;
        }

        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT secret must be Base64 encoding at least 32 random bytes");
        }
    }

    /**
     * Devuelve una representación de la configuración sin revelar datos sensibles.
     *
     * @return representación textual con los valores ocultos
     */
    @Override
    public String toString() {
        return "JwtProperties[REDACTED]";
    }
}
