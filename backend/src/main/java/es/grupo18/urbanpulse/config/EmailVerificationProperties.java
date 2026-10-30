package es.grupo18.urbanpulse.config;

import java.util.Base64;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de seguridad y limpieza de las solicitudes de verificación
 * del correo electrónico.
 *
 * @param secret            clave secreta HMAC codificada en Base64
 * @param cleanupBatchSize  número máximo de registros procesados por lote
 * @param cleanupMaxBatches número máximo de lotes procesados por fase de
 *                          limpieza
 */
@Validated
@ConfigurationProperties("security.email-verification")
public record EmailVerificationProperties(@NotBlank String secret, @Min(1) @Max(1000) int cleanupBatchSize,
        @Min(1) @Max(100) int cleanupMaxBatches) {

    /**
     * Decodifica la clave secreta de verificación y comprueba que tenga
     * una longitud mínima de 32 bytes.
     *
     * @return clave secreta decodificada en bytes
     * @throws IllegalArgumentException si la clave no es Base64 válido
     *                                  o contiene menos de 32 bytes
     */
    public byte[] keyBytes() {
        try {
            byte[] key = Base64.getDecoder().decode(secret);
            if (key.length >= 32)
                return key;
        }

        catch (IllegalArgumentException ignored) {
        }

        throw new IllegalArgumentException("La clave de verificación debe ser Base64 de al menos 32 bytes.");
    }

    /**
     * Devuelve una representación de la configuración sin revelar
     * la clave secreta de verificación.
     *
     * @return representación textual con el secreto oculto
     */
    @Override
    public String toString() {
        return "EmailVerificationProperties[secret=REDACTED]";
    }
}
