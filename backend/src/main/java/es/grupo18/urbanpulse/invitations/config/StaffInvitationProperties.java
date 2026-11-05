package es.grupo18.urbanpulse.invitations.config;

import java.util.Base64;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración privada de invitaciones; la rotación invalida códigos
 * pendientes.
 *
 * @param secret  clave Base64 independiente, al menos 32 bytes
 * @param ttlDays días exactos de 24 horas; por defecto siete, rango 1..30
 */
@Validated
@ConfigurationProperties("security.staff-invitation")
public record StaffInvitationProperties(
        @NotBlank String secret,
        @Min(1) @Max(30) int ttlDays) {

    /**
     * Valida y decodifica sin incluir la entrada en errores.
     *
     * @return material HMAC
     * @throws IllegalArgumentException si no es Base64 de al menos 32 bytes
     */
    public byte[] keyBytes() {
        try {
            byte[] bytes = Base64.getDecoder().decode(secret);
            if (bytes.length >= 32)
                return bytes;
        }

        catch (IllegalArgumentException ignored) {
            // Se sustituye el diagnóstico del decodificador para no filtrar la entrada.
        }

        throw new IllegalArgumentException("La clave de invitaciones debe ser Base64 de al menos 32 bytes.");
    }

    /** @return configuración redactada */
    @Override
    public String toString() {
        return "StaffInvitationProperties[secret=REDACTED, ttlDays=" + ttlDays + "]";
    }
}
